# 13.3 — Operational Excellence & Day-2 Operations

## Concept

Launching a service is the easy part. Running it for months and years — through Kubernetes upgrades, certificate renewals, dependency CVEs, traffic spikes, team turnover, and 3 AM pages — is where operational excellence is won or lost. Day-2 operations are everything that happens after the first successful deployment: upgrades, patching, backup/restore, disaster recovery, on-call, and the relentless elimination of toil.

The difference between a team that operates a system and a team that is operated *by* a system comes down to automation, documentation, and discipline. SRE isn't just about building reliable systems — it's about building systems that are *sustainable to operate*. This means every manual procedure has a runbook, every runbook has a candidate for automation, and every automation has monitoring to verify it works.

---

## Deep Dive

### The Operational Lifecycle

```
┌─────────────────────────────────────────────────────────────┐
│                  Service Operational Lifecycle                │
│                                                              │
│  Launch ──▶ Steady State ──▶ Growth ──▶ Maturity ──▶ Sunset │
│    │             │              │            │           │    │
│    ▼             ▼              ▼            ▼           ▼    │
│  PRR         Monitoring    Scaling      Optimization  Decom  │
│  Runbooks    Patching      Capacity     Toil reduction       │
│  Alerts      Upgrades      Sharding     Self-healing         │
│  On-call     Incidents     Migration    Knowledge transfer   │
└─────────────────────────────────────────────────────────────┘
```

### Kubernetes Version Upgrades

Kubernetes releases a new minor version every ~4 months, and each version is supported for ~14 months. Falling behind means losing security patches and eventually hitting API deprecations that break your manifests.

#### Upgrade Strategy

```
┌─────────────────────────────────────────────────────────────────┐
│              Kubernetes Upgrade Workflow                          │
│                                                                  │
│  1. Read release notes ──▶ Identify breaking changes             │
│                                                                  │
│  2. Run deprecation checker                                      │
│     └── kubent (kube-no-trouble)                                 │
│     └── pluto                                                    │
│                                                                  │
│  3. Upgrade staging cluster first                                │
│     ├── Control plane (API server, scheduler, controller-mgr)    │
│     └── Node pools (one at a time, drain → upgrade → uncordon)   │
│                                                                  │
│  4. Run integration tests against staging                        │
│                                                                  │
│  5. Upgrade production                                           │
│     ├── Control plane                                            │
│     ├── Node pool 1 (canary — 1 node)                            │
│     ├── Verify for 24 hours                                      │
│     └── Node pools 2–N (rolling)                                 │
│                                                                  │
│  6. Post-upgrade verification                                    │
│     ├── All pods healthy                                         │
│     ├── SLOs within budget                                       │
│     └── No new error patterns in logs                            │
└─────────────────────────────────────────────────────────────────┘
```

```bash
# Check for deprecated APIs in your manifests
# Install kubent (kube-no-trouble)
brew install kubent

# Scan for deprecated APIs
kubent
# → 4:17PM INF >>> Deprecated APIs removed in 1.29 <<<
# → KIND                NAMESPACE          NAME                  API_VERSION          REPLACE_WITH
# → FlowSchema          <none>            catch-all              flowcontrol/v1beta2  flowcontrol/v1
# → PriorityLevelConfig <none>            catch-all              flowcontrol/v1beta2  flowcontrol/v1

# Check current cluster version
kubectl version --short
# → Client Version: v1.29.0
# → Server Version: v1.29.0

# For kind clusters: upgrade by recreating with new image
# For managed clusters (EKS/GKE/AKS): use provider's upgrade mechanism
```

#### kind Cluster Upgrade

```bash
# kind doesn't support in-place upgrades — recreate with new version
kind delete cluster --name deployforge
kind create cluster --name deployforge \
  --image kindest/node:v1.30.0 \
  --config kind-config.yaml

# Re-apply all manifests via ArgoCD
argocd app sync deployforge-prod --force
```

#### Managed Cluster Upgrade (EKS example)

```bash
# Upgrade EKS control plane
aws eks update-cluster-version \
  --name deployforge \
  --kubernetes-version 1.30

# Monitor upgrade progress
aws eks describe-update \
  --name deployforge \
  --update-id <update-id>
# → status: "Successful"

# Upgrade node groups (rolling update)
aws eks update-nodegroup-version \
  --cluster-name deployforge \
  --nodegroup-name deployforge-workers \
  --kubernetes-version 1.30
```

### Certificate Rotation

TLS certificates expire. If you don't automate renewal, you'll learn about expiration from your users — at 3 AM.

```yaml
# cert-manager ClusterIssuer for Let's Encrypt (Module 06)
apiVersion: cert-manager.io/v1
kind: ClusterIssuer
metadata:
  name: letsencrypt-prod
spec:
  acme:
    server: https://acme-v02.api.letsencrypt.org/directory
    email: ops@deployforge.io
    privateKeySecretRef:
      name: letsencrypt-prod-account
    solvers:
      - http01:
          ingress:
            class: nginx
---
# Certificate resource — cert-manager auto-renews 30 days before expiry
apiVersion: cert-manager.io/v1
kind: Certificate
metadata:
  name: deployforge-tls
  namespace: deployforge-prod
spec:
  secretName: deployforge-tls-secret
  issuerRef:
    name: letsencrypt-prod
    kind: ClusterIssuer
  dnsNames:
    - deployforge.example.com
    - api.deployforge.example.com
  renewBefore: 720h    # Renew 30 days before expiry
```

```bash
# Check certificate status
kubectl get certificates -n deployforge-prod
# → NAME              READY   SECRET                   AGE
# → deployforge-tls   True    deployforge-tls-secret   30d

# Check expiration date
kubectl get secret deployforge-tls-secret -n deployforge-prod \
  -o jsonpath='{.data.tls\.crt}' | base64 -d | openssl x509 -noout -enddate
# → notAfter=Apr 15 00:00:00 2025 GMT

# Alert on certificates expiring within 14 days
# (PrometheusRule — add to monitoring/alerts/)
```

```yaml
# monitoring/alerts/cert-expiry.yaml
apiVersion: monitoring.coreos.com/v1
kind: PrometheusRule
metadata:
  name: cert-expiry-alerts
  namespace: monitoring
spec:
  groups:
    - name: certificate-expiry
      rules:
        - alert: CertificateExpiringSoon
          expr: |
            certmanager_certificate_expiration_timestamp_seconds
            - time() < 14 * 24 * 3600
          for: 1h
          labels:
            severity: warning
          annotations:
            summary: "Certificate {{ $labels.name }} expires in < 14 days"
            description: "Certificate in namespace {{ $labels.namespace }} expires at {{ $value | humanizeTimestamp }}."
            runbook_url: "https://deployforge.internal/runbooks/cert-renewal"
```

### etcd Backup and Restore

etcd is the brain of Kubernetes. Lose etcd and you lose the entire cluster state. Backup is non-negotiable.

```bash
# etcd backup script (runs as a CronJob in the cluster)
#!/bin/bash
# scripts/etcd-backup.sh

BACKUP_DIR="/backups/etcd"
TIMESTAMP=$(date +%Y%m%d-%H%M%S)
BACKUP_FILE="${BACKUP_DIR}/etcd-snapshot-${TIMESTAMP}.db"

# Take snapshot
ETCDCTL_API=3 etcdctl snapshot save "${BACKUP_FILE}" \
  --endpoints=https://127.0.0.1:2379 \
  --cacert=/etc/kubernetes/pki/etcd/ca.crt \
  --cert=/etc/kubernetes/pki/etcd/server.crt \
  --key=/etc/kubernetes/pki/etcd/server.key

# Verify snapshot
ETCDCTL_API=3 etcdctl snapshot status "${BACKUP_FILE}" --write-out=table
# → +----------+----------+------------+------------+
# → |   HASH   | REVISION | TOTAL KEYS | TOTAL SIZE |
# → +----------+----------+------------+------------+
# → | a]b23f4c |   489201 |       1428 |     5.7 MB |
# → +----------+----------+------------+------------+

# Upload to object storage
aws s3 cp "${BACKUP_FILE}" "s3://deployforge-backups/etcd/${TIMESTAMP}/"

# Cleanup local backups older than 7 days
find "${BACKUP_DIR}" -name "etcd-snapshot-*" -mtime +7 -delete

echo "etcd backup complete: ${BACKUP_FILE}"
```

```yaml
# k8s/ops/etcd-backup-cronjob.yaml
apiVersion: batch/v1
kind: CronJob
metadata:
  name: etcd-backup
  namespace: kube-system
spec:
  schedule: "0 */6 * * *"    # Every 6 hours
  concurrencyPolicy: Forbid
  successfulJobsHistoryLimit: 3
  failedJobsHistoryLimit: 3
  jobTemplate:
    spec:
      backoffLimit: 2
      template:
        spec:
          hostNetwork: true
          nodeSelector:
            node-role.kubernetes.io/control-plane: ""
          tolerations:
            - key: node-role.kubernetes.io/control-plane
              effect: NoSchedule
          containers:
            - name: etcd-backup
              image: bitnami/etcd:3.5
              command: ["/bin/sh", "-c"]
              args:
                - |
                  etcdctl snapshot save /backup/snapshot.db \
                    --endpoints=https://127.0.0.1:2379 \
                    --cacert=/etc/kubernetes/pki/etcd/ca.crt \
                    --cert=/etc/kubernetes/pki/etcd/server.crt \
                    --key=/etc/kubernetes/pki/etcd/server.key
                  etcdctl snapshot status /backup/snapshot.db --write-out=table
              volumeMounts:
                - name: etcd-certs
                  mountPath: /etc/kubernetes/pki/etcd
                  readOnly: true
                - name: backup-volume
                  mountPath: /backup
          restartPolicy: OnFailure
          volumes:
            - name: etcd-certs
              hostPath:
                path: /etc/kubernetes/pki/etcd
            - name: backup-volume
              persistentVolumeClaim:
                claimName: etcd-backup-pvc
```

#### etcd Restore Procedure

```bash
# DISASTER RECOVERY: Restore etcd from snapshot
# ⚠️  This is destructive — it replaces all cluster state

# 1. Stop the API server (on all control plane nodes)
sudo mv /etc/kubernetes/manifests/kube-apiserver.yaml /tmp/

# 2. Restore the snapshot
ETCDCTL_API=3 etcdctl snapshot restore /backup/etcd-snapshot-latest.db \
  --name etcd-member \
  --initial-cluster etcd-member=https://127.0.0.1:2380 \
  --initial-cluster-token etcd-cluster-1 \
  --initial-advertise-peer-urls https://127.0.0.1:2380 \
  --data-dir=/var/lib/etcd-restored

# 3. Replace etcd data directory
sudo systemctl stop etcd  # or move the static pod manifest
sudo mv /var/lib/etcd /var/lib/etcd-old
sudo mv /var/lib/etcd-restored /var/lib/etcd
sudo systemctl start etcd  # or restore the static pod manifest

# 4. Restart the API server
sudo mv /tmp/kube-apiserver.yaml /etc/kubernetes/manifests/

# 5. Verify cluster state
kubectl get nodes
kubectl get pods --all-namespaces
```

### Disaster Recovery Planning

```
┌────────────────────────────────────────────────────────────────────┐
│                  Disaster Recovery Tiers                            │
│                                                                    │
│  Tier 1: Pod Failure (seconds)                                     │
│  ├── Handled by: ReplicaSet, liveness probes                       │
│  ├── Recovery: Automatic pod restart/reschedule                    │
│  └── Your action: None (verify alerts fired correctly)             │
│                                                                    │
│  Tier 2: Node Failure (minutes)                                    │
│  ├── Handled by: Pod anti-affinity, PodDisruptionBudgets          │
│  ├── Recovery: Cluster Autoscaler provisions new node              │
│  └── Your action: Verify workloads rescheduled, check for data loss│
│                                                                    │
│  Tier 3: Zone/AZ Failure (minutes to hours)                        │
│  ├── Handled by: Multi-AZ node pools, topology spread constraints  │
│  ├── Recovery: Traffic shifts to healthy AZs                       │
│  └── Your action: Verify capacity in remaining AZs, scale if needed│
│                                                                    │
│  Tier 4: Cluster Failure (hours)                                   │
│  ├── Handled by: etcd backups, IaC (Terraform), GitOps             │
│  ├── Recovery: Recreate cluster from IaC + restore from etcd backup│
│  └── Your action: Execute DR runbook, restore services, verify SLOs│
│                                                                    │
│  Tier 5: Region Failure (hours to days)                            │
│  ├── Handled by: Multi-region deployment, DNS failover             │
│  ├── Recovery: Promote standby region, redirect DNS                │
│  └── Your action: Full DR execution, data reconciliation           │
└────────────────────────────────────────────────────────────────────┘
```

### Database Backup and Restore

```bash
# PostgreSQL backup script
#!/bin/bash
# scripts/pg-backup.sh

NAMESPACE="deployforge-prod"
POD="postgres-0"
TIMESTAMP=$(date +%Y%m%d-%H%M%S)
BACKUP_FILE="deployforge-backup-${TIMESTAMP}.sql.gz"

# Take a compressed logical backup
kubectl exec -n ${NAMESPACE} ${POD} -- \
  pg_dump -U deployforge -Fc deployforge \
  | gzip > "${BACKUP_FILE}"

echo "Backup created: ${BACKUP_FILE} ($(du -h ${BACKUP_FILE} | cut -f1))"

# Verify backup integrity
gunzip -c "${BACKUP_FILE}" | kubectl exec -i -n ${NAMESPACE} ${POD} -- \
  pg_restore --list -Fc 2>/dev/null | head -5
# → ; Archive created at 2025-01-15 10:30:00 UTC
# → ;     Dumped by pg_dump version 15.x
# → ; Selected TOC Entries:
```

```bash
# PostgreSQL restore procedure
#!/bin/bash
# scripts/pg-restore.sh

NAMESPACE="deployforge-prod"
POD="postgres-0"
BACKUP_FILE=$1

if [ -z "$BACKUP_FILE" ]; then
  echo "Usage: ./pg-restore.sh <backup-file>"
  exit 1
fi

echo "⚠️  This will replace all data in the deployforge database."
echo "Restoring from: ${BACKUP_FILE}"

# Scale down application pods to prevent writes during restore
kubectl scale deployment worker -n ${NAMESPACE} --replicas=0
kubectl scale rollout api-gateway -n ${NAMESPACE} --replicas=0

# Wait for pods to terminate
sleep 10

# Drop and recreate the database
kubectl exec -n ${NAMESPACE} ${POD} -- \
  psql -U deployforge -c "SELECT pg_terminate_backend(pid) FROM pg_stat_activity WHERE datname='deployforge' AND pid <> pg_backend_pid();"
kubectl exec -n ${NAMESPACE} ${POD} -- \
  dropdb -U deployforge deployforge
kubectl exec -n ${NAMESPACE} ${POD} -- \
  createdb -U deployforge deployforge

# Restore from backup
gunzip -c "${BACKUP_FILE}" | kubectl exec -i -n ${NAMESPACE} ${POD} -- \
  pg_restore -U deployforge -d deployforge -Fc

# Scale application back up
kubectl scale deployment worker -n ${NAMESPACE} --replicas=2
kubectl argo rollouts set image api-gateway api-gateway=deployforge/api-gateway:v1.1.0 -n ${NAMESPACE}

echo "Restore complete. Verify application health."
kubectl get pods -n ${NAMESPACE}
```

### On-Call Playbook

An on-call playbook defines how the team responds when the pager goes off. It's the bridge between an alert firing and an engineer knowing what to do.

```
┌─────────────────────────────────────────────────────────────────┐
│                    On-Call Response Flow                          │
│                                                                  │
│  Alert Fires ──▶ Acknowledge (5 min SLA)                         │
│       │                                                          │
│       ▼                                                          │
│  Triage                                                          │
│  ├── Check: Is this a real problem or a false positive?          │
│  ├── Check: What is the user impact? (SLO dashboard)             │
│  └── Check: Is this already known? (incident channel)            │
│       │                                                          │
│       ▼                                                          │
│  Severity Classification                                         │
│  ├── SEV1: Total service outage (all hands, war room)            │
│  ├── SEV2: Partial degradation (on-call + backup)                │
│  ├── SEV3: Minor impact (on-call investigates, fix in hours)     │
│  └── SEV4: No user impact (ticket for next business day)         │
│       │                                                          │
│       ▼                                                          │
│  Mitigate First, Debug Later                                     │
│  ├── Rollback if recent deploy                                   │
│  ├── Scale up if capacity-related                                │
│  ├── Restart if single-pod issue                                 │
│  └── Failover if infrastructure failure                          │
│       │                                                          │
│       ▼                                                          │
│  Resolve ──▶ Update Status Page ──▶ Postmortem (within 48h)     │
└─────────────────────────────────────────────────────────────────┘
```

#### Alert-Specific Runbooks

Every alert should link to a runbook. Here's the pattern:

```markdown
# Runbook: DeployForgeAPIHighBurnRate_Critical

## Alert Description
The API Gateway is burning error budget at 14.4× the sustainable rate.
At this rate, the 30-day error budget will be exhausted in ~2.5 days.

## Impact
API requests are failing at a rate that violates the 99.9% availability SLO.

## First Response (< 5 minutes)
1. Open the SLO dashboard: http://grafana.internal/d/deployforge-slo
2. Check recent deployments: `argocd app history deployforge-prod --grpc-web`
3. Check pod status: `kubectl get pods -n deployforge-prod`
4. Check recent logs: `kubectl logs -n deployforge-prod -l app=api-gateway --tail=100 --since=10m`

## Common Causes and Fixes

### Recent deployment is bad
```bash
# Rollback the last ArgoCD sync
argocd app rollback deployforge-prod --grpc-web
# Or abort the Argo Rollout
kubectl argo rollouts abort api-gateway -n deployforge-prod
```

### Database connection exhaustion
```bash
# Check connection count
kubectl exec -n deployforge-prod postgres-0 -- \
  psql -U deployforge -c "SELECT count(*) FROM pg_stat_activity;"
# If near max_connections, restart API pods to release connections
kubectl rollout restart deployment api-gateway -n deployforge-prod
```

### Memory pressure / OOM kills
```bash
# Check for OOM events
kubectl get events -n deployforge-prod --field-selector reason=OOMKilling
# Increase memory limits temporarily
kubectl set resources deployment api-gateway -n deployforge-prod \
  --limits=memory=1Gi
```

### External dependency failure
```bash
# Check Redis connectivity
kubectl exec -n deployforge-prod deploy/api-gateway -- \
  node -e "require('ioredis').createClient('redis://redis:6379').ping().then(console.log)"
# Check PostgreSQL connectivity
kubectl exec -n deployforge-prod deploy/api-gateway -- \
  node -e "require('pg').Pool({connectionString: process.env.DATABASE_URL}).query('SELECT 1')"
```

## Escalation
If not resolved in 30 minutes, page the secondary on-call and the
engineering manager.
```

### Toil Reduction

Google SRE defines toil as "work tied to running a production service that is manual, repetitive, automatable, tactical, devoid of enduring value, and scales linearly with service growth." The goal: keep toil below 50% of on-call engineering time.

| Common Toil | Automation |
|-------------|-----------|
| Manually restarting crashed pods | Liveness probes + automatic restart (Module 05) |
| Manually scaling during traffic spikes | HPA with custom metrics (Module 12) |
| Manually rotating certificates | cert-manager with auto-renewal |
| Manually running database migrations | ArgoCD PreSync hooks |
| Manually checking deployment health | Argo Rollouts AnalysisTemplates (Module 10) |
| Manually updating dashboards after config changes | Grafana dashboard-as-code with ConfigMap sidecar |
| Manually investigating alerts | Runbooks linked from alert annotations |
| Manually provisioning new environments | Terraform modules (Module 11) + Kustomize overlays |

```bash
# Toil tracking — measure time spent on manual operations
# Add to your team's operational log:

# scripts/log-toil.sh
#!/bin/bash
CATEGORY=$1    # e.g., "restart", "scale", "debug", "deploy"
DURATION=$2    # in minutes
DESCRIPTION=$3

echo "$(date -u +%Y-%m-%dT%H:%M:%SZ),${CATEGORY},${DURATION},${DESCRIPTION}" \
  >> operational-toil-log.csv

# Example usage:
# ./log-toil.sh restart 15 "Restarted api-gateway pods after OOM"
# ./log-toil.sh debug 45 "Investigated slow queries in PostgreSQL"
```

### Continuous Improvement Loop

```
┌─────────────────────────────────────────────────────────────────┐
│                Continuous Improvement Cycle                       │
│                                                                  │
│          ┌──────────┐                                            │
│          │  Observe  │ ← Metrics, alerts, incidents, toil log    │
│          └────┬─────┘                                            │
│               │                                                  │
│               ▼                                                  │
│          ┌──────────┐                                            │
│          │  Analyze  │ ← Postmortems, toil analysis, SLO review  │
│          └────┬─────┘                                            │
│               │                                                  │
│               ▼                                                  │
│          ┌──────────┐                                            │
│          │  Improve  │ ← Automation, runbooks, architecture      │
│          └────┬─────┘                                            │
│               │                                                  │
│               ▼                                                  │
│          ┌──────────┐                                            │
│          │  Measure  │ ← Did toil decrease? Did SLOs improve?    │
│          └────┬─────┘                                            │
│               │                                                  │
│               └──────────────▶ (repeat quarterly)                │
└─────────────────────────────────────────────────────────────────┘
```

**Quarterly operational review agenda:**
1. **SLO performance** — Did we meet our targets? Where did we burn budget?
2. **Incident review** — Top 3 incidents by user impact. What systemic fixes can prevent recurrence?
3. **Toil analysis** — What consumed the most on-call time? What can be automated?
4. **Capacity review** — Are we trending toward capacity limits? Do we need to provision more?
5. **Technical debt** — What shortcuts are accumulating risk? What's the payoff of fixing them?

### Career Path in SRE

Understanding the career landscape helps you position the skills you've built in this track:

```
┌─────────────────────────────────────────────────────────────────┐
│                    SRE Career Ladder                              │
│                                                                  │
│  Junior SRE / DevOps Engineer                                    │
│  ├── Deploy and monitor services                                 │
│  ├── Write runbooks and respond to pages                         │
│  └── Automate repetitive tasks                                   │
│       │                                                          │
│       ▼                                                          │
│  Mid-Level SRE                                                   │
│  ├── Design monitoring and alerting strategies                   │
│  ├── Build CI/CD pipelines and GitOps workflows                  │
│  ├── Lead incident response and write postmortems                │
│  └── Implement SLOs and error budget policies                    │
│       │                                                          │
│       ▼                                                          │
│  Senior SRE                                                      │
│  ├── Architect reliable distributed systems                      │
│  ├── Design capacity planning and scaling strategies             │
│  ├── Mentor junior engineers on operational practices            │
│  ├── Drive toil reduction and automation investments             │
│  └── Negotiate SLOs with product and business stakeholders       │
│       │                                                          │
│       ▼                                                          │
│  Staff / Principal SRE                                           │
│  ├── Define org-wide reliability standards and platforms         │
│  ├── Build internal developer platforms (IDP)                    │
│  ├── Lead disaster recovery and chaos engineering programs       │
│  └── Influence engineering culture toward operational excellence  │
└─────────────────────────────────────────────────────────────────┘
```

> **Key insight:** The skills you've built in this 13-module track — containerization, Kubernetes, observability, reliability engineering, CI/CD, IaC, and scaling — map directly to the mid-level and senior SRE role. The capstone project (DeployForge) is a portfolio piece that demonstrates end-to-end operational competence.

---

## Code Examples

### Automated Upgrade Verification Script

```bash
#!/bin/bash
# scripts/verify-upgrade.sh — Run after any Kubernetes or application upgrade

set -euo pipefail

NAMESPACE="deployforge-prod"
FAILURES=0

echo "=== Post-Upgrade Verification ==="

# 1. All nodes ready
echo -n "Checking nodes... "
NOT_READY=$(kubectl get nodes --no-headers | grep -v " Ready " | wc -l | tr -d ' ')
if [ "$NOT_READY" -gt 0 ]; then
  echo "FAIL: ${NOT_READY} nodes not ready"
  FAILURES=$((FAILURES + 1))
else
  echo "OK"
fi

# 2. All pods in namespace running
echo -n "Checking pods... "
NOT_RUNNING=$(kubectl get pods -n ${NAMESPACE} --no-headers \
  | grep -v "Running\|Completed" | wc -l | tr -d ' ')
if [ "$NOT_RUNNING" -gt 0 ]; then
  echo "FAIL: ${NOT_RUNNING} pods not running"
  kubectl get pods -n ${NAMESPACE} | grep -v "Running\|Completed"
  FAILURES=$((FAILURES + 1))
else
  echo "OK ($(kubectl get pods -n ${NAMESPACE} --no-headers | wc -l | tr -d ' ') pods)"
fi

# 3. API health check
echo -n "Checking API health... "
HEALTH=$(kubectl exec -n ${NAMESPACE} deploy/api-gateway -- \
  wget -qO- http://localhost:3000/health 2>/dev/null || echo "FAIL")
if echo "$HEALTH" | grep -q '"status":"ok"'; then
  echo "OK"
else
  echo "FAIL: API health check returned: ${HEALTH}"
  FAILURES=$((FAILURES + 1))
fi

# 4. Database connectivity
echo -n "Checking PostgreSQL... "
PG_READY=$(kubectl exec -n ${NAMESPACE} postgres-0 -- pg_isready -U deployforge 2>&1)
if echo "$PG_READY" | grep -q "accepting connections"; then
  echo "OK"
else
  echo "FAIL: ${PG_READY}"
  FAILURES=$((FAILURES + 1))
fi

# 5. Redis connectivity
echo -n "Checking Redis... "
REDIS_PING=$(kubectl exec -n ${NAMESPACE} deploy/redis -- redis-cli ping 2>&1)
if [ "$REDIS_PING" = "PONG" ]; then
  echo "OK"
else
  echo "FAIL: ${REDIS_PING}"
  FAILURES=$((FAILURES + 1))
fi

# 6. Prometheus scraping
echo -n "Checking Prometheus targets... "
TARGETS=$(kubectl exec -n monitoring deploy/monitoring-kube-prometheus-prometheus -- \
  wget -qO- http://localhost:9090/api/v1/targets 2>/dev/null \
  | grep -o '"health":"up"' | wc -l | tr -d ' ')
if [ "$TARGETS" -gt 10 ]; then
  echo "OK (${TARGETS} targets up)"
else
  echo "WARN: Only ${TARGETS} targets up"
  FAILURES=$((FAILURES + 1))
fi

# 7. ArgoCD sync status
echo -n "Checking ArgoCD sync... "
SYNC_STATUS=$(argocd app get deployforge-prod --grpc-web -o json 2>/dev/null \
  | grep -o '"syncStatus":"[^"]*"' | head -1 || echo "unknown")
if echo "$SYNC_STATUS" | grep -q "Synced"; then
  echo "OK"
else
  echo "WARN: ${SYNC_STATUS}"
fi

echo ""
if [ "$FAILURES" -gt 0 ]; then
  echo "⚠️  ${FAILURES} check(s) failed. Investigate before proceeding."
  exit 1
else
  echo "✅ All checks passed. Upgrade verified."
fi
```

### PodDisruptionBudget for Safe Upgrades

```yaml
# k8s/base/api-gateway/pdb.yaml — Ensure availability during node drains
apiVersion: policy/v1
kind: PodDisruptionBudget
metadata:
  name: api-gateway-pdb
  namespace: deployforge-prod
spec:
  minAvailable: 2    # Always keep at least 2 pods running
  selector:
    matchLabels:
      app: api-gateway
---
# k8s/base/postgres/pdb.yaml
apiVersion: policy/v1
kind: PodDisruptionBudget
metadata:
  name: postgres-pdb
  namespace: deployforge-prod
spec:
  maxUnavailable: 0   # Never disrupt the single PostgreSQL pod
  selector:
    matchLabels:
      app: postgres
```

```bash
kubectl apply -f k8s/base/api-gateway/pdb.yaml
kubectl apply -f k8s/base/postgres/pdb.yaml
# → poddisruptionbudget.policy/api-gateway-pdb created
# → poddisruptionbudget.policy/postgres-pdb created

# Verify PDBs are respected during drain
kubectl drain deployforge-worker --ignore-daemonsets --delete-emptydir-data
# → node/deployforge-worker cordoned
# → evicting pod deployforge-prod/api-gateway-xxxxx
# → (waits if eviction would violate PDB)
```

---

## Try It Yourself

### Challenge 1: Write an etcd Backup Verification Script

Create a script that:
1. Takes an etcd snapshot
2. Verifies the snapshot is valid (check hash, revision, key count)
3. Alerts (prints to stdout) if the snapshot is smaller than the previous one by >20%
4. Logs the result to a structured JSON file

<details>
<summary>Show solution</summary>

```bash
#!/bin/bash
# scripts/verify-etcd-backup.sh

set -euo pipefail

BACKUP_DIR="./etcd-backups"
LOG_FILE="./etcd-backup-log.json"
TIMESTAMP=$(date -u +%Y-%m-%dT%H:%M:%SZ)
SNAPSHOT="${BACKUP_DIR}/etcd-snapshot-$(date +%Y%m%d-%H%M%S).db"

mkdir -p "${BACKUP_DIR}"

# Take snapshot (for kind, use the etcd pod directly)
kubectl exec -n kube-system etcd-deployforge-control-plane -- \
  etcdctl snapshot save /tmp/snapshot.db \
    --cacert=/etc/kubernetes/pki/etcd/ca.crt \
    --cert=/etc/kubernetes/pki/etcd/server.crt \
    --key=/etc/kubernetes/pki/etcd/server.key 2>/dev/null

kubectl cp kube-system/etcd-deployforge-control-plane:/tmp/snapshot.db "${SNAPSHOT}"

# Get snapshot status
STATUS=$(ETCDCTL_API=3 etcdctl snapshot status "${SNAPSHOT}" --write-out=json 2>/dev/null)
HASH=$(echo "$STATUS" | jq -r '.hash')
REVISION=$(echo "$STATUS" | jq -r '.revision')
TOTAL_KEYS=$(echo "$STATUS" | jq -r '.totalKey')
CURRENT_SIZE=$(stat -f%z "${SNAPSHOT}" 2>/dev/null || stat --format=%s "${SNAPSHOT}")

# Check against previous backup
ALERT="none"
if [ -f "${LOG_FILE}" ]; then
  PREV_SIZE=$(jq -r 'last | .size_bytes' "${LOG_FILE}" 2>/dev/null || echo "0")
  if [ "$PREV_SIZE" -gt 0 ]; then
    CHANGE=$(( (CURRENT_SIZE - PREV_SIZE) * 100 / PREV_SIZE ))
    if [ "$CHANGE" -lt -20 ]; then
      ALERT="SIZE_DECREASED_BY_${CHANGE}%"
      echo "⚠️  WARNING: Backup size decreased by ${CHANGE}% (${PREV_SIZE} → ${CURRENT_SIZE})"
    fi
  fi
fi

# Log result as JSON
echo "{\"timestamp\":\"${TIMESTAMP}\",\"file\":\"${SNAPSHOT}\",\"hash\":\"${HASH}\",\"revision\":${REVISION},\"total_keys\":${TOTAL_KEYS},\"size_bytes\":${CURRENT_SIZE},\"alert\":\"${ALERT}\"}" \
  | jq . >> "${LOG_FILE}"

echo "✅ Backup verified: ${SNAPSHOT} (${TOTAL_KEYS} keys, ${CURRENT_SIZE} bytes, revision ${REVISION})"
```

Verify:
```bash
chmod +x scripts/verify-etcd-backup.sh
./scripts/verify-etcd-backup.sh
# → ✅ Backup verified: ./etcd-backups/etcd-snapshot-20250115-103000.db (1428 keys, 5967872 bytes, revision 489201)

cat etcd-backup-log.json | jq .
# → { "timestamp": "2025-01-15T10:30:00Z", "file": "...", "hash": "...", ... }
```

</details>

### Challenge 2: Create a Maintenance Window Automation

Write a script that puts DeployForge into "maintenance mode":
1. Pauses ArgoCD auto-sync (prevents new deployments)
2. Silences non-critical Prometheus alerts
3. Displays a maintenance banner via a ConfigMap that the API reads
4. Reverses everything when maintenance is complete

<details>
<summary>Show solution</summary>

```bash
#!/bin/bash
# scripts/maintenance-mode.sh

set -euo pipefail

ACTION=${1:-"help"}
NAMESPACE="deployforge-prod"

start_maintenance() {
  echo "🔧 Starting maintenance mode..."

  # 1. Pause ArgoCD auto-sync
  argocd app set deployforge-prod --sync-policy none --grpc-web
  echo "  ✓ ArgoCD auto-sync paused"

  # 2. Silence non-critical alerts (4 hour window)
  SILENCE_ID=$(curl -s -X POST http://localhost:9093/api/v2/silences \
    -H "Content-Type: application/json" \
    -d '{
      "matchers": [{"name": "severity", "value": "warning|info", "isRegex": true}],
      "startsAt": "'$(date -u +%Y-%m-%dT%H:%M:%S.000Z)'",
      "endsAt": "'$(date -u -v+4H +%Y-%m-%dT%H:%M:%S.000Z 2>/dev/null || date -u -d "+4 hours" +%Y-%m-%dT%H:%M:%S.000Z)'",
      "createdBy": "maintenance-script",
      "comment": "Maintenance window"
    }' | jq -r '.silenceID')
  echo "  ✓ Non-critical alerts silenced (ID: ${SILENCE_ID})"
  echo "${SILENCE_ID}" > .maintenance-silence-id

  # 3. Set maintenance banner
  kubectl create configmap maintenance-banner \
    --namespace ${NAMESPACE} \
    --from-literal=maintenance="true" \
    --from-literal=message="DeployForge is undergoing scheduled maintenance. ETA: 4 hours." \
    --dry-run=client -o yaml | kubectl apply -f -
  echo "  ✓ Maintenance banner set"

  echo ""
  echo "⚠️  Maintenance mode ACTIVE. Run '$0 stop' when complete."
}

stop_maintenance() {
  echo "🔧 Stopping maintenance mode..."

  # 1. Re-enable ArgoCD auto-sync
  argocd app set deployforge-prod \
    --sync-policy automated \
    --auto-prune \
    --self-heal \
    --grpc-web
  echo "  ✓ ArgoCD auto-sync re-enabled"

  # 2. Remove alert silence
  if [ -f .maintenance-silence-id ]; then
    SILENCE_ID=$(cat .maintenance-silence-id)
    curl -s -X DELETE "http://localhost:9093/api/v2/silence/${SILENCE_ID}"
    rm .maintenance-silence-id
    echo "  ✓ Alert silence removed"
  fi

  # 3. Remove maintenance banner
  kubectl delete configmap maintenance-banner -n ${NAMESPACE} --ignore-not-found
  echo "  ✓ Maintenance banner removed"

  # 4. Force ArgoCD sync to ensure desired state
  argocd app sync deployforge-prod --grpc-web
  echo "  ✓ ArgoCD sync triggered"

  echo ""
  echo "✅ Maintenance mode DEACTIVATED. Verify deployment health."
}

case "$ACTION" in
  start) start_maintenance ;;
  stop)  stop_maintenance ;;
  *)
    echo "Usage: $0 {start|stop}"
    echo "  start — Enter maintenance mode (pause deploys, silence alerts)"
    echo "  stop  — Exit maintenance mode (resume normal operations)"
    ;;
esac
```

Verify:
```bash
chmod +x scripts/maintenance-mode.sh

# Enter maintenance mode
./scripts/maintenance-mode.sh start
# → 🔧 Starting maintenance mode...
# →   ✓ ArgoCD auto-sync paused
# →   ✓ Non-critical alerts silenced
# →   ✓ Maintenance banner set
# → ⚠️  Maintenance mode ACTIVE.

# ... perform maintenance ...

# Exit maintenance mode
./scripts/maintenance-mode.sh stop
# → 🔧 Stopping maintenance mode...
# →   ✓ ArgoCD auto-sync re-enabled
# →   ✓ Alert silence removed
# →   ✓ Maintenance banner removed
# →   ✓ ArgoCD sync triggered
# → ✅ Maintenance mode DEACTIVATED.
```

</details>

---

## Capstone Connection

**DeployForge** applies operational excellence practices as the final layer of production maturity:

- **Upgrade automation** (`scripts/verify-upgrade.sh`) — A verification script runs after every Kubernetes or application upgrade to confirm all services, databases, and monitoring are healthy. This script is the last step in any upgrade runbook.
- **Database backup/restore** (`scripts/pg-backup.sh`, `scripts/pg-restore.sh`) — PostgreSQL backups run on a schedule via CronJob. The restore procedure is tested monthly — an untested backup is not a backup.
- **On-call playbook** (`docs/runbook.md`) — Every PrometheusRule alert has a `runbook_url` annotation linking to a specific runbook section. When the pager fires, the on-call engineer follows the runbook instead of guessing.
- **Maintenance mode** (`scripts/maintenance-mode.sh`) — A single script pauses deployments, silences non-critical alerts, and sets a user-visible maintenance banner. This prevents alert fatigue during planned maintenance windows.
- **PodDisruptionBudgets** — PDBs on the API Gateway and PostgreSQL ensure that node drains during upgrades don't violate availability requirements.
- **Toil tracking** — The team logs manual operational work to identify automation candidates. The goal is to automate the top toil category each quarter.

With this module complete, DeployForge is not just deployed — it's *operated*. The system has monitoring, alerting, automated delivery, scaling, backup, disaster recovery procedures, and documented runbooks. This is what production-grade infrastructure looks like.
