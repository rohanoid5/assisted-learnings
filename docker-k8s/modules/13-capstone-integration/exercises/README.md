# Module 13 — Exercises (Final Capstone)

Deploy, break, fix, document, and present DeployForge as a production-grade system. These exercises are the graduation exam — they prove you can operate an end-to-end platform independently.

> **Prerequisites:**
> - All previous modules (01–12) completed
> - A running `kind` cluster (or recreate one fresh for the full experience)
> - All tools installed and configured:
>   ```bash
>   kind get clusters           # → deployforge (or create one)
>   kubectl cluster-info        # → Kubernetes control plane is running
>   helm version --short        # → v3.x.x
>   docker info                 # → Docker daemon running
>   argocd version --client     # → argocd: v2.x.x
>   kubectl argo rollouts version  # → argo-rollouts: v1.x.x
>   ```

---

## Exercise 1: Full Stack Deploy

**Goal:** Deploy the complete DeployForge stack from scratch on a fresh `kind` cluster. Every service, every database, every monitoring component, every GitOps configuration — all working together.

### Steps

1. **Delete any existing cluster and start fresh:**

   ```bash
   kind delete cluster --name deployforge
   ```

2. **Create a new multi-node cluster:**

   Use the kind config from [02-end-to-end-deployment.md](../02-end-to-end-deployment.md) — 1 control plane + 2 workers with Ingress port mappings.

3. **Bootstrap cluster-wide dependencies (in order):**

   - metrics-server (patched for kind)
   - Nginx Ingress Controller
   - cert-manager (optional for local, required for production)
   - Namespaces: `deployforge-prod`, `deployforge-staging`, `monitoring`, `argocd`, `argo-rollouts`

4. **Deploy stateful services:**

   - PostgreSQL StatefulSet with PVC, secrets, health checks
   - Redis Deployment with memory limits and eviction policy
   - NetworkPolicies restricting database access to app-tier pods only

5. **Deploy application services:**

   - API Gateway (Deployment or Argo Rollout) with 3 replicas
   - Worker Service with 2 replicas
   - Ingress rules routing traffic to the API Gateway
   - All secrets created and referenced correctly

6. **Deploy the observability stack:**

   - kube-prometheus-stack via Helm (Prometheus + Grafana + Alertmanager)
   - ServiceMonitors for API Gateway and Worker
   - SLO recording rules and multi-burn-rate PrometheusRules
   - Grafana dashboard imported via ConfigMap

7. **Deploy GitOps and progressive delivery:**

   - ArgoCD installed and configured
   - ArgoCD Application pointing at `k8s/overlays/prod/`
   - Argo Rollouts installed
   - API Gateway converted to Argo Rollout with canary strategy
   - AnalysisTemplate connected to Prometheus

8. **Deploy scaling configuration:**

   - HPA v2 for API Gateway targeting custom metrics
   - ResourceQuota and LimitRange for the production namespace
   - PodDisruptionBudgets for API Gateway and PostgreSQL

9. **Run the full verification script:**

   ```bash
   ./scripts/verify-deployment.sh
   ```

   Every check should pass.

<details>
<summary>Show solution</summary>

The complete step-by-step commands are in [02-end-to-end-deployment.md](../02-end-to-end-deployment.md), Phases 1–8. Here's the condensed sequence:

```bash
# Phase 1: Cluster bootstrap
kind create cluster --name deployforge --config kind-config.yaml
kubectl apply -f https://github.com/kubernetes-sigs/metrics-server/releases/latest/download/components.yaml
kubectl patch deployment metrics-server -n kube-system --type='json' \
  -p='[{"op":"add","path":"/spec/template/spec/containers/0/args/-","value":"--kubelet-insecure-tls"}]'
kubectl apply -f https://raw.githubusercontent.com/kubernetes/ingress-nginx/main/deploy/static/provider/kind/deploy.yaml
kubectl wait --namespace ingress-nginx --for=condition=ready pod \
  --selector=app.kubernetes.io/component=controller --timeout=120s

# Create namespaces
for ns in deployforge-prod deployforge-staging monitoring argocd argo-rollouts; do
  kubectl create namespace $ns --dry-run=client -o yaml | kubectl apply -f -
done

# Phase 2: Stateful services
kubectl create secret generic postgres-credentials \
  --namespace deployforge-prod \
  --from-literal=username=deployforge \
  --from-literal=password="$(openssl rand -base64 24)"
kubectl apply -f k8s/base/postgres/
kubectl apply -f k8s/base/redis/
kubectl apply -f k8s/base/network-policies/

# Wait for databases
kubectl wait -n deployforge-prod --for=condition=ready pod -l app=postgres --timeout=120s
kubectl wait -n deployforge-prod --for=condition=ready pod -l app=redis --timeout=60s

# Phase 3: Application services
kubectl create secret generic api-gateway-config --namespace deployforge-prod \
  --from-literal=database-url="postgresql://deployforge:$(kubectl get secret postgres-credentials -n deployforge-prod -o jsonpath='{.data.password}' | base64 -d)@postgres:5432/deployforge"
kubectl create secret generic worker-config --namespace deployforge-prod \
  --from-literal=database-url="postgresql://deployforge:$(kubectl get secret postgres-credentials -n deployforge-prod -o jsonpath='{.data.password}' | base64 -d)@postgres:5432/deployforge"
kubectl apply -f k8s/base/api-gateway/
kubectl apply -f k8s/base/worker/
kubectl apply -f k8s/base/ingress/

# Phase 4: Observability
helm repo add prometheus-community https://prometheus-community.github.io/helm-charts
helm repo update
helm install monitoring prometheus-community/kube-prometheus-stack \
  --namespace monitoring \
  --set prometheus.prometheusSpec.serviceMonitorSelectorNilUsesHelmValues=false \
  --set grafana.adminPassword=admin \
  --wait --timeout 5m
kubectl apply -f monitoring/servicemonitors/
kubectl apply -f monitoring/alerts/

# Phase 5: GitOps
kubectl apply -n argocd -f https://raw.githubusercontent.com/argoproj/argo-cd/stable/manifests/install.yaml
kubectl wait -n argocd --for=condition=ready pod -l app.kubernetes.io/name=argocd-server --timeout=180s
kubectl apply -f deploy/argocd/application.yaml

# Phase 6: Progressive delivery
kubectl apply -n argo-rollouts -f https://github.com/argoproj/argo-rollouts/releases/latest/download/install.yaml
kubectl apply -f deploy/rollouts/

# Phase 7: Scaling
kubectl apply -f k8s/scaling/hpa-api.yaml
kubectl apply -f k8s/scaling/resource-quota.yaml
kubectl apply -f k8s/scaling/limit-range.yaml
kubectl apply -f k8s/base/api-gateway/pdb.yaml
kubectl apply -f k8s/base/postgres/pdb.yaml
```

Verify:
```bash
kubectl get pods -n deployforge-prod
# → NAME                           READY   STATUS    RESTARTS   AGE
# → api-gateway-xxxxx-yyyyy        1/1     Running   0          2m
# → api-gateway-xxxxx-zzzzz        1/1     Running   0          2m
# → api-gateway-xxxxx-wwwww        1/1     Running   0          2m
# → postgres-0                     1/1     Running   0          5m
# → redis-xxxxx-yyyyy              1/1     Running   0          4m
# → worker-xxxxx-yyyyy             1/1     Running   0          2m
# → worker-xxxxx-zzzzz             1/1     Running   0          2m

kubectl get pods -n monitoring | head -5
# → NAME                                                     READY   STATUS
# → monitoring-grafana-xxxxx                                  3/3     Running
# → monitoring-kube-prometheus-operator-xxxxx                  1/1     Running
# → prometheus-monitoring-kube-prometheus-prometheus-0         2/2     Running

kubectl get pods -n argocd | head -3
# → NAME                                  READY   STATUS
# → argocd-server-xxxxx                   1/1     Running
# → argocd-repo-server-xxxxx              1/1     Running

argocd app get deployforge-prod --grpc-web | grep "Sync Status"
# → Sync Status:            Synced

kubectl argo rollouts status api-gateway -n deployforge-prod
# → Healthy
```

</details>

---

## Exercise 2: Break and Fix

**Goal:** Intentionally break 5 different things in the DeployForge stack and fix each using the observability tools and operational procedures you've built.

### Steps

For each breakage scenario below:
1. Introduce the failure
2. Observe how the system detects it (alerts, dashboards, pod status)
3. Diagnose the root cause using kubectl, Prometheus, Grafana, or logs
4. Fix the issue
5. Verify recovery

### Scenario 1: Crash a Pod

```bash
# Kill the API Gateway process inside a pod
kubectl exec -n deployforge-prod deploy/api-gateway -- kill 1
```

**Observe:** What happens to the pod? Does the liveness probe catch it? Does the ReplicaSet create a replacement? How long until the service is fully healthy again?

### Scenario 2: Exhaust Memory

```bash
# Set an extremely low memory limit on the worker
kubectl set resources deployment worker -n deployforge-prod \
  --limits=memory=16Mi
```

**Observe:** Watch for OOMKilled events. Check `kubectl get events -n deployforge-prod`. How does the HPA/VPA respond?

### Scenario 3: Misconfigure a Secret

```bash
# Replace the database URL with an invalid value
kubectl create secret generic api-gateway-config \
  --namespace deployforge-prod \
  --from-literal=database-url="postgresql://wrong:wrong@nonexistent:5432/nope" \
  --dry-run=client -o yaml | kubectl apply -f -

# Restart pods to pick up the new secret
kubectl rollout restart deployment api-gateway -n deployforge-prod
```

**Observe:** How does the API Gateway behave? What do the logs show? Do the readiness probes catch it? Does the Prometheus error rate increase?

### Scenario 4: Network Partition

```bash
# Apply a NetworkPolicy that blocks all traffic to the API Gateway
cat <<'EOF' | kubectl apply -f -
apiVersion: networking.k8s.io/v1
kind: NetworkPolicy
metadata:
  name: block-api-traffic
  namespace: deployforge-prod
spec:
  podSelector:
    matchLabels:
      app: api-gateway
  policyTypes:
    - Ingress
    - Egress
  ingress: []
  egress: []
EOF
```

**Observe:** What happens to traffic? Can the Ingress still reach the API? Can the API reach the database?

### Scenario 5: Corrupt a Deployment

```bash
# Set an image that doesn't exist
kubectl set image deployment/worker worker=deployforge/worker:nonexistent-tag \
  -n deployforge-prod
```

**Observe:** What happens during the rollout? Does the deployment get stuck? What does `kubectl rollout status` show? How would an Argo Rollout handle this differently?

<details>
<summary>Show solution</summary>

### Fix 1: Crashed Pod

```bash
# The pod auto-restarts via the liveness probe + RestartPolicy
kubectl get pods -n deployforge-prod -l app=api-gateway
# → One pod will show RESTARTS: 1, STATUS: Running
# Recovery time: ~15-30 seconds (liveness probe interval + container startup)

# No manual action needed — this is self-healing by design
```

### Fix 2: Memory Exhaustion

```bash
# Pods are in CrashLoopBackOff with OOMKilled reason
kubectl get events -n deployforge-prod --field-selector reason=OOMKilling
# → OOMKilling: Memory cgroup out of memory

# Fix: Restore reasonable memory limits
kubectl set resources deployment worker -n deployforge-prod \
  --limits=memory=512Mi --requests=memory=256Mi

# Verify recovery
kubectl rollout status deployment worker -n deployforge-prod --timeout=60s
# → deployment "worker" successfully rolled out
```

### Fix 3: Bad Secret

```bash
# Pods are running but readiness probes fail (can't connect to DB)
kubectl logs -n deployforge-prod -l app=api-gateway --tail=10
# → Error: connect ECONNREFUSED nonexistent:5432

# Fix: Restore correct secret
kubectl create secret generic api-gateway-config --namespace deployforge-prod \
  --from-literal=database-url="postgresql://deployforge:$(kubectl get secret postgres-credentials -n deployforge-prod -o jsonpath='{.data.password}' | base64 -d)@postgres:5432/deployforge" \
  --dry-run=client -o yaml | kubectl apply -f -

# Restart to pick up new secret
kubectl rollout restart deployment api-gateway -n deployforge-prod
kubectl rollout status deployment api-gateway -n deployforge-prod --timeout=60s
# → deployment "api-gateway" successfully rolled out
```

### Fix 4: Network Partition

```bash
# All traffic is blocked — API is unreachable
curl -s http://deployforge.local/api/health
# → (timeout or connection refused)

# Fix: Delete the blocking NetworkPolicy
kubectl delete networkpolicy block-api-traffic -n deployforge-prod
# → networkpolicy.networking.k8s.io "block-api-traffic" deleted

# Verify
curl -s http://deployforge.local/api/health | jq
# → { "status": "ok" }
```

### Fix 5: Bad Image

```bash
# Deployment is stuck — new pods can't pull the image
kubectl rollout status deployment worker -n deployforge-prod --timeout=30s
# → Waiting for deployment "worker" rollout to finish: 1 old replicas are pending termination...
# → error: timed out

kubectl get pods -n deployforge-prod -l app=worker
# → NAME                      READY   STATUS             RESTARTS   AGE
# → worker-xxxxx (new)        0/1     ImagePullBackOff   0          30s
# → worker-yyyyy (old)        1/1     Running            0          10m

# Fix: Rollback to previous version
kubectl rollout undo deployment worker -n deployforge-prod
# → deployment.apps/worker rolled back

kubectl rollout status deployment worker -n deployforge-prod --timeout=60s
# → deployment "worker" successfully rolled out
```

</details>

---

## Exercise 3: Production Readiness Review

**Goal:** Complete a formal production readiness review for DeployForge. This is not a theoretical exercise — evaluate the actual state of what you've deployed.

### Steps

1. **Create the PRR document:**

   Create `docs/production-readiness.yaml` using the eight-pillar framework from [01-production-readiness.md](../01-production-readiness.md).

2. **For each pillar, evaluate at least 3 criteria:**

   - Check actual evidence (run commands, verify configs exist)
   - Mark each as `pass`, `partial`, or `fail`
   - For `partial` and `fail` items, write a specific remediation plan with a deadline

3. **Perform FMEA:**

   Identify the top 5 failure modes for DeployForge. Score each on severity, probability, and detection difficulty. Calculate RPN and rank.

4. **Write the summary:**

   Is DeployForge ready for production? Document conditions for a conditional pass or reasons for a fail.

5. **Verify your assessment is honest:**

   ```bash
   # Run these checks to ground your assessment in reality:

   # Security: Are images scanned?
   grep -r "trivy" .github/workflows/ 2>/dev/null && echo "PASS" || echo "FAIL: No Trivy scanning"

   # Reliability: Are SLOs defined?
   ls monitoring/alerts/slo-burn-rate.yaml 2>/dev/null && echo "PASS" || echo "FAIL: No SLO alerts"

   # Observability: Are ServiceMonitors deployed?
   kubectl get servicemonitors -n monitoring 2>/dev/null && echo "PASS" || echo "FAIL: No ServiceMonitors"

   # Scaling: Is HPA configured?
   kubectl get hpa -n deployforge-prod 2>/dev/null && echo "PASS" || echo "FAIL: No HPA"

   # Operability: Do runbooks exist?
   ls docs/runbook.md 2>/dev/null && echo "PASS" || echo "FAIL: No runbooks"
   ```

<details>
<summary>Show solution</summary>

The complete PRR YAML is provided in [01-production-readiness.md](../01-production-readiness.md), Challenge 1. Key evaluation points:

**Likely results for a local kind deployment:**

| Pillar | Status | Notes |
|--------|--------|-------|
| Architecture | Pass | Documented, dependencies mapped |
| Security | Partial | Image scanning in CI, but no mTLS between services |
| Reliability | Pass | SLOs defined, chaos experiments run, rollback tested |
| Observability | Pass | Prometheus + Grafana + alerts configured |
| Scalability | Partial | HPA configured but no 2× load test |
| Operability | Partial | Automated deployment, but runbooks not yet written (Exercise 4) |
| Data Management | Partial | Backups possible but not automated with CronJob |
| Compliance | Fail | No ADRs written yet |

**Summary:** Conditional pass. Conditions: complete runbooks (Exercise 4), write ADRs, automate database backup CronJob, run 2× load test.

</details>

---

## Exercise 4: Operational Runbook

**Goal:** Write a complete operational runbook for DeployForge covering the five most critical operational procedures.

### Steps

Create `docs/runbook.md` with the following sections:

1. **Deployment Procedure:**
   - How to deploy a new version (the GitOps flow)
   - How to verify a deployment succeeded
   - Expected timeline (commit → live traffic)

2. **Rollback Procedure:**
   - How to rollback via Argo Rollouts (abort canary)
   - How to rollback via ArgoCD (revert to previous sync)
   - How to rollback via kubectl (emergency manual rollback)
   - Maximum rollback time target

3. **Scaling Procedure:**
   - How to manually scale in an emergency
   - How auto-scaling works (HPA thresholds and behavior)
   - How to temporarily override auto-scaling
   - Capacity limits and when to add nodes

4. **Backup & Restore:**
   - PostgreSQL backup schedule and procedure
   - Redis backup considerations (ephemeral cache vs persistent data)
   - Restore procedure with step-by-step commands
   - Recovery time objective (RTO) and recovery point objective (RPO)

5. **Incident Response:**
   - Alert acknowledgment SLA (5 minutes)
   - Severity classification (SEV1–SEV4) with examples
   - Escalation path
   - Communication template for status updates
   - Postmortem timeline (48 hours)

For each section, include:
- Step-by-step commands that can be copy-pasted
- Expected output for verification
- Failure scenarios and what to do if the procedure doesn't work

<details>
<summary>Show solution</summary>

```markdown
# DeployForge Operational Runbook

Last updated: 2025-01-15
Owner: Platform Team

---

## 1. Deployment

### Standard Deployment (GitOps)

1. Merge PR to `main` branch
2. CI pipeline runs: lint → test → build → scan → push
3. CI updates image tag in `k8s/overlays/prod/kustomization.yaml`
4. ArgoCD detects drift and auto-syncs
5. Argo Rollouts executes canary: 10% → analysis → 50% → analysis → 100%

**Expected timeline:** ~15 minutes from merge to full promotion

**Verify:**
```bash
argocd app get deployforge-prod --grpc-web | grep -E "Sync|Health"
# → Sync Status: Synced
# → Health Status: Healthy

kubectl argo rollouts status api-gateway -n deployforge-prod
# → Healthy
```

### Emergency Deployment (Skip Canary)

```bash
kubectl argo rollouts promote api-gateway -n deployforge-prod --full
```

---

## 2. Rollback

### Via Argo Rollouts (Preferred)

```bash
kubectl argo rollouts abort api-gateway -n deployforge-prod
# Reverts to previous stable ReplicaSet
# Recovery time: < 60 seconds
```

### Via ArgoCD

```bash
argocd app history deployforge-prod --grpc-web
# Note the previous revision number
argocd app rollback deployforge-prod <REVISION> --grpc-web
```

### Via kubectl (Emergency)

```bash
kubectl rollout undo deployment/api-gateway -n deployforge-prod
kubectl rollout undo deployment/worker -n deployforge-prod
```

**Rollback time target:** < 5 minutes

---

## 3. Scaling

### Emergency Manual Scale

```bash
# Scale API Gateway immediately
kubectl scale rollout api-gateway -n deployforge-prod --replicas=10

# Scale Workers
kubectl scale deployment worker -n deployforge-prod --replicas=5
```

### Override Auto-Scaling

```bash
# Temporarily disable HPA
kubectl delete hpa api-gateway-hpa -n deployforge-prod

# Set desired replicas manually
kubectl scale rollout api-gateway -n deployforge-prod --replicas=8

# Re-enable HPA when emergency passes
kubectl apply -f k8s/scaling/hpa-api.yaml
```

### Capacity Limits

| Resource | Current Limit | Alert Threshold |
|----------|--------------|-----------------|
| API pods | 10 (HPA max) | 8 pods (80%) |
| Worker pods | 5 | Manual scaling |
| PostgreSQL connections | 100 | 80 connections |
| Node CPU | 16 cores total | 70% utilization |
| Node Memory | 32Gi total | 80% utilization |

---

## 4. Backup & Restore

### PostgreSQL Backup

**Schedule:** Every 6 hours via CronJob
**Retention:** 7 days local, 30 days in object storage

```bash
# Manual backup
kubectl exec -n deployforge-prod postgres-0 -- \
  pg_dump -U deployforge -Fc deployforge > backup-$(date +%Y%m%d).sql.gz
```

### PostgreSQL Restore

**RTO:** 30 minutes | **RPO:** 6 hours (last backup)

```bash
# 1. Scale down application
kubectl scale rollout api-gateway -n deployforge-prod --replicas=0
kubectl scale deployment worker -n deployforge-prod --replicas=0

# 2. Restore database
cat backup-file.sql.gz | kubectl exec -i -n deployforge-prod postgres-0 -- \
  pg_restore -U deployforge -d deployforge -Fc --clean

# 3. Scale up application
kubectl scale rollout api-gateway -n deployforge-prod --replicas=3
kubectl scale deployment worker -n deployforge-prod --replicas=2

# 4. Verify
curl -s http://deployforge.local/api/health | jq
```

### Redis

Redis is used as an ephemeral cache. No backup required.
If Redis is lost, the cache rebuilds from PostgreSQL on next access.

---

## 5. Incident Response

### Alert Acknowledgment

**SLA:** Acknowledge within 5 minutes of page.

### Severity Classification

| Severity | Definition | Response | Example |
|----------|-----------|----------|---------|
| SEV1 | Total service outage | All hands, war room | All API requests returning 500 |
| SEV2 | Major degradation | On-call + backup | p99 latency > 5s, 10% error rate |
| SEV3 | Minor impact | On-call investigates | Single worker pod crash-looping |
| SEV4 | No user impact | Next business day | Certificate expires in 14 days |

### Escalation Path

1. Primary on-call (0–15 min)
2. Secondary on-call (15–30 min)
3. Engineering Manager (30–60 min for SEV1/SEV2)
4. VP Engineering (60+ min for SEV1)

### Status Update Template

```
[SEV{N}] DeployForge — {Brief Description}

Status: Investigating / Identified / Mitigated / Resolved
Impact: {User-facing impact description}
Start time: {HH:MM UTC}
Next update: {HH:MM UTC} (every 30 min for SEV1/2)

Current actions:
- {What we're doing now}

Timeline:
- {HH:MM} Alert fired: {alert name}
- {HH:MM} On-call acknowledged
- {HH:MM} {Action taken}
```

### Postmortem

Due within 48 hours of SEV1/SEV2 resolution. Template:

1. **Summary:** What happened, duration, impact
2. **Timeline:** Minute-by-minute from detection to resolution
3. **Root cause:** The actual underlying cause (not the trigger)
4. **What went well:** Detection, response, tooling that helped
5. **What went poorly:** Gaps in monitoring, slow response, missing runbooks
6. **Action items:** Specific, assigned, with deadlines
```

</details>

---

## Exercise 5: Architecture Presentation

**Goal:** Prepare a 10-minute presentation explaining DeployForge's architecture, deployment strategy, and reliability posture to a hypothetical engineering leadership team.

### Steps

1. **Create a presentation outline** covering these topics:

   - **System Architecture** (2 min) — Component diagram, tech stack, data flow
   - **Deployment Pipeline** (2 min) — CI/CD flow from commit to production
   - **Observability** (2 min) — What we monitor, how we alert, SLO targets
   - **Reliability** (2 min) — Failure modes, chaos experiments, auto-healing
   - **Operational Maturity** (2 min) — Runbooks, on-call, scaling, DR capability

2. **For each topic, prepare:**

   - One architecture diagram (ASCII art or drawn)
   - 2–3 key metrics or data points
   - One "what happens when X fails?" scenario

3. **Practice answering these leadership questions:**

   - "What's our current uptime and how do we measure it?"
   - "What happens if we get 10× traffic tomorrow?"
   - "How long does it take to deploy a fix to production?"
   - "What's the blast radius if the database goes down?"
   - "How much does this infrastructure cost and where can we optimize?"

4. **Write the presentation as a markdown document:**

   Create `docs/architecture-presentation.md` with the outline, diagrams, and talking points.

<details>
<summary>Show solution — Presentation Outline</summary>

```markdown
# DeployForge Architecture Presentation

## 1. System Architecture (2 min)

### Component Diagram
```
                    ┌─────────────────────────────────┐
   Users ──▶ Nginx ─▶ API Gateway ──▶ PostgreSQL     │
              (Ingress) (Express/TS)    (Primary)      │
                    │       │                          │
                    │       └──▶ Redis (Cache)         │
                    │       └──▶ Worker (BullMQ)       │
                    │                                  │
                    │  Prometheus → Grafana → Alerts   │
                    │  ArgoCD → Argo Rollouts          │
                    └─────────────────────────────────┘
```

### Key Numbers
- 5 core services, 2 databases
- 7 pods in production namespace
- Express/TypeScript API, BullMQ background processing
- PostgreSQL for persistence, Redis for caching + job queue

---

## 2. Deployment Pipeline (2 min)

### Flow
```
Code Push → CI (5 min) → Image Build → Security Scan → Registry
                                                          │
                      ArgoCD Sync ← Image Tag Update ←────┘
                           │
                    Canary Rollout: 10% → 50% → 100%
                    (with Prometheus-backed health checks)
```

### Key Numbers
- Commit to production: ~15 minutes
- Automated rollback on failure: < 60 seconds
- Zero-downtime deployments via canary strategy

---

## 3. Observability (2 min)

### What We Monitor
- RED metrics: Rate, Error rate, Duration (p50/p95/p99)
- SLO: 99.9% availability, p99 latency < 500ms
- Infrastructure: CPU, memory, disk, network per pod

### Alerting Strategy
- 3-tier burn-rate alerts (critical/warning/info)
- Critical: pages on-call (error budget burns in ~2.5 days)
- Warning: alerts Slack (error budget burns in ~5 days)
- Info: creates ticket (sustained above baseline)

### Current SLO Status
- Error budget remaining: 92% (healthy)
- p99 latency: 180ms (well within 500ms target)

---

## 4. Reliability (2 min)

### Failure Scenarios Tested
| Scenario | Recovery | Method |
|----------|----------|--------|
| Pod crash | 15 sec | Liveness probe + auto-restart |
| Bad deploy | 60 sec | Argo Rollout auto-abort |
| Node failure | 2 min | Pod rescheduling + Cluster Autoscaler |
| DB connection spike | 30 sec | Connection pool limits + PgBouncer |

### Auto-Healing
- ReplicaSets maintain desired pod count
- HPA scales API Gateway from 3→10 based on request rate
- PodDisruptionBudgets prevent unsafe node drains

---

## 5. Operational Maturity (2 min)

### What's in Place
- ✅ Automated GitOps deployment (ArgoCD)
- ✅ Progressive delivery with automated analysis
- ✅ SLO-based alerting with runbooks
- ✅ Database backup every 6 hours
- ✅ Operational runbooks for top 5 procedures
- ✅ PodDisruptionBudgets for safe upgrades

### Known Gaps (with remediation plan)
- ⚠️ No multi-region DR (planned for Q2)
- ⚠️ Single PostgreSQL instance (Patroni HA planned)
- ⚠️ Load testing at 2× not yet completed

### Cost Optimization
- Resource requests right-sized via VPA recommendations
- ResourceQuotas prevent namespace budget overrun
- Spot instances for non-critical workloads (when on cloud)
```

</details>

---

## Final Capstone Checkpoint

Answer these questions to verify your mastery across all 13 modules. You should be able to answer each from memory and demonstrate with commands.

### Container Fundamentals (Module 01)
- [ ] What Linux kernel features make containers possible? Name three.
- [ ] What is the difference between a container runtime and a container engine?

### Docker (Module 02)
- [ ] What is a multi-stage build and why does it reduce image size?
- [ ] How does Docker layer caching affect build time?

### Security (Module 03)
- [ ] Name three ways to harden a container image.
- [ ] What does "shift left" mean in the context of container security?

### Kubernetes Architecture (Module 04)
- [ ] What components make up the Kubernetes control plane?
- [ ] What happens when you run `kubectl apply -f deployment.yaml`? Trace the request.

### Workloads (Module 05)
- [ ] When would you use a StatefulSet instead of a Deployment?
- [ ] What is the difference between a liveness probe and a readiness probe?

### Networking (Module 06)
- [ ] How does a Kubernetes Service route traffic to pods?
- [ ] What is a NetworkPolicy and why is a default-deny policy important?

### Storage & Config (Module 07)
- [ ] What is the difference between a ConfigMap and a Secret?
- [ ] How does Kustomize differ from Helm?

### Observability (Module 08)
- [ ] What are the RED metrics and why are they useful?
- [ ] How does Prometheus discover scrape targets in Kubernetes?

### Reliability (Module 09)
- [ ] Define SLI, SLO, and error budget. How do they relate?
- [ ] What is a multi-burn-rate alert and why is it better than a simple threshold?

### CI/CD & GitOps (Module 10)
- [ ] What makes a deployment "GitOps"?
- [ ] Describe the canary deployment strategy. What metrics verify canary health?

### Infrastructure as Code (Module 11)
- [ ] What is Terraform state and why must it be protected?
- [ ] What is the difference between Terraform `plan` and `apply`?

### Scaling & Cost (Module 12)
- [ ] How does HPA decide when to scale? What metrics can it use?
- [ ] What is a PodDisruptionBudget and when is it enforced?

### Capstone Integration (Module 13)
- [ ] Walk through the complete flow from code commit to production traffic.
- [ ] What are the eight pillars of a production readiness review?
- [ ] What is your plan for day-2 operations: backups, upgrades, incident response?

---

## Checklist

- [ ] Deployed complete DeployForge stack from scratch on fresh cluster
- [ ] All services running: API Gateway, Worker, PostgreSQL, Redis, Nginx
- [ ] Observability stack operational: Prometheus, Grafana, Alertmanager
- [ ] GitOps configured: ArgoCD Application synced and healthy
- [ ] Progressive delivery configured: Argo Rollout with canary strategy
- [ ] Auto-scaling configured: HPA for API Gateway
- [ ] Broke and fixed 5 different failure scenarios
- [ ] Completed production readiness review document
- [ ] Wrote operational runbook with 5 procedures
- [ ] Prepared architecture presentation
- [ ] Answered all capstone checkpoint questions
- [ ] Can deploy, monitor, break, fix, and explain the entire system

**Congratulations.** You've completed the Docker, Kubernetes & SRE learning track. You can now build, deploy, and operate production-grade containerized applications. The next step is to apply these skills to real systems — and to keep learning, because infrastructure never stops evolving.
