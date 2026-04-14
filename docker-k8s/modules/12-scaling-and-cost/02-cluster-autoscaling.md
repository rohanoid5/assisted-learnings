# 12.2 — Cluster Autoscaler & Node Management

## Concept

Pod autoscalers decide *how many* pods (or how large) your workloads should be, but
they can't conjure the compute to run them. When the scheduler can't place a pod
because no node has enough capacity, the pod sits in `Pending` — and your users sit
waiting. The Cluster Autoscaler (CA) closes this gap by watching for unschedulable
pods, requesting new nodes from the cloud provider, and — just as importantly —
removing underutilized nodes to stop you from paying for idle machines.

Understanding the CA's algorithm, its timing characteristics, and its limitations is
essential for any production Kubernetes operator. This section also covers Karpenter,
a newer alternative that takes a fundamentally different approach: instead of
choosing from pre-defined node groups, Karpenter provisions the exact instance type
a pending pod needs — just in time.

---

## Deep Dive

### Cluster Autoscaler Algorithm

The CA runs as a deployment in the cluster (typically in `kube-system`) and operates
on two axes: **scale-up** and **scale-down**.

```
                    Cluster Autoscaler Decision Loop
                    ════════════════════════════════

  ┌─────────────────────────────────────────────────────────────┐
  │                                                             │
  │   1. List all pods                                          │
  │      └─▶ Filter: status = Pending AND                       │
  │          schedulerError = "Insufficient cpu/memory"         │
  │                                                             │
  │   2. For each node group:                                   │
  │      └─▶ Simulate: "If I add a node of this type,          │
  │          would any pending pods become schedulable?"        │
  │                                                             │
  │   3. Pick the node group that satisfies the most pods       │
  │      └─▶ Strategy: least-waste | most-pods | random |      │
  │          price (Karpenter only)                             │
  │                                                             │
  │   4. Request node(s) from cloud provider ASG / MIG / VMSS  │
  │      └─▶ Wait for node to join cluster (1–5 min)           │
  │                                                             │
  │   5. Scheduler places pending pods on new node              │
  │                                                             │
  └─────────────────────────────────────────────────────────────┘

  ┌─────────────────────────────────────────────────────────────┐
  │  SCALE DOWN (runs every 10s, separate from scale-up):       │
  │                                                             │
  │  1. For each node, compute utilization:                     │
  │     utilization = sum(pod requests) / node allocatable      │
  │                                                             │
  │  2. If utilization < threshold (default 50%) for            │
  │     scale-down-delay (default 10 min):                      │
  │     └─▶ Can all pods be rescheduled elsewhere?              │
  │         └─▶ Yes: drain & delete node                        │
  │         └─▶ No: skip (e.g., PDB, local storage, annotation)│
  │                                                             │
  └─────────────────────────────────────────────────────────────┘
```

> **Key insight:** The CA uses **resource requests**, not actual utilization, to
> decide whether a node is underutilized. A node running pods that request 200m CPU
> but actually use 1500m will appear "almost empty" to the CA. This is why accurate
> resource requests (Module 12.1 — VPA) are critical for cost optimization.

### Scale-Up Timing

The end-to-end time from "pod is pending" to "pod is running on a new node" breaks
down as follows:

| Phase | Typical Duration | What's Happening |
|-------|-----------------|------------------|
| CA detects pending pod | 10–30s | CA scan interval (default 10s) |
| CA decides which node group | <1s | Simulation + strategy selection |
| Cloud API provisions instance | 30s–3min | AWS: launch EC2; GCP: create GCE; Azure: create VM |
| Node boots + joins cluster | 30s–2min | Kubelet starts, registers with API server |
| Node becomes `Ready` | 10–30s | Node passes readiness checks |
| Scheduler places pod | <1s | Immediate once node is Ready |
| **Total** | **~2–6 minutes** | |

```
Timeline: Pod Pending → Running
═══════════════════════════════

t=0s     Pod created, no capacity → Pending
t=10s    CA detects pending pod
t=11s    CA requests new node from ASG
t=90s    EC2 instance running, kubelet starting
t=150s   Node Ready, joins cluster
t=151s   Pod scheduled on new node
t=160s   Pod Running (after image pull)

Total: ~2.5 minutes (best case, cached AMI + warm AZ)
```

> **Production note:** If your workload can't tolerate 2–6 minutes of pending time,
> consider **overprovisioning** — running low-priority placeholder pods that can be
> preempted instantly, giving real pods immediate capacity while the CA provisions
> replacement nodes.

### Node Groups and Scaling Limits

The CA operates on **node groups** (ASG in AWS, MIG in GCP, VMSS in Azure). Each
group represents a pool of identically-configured nodes.

```yaml
# cluster-autoscaler deployment args (partial)
containers:
  - name: cluster-autoscaler
    image: registry.k8s.io/autoscaling/cluster-autoscaler:v1.29.0
    command:
      - ./cluster-autoscaler
      - --cloud-provider=aws
      - --node-group-auto-discovery=asg:tag=k8s.io/cluster-autoscaler/enabled,k8s.io/cluster-autoscaler/deployforge-prod
      - --balance-similar-node-groups=true       # ← spread across AZs
      - --skip-nodes-with-local-storage=false
      - --skip-nodes-with-system-pods=false
      - --scale-down-enabled=true
      - --scale-down-delay-after-add=10m         # ← don't remove nodes just added
      - --scale-down-delay-after-delete=0s
      - --scale-down-delay-after-failure=3m
      - --scale-down-unneeded-time=10m           # ← node must be idle for 10 min
      - --scale-down-utilization-threshold=0.5   # ← 50% request utilization
      - --max-graceful-termination-sec=600
      - --expander=least-waste                   # ← minimize leftover capacity
```

#### Expander Strategies

When multiple node groups can satisfy pending pods, the **expander** picks which one:

| Strategy | Behavior | Best For |
|----------|----------|----------|
| `random` | Pick a random eligible group | Simple setups, even distribution |
| `most-pods` | Pick the group that schedules the most pending pods | Maximize scheduling throughput |
| `least-waste` | Pick the group with least leftover resources after scheduling | Cost optimization |
| `price` | Pick the cheapest group (requires cloud pricing API) | Aggressive cost optimization |
| `priority` | Use a priority-ordered list of node groups | Prefer spot over on-demand |

```yaml
# Priority expander ConfigMap
apiVersion: v1
kind: ConfigMap
metadata:
  name: cluster-autoscaler-priority-expander
  namespace: kube-system
data:
  priorities: |-
    50:
      - .*spot.*                       # ← prefer spot node groups
    30:
      - .*on-demand-small.*            # ← then small on-demand
    10:
      - .*on-demand-large.*            # ← large on-demand as last resort
```

### Scale-Down Behavior

The CA considers a node eligible for removal when:

1. **Utilization is below threshold** (default 50%) for `scale-down-unneeded-time`.
2. **All pods can be moved** to other nodes (enough capacity exists).
3. **No blocking conditions** exist.

Conditions that **prevent** scale-down:

| Condition | Why It Blocks | Workaround |
|-----------|---------------|------------|
| Pod with local storage (emptyDir) | Data would be lost | `--skip-nodes-with-local-storage=false` |
| Pod not managed by controller | Can't be recreated | Use Deployments, not bare pods |
| Pod with restrictive PDB | Can't maintain availability | Ensure PDBs allow at least 1 disruption |
| Pod with `cluster-autoscaler.kubernetes.io/safe-to-evict: "false"` annotation | Explicit opt-out | Remove annotation when safe |
| kube-system pods without PDB | System pods are protected by default | Add PDBs to kube-system DaemonSets |

```bash
# Check why CA isn't scaling down a node
kubectl describe configmap cluster-autoscaler-status -n kube-system
# → ScaleDown:
# →   Candidates: 1
# →   LastProbeTime: 2024-01-15T10:30:00Z
# →   LastTransitionTime: 2024-01-15T10:20:00Z
# →   UnremovableNodes:
# →     node-abc123 - pod default/my-stateful-pod has local storage

# Force scale-down eligibility for a specific pod
kubectl annotate pod my-pod cluster-autoscaler.kubernetes.io/safe-to-evict="true"
```

### Overprovisioning with Placeholder Pods

The CA is reactive — it only adds nodes when pods are already pending. For latency-
sensitive workloads, you can pre-provision capacity using **low-priority placeholder
pods** that get preempted when real pods arrive:

```yaml
# k8s/scaling/overprovision-priority.yaml
apiVersion: scheduling.k8s.io/v1
kind: PriorityClass
metadata:
  name: overprovisioning
value: -1                               # ← lowest possible priority
globalDefault: false
description: "Placeholder pods for cluster overprovisioning"

---
# k8s/scaling/overprovision-deploy.yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: overprovisioner
  namespace: kube-system
spec:
  replicas: 3                           # ← 3 placeholder pods = 3 nodes of headroom
  selector:
    matchLabels:
      app: overprovisioner
  template:
    metadata:
      labels:
        app: overprovisioner
    spec:
      priorityClassName: overprovisioning
      terminationGracePeriodSeconds: 0
      containers:
        - name: pause
          image: registry.k8s.io/pause:3.9
          resources:
            requests:
              cpu: "1"                   # ← reserve 1 core per placeholder
              memory: 2Gi               # ← reserve 2Gi per placeholder
```

```
How overprovisioning works:

  Normal state:
  ┌──────────┐  ┌──────────┐  ┌──────────┐
  │ Node 1   │  │ Node 2   │  │ Node 3   │
  │ [App]    │  │ [App]    │  │ [Pause]  │  ← placeholder pod holds capacity
  │ [App]    │  │ [App]    │  │ [Pause]  │
  └──────────┘  └──────────┘  └──────────┘

  Traffic spike:
  ┌──────────┐  ┌──────────┐  ┌──────────┐  ┌──────────┐
  │ Node 1   │  │ Node 2   │  │ Node 3   │  │ Node 4   │
  │ [App]    │  │ [App]    │  │ [App] ←──│──│ preempted │
  │ [App]    │  │ [App]    │  │ [App]    │  │ [Pause]  │ ← CA adds node,
  └──────────┘  └──────────┘  └──────────┘  └──────────┘    pause moves here

  Result: App pods schedule INSTANTLY on Node 3 (no wait for new node).
  CA adds Node 4 in background to restore the overprovisioning buffer.
```

> **Key insight:** Overprovisioning trades a fixed cost (running pause containers)
> for near-zero scheduling latency. Size the placeholder requests to match your
> typical scale-up burst. For DeployForge, 2–3 placeholders at 1 CPU / 2Gi is
> usually enough to absorb a traffic spike while the CA provisions real capacity.

### Karpenter — Just-in-Time Node Provisioning

Karpenter (originally AWS-only, now expanding to other clouds) replaces the CA with
a fundamentally different model:

| Aspect | Cluster Autoscaler | Karpenter |
|--------|-------------------|-----------|
| Input | Pre-defined node groups (ASGs) | Pod requirements (directly) |
| Instance selection | Picks a node group, all nodes identical | Picks the cheapest instance type that fits |
| Speed | 2–6 min (ASG scaling) | 60–90s (direct EC2 API) |
| Configuration | ASG tags + CA flags | `NodePool` + `EC2NodeClass` CRDs |
| Multi-arch | One ASG per architecture | Single NodePool, mixed architectures |
| Consolidation | Scale-down only (remove empty nodes) | Active consolidation (move pods to cheaper nodes) |

```yaml
# k8s/scaling/karpenter-nodepool.yaml
apiVersion: karpenter.sh/v1beta1
kind: NodePool
metadata:
  name: deployforge-general
spec:
  template:
    metadata:
      labels:
        team: deployforge
    spec:
      requirements:
        - key: kubernetes.io/arch
          operator: In
          values: ["amd64", "arm64"]
        - key: karpenter.sh/capacity-type
          operator: In
          values: ["spot", "on-demand"]    # ← try spot first
        - key: karpenter.k8s.aws/instance-category
          operator: In
          values: ["c", "m", "r"]          # ← compute, general, memory families
        - key: karpenter.k8s.aws/instance-generation
          operator: Gt
          values: ["4"]                    # ← 5th gen or newer
      nodeClassRef:
        apiVersion: karpenter.k8s.aws/v1beta1
        kind: EC2NodeClass
        name: deployforge
  limits:
    cpu: "100"                             # ← max 100 CPUs total
    memory: 400Gi                          # ← max 400Gi total
  disruption:
    consolidationPolicy: WhenUnderutilized # ← actively repack pods
    expireAfter: 720h                      # ← recycle nodes every 30 days

---
apiVersion: karpenter.k8s.aws/v1beta1
kind: EC2NodeClass
metadata:
  name: deployforge
spec:
  amiFamily: AL2
  subnetSelectorTerms:
    - tags:
        karpenter.sh/discovery: deployforge-prod
  securityGroupSelectorTerms:
    - tags:
        karpenter.sh/discovery: deployforge-prod
  blockDeviceMappings:
    - deviceName: /dev/xvda
      ebs:
        volumeSize: 100Gi
        volumeType: gp3
        encrypted: true
```

> **Production note:** Karpenter's `consolidationPolicy: WhenUnderutilized`
> continuously looks for cheaper ways to run your workloads — replacing a half-empty
> `m5.2xlarge` with a right-sized `m5.large`, or moving workloads off on-demand
> instances when spot capacity becomes available. This is more aggressive than the CA
> and can cause pod disruptions; make sure your PDBs are correctly configured.

### Spot / Preemptible Instance Strategies

Using spot instances can save 60–90% on compute, but they can be reclaimed with
2 minutes notice. A production strategy involves:

```
┌─────────────────────────────────────────────────┐
│           Spot Instance Strategy                 │
├─────────────────────────────────────────────────┤
│                                                 │
│  On-Demand Base     Spot Fleet (diverse)        │
│  ┌─────────────┐    ┌─────────────────────────┐ │
│  │ API (min 2) │    │ API (overflow replicas)  │ │
│  │ Redis       │    │ Workers (all)            │ │
│  │ PostgreSQL  │    │ Batch jobs               │ │
│  │ Monitoring  │    │ Dev/staging environments │ │
│  └─────────────┘    └─────────────────────────┘ │
│                                                 │
│  Rule: Stateful + minimum HA = on-demand        │
│        Stateless + replaceable = spot           │
│                                                 │
└─────────────────────────────────────────────────┘
```

**Spot best practices:**

1. **Diversify instance types** — use at least 10+ instance types across 3+ AZs
   to reduce the chance of simultaneous reclamation.
2. **Use the Node Termination Handler** (NTH) to drain pods gracefully before spot
   reclamation.
3. **Set PodDisruptionBudgets** so spot interruptions don't violate availability.
4. **Separate node pools** — don't mix spot and on-demand in the same group.

```yaml
# Topology spread to distribute across spot and on-demand
apiVersion: apps/v1
kind: Deployment
metadata:
  name: deployforge-api
spec:
  template:
    spec:
      topologySpreadConstraints:
        - maxSkew: 1
          topologyKey: karpenter.sh/capacity-type
          whenUnsatisfiable: ScheduleAnyway
          labelSelector:
            matchLabels:
              app: deployforge-api
      affinity:
        nodeAffinity:
          preferredDuringSchedulingIgnoredDuringExecution:
            - weight: 80
              preference:
                matchExpressions:
                  - key: karpenter.sh/capacity-type
                    operator: In
                    values: ["spot"]      # ← prefer spot, but schedule on-demand if needed
```

### Node Draining and Cordoning

When removing nodes (for maintenance, scaling down, or spot interruptions), proper
draining prevents service disruption:

```bash
# Cordon: prevent new pods from scheduling on the node
kubectl cordon node-abc123
# → node/node-abc123 cordoned

# Drain: evict all pods gracefully (respects PDBs)
kubectl drain node-abc123 \
  --ignore-daemonsets \
  --delete-emptydir-data \
  --grace-period=60 \
  --timeout=300s
# → evicting pod deployforge-prod/deployforge-api-6f9b8c7d4-abc12
# → pod/deployforge-api-6f9b8c7d4-abc12 evicted
# → node/node-abc123 drained

# After maintenance, uncordon to allow scheduling again
kubectl uncordon node-abc123
```

```
Drain sequence with PDB:

  Deployment: 3 replicas, PDB: minAvailable=2

  Step 1: drain starts
  ┌──────────┐  ┌──────────┐  ┌──────────┐
  │ Node 1   │  │ Node 2   │  │ Node 3   │
  │ [Pod A]  │  │ [Pod B]  │  │ [Pod C]  │  ← drain Node 3
  └──────────┘  └──────────┘  └──────────┘
                                              PDB check: 3 running, min 2
                                              → safe to evict Pod C

  Step 2: Pod C evicted, rescheduled
  ┌──────────┐  ┌──────────┐  ┌──────────┐
  │ Node 1   │  │ Node 2   │  │ Node 3   │
  │ [Pod A]  │  │ [Pod B]  │  │ (empty)  │
  │ [Pod C'] │  │          │  │          │  ← Pod C rescheduled on Node 1
  └──────────┘  └──────────┘  └──────────┘
                                              Node 3 safe to remove
```

---

## Code Examples

### Production Cluster Autoscaler Deployment

Complete CA deployment for an EKS cluster with DeployForge:

```yaml
# k8s/scaling/cluster-autoscaler.yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: cluster-autoscaler
  namespace: kube-system
  labels:
    app: cluster-autoscaler
spec:
  replicas: 1
  selector:
    matchLabels:
      app: cluster-autoscaler
  template:
    metadata:
      labels:
        app: cluster-autoscaler
      annotations:
        prometheus.io/scrape: "true"
        prometheus.io/port: "8085"
    spec:
      serviceAccountName: cluster-autoscaler    # ← needs IAM role for ASG
      priorityClassName: system-cluster-critical # ← don't evict the autoscaler
      containers:
        - name: cluster-autoscaler
          image: registry.k8s.io/autoscaling/cluster-autoscaler:v1.29.0
          command:
            - ./cluster-autoscaler
            - --v=4
            - --stderrthreshold=info
            - --cloud-provider=aws
            - --skip-nodes-with-local-storage=false
            - --expander=priority
            - --balance-similar-node-groups=true
            - --node-group-auto-discovery=asg:tag=k8s.io/cluster-autoscaler/enabled,k8s.io/cluster-autoscaler/deployforge-prod
            - --scale-down-enabled=true
            - --scale-down-delay-after-add=10m
            - --scale-down-unneeded-time=10m
            - --scale-down-utilization-threshold=0.5
            - --max-node-provision-time=15m
            - --max-graceful-termination-sec=600
          resources:
            requests:
              cpu: 100m
              memory: 600Mi
            limits:
              memory: 600Mi
          volumeMounts:
            - name: ssl-certs
              mountPath: /etc/ssl/certs/ca-certificates.crt
              readOnly: true
      volumes:
        - name: ssl-certs
          hostPath:
            path: /etc/ssl/certs/ca-bundle.crt
```

### Node Termination Handler for Spot Instances

```yaml
# k8s/scaling/node-termination-handler.yaml
# Deployed via Helm for production; shown here for understanding
apiVersion: apps/v1
kind: DaemonSet
metadata:
  name: aws-node-termination-handler
  namespace: kube-system
spec:
  selector:
    matchLabels:
      app: aws-node-termination-handler
  template:
    metadata:
      labels:
        app: aws-node-termination-handler
    spec:
      nodeSelector:
        karpenter.sh/capacity-type: spot         # ← only on spot nodes
      serviceAccountName: aws-node-termination-handler
      hostNetwork: true
      containers:
        - name: handler
          image: public.ecr.aws/aws-ec2/aws-node-termination-handler:v1.22.0
          env:
            - name: NODE_NAME
              valueFrom:
                fieldRef:
                  fieldPath: spec.nodeName
            - name: ENABLE_SPOT_INTERRUPTION_DRAINING
              value: "true"
            - name: ENABLE_REBALANCE_DRAINING
              value: "true"
            - name: GRACE_PERIOD
              value: "120"                       # ← 2 min drain window
            - name: WEBHOOK_URL
              value: "https://hooks.slack.com/services/T.../B.../xxx"
```

### Monitoring Cluster Autoscaler Health

```bash
#!/bin/bash
# scripts/check-ca-status.sh
# Quick health check for Cluster Autoscaler

echo "=== Cluster Autoscaler Status ==="

# Check CA pod is running
kubectl get pods -n kube-system -l app=cluster-autoscaler -o wide
echo ""

# Check CA status ConfigMap
echo "--- Scale-Up Status ---"
kubectl get configmap cluster-autoscaler-status -n kube-system -o jsonpath='{.data.status}' 2>/dev/null | head -30
echo ""

# Check for pending pods (scale-up trigger)
echo "--- Pending Pods ---"
kubectl get pods --all-namespaces --field-selector=status.phase=Pending -o wide
echo ""

# Check node utilization
echo "--- Node Utilization ---"
kubectl top nodes --no-headers | awk '{
  split($2, cpu, "m");
  split($4, mem, "Mi");
  printf "%-40s CPU: %6s (%s)\tMem: %6s (%s)\n", $1, $2, $3, $4, $5
}'
echo ""

# Check recent CA events
echo "--- Recent CA Events ---"
kubectl get events -n kube-system --field-selector reason=ScaledUpGroup --sort-by=.lastTimestamp | tail -10
kubectl get events -n kube-system --field-selector reason=ScaleDown --sort-by=.lastTimestamp | tail -10
```

---

## Try It Yourself

### Challenge 1: Configure CA Scaling Parameters

Write the command-line arguments for a Cluster Autoscaler that:
- Uses `least-waste` expander
- Waits 15 minutes after adding a node before considering scale-down
- Requires nodes to be underutilized (<40%) for 5 minutes before removal
- Balances nodes across similar groups (AZs)
- Allows maximum 25 minutes for a node to provision

<details>
<summary>Show solution</summary>

```yaml
command:
  - ./cluster-autoscaler
  - --cloud-provider=aws
  - --expander=least-waste
  - --scale-down-delay-after-add=15m
  - --scale-down-unneeded-time=5m
  - --scale-down-utilization-threshold=0.4
  - --balance-similar-node-groups=true
  - --max-node-provision-time=25m
  - --scale-down-enabled=true
  - --node-group-auto-discovery=asg:tag=k8s.io/cluster-autoscaler/enabled
```

Verify:
```bash
# Check CA logs after applying
kubectl logs -n kube-system -l app=cluster-autoscaler --tail=20
# → I0115 10:30:00.123456  scale_down.go:XXX  Scale-down: utilization threshold = 0.4
# → I0115 10:30:00.123456  scale_down.go:XXX  Scale-down: unneeded time = 5m0s
```

</details>

### Challenge 2: Design an Overprovisioning Strategy

Create a PriorityClass and Deployment that reserves 2 nodes worth of headroom,
where each node has 4 CPUs and 16Gi memory. The placeholder pods should:
- Be the first to be evicted (lowest priority)
- Use the `pause` container image
- Terminate immediately (no grace period)

<details>
<summary>Show solution</summary>

```yaml
apiVersion: scheduling.k8s.io/v1
kind: PriorityClass
metadata:
  name: overprovisioning
value: -1
globalDefault: false
description: "Cluster overprovisioning placeholder"

---
apiVersion: apps/v1
kind: Deployment
metadata:
  name: overprovisioner
  namespace: kube-system
spec:
  replicas: 2
  selector:
    matchLabels:
      app: overprovisioner
  template:
    metadata:
      labels:
        app: overprovisioner
    spec:
      priorityClassName: overprovisioning
      terminationGracePeriodSeconds: 0
      containers:
        - name: pause
          image: registry.k8s.io/pause:3.9
          resources:
            requests:
              cpu: "3500m"
              memory: 14Gi
            limits:
              cpu: "3500m"
              memory: 14Gi
```

Note: Requests are set slightly below node capacity (3500m vs 4000m, 14Gi vs 16Gi) to
account for kubelet reserved resources and system pods.

Verify:
```bash
kubectl apply -f overprovisioner.yaml

kubectl get pods -n kube-system -l app=overprovisioner
# → NAME                               READY   STATUS    RESTARTS   AGE
# → overprovisioner-5f4d6c8b7-abc12    1/1     Running   0          30s
# → overprovisioner-5f4d6c8b7-def34    1/1     Running   0          30s

# When a real pod needs capacity, the placeholder is preempted:
kubectl get events --field-selector reason=Preempted -w
# → LAST SEEN   TYPE     REASON      OBJECT                              MESSAGE
# → 5s          Normal   Preempted   pod/overprovisioner-5f4d6c8b7-abc12 Preempted by deployforge-prod/api-...
```

</details>

### Challenge 3: Write a Karpenter NodePool

Create a Karpenter `NodePool` that:
- Allows both `amd64` and `arm64` architectures
- Prefers spot instances but allows on-demand as fallback
- Only uses `m`, `c`, and `r` instance families, generation 5+
- Limits to 50 CPUs and 200Gi memory total
- Consolidates underutilized nodes
- Expires nodes after 14 days

<details>
<summary>Show solution</summary>

```yaml
apiVersion: karpenter.sh/v1beta1
kind: NodePool
metadata:
  name: deployforge-general
spec:
  template:
    metadata:
      labels:
        managed-by: karpenter
        team: deployforge
    spec:
      requirements:
        - key: kubernetes.io/arch
          operator: In
          values: ["amd64", "arm64"]
        - key: karpenter.sh/capacity-type
          operator: In
          values: ["spot", "on-demand"]
        - key: karpenter.k8s.aws/instance-category
          operator: In
          values: ["m", "c", "r"]
        - key: karpenter.k8s.aws/instance-generation
          operator: Gt
          values: ["4"]
      nodeClassRef:
        apiVersion: karpenter.k8s.aws/v1beta1
        kind: EC2NodeClass
        name: deployforge
  limits:
    cpu: "50"
    memory: 200Gi
  disruption:
    consolidationPolicy: WhenUnderutilized
    expireAfter: 336h
  weight: 10
```

Verify:
```bash
kubectl apply -f karpenter-nodepool.yaml

kubectl get nodepool deployforge-general
# → NAME                  NODECLASS     NODES   READY   AGE
# → deployforge-general   deployforge   0       True    10s

# Trigger a scale-up by creating a pending pod
kubectl run test-pod --image=nginx --requests='cpu=500m,memory=512Mi'
kubectl get nodes -w
# → NAME                          STATUS   AGE
# → ip-10-0-1-123.ec2.internal    Ready    45s   ← Karpenter provisioned in ~60s
```

</details>

---

## Capstone Connection

**DeployForge** relies on both pod-level and cluster-level autoscaling working in
concert:

- **Cluster Autoscaler** (`k8s/scaling/cluster-autoscaler.yaml`): Uses the `priority`
  expander to prefer spot node groups for workers and on-demand groups for the API
  gateway's minimum replicas, keeping base costs low while ensuring HA for
  customer-facing traffic.
- **Overprovisioning** (`k8s/scaling/overprovision-deploy.yaml`): Two pause-container
  pods hold 2 nodes worth of headroom so that HPA scale-up events for the API don't
  wait for new nodes — real pods preempt the placeholders instantly.
- **Spot strategy**: Workers and batch jobs run on spot instances with a Node
  Termination Handler draining pods 2 minutes before reclamation. PDBs ensure at
  least 50% of workers remain available during spot interruptions.

In the next section, you'll learn how **ResourceQuotas, LimitRanges, and FinOps
practices** keep all this scaling under financial control.
