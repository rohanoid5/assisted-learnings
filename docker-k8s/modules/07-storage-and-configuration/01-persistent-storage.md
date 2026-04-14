# 7.1 — PersistentVolumes, PVCs & StorageClasses

## Concept

Containers are ephemeral — when a pod dies, its filesystem dies with it. That's a feature for stateless workloads, but a catastrophe for databases, message queues, and anything that needs to survive restarts. Kubernetes solves this with a three-layer storage abstraction: **PersistentVolumes** (PVs) represent actual storage, **PersistentVolumeClaims** (PVCs) are requests for storage, and **StorageClasses** define _how_ storage is dynamically provisioned. Understanding this lifecycle — and its failure modes — is critical for running stateful workloads in production.

The model is deliberately decoupled: cluster administrators provision storage classes and policies, while application developers simply claim storage by size and access mode. This separation of concerns is what makes Kubernetes storage portable across clouds, on-prem, and hybrid environments.

---

## Deep Dive

### The PV → PVC → Pod Binding Lifecycle

The storage lifecycle has distinct phases. Understanding each phase — and what can go wrong at each — is essential for debugging.

```
┌─────────────────────────────────────────────────────────────────────┐
│                PersistentVolume Lifecycle                             │
│                                                                     │
│  1. PROVISIONING                                                     │
│     Admin creates PV (static)  ─or─  StorageClass provisions (dynamic)│
│     ┌──────────┐                                                    │
│     │ PV: 10Gi  │  Status: Available                                 │
│     │ RWO       │  No claim bound yet                                │
│     └──────────┘                                                    │
│         │                                                            │
│  2. BINDING                                                          │
│     PVC requests storage → control plane matches PV                  │
│     ┌──────────┐     ┌──────────┐                                   │
│     │ PVC       │────▶│ PV        │  Status: Bound                   │
│     │ req: 5Gi  │     │ cap: 10Gi │  PVC → PV is 1:1                │
│     └──────────┘     └──────────┘                                   │
│         │                                                            │
│  3. USING                                                            │
│     Pod mounts PVC as a volume                                       │
│     ┌──────────┐     ┌──────────┐     ┌──────────┐                  │
│     │ Pod       │────▶│ PVC       │────▶│ PV        │                │
│     │ /data     │     │ Bound     │     │ Bound     │                │
│     └──────────┘     └──────────┘     └──────────┘                  │
│         │                                                            │
│  4. RECLAIMING                                                       │
│     Pod deleted → PVC deleted → what happens to PV?                  │
│     ┌──────────────────────────────────────────────────────┐        │
│     │  Retain   → PV stays, data preserved, manual cleanup  │        │
│     │  Delete   → PV and backing storage are destroyed       │        │
│     │  Recycle  → (deprecated) PV is scrubbed and reused    │        │
│     └──────────────────────────────────────────────────────┘        │
└─────────────────────────────────────────────────────────────────────┘
```

> **Key insight:** In production, always use `Retain` for databases. The `Delete` policy is convenient for ephemeral workloads but will destroy your data when a PVC is deleted — including accidental deletions via `kubectl delete -f`.

---

### Access Modes

Access modes define how a volume can be mounted. The mode you choose constrains which workload patterns are possible:

| Mode | Short | Description | Use Case |
|------|-------|-------------|----------|
| ReadWriteOnce | RWO | One node mounts read-write | Databases (PostgreSQL, MySQL) |
| ReadOnlyMany | ROX | Many nodes mount read-only | Shared config, static assets |
| ReadWriteMany | RWX | Many nodes mount read-write | Shared file uploads, CMS media |
| ReadWriteOncePod | RWOP | Single pod mounts read-write | Strict single-writer guarantees (K8s 1.27+) |

```
┌────────────────────────────────────────────────────────────────────┐
│                      Access Mode Comparison                         │
│                                                                    │
│  RWO (ReadWriteOnce)              RWX (ReadWriteMany)              │
│  ┌──── Node 1 ──────┐            ┌──── Node 1 ──────┐             │
│  │  ┌─────────────┐  │            │  ┌─────────────┐  │             │
│  │  │ postgres-0   │  │            │  │ web-server-0 │  │             │
│  │  │ RW mount     │  │            │  │ RW mount     │  │             │
│  │  └──────┬──────┘  │            │  └──────┬──────┘  │             │
│  │         │         │            │         │         │             │
│  │         ▼         │            │         ▼         │             │
│  │  ┌─────────────┐  │            │  ┌─────────────┐  │             │
│  │  │  PV: 10Gi   │  │            │  │  NFS Share   │  │             │
│  │  │  (block)    │  │            │  │  (network)   │  │             │
│  │  └─────────────┘  │            │  └──────┬──────┘  │             │
│  └───────────────────┘            └─────────┼────────┘             │
│  ┌──── Node 2 ──────┐                      │                      │
│  │  Pod cannot mount │            ┌──── Node 2 ──────┐             │
│  │  this PV ✗        │            │  ┌─────────────┐  │             │
│  └───────────────────┘            │  │ web-server-1 │  │             │
│                                   │  │ RW mount ✓   │  │             │
│  Only one node at a time.         │  └──────┬──────┘  │             │
│  Pods on the SAME node can        │         ▼         │             │
│  share the mount (use RWOP        │  Same NFS share   │             │
│  if you need pod-level            │  mounted on both  │             │
│  exclusivity).                    │  nodes.            │             │
│                                   └───────────────────┘             │
└────────────────────────────────────────────────────────────────────┘
```

> **Warning:** Not all storage backends support all access modes. EBS volumes are RWO only. NFS supports RWX. Always check your CSI driver's capabilities.

---

### Static vs Dynamic Provisioning

**Static provisioning** means a cluster admin manually creates PV objects. The control plane matches PVCs to available PVs based on size, access mode, and StorageClass.

**Dynamic provisioning** means the StorageClass creates PVs automatically when a PVC is submitted. This is the production standard — no one manually provisions volumes in a 500-node cluster.

```yaml
# Static PV — admin creates this ahead of time
apiVersion: v1
kind: PersistentVolume
metadata:
  name: postgres-pv-manual
spec:
  capacity:
    storage: 20Gi
  accessModes:
    - ReadWriteOnce
  persistentVolumeReclaimPolicy: Retain
  storageClassName: manual
  hostPath:
    path: /mnt/data/postgres    # Only for dev/kind — never in production
```

```yaml
# PVC that binds to the static PV above
apiVersion: v1
kind: PersistentVolumeClaim
metadata:
  name: postgres-data
  namespace: deployforge
spec:
  accessModes:
    - ReadWriteOnce
  storageClassName: manual
  resources:
    requests:
      storage: 20Gi
```

---

### StorageClasses and Dynamic Provisioning

A StorageClass is the policy layer between "I need storage" and "here's actual storage." It defines the provisioner (which CSI driver), parameters (disk type, IOPS, encryption), and reclaim policy.

```yaml
# StorageClass for fast SSD-backed volumes
apiVersion: storage.k8s.io/v1
kind: StorageClass
metadata:
  name: fast-ssd
provisioner: ebs.csi.aws.com        # AWS EBS CSI driver
parameters:
  type: gp3
  iopsPerGB: "50"
  encrypted: "true"
  kmsKeyId: "arn:aws:kms:us-east-1:123456789:key/abc-123"
reclaimPolicy: Retain
allowVolumeExpansion: true           # Allow PVC resize without data loss
volumeBindingMode: WaitForFirstConsumer  # Don't bind until pod is scheduled
mountOptions:
  - noatime                          # Performance optimization
```

```yaml
# PVC using the StorageClass — PV created automatically
apiVersion: v1
kind: PersistentVolumeClaim
metadata:
  name: postgres-data
  namespace: deployforge
spec:
  accessModes:
    - ReadWriteOnce
  storageClassName: fast-ssd
  resources:
    requests:
      storage: 50Gi
```

> **Production tip:** Always set `volumeBindingMode: WaitForFirstConsumer`. The default `Immediate` mode provisions the volume before the pod is scheduled, which can create zone-mismatch problems where the PV is in `us-east-1a` but the pod lands in `us-east-1b`.

---

### CSI Drivers

The Container Storage Interface (CSI) is a standard that lets Kubernetes communicate with any storage backend. CSI drivers run as DaemonSets and handle provisioning, attaching, mounting, snapshotting, and resizing.

```
┌────────────────────────────────────────────────────────────────────┐
│                     CSI Architecture                                │
│                                                                    │
│  ┌──── Control Plane ────────────────────────────────────────────┐ │
│  │                                                                │ │
│  │  PVC ──▶ PV Controller ──▶ CSI Controller (Deployment)         │ │
│  │                              │                                 │ │
│  │                              │ CreateVolume()                  │ │
│  │                              ▼                                 │ │
│  │                     ┌─────────────────────┐                    │ │
│  │                     │ Cloud Provider API    │                    │ │
│  │                     │ (AWS EBS / GCP PD /   │                    │ │
│  │                     │  Azure Disk)          │                    │ │
│  │                     └─────────────────────┘                    │ │
│  └────────────────────────────────────────────────────────────────┘ │
│                                                                    │
│  ┌──── Worker Node ──────────────────────────────────────────────┐ │
│  │                                                                │ │
│  │  Pod ──▶ kubelet ──▶ CSI Node Plugin (DaemonSet)               │ │
│  │                        │                                       │ │
│  │                        │ NodeStageVolume()                     │ │
│  │                        │ NodePublishVolume()                   │ │
│  │                        ▼                                       │ │
│  │                   mount /dev/xvdf → /var/lib/kubelet/pods/...  │ │
│  │                                                                │ │
│  └────────────────────────────────────────────────────────────────┘ │
└────────────────────────────────────────────────────────────────────┘
```

Common CSI drivers in production:

| Provider | CSI Driver | Storage Type |
|----------|-----------|--------------|
| AWS | `ebs.csi.aws.com` | EBS block volumes |
| AWS | `efs.csi.aws.com` | EFS (NFS-like, RWX) |
| GCP | `pd.csi.storage.gke.io` | Persistent Disk |
| Azure | `disk.csi.azure.com` | Azure Managed Disk |
| On-prem | `ceph.rook.io` | Ceph (block + file) |
| Any | `nfs.csi.k8s.io` | NFS shares |

---

### Volume Snapshots

VolumeSnapshots let you create point-in-time copies of PVCs — essential for backups, migration, and disaster recovery. They're a CSI-level feature, so your driver must support them.

```yaml
# VolumeSnapshotClass — like StorageClass but for snapshots
apiVersion: snapshot.storage.k8s.io/v1
kind: VolumeSnapshotClass
metadata:
  name: ebs-snapshot-class
driver: ebs.csi.aws.com
deletionPolicy: Retain              # Keep snapshot even if VolumeSnapshot CR is deleted
parameters:
  tagSpecification_1: "Team=deployforge"

---
# Take a snapshot of the PostgreSQL PVC
apiVersion: snapshot.storage.k8s.io/v1
kind: VolumeSnapshot
metadata:
  name: postgres-snapshot-20240115
  namespace: deployforge
spec:
  volumeSnapshotClassName: ebs-snapshot-class
  source:
    persistentVolumeClaimName: postgres-data

---
# Restore: create a new PVC from the snapshot
apiVersion: v1
kind: PersistentVolumeClaim
metadata:
  name: postgres-data-restored
  namespace: deployforge
spec:
  accessModes:
    - ReadWriteOnce
  storageClassName: fast-ssd
  resources:
    requests:
      storage: 50Gi
  dataSource:
    name: postgres-snapshot-20240115
    kind: VolumeSnapshot
    apiGroup: snapshot.storage.k8s.io
```

```bash
# Verify snapshot status
kubectl get volumesnapshot -n deployforge
# NAME                          READYTOUSE   RESTORESIZE   AGE
# postgres-snapshot-20240115    true         50Gi          5m

# Describe for detailed status
kubectl describe volumesnapshot postgres-snapshot-20240115 -n deployforge
```

---

### Backup Strategies for Stateful Workloads

VolumeSnapshots are one piece of the puzzle. A production backup strategy includes:

```
┌─────────────────────────────────────────────────────────────────────┐
│               Backup Strategy — Defense in Depth                     │
│                                                                     │
│  Layer 1: Application-Level Backups                                  │
│  ┌──────────────────────────────────────────────────────────────┐   │
│  │ pg_dump / pg_basebackup → S3 bucket                          │   │
│  │ Redis BGSAVE → persistent volume → snapshot                  │   │
│  │ CronJob runs nightly, retains 7 daily + 4 weekly             │   │
│  └──────────────────────────────────────────────────────────────┘   │
│                                                                     │
│  Layer 2: Volume Snapshots (CSI)                                     │
│  ┌──────────────────────────────────────────────────────────────┐   │
│  │ VolumeSnapshot CronJob → point-in-time PV copies             │   │
│  │ Faster than app-level, but crash-consistent (not app-aware)  │   │
│  │ Good for quick recovery; not a substitute for pg_dump        │   │
│  └──────────────────────────────────────────────────────────────┘   │
│                                                                     │
│  Layer 3: Cross-Region Replication                                   │
│  ┌──────────────────────────────────────────────────────────────┐   │
│  │ AWS: EBS snapshot → copy to another region                    │   │
│  │ GCP: PD snapshot → replicate to another region                │   │
│  │ For disaster recovery: RPO < 1 hour                          │   │
│  └──────────────────────────────────────────────────────────────┘   │
│                                                                     │
│  Layer 4: Velero (Cluster-Level Backup)                              │
│  ┌──────────────────────────────────────────────────────────────┐   │
│  │ velero backup create --include-namespaces deployforge         │   │
│  │ Backs up both K8s resources AND PV data                       │   │
│  │ Can restore entire namespace to a new cluster                 │   │
│  └──────────────────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────────────────┘
```

---

### StatefulSets and volumeClaimTemplates

StatefulSets have first-class PVC integration via `volumeClaimTemplates`. Each pod gets its own PVC with a predictable name:

```yaml
apiVersion: apps/v1
kind: StatefulSet
metadata:
  name: postgres
  namespace: deployforge
spec:
  serviceName: postgres-headless
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
        - name: PGDATA
          value: /var/lib/postgresql/data/pgdata
        volumeMounts:
        - name: postgres-data
          mountPath: /var/lib/postgresql/data
  volumeClaimTemplates:
  - metadata:
      name: postgres-data
    spec:
      accessModes: ["ReadWriteOnce"]
      storageClassName: fast-ssd
      resources:
        requests:
          storage: 20Gi
```

This creates a PVC named `postgres-data-postgres-0` (template name + STS name + ordinal). If the pod is rescheduled to another node, the PVC follows — Kubernetes ensures the same volume is reattached.

```bash
# Check PVCs created by StatefulSet
kubectl get pvc -n deployforge
# NAME                       STATUS   VOLUME                                     CAPACITY   ACCESS MODES   STORAGECLASS
# postgres-data-postgres-0   Bound    pvc-abc12345-6789-...                      20Gi       RWO            fast-ssd

# The PVC survives pod deletion — this is by design
kubectl delete pod postgres-0 -n deployforge
kubectl get pvc -n deployforge  # Still there!
```

> **Warning:** Deleting a StatefulSet does NOT delete its PVCs. This is intentional — it prevents accidental data loss. You must delete PVCs manually: `kubectl delete pvc postgres-data-postgres-0 -n deployforge`.

---

### Resizing PersistentVolumeClaims

If your StorageClass has `allowVolumeExpansion: true`, you can resize PVCs without downtime (for most CSI drivers):

```bash
# Edit the PVC to increase storage
kubectl patch pvc postgres-data-postgres-0 -n deployforge \
  -p '{"spec": {"resources": {"requests": {"storage": "50Gi"}}}}'

# Check resize status
kubectl describe pvc postgres-data-postgres-0 -n deployforge
# Conditions:
#   Type                      Status
#   FileSystemResizePending   True     ← needs pod restart for filesystem resize

# For block-mode CSI drivers, the pod must be restarted for the filesystem
# to be expanded. Delete the pod — the StatefulSet recreates it:
kubectl delete pod postgres-0 -n deployforge

# Verify new size
kubectl exec -n deployforge postgres-0 -- df -h /var/lib/postgresql/data
```

> **Caution:** You can only _expand_ PVCs, never shrink. Plan your initial size carefully or use monitoring to auto-resize before hitting capacity.

---

## Code Examples

### Complete Storage Setup for a Kind Cluster

```yaml
# storage-setup.yaml — for local development with kind
---
# StorageClass using the local-path provisioner (kind default)
apiVersion: storage.k8s.io/v1
kind: StorageClass
metadata:
  name: deployforge-fast
provisioner: rancher.io/local-path    # kind's built-in provisioner
reclaimPolicy: Retain
volumeBindingMode: WaitForFirstConsumer

---
# PostgreSQL PVC
apiVersion: v1
kind: PersistentVolumeClaim
metadata:
  name: postgres-data-manual
  namespace: deployforge
spec:
  accessModes:
    - ReadWriteOnce
  storageClassName: deployforge-fast
  resources:
    requests:
      storage: 10Gi

---
# Redis PVC
apiVersion: v1
kind: PersistentVolumeClaim
metadata:
  name: redis-data-manual
  namespace: deployforge
spec:
  accessModes:
    - ReadWriteOnce
  storageClassName: deployforge-fast
  resources:
    requests:
      storage: 5Gi
```

```bash
# Apply and verify
kubectl apply -f storage-setup.yaml
kubectl get sc
kubectl get pvc -n deployforge
kubectl describe pvc postgres-data-manual -n deployforge
```

---

### Debugging Storage Issues

```bash
# PVC stuck in Pending? Check events:
kubectl describe pvc postgres-data-manual -n deployforge
# Look for: "no persistent volumes available for this claim"
# Common causes:
#   1. StorageClass doesn't exist
#   2. No provisioner for the StorageClass
#   3. Size/access mode mismatch
#   4. WaitForFirstConsumer — PV won't provision until pod is scheduled

# Check StorageClass provisioner is running:
kubectl get pods -n kube-system -l app=local-path-provisioner

# Check PV → PVC binding:
kubectl get pv -o custom-columns=\
'NAME:.metadata.name,CAPACITY:.spec.capacity.storage,STATUS:.status.phase,CLAIM:.spec.claimRef.name'

# Check volume mount inside pod:
kubectl exec -n deployforge postgres-0 -- mount | grep /var/lib/postgresql
kubectl exec -n deployforge postgres-0 -- df -h /var/lib/postgresql/data
```

---

## Try It Yourself

### Challenge 1: Trace the Full PV Lifecycle

Create a PVC, bind it to a pod, write data, delete the pod, recreate it, and verify the data persists. Then delete the PVC and observe what happens to the PV based on the reclaim policy.

<details>
<summary>Show solution</summary>

```bash
#!/bin/bash
set -euo pipefail

NS="deployforge"

echo "=== Step 1: Create PVC ==="
cat <<'EOF' | kubectl apply -f -
apiVersion: v1
kind: PersistentVolumeClaim
metadata:
  name: lifecycle-test
  namespace: deployforge
spec:
  accessModes: ["ReadWriteOnce"]
  storageClassName: standard
  resources:
    requests:
      storage: 1Gi
EOF

echo ""
echo "=== Step 2: Create pod that mounts the PVC ==="
cat <<'EOF' | kubectl apply -f -
apiVersion: v1
kind: Pod
metadata:
  name: pv-writer
  namespace: deployforge
spec:
  containers:
  - name: writer
    image: busybox:1.36
    command: ['sh', '-c', 'echo "PV lifecycle test — data written at $(date)" > /data/test.txt && sleep 3600']
    volumeMounts:
    - name: data
      mountPath: /data
  volumes:
  - name: data
    persistentVolumeClaim:
      claimName: lifecycle-test
EOF

kubectl wait --for=condition=Ready pod/pv-writer -n $NS --timeout=60s

echo ""
echo "=== Step 3: Verify data was written ==="
kubectl exec -n $NS pv-writer -- cat /data/test.txt

echo ""
echo "=== Step 4: Delete pod (PVC survives) ==="
kubectl delete pod pv-writer -n $NS --grace-period=0
kubectl get pvc lifecycle-test -n $NS
# → Still Bound

echo ""
echo "=== Step 5: Recreate pod — data persists ==="
cat <<'EOF' | kubectl apply -f -
apiVersion: v1
kind: Pod
metadata:
  name: pv-reader
  namespace: deployforge
spec:
  containers:
  - name: reader
    image: busybox:1.36
    command: ['sh', '-c', 'cat /data/test.txt && sleep 3600']
    volumeMounts:
    - name: data
      mountPath: /data
  volumes:
  - name: data
    persistentVolumeClaim:
      claimName: lifecycle-test
EOF

kubectl wait --for=condition=Ready pod/pv-reader -n $NS --timeout=60s
kubectl logs pv-reader -n $NS
# → Shows the original timestamp — data persisted!

echo ""
echo "=== Cleanup ==="
kubectl delete pod pv-reader -n $NS --grace-period=0
PV_NAME=$(kubectl get pvc lifecycle-test -n $NS -o jsonpath='{.spec.volumeName}')
kubectl delete pvc lifecycle-test -n $NS

echo "PV $PV_NAME status after PVC deletion:"
kubectl get pv "$PV_NAME" 2>/dev/null || echo "PV was deleted (Delete reclaim policy)"
```

</details>

### Challenge 2: Create a Multi-Volume StatefulSet

Create a StatefulSet with two separate PVCs — one for data and one for logs. Verify that each pod gets its own pair of volumes.

<details>
<summary>Show solution</summary>

```yaml
# multi-volume-sts.yaml
apiVersion: apps/v1
kind: StatefulSet
metadata:
  name: multi-vol-test
  namespace: deployforge
spec:
  serviceName: multi-vol-test
  replicas: 2
  selector:
    matchLabels:
      app: multi-vol-test
  template:
    metadata:
      labels:
        app: multi-vol-test
    spec:
      containers:
      - name: app
        image: busybox:1.36
        command: ['sh', '-c', 'echo "data" > /data/test.txt && echo "log" > /logs/test.txt && sleep 3600']
        volumeMounts:
        - name: data
          mountPath: /data
        - name: logs
          mountPath: /logs
  volumeClaimTemplates:
  - metadata:
      name: data
    spec:
      accessModes: ["ReadWriteOnce"]
      storageClassName: standard
      resources:
        requests:
          storage: 1Gi
  - metadata:
      name: logs
    spec:
      accessModes: ["ReadWriteOnce"]
      storageClassName: standard
      resources:
        requests:
          storage: 512Mi
```

```bash
kubectl apply -f multi-volume-sts.yaml
kubectl get pvc -n deployforge -l app=multi-vol-test
# NAME                          STATUS   CAPACITY   STORAGECLASS
# data-multi-vol-test-0         Bound    1Gi        standard
# data-multi-vol-test-1         Bound    1Gi        standard
# logs-multi-vol-test-0         Bound    512Mi      standard
# logs-multi-vol-test-1         Bound    512Mi      standard

# Each pod gets its own pair of volumes — 2 replicas × 2 templates = 4 PVCs

# Cleanup
kubectl delete sts multi-vol-test -n deployforge
kubectl delete pvc -l app=multi-vol-test -n deployforge
```

</details>

---

## Capstone Connection

**DeployForge** uses PersistentVolumes as the data backbone for its stateful components:

- **PostgreSQL data volume** — A `volumeClaimTemplate` in the PostgreSQL StatefulSet provisions a `fast-ssd` PVC per replica. The `Retain` reclaim policy ensures data survives even accidental PVC deletions. `PGDATA` is set to a subdirectory within the mount to avoid init conflicts.
- **Redis persistence** — Redis AOF/RDB files are stored on a persistent volume so in-memory data survives pod restarts. The PVC uses `ReadWriteOnce` because each Redis instance writes its own persistence files.
- **StorageClass strategy** — DeployForge defines `deployforge-fast` (SSD, for databases) and `deployforge-standard` (HDD, for logs and backups) StorageClasses. In kind, both use `local-path`; in production, they map to `gp3` and `sc1` on AWS.
- **Volume snapshots** — A CronJob takes nightly VolumeSnapshots of the PostgreSQL PVC. In Module 09 (Reliability Engineering), you'll build a full disaster recovery runbook using these snapshots.
- **Backup layering** — DeployForge uses both volume snapshots (fast, crash-consistent) and `pg_dump` CronJobs (slower, application-consistent) for defense-in-depth. The Helm chart (Module 07.3) parameterizes backup schedules per environment.
