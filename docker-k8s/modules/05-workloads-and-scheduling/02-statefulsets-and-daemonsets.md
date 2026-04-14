# 5.2 — StatefulSets, DaemonSets & Jobs

## Concept

Not every workload is a stateless web server that you can freely scale up and down. Databases need stable identities and persistent storage. Log collectors need to run on _every_ node. Batch tasks need to run to completion and then stop. Kubernetes provides specialized workload controllers for each of these patterns — StatefulSets, DaemonSets, Jobs, and CronJobs.

Choosing the wrong workload type is one of the most common mistakes in Kubernetes. Running PostgreSQL as a Deployment with a PersistentVolumeClaim might _seem_ to work, but you'll discover the pain when you try to scale it or when a pod gets rescheduled to a different node and can't access its data. Understanding the guarantees each controller provides is essential.

---

## Deep Dive

### StatefulSets

StatefulSets manage stateful applications that need one or more of these guarantees:

1. **Stable network identity** — Each pod gets a predictable hostname: `<name>-0`, `<name>-1`, `<name>-2`
2. **Ordered deployment and scaling** — Pods are created sequentially (0, 1, 2) and terminated in reverse (2, 1, 0)
3. **Stable persistent storage** — Each pod gets its own PersistentVolumeClaim that survives rescheduling

```
┌─────────────────────────────────────────────────────────────────┐
│                     StatefulSet: postgres                         │
│                                                                  │
│  ┌──────────────────┐  ┌──────────────────┐                     │
│  │  postgres-0       │  │  postgres-1       │                    │
│  │  (primary)        │  │  (replica)         │                    │
│  │                   │  │                    │                    │
│  │  DNS:             │  │  DNS:              │                    │
│  │  postgres-0.      │  │  postgres-1.       │                    │
│  │   postgres.       │  │   postgres.        │                    │
│  │   deployforge.    │  │   deployforge.     │                    │
│  │   svc.cluster.    │  │   svc.cluster.     │                    │
│  │   local           │  │   local            │                    │
│  │                   │  │                    │                    │
│  │  ┌─────────────┐ │  │  ┌─────────────┐  │                    │
│  │  │ PVC: data-  │ │  │  │ PVC: data-  │  │                    │
│  │  │ postgres-0  │ │  │  │ postgres-1  │  │                    │
│  │  │ 10Gi        │ │  │  │ 10Gi        │  │                    │
│  │  └─────────────┘ │  │  └─────────────┘  │                    │
│  └──────────────────┘  └──────────────────┘                     │
│                                                                  │
│  Ordering: postgres-0 starts first → becomes Ready →             │
│            only then postgres-1 starts                           │
│                                                                  │
│  Scaling down: postgres-1 terminates first → only then           │
│                postgres-0 (reverse order)                        │
└─────────────────────────────────────────────────────────────────┘
```

> **Key insight:** When a StatefulSet pod is rescheduled (e.g., node failure), the new pod gets the _same_ name and the _same_ PersistentVolumeClaim. `postgres-0` is always `postgres-0`, no matter which node it runs on. This is what makes StatefulSets suitable for databases — the data follows the identity.

#### StatefulSet YAML

```yaml
apiVersion: apps/v1
kind: StatefulSet
metadata:
  name: postgres
  namespace: deployforge
spec:
  serviceName: postgres              # Required: name of the headless Service
  replicas: 2
  podManagementPolicy: OrderedReady  # OrderedReady (default) | Parallel
  updateStrategy:
    type: RollingUpdate
    rollingUpdate:
      partition: 0                   # Update all pods (partition=N skips pods < N)
  selector:
    matchLabels:
      app: postgres
  template:
    metadata:
      labels:
        app: postgres
    spec:
      terminationGracePeriodSeconds: 60
      containers:
      - name: postgres
        image: postgres:15-alpine
        ports:
        - containerPort: 5432
          name: postgres
        env:
        - name: POSTGRES_USER
          value: "deployforge"
        - name: POSTGRES_PASSWORD
          valueFrom:
            secretKeyRef:
              name: postgres-credentials
              key: password
        - name: POSTGRES_DB
          value: "deployforge_dev"
        - name: PGDATA
          value: "/var/lib/postgresql/data/pgdata"
        volumeMounts:
        - name: data
          mountPath: /var/lib/postgresql/data
        resources:
          requests:
            cpu: "500m"
            memory: "512Mi"
          limits:
            cpu: "500m"
            memory: "512Mi"          # Guaranteed QoS for the database
  volumeClaimTemplates:              # Each pod gets its own PVC
  - metadata:
      name: data
    spec:
      accessModes: ["ReadWriteOnce"]
      resources:
        requests:
          storage: 10Gi
```

#### Headless Services for StatefulSets

A headless Service (`.spec.clusterIP: None`) is required for StatefulSets. It creates DNS records for each pod instead of load-balancing across them.

```yaml
apiVersion: v1
kind: Service
metadata:
  name: postgres
  namespace: deployforge
spec:
  clusterIP: None                    # Headless — no load balancing
  selector:
    app: postgres
  ports:
  - port: 5432
    targetPort: 5432
    name: postgres
```

This creates DNS records:

```
# Individual pod DNS (stable — survives rescheduling)
postgres-0.postgres.deployforge.svc.cluster.local → Pod IP of postgres-0
postgres-1.postgres.deployforge.svc.cluster.local → Pod IP of postgres-1

# Service DNS (returns all pod IPs)
postgres.deployforge.svc.cluster.local → [Pod IP of postgres-0, Pod IP of postgres-1]
```

> **Production note:** For a primary/replica database setup, your application connects to `postgres-0.postgres.deployforge.svc.cluster.local` for writes (the primary) and uses the service DNS `postgres.deployforge.svc.cluster.local` for read replicas. This is a fundamental pattern for any stateful service.

#### StatefulSet Update Strategies

| Strategy | Behavior | Use Case |
|----------|----------|----------|
| `RollingUpdate` (default) | Updates pods in reverse order (N-1 → 0) | Standard updates |
| `RollingUpdate` with `partition: N` | Only updates pods with ordinal ≥ N | Canary updates — update replica first, verify, then primary |
| `OnDelete` | Only updates pods when manually deleted | Full manual control for sensitive databases |

```bash
# Canary-style StatefulSet update:
# 1. Set partition=1 (only update pods >= 1, i.e., replicas)
kubectl patch statefulset postgres -n deployforge \
  -p '{"spec":{"updateStrategy":{"rollingUpdate":{"partition":1}}}}'

# 2. Update the image — only postgres-1 gets the new version
kubectl set image statefulset/postgres postgres=postgres:16-alpine -n deployforge

# 3. Verify postgres-1 works with the new version
# 4. Remove the partition to update postgres-0 (primary)
kubectl patch statefulset postgres -n deployforge \
  -p '{"spec":{"updateStrategy":{"rollingUpdate":{"partition":0}}}}'
```

---

### DaemonSets

A DaemonSet ensures that a copy of a pod runs on every node (or a subset of nodes). When a new node joins the cluster, the DaemonSet controller automatically schedules a pod on it.

```
┌─────────────────────────────────────────────────────────────┐
│                  DaemonSet: nginx-proxy                       │
│                                                             │
│  ┌─────────────────┐  ┌─────────────────┐                  │
│  │  Worker Node 1   │  │  Worker Node 2   │                 │
│  │                  │  │                  │                  │
│  │  ┌────────────┐ │  │  ┌────────────┐ │                  │
│  │  │ nginx-proxy│ │  │  │ nginx-proxy│ │                  │
│  │  │ (auto)     │ │  │  │ (auto)     │ │                  │
│  │  └────────────┘ │  │  └────────────┘ │                  │
│  └─────────────────┘  └─────────────────┘                  │
│                                                             │
│  New node joins → DaemonSet automatically schedules a pod   │
│  Node removed → Pod is garbage collected                    │
└─────────────────────────────────────────────────────────────┘
```

Common DaemonSet use cases:

| Use Case | Example |
|----------|---------|
| Log collection | Fluent Bit, Filebeat |
| Monitoring agent | Prometheus Node Exporter, Datadog Agent |
| Network plugin | Calico, Cilium, kube-proxy |
| Storage driver | CSI node plugin |
| Node-level proxy | Nginx, Envoy (ingress per node) |

#### DaemonSet YAML

```yaml
apiVersion: apps/v1
kind: DaemonSet
metadata:
  name: nginx-proxy
  namespace: deployforge
  labels:
    app: nginx-proxy
spec:
  selector:
    matchLabels:
      app: nginx-proxy
  updateStrategy:
    type: RollingUpdate
    rollingUpdate:
      maxUnavailable: 1             # Update one node at a time
  template:
    metadata:
      labels:
        app: nginx-proxy
    spec:
      tolerations:
      # Allow scheduling on control plane nodes if needed
      - key: node-role.kubernetes.io/control-plane
        operator: Exists
        effect: NoSchedule
      containers:
      - name: nginx
        image: nginx:1.25-alpine
        ports:
        - containerPort: 80
          hostPort: 80              # Bind to host port on each node
        - containerPort: 443
          hostPort: 443
        resources:
          requests:
            cpu: "100m"
            memory: "128Mi"
          limits:
            cpu: "200m"
            memory: "256Mi"
        volumeMounts:
        - name: nginx-config
          mountPath: /etc/nginx/conf.d
      volumes:
      - name: nginx-config
        configMap:
          name: nginx-proxy-config
```

> **Key insight:** DaemonSets bypass the normal scheduler for guaranteed node placement. They use `nodeAffinity` internally (set by the DaemonSet controller) to target specific nodes. You can combine this with `nodeSelector` or `affinity` to run DaemonSets on only a subset of nodes.

---

### Jobs

A Job creates one or more pods and ensures they run to completion. Unlike Deployments, which maintain pods indefinitely, Jobs are for finite tasks.

```yaml
apiVersion: batch/v1
kind: Job
metadata:
  name: db-migration
  namespace: deployforge
spec:
  completions: 1                    # How many pods need to succeed
  parallelism: 1                    # How many pods run concurrently
  backoffLimit: 3                   # Retries before marking as failed
  activeDeadlineSeconds: 300        # Timeout for the entire job
  ttlSecondsAfterFinished: 3600    # Auto-delete 1 hour after completion
  template:
    spec:
      restartPolicy: Never          # Never | OnFailure (not Always)
      containers:
      - name: migrate
        image: deployforge/api-gateway:1.2.0
        command: ['npx', 'prisma', 'migrate', 'deploy']
        env:
        - name: DATABASE_URL
          valueFrom:
            secretKeyRef:
              name: postgres-credentials
              key: url
```

#### Job Patterns

| Pattern | completions | parallelism | Use Case |
|---------|-------------|-------------|----------|
| Single task | 1 | 1 | Database migration, backup |
| Fixed completion count | N | M | Process N items, M at a time |
| Work queue | unset | M | Pods pull work from a queue until it's empty |

```yaml
# Parallel processing: Process 10 items, 3 at a time
apiVersion: batch/v1
kind: Job
metadata:
  name: batch-processor
  namespace: deployforge
spec:
  completions: 10
  parallelism: 3
  backoffLimit: 5
  template:
    spec:
      restartPolicy: OnFailure
      containers:
      - name: processor
        image: deployforge/worker:1.0.0
        command: ['node', 'process-batch.js']
```

```
┌───────────────────────────────────────────────────────────┐
│  Job: batch-processor                                      │
│  completions: 10, parallelism: 3                           │
│                                                           │
│  Time ──────────────────────────────────────────────▶      │
│                                                           │
│  Slot 1: [pod-1 ✓] [pod-4 ✓] [pod-7 ✓] [pod-10 ✓]      │
│  Slot 2: [pod-2 ✓] [pod-5 ✓] [pod-8 ✓]                  │
│  Slot 3: [pod-3 ✓] [pod-6 ✓] [pod-9 ✓]                  │
│                                                           │
│  Status: 10/10 completions — Job succeeded                │
└───────────────────────────────────────────────────────────┘
```

#### restart vs backoffLimit

| `restartPolicy` | Behavior on Failure | Pod Status |
|-----------------|---------------------|------------|
| `Never` | Creates a new pod (old one stays for debugging) | Failed pods remain |
| `OnFailure` | Restarts the container in the same pod | Pod stays, container restarts |

The `backoffLimit` counts retries. When reached, the entire Job is marked as failed. Backoff is exponential: 10s, 20s, 40s, up to 6 minutes.

---

### CronJobs

A CronJob creates Jobs on a schedule, using standard cron syntax.

```yaml
apiVersion: batch/v1
kind: CronJob
metadata:
  name: db-backup
  namespace: deployforge
spec:
  schedule: "0 2 * * *"             # 2:00 AM daily
  timeZone: "America/New_York"      # K8s 1.27+ supports timezone
  concurrencyPolicy: Forbid         # Don't start new if previous still running
  successfulJobsHistoryLimit: 3     # Keep last 3 successful jobs
  failedJobsHistoryLimit: 3         # Keep last 3 failed jobs
  startingDeadlineSeconds: 600      # Skip if can't start within 10 min
  jobTemplate:
    spec:
      backoffLimit: 2
      activeDeadlineSeconds: 1800   # Timeout per job run
      ttlSecondsAfterFinished: 86400
      template:
        spec:
          restartPolicy: OnFailure
          containers:
          - name: backup
            image: postgres:15-alpine
            command:
            - sh
            - -c
            - |
              pg_dump -h postgres-0.postgres.deployforge.svc.cluster.local \
                -U deployforge deployforge_dev | gzip > /backups/$(date +%Y%m%d_%H%M%S).sql.gz
            volumeMounts:
            - name: backups
              mountPath: /backups
          volumes:
          - name: backups
            persistentVolumeClaim:
              claimName: db-backups
```

#### Cron Schedule Syntax

```
┌───────────── minute (0 - 59)
│ ┌───────────── hour (0 - 23)
│ │ ┌───────────── day of month (1 - 31)
│ │ │ ┌───────────── month (1 - 12)
│ │ │ │ ┌───────────── day of week (0 - 6, Sun=0)
│ │ │ │ │
* * * * *
```

| Expression | Meaning |
|------------|---------|
| `*/15 * * * *` | Every 15 minutes |
| `0 * * * *` | Every hour on the hour |
| `0 2 * * *` | Daily at 2:00 AM |
| `0 2 * * 1` | Every Monday at 2:00 AM |
| `0 0 1 * *` | First day of every month at midnight |

#### Concurrency Policies

| Policy | Behavior |
|--------|----------|
| `Allow` (default) | Multiple jobs can run concurrently |
| `Forbid` | Skip the new run if previous is still running |
| `Replace` | Cancel the running job and start a new one |

> **Production note:** For database backups, always use `concurrencyPolicy: Forbid`. Overlapping `pg_dump` runs can cause lock contention and performance degradation. For cleanup tasks, `Replace` is often appropriate.

---

### TTL Controller

The TTL-after-finished controller automatically cleans up completed Jobs. Without it, finished Job pods accumulate in the cluster.

```yaml
spec:
  ttlSecondsAfterFinished: 3600   # Delete 1 hour after completion
```

```bash
# Check for stale completed jobs
kubectl get jobs -n deployforge --field-selector status.successful=1
```

---

## Code Examples

### Example 1: Deploy PostgreSQL as StatefulSet

```bash
# Create the headless service
cat <<'EOF' | kubectl apply -f -
apiVersion: v1
kind: Service
metadata:
  name: postgres
  namespace: deployforge
spec:
  clusterIP: None
  selector:
    app: postgres
  ports:
  - port: 5432
    targetPort: 5432
    name: postgres
EOF

# Create the StatefulSet
cat <<'EOF' | kubectl apply -f -
apiVersion: apps/v1
kind: StatefulSet
metadata:
  name: postgres
  namespace: deployforge
spec:
  serviceName: postgres
  replicas: 1
  selector:
    matchLabels:
      app: postgres
  template:
    metadata:
      labels:
        app: postgres
    spec:
      containers:
      - name: postgres
        image: postgres:15-alpine
        ports:
        - containerPort: 5432
        env:
        - name: POSTGRES_USER
          value: "deployforge"
        - name: POSTGRES_PASSWORD
          value: "password"
        - name: POSTGRES_DB
          value: "deployforge_dev"
        - name: PGDATA
          value: "/var/lib/postgresql/data/pgdata"
        volumeMounts:
        - name: data
          mountPath: /var/lib/postgresql/data
        resources:
          requests:
            cpu: "250m"
            memory: "256Mi"
          limits:
            cpu: "250m"
            memory: "256Mi"
  volumeClaimTemplates:
  - metadata:
      name: data
    spec:
      accessModes: ["ReadWriteOnce"]
      resources:
        requests:
          storage: 1Gi
EOF

# Verify
kubectl get statefulset,pods,pvc -n deployforge -l app=postgres
# → StatefulSet: 1/1 ready
# → Pod: postgres-0 Running
# → PVC: data-postgres-0 Bound
```

### Example 2: Run a Database Migration Job

```bash
cat <<'EOF' | kubectl apply -f -
apiVersion: batch/v1
kind: Job
metadata:
  name: db-seed
  namespace: deployforge
spec:
  backoffLimit: 2
  ttlSecondsAfterFinished: 300
  template:
    spec:
      restartPolicy: Never
      containers:
      - name: seed
        image: postgres:15-alpine
        command:
        - sh
        - -c
        - |
          until pg_isready -h postgres-0.postgres.deployforge.svc.cluster.local -U deployforge; do
            echo "Waiting for PostgreSQL..."
            sleep 2
          done
          psql -h postgres-0.postgres.deployforge.svc.cluster.local \
            -U deployforge -d deployforge_dev -c "
            CREATE TABLE IF NOT EXISTS deployments (
              id SERIAL PRIMARY KEY,
              name VARCHAR(255) NOT NULL,
              status VARCHAR(50) DEFAULT 'pending',
              created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
            );
            INSERT INTO deployments (name, status) VALUES ('initial-deploy', 'completed');
          "
        env:
        - name: PGPASSWORD
          value: "password"
EOF

# Watch the job
kubectl get jobs -n deployforge -w
# → db-seed  1/1  Completed
```

### Example 3: DaemonSet Status Check

```bash
# Check DaemonSet status
kubectl get daemonset -n deployforge
# NAME          DESIRED   CURRENT   READY   UP-TO-DATE   AVAILABLE   NODE SELECTOR   AGE
# nginx-proxy   2         2         2       2            2           <none>          5m

# DESIRED = number of nodes matching the selector
# CURRENT = pods that exist
# READY = pods that passed readiness checks
# UP-TO-DATE = pods running the latest template

# List which node each DaemonSet pod runs on
kubectl get pods -n deployforge -l app=nginx-proxy -o wide
```

---

## Try It Yourself

### Challenge 1: Redis StatefulSet

Deploy Redis as a StatefulSet in the DeployForge namespace with:
- 1 replica
- Headless service on port 6379
- A `volumeClaimTemplate` for data persistence (1Gi)
- Guaranteed QoS (requests == limits: 100m CPU, 128Mi memory)
- Verify you can connect and write data

<details>
<summary>Show solution</summary>

```bash
#!/bin/bash
set -euo pipefail

echo "=== Creating Redis headless service ==="
cat <<'EOF' | kubectl apply -f -
apiVersion: v1
kind: Service
metadata:
  name: redis
  namespace: deployforge
spec:
  clusterIP: None
  selector:
    app: redis
  ports:
  - port: 6379
    targetPort: 6379
    name: redis
EOF

echo ""
echo "=== Creating Redis StatefulSet ==="
cat <<'EOF' | kubectl apply -f -
apiVersion: apps/v1
kind: StatefulSet
metadata:
  name: redis
  namespace: deployforge
spec:
  serviceName: redis
  replicas: 1
  selector:
    matchLabels:
      app: redis
  template:
    metadata:
      labels:
        app: redis
    spec:
      containers:
      - name: redis
        image: redis:7-alpine
        ports:
        - containerPort: 6379
        command: ['redis-server', '--appendonly', 'yes', '--dir', '/data']
        volumeMounts:
        - name: data
          mountPath: /data
        resources:
          requests:
            cpu: "100m"
            memory: "128Mi"
          limits:
            cpu: "100m"
            memory: "128Mi"
  volumeClaimTemplates:
  - metadata:
      name: data
    spec:
      accessModes: ["ReadWriteOnce"]
      resources:
        requests:
          storage: 1Gi
EOF

echo ""
echo "=== Waiting for Redis to be ready ==="
kubectl rollout status statefulset/redis -n deployforge --timeout=60s

echo ""
echo "=== Verifying ==="
kubectl get statefulset,pods,pvc -n deployforge -l app=redis

echo ""
echo "=== Testing Redis connection ==="
kubectl exec -n deployforge redis-0 -- redis-cli SET deployforge:test "hello from StatefulSet"
kubectl exec -n deployforge redis-0 -- redis-cli GET deployforge:test
# → "hello from StatefulSet"

echo ""
echo "=== Verifying QoS class ==="
kubectl get pod redis-0 -n deployforge -o jsonpath='{.status.qosClass}'
echo ""
# → Guaranteed
```

</details>

### Challenge 2: Database Backup CronJob

Create a CronJob that runs every hour, dumps the DeployForge PostgreSQL database, and stores the backup. Requirements:
- Schedule: every hour on the hour
- Concurrency policy: Forbid
- Backoff limit: 2
- Keep last 3 successful jobs, 1 failed job
- TTL: delete 2 hours after completion

<details>
<summary>Show solution</summary>

```yaml
# db-backup-cronjob.yaml
apiVersion: batch/v1
kind: CronJob
metadata:
  name: db-backup
  namespace: deployforge
spec:
  schedule: "0 * * * *"
  concurrencyPolicy: Forbid
  successfulJobsHistoryLimit: 3
  failedJobsHistoryLimit: 1
  startingDeadlineSeconds: 300
  jobTemplate:
    spec:
      backoffLimit: 2
      activeDeadlineSeconds: 600
      ttlSecondsAfterFinished: 7200
      template:
        spec:
          restartPolicy: OnFailure
          containers:
          - name: backup
            image: postgres:15-alpine
            command:
            - sh
            - -c
            - |
              echo "Starting backup at $(date)"
              pg_dump -h postgres-0.postgres.deployforge.svc.cluster.local \
                -U deployforge deployforge_dev > /dev/null
              echo "Backup completed at $(date)"
            env:
            - name: PGPASSWORD
              value: "password"
            resources:
              requests:
                cpu: "100m"
                memory: "128Mi"
              limits:
                cpu: "200m"
                memory: "256Mi"
```

```bash
#!/bin/bash
set -euo pipefail

kubectl apply -f db-backup-cronjob.yaml

echo "=== CronJob created ==="
kubectl get cronjob -n deployforge

# Manually trigger a job to test
echo ""
echo "=== Triggering manual run ==="
kubectl create job --from=cronjob/db-backup db-backup-manual -n deployforge

# Watch the job
kubectl wait --for=condition=complete job/db-backup-manual -n deployforge --timeout=60s

echo ""
echo "=== Job completed ==="
kubectl get jobs -n deployforge
kubectl logs job/db-backup-manual -n deployforge
```

</details>

---

## Capstone Connection

**DeployForge** uses every workload type covered in this section:

- **PostgreSQL StatefulSet** — The database requires stable network identity (`postgres-0`) for primary/replica patterns, ordered deployment (primary starts before replicas), and persistent storage that survives rescheduling. The headless service provides stable DNS for connection strings.
- **Redis StatefulSet** — While Redis can be run as a Deployment for simple caching, DeployForge uses a StatefulSet for data persistence (the BullMQ job queue state must survive restarts). In Module 09 (Reliability Engineering), you'll add Redis Sentinel for automatic failover.
- **Worker Deployment** — BullMQ workers are stateless (they pull jobs from Redis) so they run as a standard Deployment. In Module 12 (Scaling & Cost), you'll configure Horizontal Pod Autoscaler to scale workers based on queue depth.
- **Nginx DaemonSet** — The reverse proxy runs on every worker node to handle incoming traffic. Each pod uses `hostPort` to bind to the node's port 80/443, ensuring traffic reaches Nginx regardless of which node it hits.
- **Database migration Job** — Schema migrations run as Jobs with `backoffLimit: 3` to handle transient connection failures. Init containers in the API Gateway wait for the migration Job to complete before starting the application.
- **Database backup CronJob** — Scheduled backups run hourly with `concurrencyPolicy: Forbid` to prevent overlapping `pg_dump` processes. In Module 09, you'll enhance this with backup verification and off-cluster storage.
