# 10.3 — Progressive Delivery: Canary, Blue-Green & Feature Flags

## Concept

Deploying software isn't the same as releasing it. A deployment puts new code on servers; a release exposes it to users. Progressive delivery separates these concerns, letting you deploy to production without immediately sending traffic to the new version. If the new version is buggy, zero users are affected — you just roll back the deployment.

Kubernetes' built-in rolling update strategy is a good start, but it has a fatal flaw: once a rollout starts, traffic reaches the new version with no automated quality gate. If your new pods pass readiness probes but serve incorrect data, you won't know until users complain. Progressive delivery strategies — canary, blue-green, and feature flags — add automated analysis between "deployed" and "released," giving you the ability to verify in production before full exposure.

This section covers Argo Rollouts for canary and blue-green deployments, AnalysisTemplates that query Prometheus for automated promotion decisions, and feature flags that give you fine-grained control over who sees what.

---

## Deep Dive

### Why Rolling Updates Aren't Enough

Kubernetes' default `RollingUpdate` strategy replaces pods incrementally:

```yaml
strategy:
  type: RollingUpdate
  rollingUpdate:
    maxSurge: 25%
    maxUnavailable: 25%
```

```
┌──────────────────────────────────────────────────────────────┐
│              Rolling Update — The Problem                      │
├──────────────────────────────────────────────────────────────┤
│                                                               │
│  Time 0: [v1] [v1] [v1] [v1]        ← all traffic to v1     │
│  Time 1: [v2] [v1] [v1] [v1]        ← 25% of pods are v2    │
│  Time 2: [v2] [v2] [v1] [v1]        ← 50% v2, no analysis   │
│  Time 3: [v2] [v2] [v2] [v1]        ← 75% v2, still no gate │
│  Time 4: [v2] [v2] [v2] [v2]        ← 100% v2, too late     │
│                                                               │
│  ✗ No traffic control (pod-level, not traffic-level)          │
│  ✗ No automated metric analysis                               │
│  ✗ Rollback requires another full rollout                     │
│  ✗ No pause points for human approval                         │
└──────────────────────────────────────────────────────────────┘
```

| Limitation | Impact |
|-----------|--------|
| Pod-based, not traffic-based | Can't send exactly 5% of traffic to canary |
| No analysis between steps | Bad version reaches 100% before metrics show problems |
| Rollback = new rollout | Rolling back is just as slow as rolling forward |
| No pause points | Can't hold at 10% and wait for human approval |

> **Key insight:** Rolling updates are *deployment* strategies. Canary and blue-green are *release* strategies. The distinction matters: deployment affects pods, release affects traffic. Argo Rollouts gives you traffic-level control with automated analysis gates.

### Canary Deployments

A canary deployment sends a small percentage of traffic to the new version, analyzes metrics, and gradually increases traffic if metrics are healthy:

```
┌──────────────────────────────────────────────────────────────┐
│                  Canary Deployment Progression                │
├──────────────────────────────────────────────────────────────┤
│                                                               │
│  Step 1: setWeight: 5                                         │
│  ┌────────────────────────────────┐ ┌───┐                    │
│  │  Stable (v1) — 95% traffic     │ │v2 │ 5%                 │
│  └────────────────────────────────┘ └───┘                    │
│  → Run AnalysisTemplate (check error rate, latency)           │
│                                                               │
│  Step 2: setWeight: 20                                        │
│  ┌──────────────────────────┐ ┌────────┐                     │
│  │  Stable (v1) — 80%       │ │ v2 20% │                     │
│  └──────────────────────────┘ └────────┘                     │
│  → Run AnalysisTemplate (check error rate, latency)           │
│                                                               │
│  Step 3: setWeight: 50                                        │
│  ┌────────────────┐ ┌────────────────┐                       │
│  │ Stable (v1) 50%│ │ Canary (v2) 50%│                       │
│  └────────────────┘ └────────────────┘                       │
│  → Run AnalysisTemplate (check error rate, latency)           │
│                                                               │
│  Step 4: setWeight: 100 (promote)                             │
│  ┌────────────────────────────────────┐                      │
│  │    New Stable (v2) — 100% traffic  │                      │
│  └────────────────────────────────────┘                      │
│                                                               │
│  If ANY analysis fails → instant rollback to v1               │
└──────────────────────────────────────────────────────────────┘
```

### Argo Rollouts

Argo Rollouts is a Kubernetes controller that replaces `Deployment` with a `Rollout` CRD, adding canary and blue-green strategies with traffic management and analysis:

```bash
# Install Argo Rollouts
kubectl create namespace argo-rollouts
kubectl apply -n argo-rollouts \
  -f https://github.com/argoproj/argo-rollouts/releases/latest/download/install.yaml

# Install the kubectl plugin
brew install argoproj/tap/kubectl-argo-rollouts

# Watch rollout progress in real-time
kubectl argo rollouts dashboard &
# → opens dashboard on http://localhost:3100
```

### Canary Rollout with Argo Rollouts

```yaml
apiVersion: argoproj.io/v1alpha1
kind: Rollout
metadata:
  name: deployforge-api
  namespace: deployforge
spec:
  replicas: 5
  revisionHistoryLimit: 3
  selector:
    matchLabels:
      app: deployforge-api
  template:
    metadata:
      labels:
        app: deployforge-api
    spec:
      containers:
        - name: api
          image: ghcr.io/org/deployforge-api:sha-abc1234
          ports:
            - containerPort: 3000
          resources:
            requests:
              cpu: 100m
              memory: 128Mi
            limits:
              cpu: 500m
              memory: 512Mi
          readinessProbe:
            httpGet:
              path: /health
              port: 3000
            initialDelaySeconds: 5
            periodSeconds: 10
  strategy:
    canary:
      canaryService: deployforge-api-canary    # ← Service for canary pods
      stableService: deployforge-api-stable    # ← Service for stable pods

      # Traffic routing via Istio, NGINX, or Traefik
      trafficRouting:
        nginx:
          stableIngress: deployforge-api       # ← existing Ingress name
          additionalIngressAnnotations:
            canary-by-header: X-Canary

      steps:
        - setWeight: 5
        - pause: { duration: 2m }              # ← observe for 2 minutes
        - analysis:
            templates:
              - templateName: success-rate
            args:
              - name: service-name
                value: deployforge-api-canary

        - setWeight: 20
        - pause: { duration: 2m }
        - analysis:
            templates:
              - templateName: success-rate

        - setWeight: 50
        - pause: { duration: 5m }
        - analysis:
            templates:
              - templateName: success-rate
              - templateName: latency-check

        - setWeight: 100                       # ← full promotion
```

The required Services:

```yaml
# Stable service — routes to current stable version
apiVersion: v1
kind: Service
metadata:
  name: deployforge-api-stable
spec:
  selector:
    app: deployforge-api
  ports:
    - port: 80
      targetPort: 3000

---
# Canary service — routes to canary pods only
apiVersion: v1
kind: Service
metadata:
  name: deployforge-api-canary
spec:
  selector:
    app: deployforge-api
  ports:
    - port: 80
      targetPort: 3000
```

> **Production note:** Argo Rollouts manages the selector labels on these Services dynamically. During a canary rollout, `deployforge-api-canary` points to the new ReplicaSet and `deployforge-api-stable` points to the old one. You don't manage this yourself.

### AnalysisTemplates — Automated Canary Decisions

AnalysisTemplates define metrics queries that determine whether a canary is healthy:

```yaml
apiVersion: argoproj.io/v1alpha1
kind: AnalysisTemplate
metadata:
  name: success-rate
  namespace: deployforge
spec:
  args:
    - name: service-name
      value: deployforge-api-canary
  metrics:
    - name: success-rate
      # Run every 60 seconds for 5 minutes
      interval: 60s
      count: 5
      successCondition: result[0] >= 0.95     # ← 95% success rate required
      failureLimit: 2                          # ← allow up to 2 failed checks
      provider:
        prometheus:
          address: http://prometheus.monitoring:9090
          query: |
            sum(rate(
              http_requests_total{
                service="{{args.service-name}}",
                status=~"2.."
              }[2m]
            )) /
            sum(rate(
              http_requests_total{
                service="{{args.service-name}}"
              }[2m]
            ))
```

```yaml
apiVersion: argoproj.io/v1alpha1
kind: AnalysisTemplate
metadata:
  name: latency-check
  namespace: deployforge
spec:
  metrics:
    - name: p99-latency
      interval: 60s
      count: 5
      successCondition: result[0] <= 500       # ← p99 must be under 500ms
      failureLimit: 2
      provider:
        prometheus:
          address: http://prometheus.monitoring:9090
          query: |
            histogram_quantile(0.99,
              sum(rate(
                http_request_duration_seconds_bucket{
                  service="deployforge-api-canary"
                }[2m]
              )) by (le)
            ) * 1000
```

> **Key insight:** AnalysisTemplates turn "is this deploy safe?" from a human judgment call into a data-driven automated decision. The canary gets promoted only when real production traffic proves it's healthy. No dashboards to stare at, no Slack threads asking "looks good to you?"

### What Happens When Analysis Fails

```
┌──────────────────────────────────────────────────────────────┐
│            Canary Analysis Failure → Rollback                 │
├──────────────────────────────────────────────────────────────┤
│                                                               │
│  1. Image updated: sha-abc1234 → sha-def5678                │
│  2. setWeight: 5% → canary pods start receiving traffic       │
│  3. Analysis runs: success rate = 98% ✓                       │
│  4. setWeight: 20%                                            │
│  5. Analysis runs: success rate = 87% ✗ (below 95%)          │
│  6. failureLimit reached (2/2 failures)                       │
│  7. ┌─────────────────────────────────────┐                  │
│     │ ROLLBACK TRIGGERED                   │                  │
│     │ - Canary pods scaled to 0            │                  │
│     │ - 100% traffic to stable (v1)        │                  │
│     │ - Status: Degraded                   │                  │
│     │ - Takes: ~10 seconds                 │                  │
│     └─────────────────────────────────────┘                  │
│  8. Notification sent to Slack / PagerDuty                    │
│  9. Developer investigates sha-def5678                        │
│                                                               │
│  Impact: Only 20% of users saw the bad version, for ~4 min   │
│  Compare: Rolling update → 100% of users, ~10 min exposure   │
└──────────────────────────────────────────────────────────────┘
```

### Blue-Green Deployments

Blue-green maintains two complete environments. The "blue" environment is live; the "green" environment has the new version. Traffic switches instantly:

```
┌──────────────────────────────────────────────────────────────┐
│                    Blue-Green Deployment                       │
├──────────────────────────────────────────────────────────────┤
│                                                               │
│  Before switch:                                               │
│  ┌─────────────────────────┐  ┌─────────────────────────┐   │
│  │  BLUE (v1) — ACTIVE     │  │  GREEN (v2) — PREVIEW   │   │
│  │  ┌───┐ ┌───┐ ┌───┐     │  │  ┌───┐ ┌───┐ ┌───┐     │   │
│  │  │pod│ │pod│ │pod│      │  │  │pod│ │pod│ │pod│      │   │
│  │  └───┘ └───┘ └───┘     │  │  └───┘ └───┘ └───┘     │   │
│  │  100% production traffic │  │  preview traffic only   │   │
│  └─────────────────────────┘  └─────────────────────────┘   │
│               ▲                                               │
│               │ Service selector                              │
│                                                               │
│  After switch (instant):                                      │
│  ┌─────────────────────────┐  ┌─────────────────────────┐   │
│  │  BLUE (v1) — STANDBY    │  │  GREEN (v2) — ACTIVE    │   │
│  │  ┌───┐ ┌───┐ ┌───┐     │  │  ┌───┐ ┌───┐ ┌───┐     │   │
│  │  │pod│ │pod│ │pod│      │  │  │pod│ │pod│ │pod│      │   │
│  │  └───┘ └───┘ └───┘     │  │  └───┘ └───┘ └───┘     │   │
│  │  kept for rollback       │  │  100% production traffic│   │
│  └─────────────────────────┘  └─────────────────────────┘   │
│                                               ▲               │
│                                               │ Service       │
│                                                 selector      │
│  Rollback: switch Service selector back to BLUE — instant     │
└──────────────────────────────────────────────────────────────┘
```

```yaml
apiVersion: argoproj.io/v1alpha1
kind: Rollout
metadata:
  name: deployforge-api
  namespace: deployforge
spec:
  replicas: 3
  selector:
    matchLabels:
      app: deployforge-api
  template:
    metadata:
      labels:
        app: deployforge-api
    spec:
      containers:
        - name: api
          image: ghcr.io/org/deployforge-api:sha-abc1234
          ports:
            - containerPort: 3000
  strategy:
    blueGreen:
      activeService: deployforge-api-active       # ← production traffic
      previewService: deployforge-api-preview      # ← preview / smoke test
      autoPromotionEnabled: false                  # ← require manual approval
      prePromotionAnalysis:
        templates:
          - templateName: smoke-test
        args:
          - name: preview-url
            value: http://deployforge-api-preview.deployforge.svc
      scaleDownDelaySeconds: 300                   # ← keep old version 5 min
      abortScaleDownDelaySeconds: 30
```

| Feature | Canary | Blue-Green |
|---------|--------|------------|
| **Traffic control** | Gradual (5% → 20% → 50% → 100%) | Instant (0% → 100%) |
| **Resource cost** | Minimal extra pods during canary | Double resources during transition |
| **Rollback speed** | Instant (shift traffic back) | Instant (switch Service selector) |
| **Verification** | Real traffic analysis over time | Smoke tests on preview environment |
| **Best for** | Stateless APIs, gradual confidence | Database migrations, binary changes |

### Feature Flags

Feature flags decouple deployment from release at the application level. The code is deployed, but the feature is behind a flag that controls who sees it:

```
┌──────────────────────────────────────────────────────────────┐
│                Feature Flag Architecture                      │
├──────────────────────────────────────────────────────────────┤
│                                                               │
│   ┌──────────────┐                                           │
│   │ Flag Service │  (LaunchDarkly / Flagsmith / OpenFeature) │
│   │              │                                           │
│   │  new-ui: ON  │  (for 10% of users)                      │
│   │  dark-api: ON│  (for internal team only)                 │
│   │  beta-x: OFF │  (killed globally)                        │
│   └──────┬───────┘                                           │
│          │ SDK evaluates flags                               │
│          ▼                                                    │
│   ┌──────────────────────────────────────────┐               │
│   │ Application Code                          │               │
│   │                                           │               │
│   │  if (flags.isEnabled('new-ui', user)) {   │               │
│   │    return renderNewUI();                  │               │
│   │  } else {                                 │               │
│   │    return renderOldUI();                  │               │
│   │  }                                        │               │
│   └──────────────────────────────────────────┘               │
│                                                               │
│   Use cases:                                                  │
│   - Canary by user segment (not just traffic %)              │
│   - Kill switch for broken features                           │
│   - A/B testing (50/50 split by user ID)                     │
│   - Dark launches (internal team only)                        │
│   - Gradual rollout by geography / account tier              │
└──────────────────────────────────────────────────────────────┘
```

### OpenFeature — The Vendor-Neutral Standard

OpenFeature is a CNCF project that provides a standard API for feature flags, avoiding vendor lock-in:

```typescript
// Install OpenFeature SDK and a provider
// npm install @openfeature/server-sdk @openfeature/flagd-provider

import { OpenFeature } from '@openfeature/server-sdk';
import { FlagdProvider } from '@openfeature/flagd-provider';

// Configure once at startup
OpenFeature.setProvider(new FlagdProvider({
  host: 'flagd.deployforge.svc.cluster.local',
  port: 8013,
}));

const client = OpenFeature.getClient();

// Evaluate a boolean flag with user context
async function handleRequest(req: Request, res: Response) {
  const context = {
    targetingKey: req.userId,
    email: req.userEmail,
    tier: req.accountTier,        // 'free', 'pro', 'enterprise'
    region: req.geoRegion,
  };

  const useNewDashboard = await client.getBooleanValue(
    'new-dashboard',
    false,                         // ← default if flag service is unreachable
    context
  );

  if (useNewDashboard) {
    return res.json(buildNewDashboard(req));
  }
  return res.json(buildLegacyDashboard(req));
}
```

### Flagd — Kubernetes-Native Feature Flag Daemon

Flagd is a lightweight, open-source feature flag daemon that reads flag definitions from ConfigMaps or files:

```yaml
# flagd ConfigMap — flag definitions
apiVersion: v1
kind: ConfigMap
metadata:
  name: feature-flags
  namespace: deployforge
data:
  flags.json: |
    {
      "flags": {
        "new-dashboard": {
          "state": "ENABLED",
          "variants": {
            "on": true,
            "off": false
          },
          "defaultVariant": "off",
          "targeting": {
            "if": [
              { "in": ["@company.com", { "var": "email" }] },
              "on",
              { "if": [
                { "<=": [{ "var": "percentage" }, 10] },
                "on",
                "off"
              ]}
            ]
          }
        },
        "experimental-api": {
          "state": "ENABLED",
          "variants": {
            "on": true,
            "off": false
          },
          "defaultVariant": "off",
          "targeting": {
            "if": [
              { "in": [{ "var": "tier" }, ["enterprise"]] },
              "on",
              "off"
            ]
          }
        }
      }
    }
```

```yaml
# flagd Deployment
apiVersion: apps/v1
kind: Deployment
metadata:
  name: flagd
  namespace: deployforge
spec:
  replicas: 2
  selector:
    matchLabels:
      app: flagd
  template:
    metadata:
      labels:
        app: flagd
    spec:
      containers:
        - name: flagd
          image: ghcr.io/open-feature/flagd:latest
          ports:
            - containerPort: 8013
          args:
            - start
            - --uri
            - file:/etc/flagd/flags.json
          volumeMounts:
            - name: flags
              mountPath: /etc/flagd
      volumes:
        - name: flags
          configMap:
            name: feature-flags
```

> **Key insight:** Feature flags stored in a ConfigMap can be managed through GitOps. Change the flag definition in Git → ArgoCD syncs the ConfigMap → flagd picks up the change. No deployments needed to toggle features. This is the ultimate separation of deploy and release.

### Dark Launches and A/B Testing

**Dark launches** deploy new code paths but only enable them for internal users. This lets you test with real production data without user exposure:

```typescript
// Dark launch pattern: shadow traffic
async function processOrder(order: Order) {
  // Always run the existing path
  const result = await existingOrderProcessor(order);

  // Also run the new path in the background (dark launch)
  const useDarkLaunch = await flags.getBooleanValue('dark-order-processor', false, {
    targetingKey: 'system',
    internal: true,
  });

  if (useDarkLaunch) {
    // Fire and forget — don't affect the response
    newOrderProcessor(order)
      .then(darkResult => {
        // Compare results for correctness
        metrics.recordComparison('order-processor', {
          match: deepEqual(result, darkResult),
          oldLatency: result.latencyMs,
          newLatency: darkResult.latencyMs,
        });
      })
      .catch(err => {
        metrics.increment('dark-launch.errors', { processor: 'order' });
      });
  }

  return result;  // Always return the stable result
}
```

**A/B testing** uses flags to split users into cohorts and measure business metrics:

```typescript
// A/B test with metric tracking
async function renderPricingPage(req: Request) {
  const variant = await flags.getStringValue(
    'pricing-page-variant',
    'control',
    { targetingKey: req.userId }
  );

  metrics.increment('ab-test.impression', {
    test: 'pricing-page',
    variant,
    userId: req.userId,
  });

  switch (variant) {
    case 'control':
      return renderOriginalPricing();
    case 'variant-a':
      return renderSimplifiedPricing();    // fewer tiers
    case 'variant-b':
      return renderAnnualFirstPricing();   // annual pricing prominent
    default:
      return renderOriginalPricing();
  }
}
```

### Combining Strategies: The Progressive Delivery Stack

In practice, you combine infrastructure-level and application-level strategies:

```
┌──────────────────────────────────────────────────────────────┐
│             The Progressive Delivery Stack                    │
├──────────────────────────────────────────────────────────────┤
│                                                               │
│  Layer 4: Feature Flags                                       │
│  ┌──────────────────────────────────────────────────────┐    │
│  │ Per-user targeting, A/B tests, kill switches          │    │
│  │ "Release to 10% of enterprise users"                  │    │
│  └──────────────────────────────────────────────────────┘    │
│                                                               │
│  Layer 3: Canary / Blue-Green (Argo Rollouts)                │
│  ┌──────────────────────────────────────────────────────┐    │
│  │ Traffic splitting, automated metric analysis           │    │
│  │ "Send 5% of traffic, verify error rate < 1%"          │    │
│  └──────────────────────────────────────────────────────┘    │
│                                                               │
│  Layer 2: GitOps (ArgoCD)                                     │
│  ┌──────────────────────────────────────────────────────┐    │
│  │ Declarative desired state, drift detection, self-heal  │    │
│  │ "Git is the source of truth"                           │    │
│  └──────────────────────────────────────────────────────┘    │
│                                                               │
│  Layer 1: CI Pipeline (GitHub Actions)                        │
│  ┌──────────────────────────────────────────────────────┐    │
│  │ Build, test, scan, push — automated quality gates      │    │
│  │ "No untested code reaches the registry"                │    │
│  └──────────────────────────────────────────────────────┘    │
│                                                               │
│  Each layer adds confidence. Together, they make shipping     │
│  to production a non-event.                                   │
└──────────────────────────────────────────────────────────────┘
```

---

## Code Examples

### Installing Argo Rollouts

```bash
#!/bin/bash
set -euo pipefail

# Install Argo Rollouts controller
kubectl create namespace argo-rollouts 2>/dev/null || true
kubectl apply -n argo-rollouts \
  -f https://github.com/argoproj/argo-rollouts/releases/latest/download/install.yaml

# Wait for controller
kubectl wait --for=condition=Ready pod \
  -l app.kubernetes.io/name=argo-rollouts \
  -n argo-rollouts \
  --timeout=120s

echo "✓ Argo Rollouts controller installed"

# Install kubectl plugin
brew install argoproj/tap/kubectl-argo-rollouts

# Verify installation
kubectl argo rollouts version
# → argo-rollouts: v1.7.x
```

### Complete Canary Rollout with Analysis

```yaml
# rollout.yaml
apiVersion: argoproj.io/v1alpha1
kind: Rollout
metadata:
  name: deployforge-api
  namespace: deployforge
spec:
  replicas: 5
  revisionHistoryLimit: 3
  selector:
    matchLabels:
      app: deployforge-api
  template:
    metadata:
      labels:
        app: deployforge-api
      annotations:
        prometheus.io/scrape: "true"
        prometheus.io/port: "3000"
        prometheus.io/path: "/metrics"
    spec:
      containers:
        - name: api
          image: ghcr.io/org/deployforge-api:sha-abc1234
          ports:
            - name: http
              containerPort: 3000
            - name: metrics
              containerPort: 9090
          env:
            - name: NODE_ENV
              value: production
            - name: FLAGD_HOST
              value: flagd.deployforge.svc.cluster.local
          readinessProbe:
            httpGet:
              path: /health/ready
              port: http
            initialDelaySeconds: 5
            periodSeconds: 10
          livenessProbe:
            httpGet:
              path: /health/live
              port: http
            initialDelaySeconds: 15
            periodSeconds: 20
          resources:
            requests:
              cpu: 100m
              memory: 128Mi
            limits:
              cpu: 500m
              memory: 512Mi
  strategy:
    canary:
      canaryService: deployforge-api-canary
      stableService: deployforge-api-stable
      trafficRouting:
        nginx:
          stableIngress: deployforge-api
      steps:
        # Step 1: 5% traffic, run analysis for 2 minutes
        - setWeight: 5
        - analysis:
            templates:
              - templateName: canary-success-rate
            args:
              - name: canary-service
                value: deployforge-api-canary
              - name: stable-service
                value: deployforge-api-stable

        # Step 2: 25% traffic, longer analysis
        - setWeight: 25
        - pause: { duration: 3m }
        - analysis:
            templates:
              - templateName: canary-success-rate
              - templateName: canary-latency

        # Step 3: 50% traffic, final analysis before full promotion
        - setWeight: 50
        - pause: { duration: 5m }
        - analysis:
            templates:
              - templateName: canary-success-rate
              - templateName: canary-latency
              - templateName: canary-error-log-check

        # Step 4: promote to 100%
        - setWeight: 100

---
# analysis-success-rate.yaml
apiVersion: argoproj.io/v1alpha1
kind: AnalysisTemplate
metadata:
  name: canary-success-rate
  namespace: deployforge
spec:
  args:
    - name: canary-service
    - name: stable-service
  metrics:
    - name: success-rate
      interval: 30s
      count: 5
      failureLimit: 1
      successCondition: result[0] >= 0.95
      provider:
        prometheus:
          address: http://prometheus.monitoring:9090
          query: |
            sum(rate(
              http_requests_total{
                service="{{args.canary-service}}",
                status=~"[23].."
              }[2m]
            )) /
            sum(rate(
              http_requests_total{
                service="{{args.canary-service}}"
              }[2m]
            ))
    - name: canary-vs-stable
      interval: 60s
      count: 3
      failureLimit: 1
      # Canary error rate should not be 2x worse than stable
      successCondition: result[0] <= 2.0
      provider:
        prometheus:
          address: http://prometheus.monitoring:9090
          query: |
            (
              sum(rate(http_requests_total{service="{{args.canary-service}}", status=~"5.."}[2m]))
              / sum(rate(http_requests_total{service="{{args.canary-service}}"}[2m]))
            ) / (
              sum(rate(http_requests_total{service="{{args.stable-service}}", status=~"5.."}[2m]))
              / sum(rate(http_requests_total{service="{{args.stable-service}}"}[2m]))
            )

---
# analysis-latency.yaml
apiVersion: argoproj.io/v1alpha1
kind: AnalysisTemplate
metadata:
  name: canary-latency
  namespace: deployforge
spec:
  metrics:
    - name: p99-latency-ms
      interval: 60s
      count: 3
      failureLimit: 1
      successCondition: result[0] <= 500
      provider:
        prometheus:
          address: http://prometheus.monitoring:9090
          query: |
            histogram_quantile(0.99,
              sum(rate(
                http_request_duration_seconds_bucket{
                  service="deployforge-api-canary"
                }[2m]
              )) by (le)
            ) * 1000
```

### Monitoring a Rollout

```bash
# Watch rollout status (real-time)
kubectl argo rollouts get rollout deployforge-api -n deployforge --watch
# Name:            deployforge-api
# Namespace:       deployforge
# Status:          ◌ Progressing
# Strategy:        Canary
#   Step:          2/8
#   SetWeight:     25
#   ActualWeight:  25
# Images:          ghcr.io/org/deployforge-api:sha-abc1234 (stable)
#                  ghcr.io/org/deployforge-api:sha-def5678 (canary)
# Replicas:
#   Desired:       5
#   Current:       6
#   Updated:       2
#   Ready:         6
#   Available:     6

# Manually promote (skip remaining steps)
kubectl argo rollouts promote deployforge-api -n deployforge

# Abort and rollback
kubectl argo rollouts abort deployforge-api -n deployforge

# Retry a failed rollout
kubectl argo rollouts retry rollout deployforge-api -n deployforge
```

### Feature Flag Integration in Kubernetes

```yaml
# flagd deployment for DeployForge
apiVersion: apps/v1
kind: Deployment
metadata:
  name: flagd
  namespace: deployforge
spec:
  replicas: 2
  selector:
    matchLabels:
      app: flagd
  template:
    metadata:
      labels:
        app: flagd
    spec:
      containers:
        - name: flagd
          image: ghcr.io/open-feature/flagd:v0.10.0
          ports:
            - containerPort: 8013
              name: grpc
            - containerPort: 8014
              name: metrics
          args:
            - start
            - --uri
            - file:/etc/flagd/flags.json
            - --metrics-exporter
            - prometheus
          volumeMounts:
            - name: flags
              mountPath: /etc/flagd
          readinessProbe:
            grpc:
              port: 8013
          resources:
            requests:
              cpu: 50m
              memory: 64Mi
            limits:
              cpu: 200m
              memory: 128Mi
      volumes:
        - name: flags
          configMap:
            name: deployforge-feature-flags

---
apiVersion: v1
kind: Service
metadata:
  name: flagd
  namespace: deployforge
spec:
  selector:
    app: flagd
  ports:
    - name: grpc
      port: 8013
    - name: metrics
      port: 8014
```

---

## Try It Yourself

### Challenge 1: Design a Canary Strategy for a Payment Service

You're deploying a new version of a payment processing service. It must:
- Start at 1% traffic (payment bugs are expensive)
- Run for 10 minutes before advancing
- Check both success rate (>99.5%) and p99 latency (<200ms)
- Include a manual approval step before going above 25%
- Automatically promote at 100% after final analysis

Write the Rollout manifest and AnalysisTemplates.

<details>
<summary>Show solution</summary>

```yaml
apiVersion: argoproj.io/v1alpha1
kind: Rollout
metadata:
  name: payment-service
spec:
  replicas: 10
  selector:
    matchLabels:
      app: payment-service
  template:
    metadata:
      labels:
        app: payment-service
    spec:
      containers:
        - name: payment
          image: ghcr.io/org/payment-service:sha-abc1234
          ports:
            - containerPort: 3000
  strategy:
    canary:
      canaryService: payment-canary
      stableService: payment-stable
      steps:
        # Ultra-conservative start: 1%
        - setWeight: 1
        - pause: { duration: 10m }
        - analysis:
            templates:
              - templateName: payment-health
            args:
              - name: threshold-success
                value: "0.995"
              - name: threshold-latency
                value: "200"

        # 5% — still cautious
        - setWeight: 5
        - pause: { duration: 10m }
        - analysis:
            templates:
              - templateName: payment-health
            args:
              - name: threshold-success
                value: "0.995"
              - name: threshold-latency
                value: "200"

        # Human gate before significant traffic
        - setWeight: 25
        - pause: {}                        # ← indefinite pause, requires manual promote

        # After manual approval, ramp up
        - setWeight: 50
        - pause: { duration: 5m }
        - analysis:
            templates:
              - templateName: payment-health
            args:
              - name: threshold-success
                value: "0.995"
              - name: threshold-latency
                value: "200"

        - setWeight: 100

---
apiVersion: argoproj.io/v1alpha1
kind: AnalysisTemplate
metadata:
  name: payment-health
spec:
  args:
    - name: threshold-success
    - name: threshold-latency
  metrics:
    - name: success-rate
      interval: 60s
      count: 10
      failureLimit: 1
      successCondition: "result[0] >= {{args.threshold-success}}"
      provider:
        prometheus:
          address: http://prometheus.monitoring:9090
          query: |
            sum(rate(payment_requests_total{status="success", canary="true"}[5m]))
            /
            sum(rate(payment_requests_total{canary="true"}[5m]))
    - name: p99-latency
      interval: 60s
      count: 10
      failureLimit: 1
      successCondition: "result[0] <= {{args.threshold-latency}}"
      provider:
        prometheus:
          address: http://prometheus.monitoring:9090
          query: |
            histogram_quantile(0.99,
              sum(rate(payment_request_duration_ms_bucket{canary="true"}[5m]))
              by (le)
            )
```

Key design decisions:
- **1% start** because payment bugs have direct revenue impact
- **10-minute pause** at low weights gives time for error rate signals to emerge
- **`pause: {}`** (no duration) creates a manual gate — requires `kubectl argo rollouts promote`
- **Higher failure threshold** (99.5%) than typical services because payment correctness is critical
- **10 measurement points** (count: 10) at 60s intervals = 10 minutes of analysis per step

</details>

### Challenge 2: Implement a Blue-Green Deployment with Smoke Tests

Write a blue-green Rollout that:
- Deploys the new version as "preview"
- Runs an automated smoke test AnalysisTemplate against the preview Service
- Waits for manual promotion after smoke tests pass
- Keeps the old version for 10 minutes after promotion (for quick rollback)

<details>
<summary>Show solution</summary>

```yaml
apiVersion: argoproj.io/v1alpha1
kind: Rollout
metadata:
  name: deployforge-api
  namespace: deployforge
spec:
  replicas: 3
  selector:
    matchLabels:
      app: deployforge-api
  template:
    metadata:
      labels:
        app: deployforge-api
    spec:
      containers:
        - name: api
          image: ghcr.io/org/deployforge-api:sha-abc1234
          ports:
            - containerPort: 3000
  strategy:
    blueGreen:
      activeService: deployforge-api-active
      previewService: deployforge-api-preview
      autoPromotionEnabled: false
      scaleDownDelaySeconds: 600              # 10 minutes
      prePromotionAnalysis:
        templates:
          - templateName: smoke-test
        args:
          - name: host
            value: deployforge-api-preview.deployforge.svc.cluster.local

---
apiVersion: argoproj.io/v1alpha1
kind: AnalysisTemplate
metadata:
  name: smoke-test
  namespace: deployforge
spec:
  args:
    - name: host
  metrics:
    - name: health-check
      count: 1
      failureLimit: 0
      provider:
        job:
          spec:
            backoffLimit: 1
            template:
              spec:
                restartPolicy: Never
                containers:
                  - name: smoke
                    image: curlimages/curl:8.5.0
                    command:
                      - /bin/sh
                      - -c
                      - |
                        set -e
                        echo "Running smoke tests against {{args.host}}..."

                        # Health endpoint
                        curl -sf http://{{args.host}}:3000/health || exit 1
                        echo "✓ Health check passed"

                        # API responds with valid JSON
                        curl -sf http://{{args.host}}:3000/api/v1/status | \
                          grep -q '"status":"ok"' || exit 1
                        echo "✓ API status check passed"

                        # Auth endpoint rejects unauthenticated requests
                        HTTP_CODE=$(curl -s -o /dev/null -w "%{http_code}" \
                          http://{{args.host}}:3000/api/v1/protected)
                        [ "$HTTP_CODE" = "401" ] || exit 1
                        echo "✓ Auth check passed"

                        echo "All smoke tests passed ✓"

---
apiVersion: v1
kind: Service
metadata:
  name: deployforge-api-active
  namespace: deployforge
spec:
  selector:
    app: deployforge-api
  ports:
    - port: 80
      targetPort: 3000

---
apiVersion: v1
kind: Service
metadata:
  name: deployforge-api-preview
  namespace: deployforge
spec:
  selector:
    app: deployforge-api
  ports:
    - port: 80
      targetPort: 3000
```

The smoke test uses a Job-based AnalysisTemplate (not Prometheus). The Job runs `curl` commands against the preview Service. If any check fails, the entire analysis fails and the promotion is blocked.

After smoke tests pass, promote manually:
```bash
kubectl argo rollouts promote deployforge-api -n deployforge
```

</details>

### Challenge 3: Create a Feature Flag Configuration

Design a feature flag setup for DeployForge that:
- Enables a "new-dashboard" feature for internal users (@company.com) and 10% of external users
- Enables "experimental-api" only for enterprise-tier accounts
- Has a global "maintenance-mode" kill switch

Write the flagd ConfigMap and the TypeScript integration code.

<details>
<summary>Show solution</summary>

```yaml
apiVersion: v1
kind: ConfigMap
metadata:
  name: deployforge-feature-flags
  namespace: deployforge
data:
  flags.json: |
    {
      "$schema": "https://flagd.dev/schema/v0/flags.json",
      "flags": {
        "new-dashboard": {
          "state": "ENABLED",
          "variants": { "on": true, "off": false },
          "defaultVariant": "off",
          "targeting": {
            "if": [
              { "ends_with": [{ "var": "email" }, "@company.com"] },
              "on",
              {
                "if": [
                  { "<=": [{ "var": ["$flagd", "fractional"] }, 10] },
                  "on",
                  "off"
                ]
              }
            ]
          }
        },
        "experimental-api": {
          "state": "ENABLED",
          "variants": { "on": true, "off": false },
          "defaultVariant": "off",
          "targeting": {
            "if": [
              { "in": [{ "var": "tier" }, ["enterprise"]] },
              "on",
              "off"
            ]
          }
        },
        "maintenance-mode": {
          "state": "ENABLED",
          "variants": { "on": true, "off": false },
          "defaultVariant": "off"
        }
      }
    }
```

```typescript
// src/flags.ts — Feature flag client for DeployForge
import { OpenFeature, Client } from '@openfeature/server-sdk';
import { FlagdProvider } from '@openfeature/flagd-provider';

let flagClient: Client;

export async function initFeatureFlags(): Promise<void> {
  const provider = new FlagdProvider({
    host: process.env.FLAGD_HOST || 'localhost',
    port: parseInt(process.env.FLAGD_PORT || '8013'),
    tls: false,
  });

  await OpenFeature.setProviderAndWait(provider);
  flagClient = OpenFeature.getClient('deployforge');
  console.log('Feature flags initialized');
}

export interface UserContext {
  userId: string;
  email: string;
  tier: 'free' | 'pro' | 'enterprise';
}

export async function isFeatureEnabled(
  flag: string,
  user: UserContext
): Promise<boolean> {
  return flagClient.getBooleanValue(flag, false, {
    targetingKey: user.userId,
    email: user.email,
    tier: user.tier,
  });
}

// Usage in route handler
import { isFeatureEnabled, UserContext } from './flags';

app.get('/api/v1/dashboard', async (req, res) => {
  const user: UserContext = {
    userId: req.auth.userId,
    email: req.auth.email,
    tier: req.auth.tier,
  };

  // Maintenance mode — global kill switch
  const inMaintenance = await isFeatureEnabled('maintenance-mode', user);
  if (inMaintenance) {
    return res.status(503).json({
      error: 'Service temporarily unavailable for maintenance',
    });
  }

  // New dashboard — gradual rollout
  const useNewDashboard = await isFeatureEnabled('new-dashboard', user);
  if (useNewDashboard) {
    return res.json(await buildNewDashboard(user));
  }
  return res.json(await buildLegacyDashboard(user));
});

app.get('/api/v2/experimental', async (req, res) => {
  const user: UserContext = req.auth;

  const hasAccess = await isFeatureEnabled('experimental-api', user);
  if (!hasAccess) {
    return res.status(404).json({ error: 'Not found' });
  }
  return res.json(await handleExperimentalEndpoint(req));
});
```

Key design decisions:
- **`maintenance-mode`** has no targeting rules — it's a simple on/off switch. Flip it to `"defaultVariant": "on"` in an emergency.
- **`new-dashboard`** uses a two-tier targeting: internal users always see it; external users get a 10% fractional rollout.
- **`experimental-api`** returns 404 (not 403) when disabled — this prevents information leakage about upcoming features.
- The TypeScript client wraps OpenFeature with DeployForge-specific types for type safety.

</details>

---

## Capstone Connection

**DeployForge** combines all three progressive delivery strategies:

- **`deploy/rollouts/rollout.yaml`** — An Argo Rollout with a canary strategy. The API service starts at 5% traffic, runs automated analysis at each step, and promotes to 100% only after success rate and latency metrics are verified against Prometheus.
- **`deploy/rollouts/analysis.yaml`** — AnalysisTemplates that query Prometheus for DeployForge's HTTP success rate (≥95%) and p99 latency (≤500ms). Failed analysis triggers instant rollback.
- **Blue-green for database migrations** — When DeployForge ships schema changes, a blue-green Rollout runs the migration on the preview environment, validates with smoke tests, then cuts over.
- **Feature flags with flagd** — DeployForge uses OpenFeature + flagd for gradual feature rollouts. New features launch behind flags for internal users first, then 10% → 50% → 100% external rollout. A `maintenance-mode` kill switch provides instant circuit-breaking.
- **The full stack** — CI pipeline (GitHub Actions) → GitOps (ArgoCD) → Progressive delivery (Argo Rollouts) → Feature flags (flagd). A code change flows through all four layers automatically, with analysis gates at each transition.
