# 9.2 — Chaos Engineering: Breaking Things on Purpose

## Concept

Your system *will* fail in production. The question is whether you discover the failure mode at 3 AM when real users are affected, or at 2 PM on a Tuesday during a controlled experiment. Chaos engineering is the discipline of proactively injecting failures into a system to build confidence in its ability to withstand turbulent conditions.

The key word is *confidence* — not *proof*. Chaos experiments don't prove a system is reliable; they reveal specific weaknesses you didn't know about. Every experiment either confirms a hypothesis ("the system gracefully handles a pod crash") or — more valuably — uncovers a gap in your resilience.

---

## Deep Dive

### Principles of Chaos Engineering

The Principles of Chaos Engineering (originally from Netflix) define a rigorous experimental methodology:

```
┌─────────────────────────────────────────────────────────────────────┐
│                  The Chaos Experiment Lifecycle                       │
│                                                                      │
│  1. Define Steady State          "What does 'normal' look like?"     │
│     │                                                                │
│     ▼                                                                │
│  2. Form Hypothesis              "The system will maintain steady    │
│     │                             state when we kill a pod"          │
│     ▼                                                                │
│  3. Introduce Variables          Inject the failure                  │
│     │                             (pod kill, network delay, etc.)    │
│     ▼                                                                │
│  4. Observe Difference           Compare against steady state        │
│     │                                                                │
│     ├──▶ Hypothesis confirmed    System is resilient to this failure │
│     │                                                                │
│     └──▶ Hypothesis disproved    You found a weakness — fix it!     │
│                                                                      │
│  5. Minimize Blast Radius        Start small, widen gradually        │
│                                                                      │
└─────────────────────────────────────────────────────────────────────┘
```

| Principle | Description | Example |
|-----------|-------------|---------|
| **Steady State Hypothesis** | Define measurable "normal" behavior before the experiment | p99 latency < 500ms, error rate < 0.1%, queue depth < 100 |
| **Vary Real-World Events** | Inject failures that actually happen in production | Pod OOMKill, network partition, disk pressure, clock skew |
| **Run in Production** | Test against real systems (start with staging, graduate to prod) | Run during business hours with the team watching |
| **Automate to Run Continuously** | Experiments should run on a schedule, not just during GameDays | CI/CD pipeline includes chaos suite on every deploy |
| **Minimize Blast Radius** | Start with the smallest possible scope | Kill one pod, not the whole deployment; one AZ, not the region |

> **Key insight:** Chaos engineering is *not* random destruction. It's the scientific method applied to distributed systems — hypothesis, experiment, observation, conclusion.

### Chaos Experiment Types

| Type | What It Tests | Tools | Blast Radius |
|------|--------------|-------|-------------|
| **Pod failure** | Restart/kill tolerance, replica failover | Chaos Mesh PodChaos, `kubectl delete pod` | Single pod → Deployment |
| **Network partition** | Service mesh resilience, timeout handling | Chaos Mesh NetworkChaos, `tc netem` | Pod-to-pod → Namespace |
| **Network latency** | Timeout configuration, retry logic, circuit breakers | Chaos Mesh NetworkChaos | Single route → All egress |
| **CPU stress** | Autoscaling, throttling, priority-based scheduling | Chaos Mesh StressChaos, `stress-ng` | Single container → Node |
| **Memory pressure** | OOM handling, eviction order, resource limits | Chaos Mesh StressChaos | Single container → Node |
| **Disk fill** | Log rotation, PVC monitoring, graceful degradation | Chaos Mesh IOChaos | Single PVC → Node disk |
| **DNS failure** | DNS caching, fallback resolution, hardcoded IPs | Chaos Mesh DNSChaos | Single service → Cluster DNS |
| **Clock skew** | Certificate validation, token expiry, log ordering | Chaos Mesh TimeChaos | Single pod |

### Chaos Mesh: Kubernetes-Native Chaos

Chaos Mesh is a CNCF incubating project that runs chaos experiments as Kubernetes custom resources. This makes experiments declarative, version-controlled, and integrated into GitOps workflows.

```bash
# Install Chaos Mesh into your kind cluster
helm repo add chaos-mesh https://charts.chaos-mesh.org
helm repo update

helm install chaos-mesh chaos-mesh/chaos-mesh \
  --namespace chaos-mesh \
  --create-namespace \
  --set chaosDaemon.runtime=containerd \
  --set chaosDaemon.socketPath=/run/containerd/containerd.sock \
  --version 2.7.0

# Verify installation
kubectl get pods -n chaos-mesh
# → chaos-controller-manager-xxx   Running
# → chaos-daemon-xxx               Running (DaemonSet, one per node)
# → chaos-dashboard-xxx            Running
```

### Experiment 1: Pod Failure

Test that DeployForge API Gateway recovers when a pod is killed:

```yaml
# chaos-pod-kill.yaml
apiVersion: chaos-mesh.org/v1alpha1
kind: PodChaos
metadata:
  name: api-gateway-pod-kill
  namespace: deployforge
  labels:
    experiment: pod-resilience
    target: api-gateway
spec:
  action: pod-kill
  mode: one                    # Kill exactly one pod
  selector:
    namespaces:
      - deployforge
    labelSelectors:
      app: api-gateway
  duration: "30s"              # Wait 30s, then Chaos Mesh cleans up
```

```bash
# Before the experiment: record steady state
kubectl get pods -n deployforge -l app=api-gateway
# → api-gateway-7d8f9b6c5-abc12   1/1   Running
# → api-gateway-7d8f9b6c5-def34   1/1   Running
# → api-gateway-7d8f9b6c5-ghi56   1/1   Running

# Apply the experiment
kubectl apply -f chaos-pod-kill.yaml

# Observe: one pod killed, Deployment controller creates replacement
kubectl get pods -n deployforge -l app=api-gateway -w
# → api-gateway-7d8f9b6c5-abc12   1/1   Terminating
# → api-gateway-7d8f9b6c5-jkl78   0/1   ContainerCreating
# → api-gateway-7d8f9b6c5-jkl78   1/1   Running

# Verify steady state maintained: check error rate during the experiment
# (In Grafana or via PromQL)
# rate(http_requests_total{job="api-gateway",status=~"5.."}[1m]) should stay near 0
```

**Hypothesis:** With 3 replicas and a Kubernetes Service load-balancing traffic, killing one pod should cause zero user-visible errors because traffic shifts to the remaining 2 pods before the replacement starts.

### Experiment 2: Network Latency Injection

Test that the API Gateway handles slow responses from the Worker Service:

```yaml
# chaos-network-latency.yaml
apiVersion: chaos-mesh.org/v1alpha1
kind: NetworkChaos
metadata:
  name: worker-network-delay
  namespace: deployforge
  labels:
    experiment: network-resilience
    target: worker
spec:
  action: delay
  mode: all                     # Affect all matching pods
  selector:
    namespaces:
      - deployforge
    labelSelectors:
      app: worker
  delay:
    latency: "200ms"            # Add 200ms to every packet
    correlation: "100"          # Apply to 100% of packets
    jitter: "50ms"              # ±50ms variation
  direction: to                 # Affect incoming traffic to worker
  duration: "5m"
```

```bash
kubectl apply -f chaos-network-latency.yaml

# Monitor the API Gateway's latency SLI during the experiment
# Expected: p99 latency rises from ~100ms to ~300ms
# The SLO (p99 < 500ms) should still hold if the system is well-configured

# Check if circuit breaker / timeout logic kicks in
kubectl logs -n deployforge -l app=api-gateway --tail=50 | grep -i "timeout\|circuit\|retry"
```

**Hypothesis:** The API Gateway has a 1-second timeout for worker calls and retry logic with exponential backoff. Adding 200ms latency should increase p99 but stay within the 500ms SLO threshold.

### Experiment 3: CPU Stress

Test autoscaling and request prioritization under CPU pressure:

```yaml
# chaos-cpu-stress.yaml
apiVersion: chaos-mesh.org/v1alpha1
kind: StressChaos
metadata:
  name: api-gateway-cpu-stress
  namespace: deployforge
  labels:
    experiment: resource-resilience
    target: api-gateway
spec:
  mode: one                     # Stress one pod
  selector:
    namespaces:
      - deployforge
    labelSelectors:
      app: api-gateway
  stressors:
    cpu:
      workers: 2                # 2 CPU stress workers
      load: 80                  # Target 80% CPU usage
  duration: "3m"
```

### Safety Mechanisms: Abort Conditions

Every chaos experiment needs an escape hatch. Define abort conditions that automatically halt the experiment if things go wrong:

```yaml
# chaos-pod-kill-with-abort.yaml
apiVersion: chaos-mesh.org/v1alpha1
kind: PodChaos
metadata:
  name: api-gateway-pod-kill-safe
  namespace: deployforge
  annotations:
    experiment.chaos-mesh.org/abort-on-slo-breach: "true"
spec:
  action: pod-kill
  mode: one
  selector:
    namespaces:
      - deployforge
    labelSelectors:
      app: api-gateway
  duration: "60s"
```

For more sophisticated abort conditions, use a **Workflow**:

```yaml
# chaos-workflow-with-abort.yaml
apiVersion: chaos-mesh.org/v1alpha1
kind: Workflow
metadata:
  name: api-resilience-suite
  namespace: deployforge
spec:
  entry: serial-experiments
  templates:
    - name: serial-experiments
      templateType: Serial
      deadline: "10m"           # Hard deadline: abort everything after 10 min
      children:
        - steady-state-check
        - pod-kill-experiment
        - verify-recovery

    - name: steady-state-check
      templateType: Task
      task:
        # Verify SLO is healthy before starting
        container:
          name: check
          image: curlimages/curl:latest
          command:
            - sh
            - -c
            - |
              ERROR_RATE=$(curl -s "http://prometheus.monitoring:9090/api/v1/query" \
                --data-urlencode 'query=deployforge:api_errors:ratio_rate5m' \
                | jq -r '.data.result[0].value[1]')
              if (( $(echo "$ERROR_RATE > 0.001" | bc -l) )); then
                echo "ABORT: Error rate already elevated ($ERROR_RATE)"
                exit 1
              fi
              echo "Steady state confirmed: error rate = $ERROR_RATE"

    - name: pod-kill-experiment
      templateType: PodChaos
      deadline: "2m"
      podChaos:
        action: pod-kill
        mode: one
        selector:
          namespaces:
            - deployforge
          labelSelectors:
            app: api-gateway

    - name: verify-recovery
      templateType: Task
      task:
        container:
          name: verify
          image: curlimages/curl:latest
          command:
            - sh
            - -c
            - |
              sleep 30
              PODS_READY=$(kubectl get pods -n deployforge -l app=api-gateway \
                -o jsonpath='{.items[*].status.conditions[?(@.type=="Ready")].status}' \
                | tr ' ' '\n' | grep -c True)
              echo "Ready pods: $PODS_READY"
              if [ "$PODS_READY" -lt 3 ]; then
                echo "WARNING: Not all pods recovered"
                exit 1
              fi
              echo "Recovery confirmed"
```

### GameDay Planning

A GameDay is a structured event where the team intentionally runs chaos experiments against a system. Think of it as a fire drill for your infrastructure.

```
┌─────────────────────────────────────────────────────────────────────┐
│                      GameDay Timeline                                │
│                                                                      │
│  Week Before                                                         │
│  ├── Define scope (which services, which failure modes)              │
│  ├── Write experiment manifests and abort conditions                 │
│  ├── Notify stakeholders (product, support, management)              │
│  ├── Ensure monitoring dashboards are ready                          │
│  └── Assign roles: Operator, Observer, Incident Commander            │
│                                                                      │
│  GameDay Morning                                                     │
│  ├── 09:00  Kickoff meeting — review scope and abort criteria        │
│  ├── 09:30  Verify steady state baseline                             │
│  ├── 10:00  Experiment 1: Pod kill (low blast radius)                │
│  ├── 10:30  Review results, document findings                        │
│  ├── 11:00  Experiment 2: Network latency (medium blast radius)      │
│  ├── 11:30  Review results, document findings                        │
│  └── 12:00  Lunch break                                              │
│                                                                      │
│  GameDay Afternoon                                                   │
│  ├── 13:00  Experiment 3: CPU stress + pod kill (combined)           │
│  ├── 13:30  Review results, document findings                        │
│  ├── 14:00  Experiment 4: DNS failure (if confidence is high)        │
│  ├── 14:30  Final review and findings summary                        │
│  └── 15:00  Retrospective — action items and next GameDay plan       │
│                                                                      │
│  Week After                                                          │
│  ├── File bugs for discovered weaknesses                             │
│  ├── Prioritize fixes against error budget status                    │
│  └── Schedule next GameDay (monthly cadence)                         │
└─────────────────────────────────────────────────────────────────────┘
```

> **Production note:** Your first GameDay should be in a staging environment. Graduate to production only after the team has run at least 2-3 successful staging GameDays and has confidence in abort mechanisms.

### GameDay Roles

| Role | Responsibility | Who |
|------|---------------|-----|
| **Operator** | Applies chaos experiments, monitors blast radius | SRE / Platform engineer |
| **Observer** | Watches dashboards, records SLI impact, takes notes | Any engineer |
| **Incident Commander** | Calls abort if things go wrong, manages communication | Senior SRE |
| **Stakeholder Liaison** | Keeps product/management informed of progress | Engineering manager |

### The Chaos Maturity Model

Teams progress through levels of chaos engineering maturity:

```
Level 0: Ad-Hoc          "We've never intentionally broken anything"
    │
    ▼
Level 1: Manual          "We run kubectl delete pod during GameDays"
    │
    ▼
Level 2: Automated       "Chaos Mesh experiments run on a schedule in staging"
    │
    ▼
Level 3: Continuous      "Chaos experiments run in production CI/CD pipeline"
    │
    ▼
Level 4: Antifragile     "The system automatically detects and remediates
                          failures faster than chaos can inject them"
```

| Level | Characteristics | DeployForge Target |
|-------|-----------------|-------------------|
| 0 — Ad-Hoc | No intentional failure testing | — |
| 1 — Manual | GameDays, manual `kubectl delete pod` | Module 09 starting point |
| 2 — Automated | Chaos Mesh experiments in Git, scheduled runs in staging | Module 09 goal |
| 3 — Continuous | Chaos in CI/CD pipeline, automated in production | Module 10 stretch goal |
| 4 — Antifragile | Self-healing with automated runbooks, minimal human intervention | Long-term vision |

### Choosing What to Break

Not all experiments are equally valuable. Prioritize based on:

```
                    High Impact
                        │
         ┌──────────────┼──────────────┐
         │              │              │
         │   ★ START    │   Aspirational│
         │   HERE       │   (do later)  │
         │              │              │
    Easy ├──────────────┼──────────────┤ Hard
    to   │              │              │ to
    Run  │   Low value  │   Skip       │ Run
         │   (don't     │   (not worth │
         │    bother)   │    the cost) │
         │              │              │
         └──────────────┼──────────────┘
                        │
                    Low Impact
```

> **Production tip:** Start with the experiments most likely to find real problems: pod failures (tests replica count and readiness probes), network latency (tests timeouts and retries), and dependency unavailability (tests circuit breakers and graceful degradation).

---

## Code Examples

### TypeScript: Chaos Experiment Runner with Safety Checks

```typescript
// chaos-runner.ts — Programmatic chaos experiment execution
import { KubeConfig, CustomObjectsApi, CoreV1Api } from "@kubernetes/client-node";

interface ChaosExperiment {
  name: string;
  kind: "PodChaos" | "NetworkChaos" | "StressChaos";
  manifest: object;
  steadyStateCheck: () => Promise<boolean>;
  abortThreshold: {
    maxErrorRate: number;
    maxLatencyP99Ms: number;
  };
}

async function runExperiment(experiment: ChaosExperiment): Promise<{
  passed: boolean;
  findings: string[];
}> {
  const kc = new KubeConfig();
  kc.loadFromDefault();
  const customApi = kc.makeApiClient(CustomObjectsApi);
  const findings: string[] = [];

  // Step 1: Verify steady state before starting
  console.log(`[${experiment.name}] Checking steady state...`);
  const steadyStateOk = await experiment.steadyStateCheck();
  if (!steadyStateOk) {
    return {
      passed: false,
      findings: ["ABORT: Steady state check failed before experiment started"],
    };
  }

  // Step 2: Apply the chaos experiment
  console.log(`[${experiment.name}] Injecting failure...`);
  await customApi.createNamespacedCustomObject(
    "chaos-mesh.org",
    "v1alpha1",
    "deployforge",
    experiment.kind.toLowerCase(),
    experiment.manifest
  );

  // Step 3: Monitor during the experiment (poll every 10s for 2 min)
  let aborted = false;
  for (let i = 0; i < 12; i++) {
    await new Promise((r) => setTimeout(r, 10_000));

    const stillSteady = await experiment.steadyStateCheck();
    if (!stillSteady) {
      findings.push(`Steady state violated at T+${(i + 1) * 10}s`);
      // Check if we should abort
      aborted = true;
      console.log(`[${experiment.name}] ABORTING — steady state violated`);
      break;
    }
  }

  // Step 4: Clean up
  console.log(`[${experiment.name}] Cleaning up...`);
  await customApi.deleteNamespacedCustomObject(
    "chaos-mesh.org",
    "v1alpha1",
    "deployforge",
    experiment.kind.toLowerCase(),
    experiment.name
  );

  // Step 5: Wait for recovery and verify
  await new Promise((r) => setTimeout(r, 30_000));
  const recovered = await experiment.steadyStateCheck();
  if (!recovered) {
    findings.push("System did not recover within 30s after experiment ended");
  }

  return {
    passed: !aborted && recovered,
    findings: findings.length > 0 ? findings : ["No issues found — hypothesis confirmed"],
  };
}

// Example usage
const podKillExperiment: ChaosExperiment = {
  name: "api-gateway-pod-kill",
  kind: "PodChaos",
  manifest: {
    apiVersion: "chaos-mesh.org/v1alpha1",
    kind: "PodChaos",
    metadata: { name: "api-gateway-pod-kill", namespace: "deployforge" },
    spec: {
      action: "pod-kill",
      mode: "one",
      selector: {
        namespaces: ["deployforge"],
        labelSelectors: { app: "api-gateway" },
      },
      duration: "30s",
    },
  },
  steadyStateCheck: async () => {
    // Query Prometheus for current error rate
    const res = await fetch(
      `http://localhost:9090/api/v1/query?query=deployforge:api_errors:ratio_rate5m`
    );
    const data = await res.json();
    const errorRate = parseFloat(data.data.result[0]?.value[1] ?? "0");
    return errorRate < 0.001; // < 0.1% errors
  },
  abortThreshold: { maxErrorRate: 0.01, maxLatencyP99Ms: 1000 },
};
```

### Bash: Quick Chaos Experiment Script

```bash
#!/usr/bin/env bash
# run-chaos.sh — Run a chaos experiment with safety checks
set -euo pipefail

EXPERIMENT_FILE="${1:?Usage: run-chaos.sh <experiment.yaml>}"
NAMESPACE="deployforge"
PROMETHEUS="http://localhost:9090"
MAX_ERROR_RATE="0.01"

echo "=== Pre-flight Steady State Check ==="
ERROR_RATE=$(curl -s "${PROMETHEUS}/api/v1/query" \
  --data-urlencode "query=deployforge:api_errors:ratio_rate5m" \
  | jq -r '.data.result[0].value[1] // "0"')

if (( $(echo "$ERROR_RATE > $MAX_ERROR_RATE" | bc -l) )); then
  echo "❌ ABORT: Error rate already elevated (${ERROR_RATE})"
  exit 1
fi
echo "✅ Steady state OK (error rate: ${ERROR_RATE})"

echo ""
echo "=== Applying Chaos Experiment ==="
kubectl apply -f "$EXPERIMENT_FILE"
EXPERIMENT_NAME=$(kubectl get -f "$EXPERIMENT_FILE" -o jsonpath='{.metadata.name}')

echo "Monitoring for 2 minutes..."
for i in $(seq 1 12); do
  sleep 10
  CURRENT_RATE=$(curl -s "${PROMETHEUS}/api/v1/query" \
    --data-urlencode "query=deployforge:api_errors:ratio_rate5m" \
    | jq -r '.data.result[0].value[1] // "0"')
  echo "  T+$((i * 10))s — error rate: ${CURRENT_RATE}"

  if (( $(echo "$CURRENT_RATE > $MAX_ERROR_RATE" | bc -l) )); then
    echo "⚠️  Error rate exceeded threshold — aborting!"
    kubectl delete -f "$EXPERIMENT_FILE"
    exit 1
  fi
done

echo ""
echo "=== Cleaning Up ==="
kubectl delete -f "$EXPERIMENT_FILE"

echo ""
echo "=== Post-Experiment Recovery Check ==="
sleep 30
FINAL_RATE=$(curl -s "${PROMETHEUS}/api/v1/query" \
  --data-urlencode "query=deployforge:api_errors:ratio_rate5m" \
  | jq -r '.data.result[0].value[1] // "0"')

PODS_READY=$(kubectl get pods -n "$NAMESPACE" -l app=api-gateway \
  -o jsonpath='{.items[*].status.conditions[?(@.type=="Ready")].status}' \
  | tr ' ' '\n' | grep -c True || true)

echo "Error rate:  ${FINAL_RATE}"
echo "Ready pods:  ${PODS_READY}"

if (( $(echo "$FINAL_RATE < $MAX_ERROR_RATE" | bc -l) )) && [ "$PODS_READY" -ge 3 ]; then
  echo "✅ Experiment PASSED — system recovered successfully"
else
  echo "❌ Experiment FAILED — system did not fully recover"
  exit 1
fi
```

---

## Try It Yourself

### Challenge 1: Write a Network Partition Experiment

Create a Chaos Mesh `NetworkChaos` manifest that partitions the API Gateway from the Worker Service (drops all traffic between them) for 60 seconds. Include a hypothesis about what should happen.

<details>
<summary>Show solution</summary>

**Hypothesis:** The API Gateway has a 5-second timeout for worker calls. During the partition, API requests that require worker processing will fail with a 504 timeout, but health checks and static endpoints will continue to succeed. After the partition is removed, normal operation resumes within 10 seconds.

```yaml
# chaos-network-partition.yaml
apiVersion: chaos-mesh.org/v1alpha1
kind: NetworkChaos
metadata:
  name: gateway-worker-partition
  namespace: deployforge
  labels:
    experiment: network-resilience
spec:
  action: partition
  mode: all
  selector:
    namespaces:
      - deployforge
    labelSelectors:
      app: api-gateway
  direction: to
  target:
    selector:
      namespaces:
        - deployforge
      labelSelectors:
        app: worker
    mode: all
  duration: "60s"
```

**Verification:**

```bash
# During the experiment:
# - Health endpoint should still work
curl http://localhost:8080/health
# → {"status":"ok"}

# - Endpoints requiring worker should timeout
curl http://localhost:8080/api/deployments
# → {"error":"Service Unavailable","message":"Worker timeout"}

# After cleanup: all endpoints recover
curl http://localhost:8080/api/deployments
# → {"deployments":[...]}
```

</details>

### Challenge 2: Design a Combined Failure Experiment

Write a Chaos Mesh Workflow that applies two failures simultaneously: CPU stress on the worker AND network latency between gateway and worker. This simulates a noisy-neighbor scenario on a shared node.

<details>
<summary>Show solution</summary>

```yaml
# chaos-combined-stress.yaml
apiVersion: chaos-mesh.org/v1alpha1
kind: Workflow
metadata:
  name: combined-stress-test
  namespace: deployforge
spec:
  entry: parallel-failures
  templates:
    - name: parallel-failures
      templateType: Parallel
      deadline: "5m"
      children:
        - cpu-stress
        - network-latency

    - name: cpu-stress
      templateType: StressChaos
      deadline: "3m"
      stressChaos:
        mode: one
        selector:
          namespaces:
            - deployforge
          labelSelectors:
            app: worker
        stressors:
          cpu:
            workers: 2
            load: 70
        duration: "3m"

    - name: network-latency
      templateType: NetworkChaos
      deadline: "3m"
      networkChaos:
        action: delay
        mode: all
        selector:
          namespaces:
            - deployforge
          labelSelectors:
            app: api-gateway
        delay:
          latency: "150ms"
          jitter: "30ms"
          correlation: "80"
        direction: to
        target:
          selector:
            namespaces:
              - deployforge
            labelSelectors:
              app: worker
          mode: all
        duration: "3m"
```

**Hypothesis:** Under combined CPU stress and network latency, the API Gateway's p99 latency will rise above 500ms (breaching the latency SLO), but the availability SLO should hold because requests still succeed — they're just slow. This experiment reveals whether your SLO alerting correctly distinguishes between availability and latency budget consumption.

</details>

### Challenge 3: Create a GameDay Checklist

Write a complete GameDay checklist for DeployForge that includes: pre-flight checks, 3 experiments in order of increasing blast radius, abort criteria, and a post-GameDay action item template.

<details>
<summary>Show solution</summary>

```markdown
# DeployForge GameDay Checklist

## Pre-Flight (30 min before)
- [ ] Verify all DeployForge pods are Running and Ready
      `kubectl get pods -n deployforge`
- [ ] Confirm error budget is > 50% for both SLOs
      Check Grafana error budget dashboard
- [ ] Verify Chaos Mesh is installed and healthy
      `kubectl get pods -n chaos-mesh`
- [ ] Confirm all monitoring dashboards load correctly
- [ ] Notify #deployforge-ops channel: "GameDay starting in 30 min"
- [ ] Assign roles: Operator, Observer, Incident Commander
- [ ] Ensure abort procedure is understood by all participants

## Abort Criteria
- Error rate exceeds 5% for more than 30 seconds
- Any pod stuck in CrashLoopBackOff for more than 2 minutes
- Error budget drops below 25%
- Incident Commander calls abort for any reason

## Experiments

### Experiment 1: Single Pod Kill (Low Blast Radius)
- [ ] Record baseline: pod count, error rate, p99 latency
- [ ] Apply: `kubectl apply -f chaos-pod-kill.yaml`
- [ ] Observe: pod replacement time, error rate impact
- [ ] Verify: all 3 replicas running within 60s
- [ ] Document: time to recovery, errors during experiment
- [ ] 5-minute cool-down before next experiment

### Experiment 2: Network Latency 200ms (Medium Blast Radius)
- [ ] Record baseline
- [ ] Apply: `kubectl apply -f chaos-network-latency.yaml`
- [ ] Observe: p99 latency change, timeout behavior
- [ ] Check: circuit breaker activation in logs
- [ ] Verify: latency returns to baseline after cleanup
- [ ] Document findings
- [ ] 5-minute cool-down

### Experiment 3: Combined CPU Stress + Pod Kill (High Blast Radius)
- [ ] Record baseline
- [ ] Apply CPU stress first, wait 30s for it to take effect
- [ ] Then apply pod kill
- [ ] Observe: HPA response, recovery under load
- [ ] Verify: all pods healthy, metrics normal within 2 min
- [ ] Document findings

## Post-GameDay
- [ ] Remove all chaos experiments
      `kubectl delete podchaos,networkchaos,stresschaos --all -n deployforge`
- [ ] Verify steady state restored
- [ ] Write findings summary (template below)

## Findings Template
| Experiment | Hypothesis | Result | Finding | Action Item | Priority |
|-----------|-----------|--------|---------|-------------|----------|
| Pod Kill  | Zero errors | ... | ... | ... | P1/P2/P3 |
| Net Delay | p99 < 500ms | ... | ... | ... | P1/P2/P3 |
| Combined  | Avail holds | ... | ... | ... | P1/P2/P3 |
```

</details>

---

## Capstone Connection

**DeployForge** uses chaos engineering to validate its reliability claims before users discover weaknesses:

- **Chaos Mesh integration** — Chaos Mesh is installed in the `kind` cluster alongside DeployForge. Experiment manifests live in the `deployforge/chaos/` directory and are version-controlled alongside application code. This means chaos experiments evolve with the system they test.
- **Pod resilience suite** — `PodChaos` experiments validate that the API Gateway and Worker Service recover from pod failures within 30 seconds, with zero user-visible errors when running with 3+ replicas. The Deployment's `maxUnavailable: 1` and readiness probes ensure traffic shifts before the replacement pod is ready.
- **Network resilience suite** — `NetworkChaos` experiments inject latency and partitions between services to validate timeout configuration, retry logic, and circuit breaker behavior. These experiments directly test the resilience patterns configured in the service mesh.
- **GameDay cadence** — DeployForge follows a monthly GameDay schedule with experiments that increase in blast radius over time. Results feed into the error budget policy from Section 9.1 — if a GameDay reveals a weakness, the error budget allocation for the next sprint adjusts to account for reliability work.
- **CI/CD integration** — In Module 10, a lightweight chaos smoke test (single pod kill + network delay) will run as part of the deployment pipeline. If the new version can't survive basic failures, the deployment rolls back automatically.
