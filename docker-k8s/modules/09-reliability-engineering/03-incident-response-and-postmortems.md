# 9.3 — Incident Response & Blameless Postmortems

## Concept

No matter how good your SLOs are or how many chaos experiments you run, incidents *will* happen. The difference between a mature engineering organization and an immature one isn't the number of incidents — it's how they respond to them and what they learn afterward. Structured incident response minimizes the blast radius and time to recovery. Blameless postmortems turn every incident into a systemic improvement.

The key shift: incidents aren't caused by *people* making mistakes — they're caused by *systems* that allowed mistakes to have outsized impact. A blameless culture doesn't mean accountability-free; it means we fix the system instead of blaming the human.

---

## Deep Dive

### Incident Severity Levels

Severity levels create a shared vocabulary for "how bad is this?" and determine the response urgency:

| Level | Name | Definition | Response Time | Examples |
|-------|------|-----------|--------------|---------|
| **SEV1** | Critical | Complete service outage or data loss affecting all users | Immediate page, all hands | API returns 500 for all requests, database corruption, security breach |
| **SEV2** | Major | Significant degradation affecting many users | Page within 15 min, dedicated responders | p99 latency 10× normal, 50% of deployments failing, one AZ down |
| **SEV3** | Minor | Partial degradation affecting some users or features | Respond within 1 hour, business hours | Single non-critical endpoint erroring, elevated but not critical error rate |
| **SEV4** | Low | Cosmetic or minor issue, workaround available | Next business day | Dashboard rendering glitch, non-blocking warning in logs |

```
  Impact
    ▲
    │   ┌─────────────┐
    │   │    SEV1      │  All users affected, no workaround
    │   │  Page NOW    │
    │   ├─────────────┤
    │   │    SEV2      │  Many users affected or degraded experience
    │   │  Page soon   │
    │   ├─────────────┤
    │   │    SEV3      │  Some users affected, workaround exists
    │   │  Respond 1h  │
    │   ├─────────────┤
    │   │    SEV4      │  Cosmetic, no user impact
    │   │  Next day    │
    │   └─────────────┘
    └──────────────────▶ Urgency
```

> **Key insight:** When in doubt, escalate. It's far better to page for a SEV2 that turns out to be a SEV3 than to wait on a SEV1 thinking it's a SEV2. You can always downgrade severity — you can't un-lose the 30 minutes you spent debating.

### Incident Response Roles

Clear roles prevent the "everybody is debugging, nobody is communicating" chaos that makes incidents worse:

```
┌─────────────────────────────────────────────────────────────────────┐
│                     Incident Response Team                           │
│                                                                      │
│  ┌─────────────────────────┐                                        │
│  │  Incident Commander (IC) │  Single point of authority             │
│  │  • Coordinates response  │  • Assigns tasks                      │
│  │  • Makes decisions       │  • Calls for escalation               │
│  │  • Declares resolution   │  • Owns the incident channel          │
│  └────────────┬────────────┘                                        │
│               │                                                      │
│    ┌──────────┼──────────────────┐                                  │
│    │          │                  │                                    │
│    ▼          ▼                  ▼                                    │
│  ┌──────┐  ┌──────────────┐  ┌────────────────┐                    │
│  │ Ops  │  │ Comms Lead   │  │ Subject Matter │                    │
│  │ Lead │  │              │  │ Experts (SMEs) │                    │
│  │      │  │ • Status page│  │                │                    │
│  │ • Debug│ │ • Slack/chat │  │ • App experts  │                    │
│  │ • Fix │  │ • Stakeholders│ │ • Infra experts│                    │
│  │ • Verify│ │ • Customers │  │ • DB experts   │                    │
│  └──────┘  └──────────────┘  └────────────────┘                    │
└─────────────────────────────────────────────────────────────────────┘
```

| Role | Responsibility | Key Phrases |
|------|---------------|-------------|
| **Incident Commander (IC)** | Owns the incident. Coordinates, delegates, decides. Does NOT debug. | "I'm IC for this incident." "Alice, investigate the API pods. Bob, check the database." |
| **Operations Lead** | Hands-on debugging and mitigation. Executes the technical fix. | "I see 500s from the gateway. Checking pod logs now." |
| **Communications Lead** | Updates stakeholders, status page, and the incident channel timeline. | "Posting status update: 'Investigating elevated API errors.'" |
| **Subject Matter Expert (SME)** | Provides domain expertise when called in by IC. | "The connection pool maxes at 20 — if we're seeing timeouts, check `pg_stat_activity`." |

> **Production note:** In small teams, one person may hold multiple roles. The IC should never also be the Ops Lead — you can't coordinate and debug simultaneously. If you only have 2 people, one is IC+Comms and the other is Ops Lead.

### The Incident Lifecycle

```
  Alert Fires
      │
      ▼
┌─────────────┐
│  1. TRIAGE   │  Assess severity, assign IC, open incident channel
│  (5 min)     │
└──────┬──────┘
       │
       ▼
┌─────────────┐
│  2. MITIGATE │  Stop the bleeding — rollback, scale up, failover
│  (minutes)   │  Priority: restore service, NOT find root cause
└──────┬──────┘
       │
       ▼
┌─────────────┐
│  3. RESOLVE  │  Confirm service is healthy, error rate back to normal
│  (minutes)   │  Monitor for recurrence (30-60 min soak)
└──────┬──────┘
       │
       ▼
┌─────────────┐
│  4. FOLLOW UP│  Write postmortem, create action items, update runbooks
│  (1-3 days)  │
└─────────────┘
```

> **Key insight:** The goal during an incident is to *mitigate*, not to *root-cause*. Rolling back a deployment that caused errors is a 2-minute fix. Finding the exact line of code that caused the error is a 2-hour investigation. Do the 2-minute fix first, investigate later.

### Communication Templates

Structure reduces cognitive load during high-stress incidents. Use these templates:

**Incident Declaration (Slack/Chat):**

```
🔴 INCIDENT DECLARED — SEV2
Title: Elevated API error rate on DeployForge API Gateway
IC: @alice
Ops Lead: @bob
Comms: @carol
Impact: ~30% of API requests returning 503
Timeline: Errors started at 14:32 UTC
Incident Channel: #inc-2024-0615-api-errors
Status Page: Updated to "Investigating"
```

**Status Update (every 15-30 min for SEV1/SEV2):**

```
📋 STATUS UPDATE — SEV2 — 14:55 UTC
Current state: MITIGATING
What we know: Deployment at 14:30 introduced a connection pool regression.
  The API Gateway is exhausting PostgreSQL connections under load.
What we're doing: Rolling back to previous version (v2.3.1 → v2.3.0).
  Rollback initiated at 14:50, pods cycling now.
Next update: 15:10 UTC or sooner if status changes.
```

**Incident Resolution:**

```
✅ INCIDENT RESOLVED — SEV2 — 15:05 UTC
Title: Elevated API error rate on DeployForge API Gateway
Duration: 33 minutes (14:32 – 15:05 UTC)
Resolution: Rolled back deployment from v2.3.1 to v2.3.0
Impact: ~30% error rate for 33 minutes, consuming 10 min of error budget
Follow-up: Postmortem scheduled for 2024-06-17 at 10:00 UTC
Postmortem doc: [link]
```

### On-Call Rotations

On-call is a responsibility, not a punishment. A well-designed rotation respects work-life balance while ensuring coverage:

```yaml
# oncall-rotation.yaml — PagerDuty/OpsGenie style configuration
rotation:
  name: deployforge-primary
  description: Primary on-call for DeployForge services
  timezone: UTC

  participants:
    - alice    # Week 1
    - bob      # Week 2
    - carol    # Week 3
    - dave     # Week 4

  schedule:
    type: weekly
    handoff_time: "09:00"      # Monday 09:00 UTC
    handoff_day: monday

  escalation:
    - level: 1
      target: current-oncall
      timeout: 5m               # Page again if no ack in 5 min

    - level: 2
      target: secondary-oncall   # Next person in rotation
      timeout: 10m

    - level: 3
      target: engineering-manager
      timeout: 15m

  overrides:
    - participant: alice
      start: "2024-07-01"
      end: "2024-07-08"
      replacement: eve           # Alice on vacation, Eve covers
```

**On-call best practices:**

| Practice | Why |
|----------|-----|
| Maximum 1 week on-call at a time | Prevent burnout |
| Compensatory time off after on-call | On-call is work, not a favor |
| No more than 2 pages per on-call shift (target) | More means your alerts are too noisy |
| Runbooks for every alert | On-call shouldn't require tribal knowledge |
| Shadow on-call for new team members | Learn the ropes before going solo |
| Post-on-call handoff notes | "What happened this week, what to watch for" |

### Triage and Mitigation Strategies

When an alert fires, the on-call engineer follows a decision tree:

```
Alert Fires
    │
    ▼
Is this a known issue with a runbook?
    │
    ├── YES ──▶ Follow the runbook
    │
    └── NO
        │
        ▼
    Can you quickly identify the change that caused it?
        │
        ├── YES ──▶ Rollback the change
        │
        └── NO
            │
            ▼
        Can you mitigate without understanding root cause?
            │
            ├── YES ──▶ Scale up / restart / failover
            │           (then investigate root cause)
            │
            └── NO ──▶ Escalate: declare incident, assign IC
```

**Common mitigation strategies:**

| Strategy | When to Use | Command |
|----------|------------|---------|
| **Rollback** | Bad deploy identified | `kubectl rollout undo deployment/api-gateway -n deployforge` |
| **Scale up** | Traffic spike or resource exhaustion | `kubectl scale deployment/api-gateway --replicas=5 -n deployforge` |
| **Restart** | Memory leak or corrupted state | `kubectl rollout restart deployment/api-gateway -n deployforge` |
| **Failover** | Single-AZ or single-node failure | Redirect traffic to healthy nodes via Service/Ingress |
| **Feature flag** | Specific feature causing issues | Disable via ConfigMap + restart |
| **Rate limit** | DDoS or runaway client | Apply rate limiting at ingress level |

### Blameless Postmortems

A postmortem is not a blame document — it's a learning document. The goal is to understand *what happened*, *why the system allowed it to happen*, and *how to prevent recurrence*.

> **Key insight (from John Allspaw):** "We need to look at how people's work helped to prevent the incident from being worse." Humans are the adaptive element — they notice things, make judgment calls, and compensate for system failures. A blameless postmortem acknowledges this.

### Postmortem Template

```markdown
# Postmortem: [Incident Title]

**Date:** YYYY-MM-DD
**Authors:** [Names of postmortem authors]
**Status:** Draft | In Review | Complete
**Severity:** SEV1 / SEV2 / SEV3

## Summary

[2-3 sentence summary of what happened and the impact]

## Impact

- **Duration:** X minutes/hours (start time – end time UTC)
- **Users affected:** X% of users / Y total users
- **Error budget consumed:** Z minutes of the 30-day budget
- **Revenue impact:** $X (if applicable)
- **Data loss:** None / Describe

## Timeline (all times UTC)

| Time | Event |
|------|-------|
| 14:30 | Deployment v2.3.1 rolled out via CI/CD pipeline |
| 14:32 | Monitoring alert: `DeployForgeHighErrorBurnRate` fires |
| 14:35 | On-call engineer @alice acknowledges alert |
| 14:37 | IC declared, incident channel opened |
| 14:42 | Root cause identified: connection pool regression in v2.3.1 |
| 14:50 | Rollback to v2.3.0 initiated |
| 14:55 | Rollback complete, error rate declining |
| 15:05 | Error rate back to baseline, incident resolved |

## Root Cause

[Detailed technical explanation of what went wrong and why.
Focus on the systemic cause, not human error.]

Example:
The v2.3.1 deployment included a change to the database connection pool
configuration that reduced the maximum pool size from 20 to 5 connections.
Under the production request rate of ~200 req/s, the 5-connection pool
was immediately exhausted, causing connection timeout errors for ~30%
of requests.

The configuration change was made in a PR that modified 47 files. The
connection pool change was in a shared config file that wasn't specifically
reviewed. The CI/CD pipeline's load test step uses a lower request rate
than production, so the bottleneck wasn't caught in staging.

## Contributing Factors

- [ ] Large PR size (47 files) reduced review thoroughness
- [ ] CI/CD load test doesn't match production traffic patterns
- [ ] No specific alert for connection pool exhaustion
- [ ] Connection pool size not included in deployment diff summary

## What Went Well

- Alert fired within 2 minutes of the issue starting
- IC was declared quickly and the rollback decision was fast
- Rollback was clean and completed in under 5 minutes
- Error budget tracking made impact assessment immediate

## What Went Poorly

- It took 10 minutes from alert to identifying root cause
- No runbook existed for connection pool exhaustion
- The PR review process didn't catch the config change

## Action Items

| ID | Action | Owner | Priority | Due Date | Status |
|----|--------|-------|----------|----------|--------|
| 1 | Add connection pool size to deployment diff summary | @bob | P1 | 2024-06-24 | Open |
| 2 | Create alert for `pg_stat_activity` connection count > 80% of pool | @alice | P1 | 2024-06-21 | Open |
| 3 | Increase CI/CD load test to match production traffic patterns | @carol | P2 | 2024-07-01 | Open |
| 4 | Write runbook for connection pool exhaustion | @alice | P2 | 2024-06-28 | Open |
| 5 | Enforce PR size limit (< 20 files) or require extra reviewer | @dave | P3 | 2024-07-15 | Open |

## Lessons Learned

1. Configuration changes buried in large PRs are a reliability risk.
   Infra config should have dedicated PRs with targeted review.
2. Load tests that don't match production traffic are false confidence.
3. Connection pool metrics should be a first-class observable.
```

### Tracking Action Items

Postmortems are worthless if action items aren't completed. Track them rigorously:

```
┌─────────────────────────────────────────────────────────────────────┐
│              Action Item Lifecycle                                    │
│                                                                      │
│  Postmortem ──▶ Action Items ──▶ Jira/GitHub Issues ──▶ Sprint      │
│                     │                                                │
│                     ├── P1: Must fix this sprint                     │
│                     ├── P2: Must fix within 30 days                  │
│                     └── P3: Nice-to-have, plan within quarter        │
│                                                                      │
│  Monthly Review: "Are all P1/P2 items from postmortems completed?"  │
│  Quarterly Review: "What themes emerge from our postmortems?"        │
└─────────────────────────────────────────────────────────────────────┘
```

> **Production note:** If your postmortem action items have a completion rate below 80%, the postmortem process is failing. Either the items are too ambitious, the ownership is unclear, or there's no follow-up mechanism. Fix the process.

### Toil Identification and Reduction

Toil is repetitive, manual, automatable work that scales linearly with service growth. SREs should spend no more than 50% of their time on toil — the rest goes to engineering work that reduces future toil.

| Is It Toil? | Yes | No |
|-------------|-----|-----|
| Manual? | Clicking through a UI to restart pods | Writing a script to auto-restart |
| Repetitive? | Running the same deploy steps weekly | Designing a new deployment architecture |
| Automatable? | Rotating certificates by hand | Investigating a novel performance issue |
| Reactive? | Responding to the same alert daily | Building a dashboard proactively |
| Grows with service? | More users = more manual scaling | Autoscaling handles growth automatically |

**Toil reduction strategies:**

```yaml
# toil-inventory.yaml — Track and prioritize toil reduction
toil_items:
  - name: Manual certificate rotation
    frequency: monthly
    time_per_occurrence: 2h
    annual_cost: 24h
    automation: cert-manager with Let's Encrypt auto-renewal
    effort_to_automate: 4h
    roi: 6× (4h investment saves 24h/year)

  - name: Manually scaling before marketing events
    frequency: quarterly
    time_per_occurrence: 1h
    annual_cost: 4h
    automation: HPA with custom metrics + event-triggered pre-scaling
    effort_to_automate: 8h
    roi: 0.5× (not worth automating yet, but track it)

  - name: Restarting pods after memory leak
    frequency: weekly
    time_per_occurrence: 15min
    annual_cost: 13h
    automation: Fix the memory leak (root cause) or add memory-based liveness probe
    effort_to_automate: 2h
    roi: 6.5× (fix the root cause!)
```

> **Key insight:** Not all toil is worth automating. Calculate ROI: `annual_toil_hours / automation_effort_hours`. If the ratio is less than 2×, it might not be worth the investment yet — unless the toil is also error-prone or blocks higher-priority work.

### Learning from Incidents at Scale

As your organization grows, individual postmortems aren't enough. You need to identify *themes*:

```
┌─────────────────────────────────────────────────────────────────────┐
│              Incident Theme Analysis (Quarterly)                     │
│                                                                      │
│  Category          │ Q1 │ Q2 │ Trend │ Top Contributing Factor      │
│  ──────────────────┼────┼────┼───────┼──────────────────────────    │
│  Config changes    │  4 │  6 │  ▲    │ Large PRs with hidden config │
│  Dependency failure│  2 │  3 │  ▲    │ No circuit breaker on DB     │
│  Capacity          │  1 │  1 │  ─    │ Manual scaling, no HPA       │
│  Deploy failure    │  3 │  1 │  ▼    │ Added canary (Module 10)     │
│  Security          │  0 │  1 │  ▲    │ Expired certificate          │
│                                                                      │
│  Top Action: Enforce config-change-only PRs with dedicated review    │
└─────────────────────────────────────────────────────────────────────┘
```

---

## Code Examples

### TypeScript: Incident Severity Classifier

```typescript
// incident-classifier.ts
interface AlertContext {
  errorRate: number;         // current error rate (0-1)
  p99LatencyMs: number;      // current p99 latency
  affectedEndpoints: number; // how many endpoints are affected
  totalEndpoints: number;
  errorBudgetRemaining: number; // 0-1
  dataLossDetected: boolean;
}

type Severity = "SEV1" | "SEV2" | "SEV3" | "SEV4";

function classifyIncident(ctx: AlertContext): {
  severity: Severity;
  reason: string;
  actions: string[];
} {
  // SEV1: Complete outage or data loss
  if (ctx.dataLossDetected) {
    return {
      severity: "SEV1",
      reason: "Data loss detected",
      actions: [
        "Page all on-call immediately",
        "Declare incident, assign IC",
        "Stop all writes to affected datastore",
        "Begin data recovery procedure",
      ],
    };
  }

  if (ctx.errorRate > 0.5 || ctx.affectedEndpoints === ctx.totalEndpoints) {
    return {
      severity: "SEV1",
      reason: `Complete outage: ${(ctx.errorRate * 100).toFixed(0)}% error rate`,
      actions: [
        "Page all on-call immediately",
        "Declare incident, assign IC",
        "Check recent deployments for rollback",
        "Scale up if resource-related",
      ],
    };
  }

  // SEV2: Major degradation
  if (ctx.errorRate > 0.1 || ctx.p99LatencyMs > 5000) {
    return {
      severity: "SEV2",
      reason: `Major degradation: ${(ctx.errorRate * 100).toFixed(1)}% errors, p99=${ctx.p99LatencyMs}ms`,
      actions: [
        "Page primary on-call",
        "Declare incident if not self-resolving in 5 min",
        "Check error budget dashboard",
        "Investigate recent changes",
      ],
    };
  }

  // SEV3: Minor degradation
  if (ctx.errorRate > 0.01 || ctx.p99LatencyMs > 1000) {
    return {
      severity: "SEV3",
      reason: `Minor degradation: ${(ctx.errorRate * 100).toFixed(2)}% errors`,
      actions: [
        "Acknowledge alert",
        "Investigate during business hours",
        "Check if error budget allows deferring",
      ],
    };
  }

  // SEV4: Low-impact
  return {
    severity: "SEV4",
    reason: "Low-impact issue detected",
    actions: ["Create ticket for next sprint", "Monitor for escalation"],
  };
}
```

### Bash: Incident Response Runbook for DeployForge

```bash
#!/usr/bin/env bash
# runbook-api-errors.sh — Automated triage for DeployForge API errors
set -euo pipefail

NAMESPACE="deployforge"
echo "=== DeployForge API Error Triage Runbook ==="
echo "Time: $(date -u '+%Y-%m-%d %H:%M:%S UTC')"
echo ""

# Step 1: Check pod health
echo "--- Step 1: Pod Health ---"
kubectl get pods -n "$NAMESPACE" -l app=api-gateway \
  -o custom-columns='NAME:.metadata.name,STATUS:.status.phase,RESTARTS:.status.containerStatuses[0].restartCount,AGE:.metadata.creationTimestamp'
echo ""

CRASH_LOOPS=$(kubectl get pods -n "$NAMESPACE" -l app=api-gateway \
  -o jsonpath='{.items[?(@.status.containerStatuses[0].state.waiting.reason=="CrashLoopBackOff")].metadata.name}')
if [ -n "$CRASH_LOOPS" ]; then
  echo "⚠️  CrashLoopBackOff detected: $CRASH_LOOPS"
  echo "   → Check logs: kubectl logs -n $NAMESPACE $CRASH_LOOPS --previous"
fi

# Step 2: Check recent deployments
echo "--- Step 2: Recent Deployments ---"
kubectl rollout history deployment/api-gateway -n "$NAMESPACE" | tail -5
echo ""

LAST_DEPLOY=$(kubectl get deployment api-gateway -n "$NAMESPACE" \
  -o jsonpath='{.metadata.annotations.kubectl\.kubernetes\.io/last-applied-configuration}' 2>/dev/null \
  | jq -r '.spec.template.metadata.labels.version // "unknown"')
echo "Current version: $LAST_DEPLOY"
echo ""

# Step 3: Check resource usage
echo "--- Step 3: Resource Usage ---"
kubectl top pods -n "$NAMESPACE" -l app=api-gateway 2>/dev/null || echo "(metrics-server not available)"
echo ""

# Step 4: Check recent logs for errors
echo "--- Step 4: Recent Error Logs (last 50 lines) ---"
kubectl logs -n "$NAMESPACE" -l app=api-gateway --tail=50 --since=5m 2>/dev/null \
  | grep -i "error\|fatal\|panic\|timeout" | tail -20 || echo "(no errors in last 5 min)"
echo ""

# Step 5: Suggested actions
echo "=== Suggested Actions ==="
echo "1. Rollback:  kubectl rollout undo deployment/api-gateway -n $NAMESPACE"
echo "2. Scale up:  kubectl scale deployment/api-gateway --replicas=5 -n $NAMESPACE"
echo "3. Restart:   kubectl rollout restart deployment/api-gateway -n $NAMESPACE"
echo "4. Full logs: kubectl logs -n $NAMESPACE -l app=api-gateway --since=15m"
```

### YAML: Alertmanager Routing for Incident Severity

```yaml
# alertmanager-config.yaml
apiVersion: v1
kind: Secret
metadata:
  name: alertmanager-config
  namespace: monitoring
stringData:
  alertmanager.yml: |
    global:
      resolve_timeout: 5m

    route:
      receiver: default
      group_by: ['alertname', 'namespace']
      group_wait: 30s
      group_interval: 5m
      repeat_interval: 4h

      routes:
        # SEV1: Critical — page immediately
        - match:
            severity: critical
          receiver: pagerduty-critical
          group_wait: 0s           # Don't wait, page immediately
          repeat_interval: 5m      # Re-page every 5 min until ack

        # SEV2: Warning — page within 15 min
        - match:
            severity: warning
          receiver: pagerduty-warning
          group_wait: 2m
          repeat_interval: 30m

        # SEV3/4: Ticket — Slack notification
        - match:
            severity: ticket
          receiver: slack-tickets
          repeat_interval: 24h

    receivers:
      - name: default
        slack_configs:
          - channel: '#deployforge-alerts'
            send_resolved: true

      - name: pagerduty-critical
        pagerduty_configs:
          - service_key_file: /etc/alertmanager/secrets/pagerduty-key
            severity: critical
            description: '{{ .CommonAnnotations.summary }}'
            details:
              runbook: '{{ .CommonAnnotations.runbook_url }}'

      - name: pagerduty-warning
        pagerduty_configs:
          - service_key_file: /etc/alertmanager/secrets/pagerduty-key
            severity: warning

      - name: slack-tickets
        slack_configs:
          - channel: '#deployforge-tickets'
            title: '🎫 {{ .CommonLabels.alertname }}'
            text: '{{ .CommonAnnotations.description }}'
            send_resolved: true
```

---

## Try It Yourself

### Challenge 1: Classify an Incident

You receive the following alert at 03:00 UTC:

```
Alert: DeployForgeHighErrorBurnRate
Error rate (1h): 0.025 (2.5%)
Error rate (5m): 0.031 (3.1%)
p99 latency: 1200ms
Affected endpoints: 3 of 8
Error budget remaining: 62%
```

Determine: severity level, IC action in the first 5 minutes, and three diagnostic commands to run.

<details>
<summary>Show solution</summary>

**Severity: SEV2**

Reasoning: 2.5-3.1% error rate is significant (above 1% threshold) and p99 is 2.4× normal, but it's not a complete outage (3 of 8 endpoints, 62% budget remaining). This warrants a page but not all-hands.

**IC first 5 minutes:**

1. Acknowledge the page, open incident channel `#inc-2024-MMDD-api-errors`
2. Post incident declaration with severity, impact, and assign roles
3. Direct Ops Lead to investigate while IC manages communication

**Diagnostic commands:**

```bash
# 1. Check for recent deployments (most common cause)
kubectl rollout history deployment/api-gateway -n deployforge | tail -5

# 2. Check pod health and restart counts
kubectl get pods -n deployforge -l app=api-gateway -o wide
kubectl top pods -n deployforge -l app=api-gateway

# 3. Check error logs for the pattern
kubectl logs -n deployforge -l app=api-gateway --since=10m \
  | grep -c "error" | sort | uniq -c | sort -rn | head -5
```

If a recent deployment is identified, the fastest mitigation is:

```bash
kubectl rollout undo deployment/api-gateway -n deployforge
```

</details>

### Challenge 2: Write a Blameless Postmortem

A simulated incident occurred: DeployForge's Worker Service stopped processing jobs for 45 minutes because a Kubernetes node ran out of disk space due to unrotated container logs. Write a complete postmortem using the template from this section.

<details>
<summary>Show solution</summary>

```markdown
# Postmortem: Worker Service Outage Due to Node Disk Pressure

**Date:** 2024-06-20
**Authors:** Alice (IC), Bob (Ops Lead)
**Status:** Complete
**Severity:** SEV2

## Summary

The DeployForge Worker Service stopped processing jobs for 45 minutes
when its Kubernetes node entered DiskPressure condition. Container logs
from all pods on the node had accumulated to 47GB over 3 weeks, filling
the node's 50GB root disk. The kubelet evicted non-critical pods, but
the Worker's QoS class (Burstable) caused it to be evicted as well.

## Impact

- **Duration:** 45 minutes (10:15 – 11:00 UTC)
- **Users affected:** All users with pending deployments (est. 15 users)
- **Error budget consumed:** 45 minutes of the 432-minute budget (10.4%)
- **Jobs affected:** 23 jobs queued during the outage, processed after recovery
- **Data loss:** None — jobs were queued in Redis and processed after recovery

## Timeline (all times UTC)

| Time  | Event |
|-------|-------|
| 10:15 | kubelet reports DiskPressure condition on node-2 |
| 10:17 | kubelet begins evicting pods with QoS class Burstable |
| 10:18 | Worker pod evicted from node-2 |
| 10:20 | Alert fires: WorkerHighBurnRate (job success rate = 0%) |
| 10:25 | On-call @alice acknowledges, opens #inc-2024-0620-worker |
| 10:30 | IC assigns @bob to investigate; node disk pressure identified |
| 10:35 | @bob identifies 47GB of container logs consuming disk |
| 10:40 | @bob manually clears old logs: `find /var/log/containers -mtime +7 -delete` |
| 10:45 | DiskPressure condition clears, node accepts scheduling |
| 10:50 | Worker pod rescheduled and starts processing queued jobs |
| 11:00 | All queued jobs processed, error rate returns to baseline |

## Root Cause

The kind cluster nodes were configured with 50GB root disks. Container
runtime log rotation was not configured (Docker/containerd default: no
rotation). Over 3 weeks of operation, container logs accumulated to 47GB.
When the node's available disk dropped below the kubelet's
eviction threshold (15%), it began evicting pods.

The Worker Service had QoS class Burstable (requests < limits) and was
evicted before higher-priority Guaranteed pods. After eviction, the
Deployment controller couldn't reschedule because the node was the only
one with available resources matching the Worker's node affinity rules.

## Contributing Factors

- [ ] Container log rotation not configured on cluster nodes
- [ ] No monitoring alert for node disk usage > 80%
- [ ] Worker pod QoS class was Burstable instead of Guaranteed
- [ ] Single-node affinity for worker limited rescheduling options
- [ ] No disk usage item in the operational checklist

## What Went Well

- Alert fired within 5 minutes of the eviction
- Root cause was identified in 10 minutes
- No data was lost — BullMQ's Redis-backed queue held all jobs
- Clear communication in the incident channel

## What Went Poorly

- 45-minute outage for a preventable issue
- No existing runbook for disk pressure
- Log rotation should have been configured at cluster setup
- Worker was on a single node with affinity constraints

## Action Items

| ID | Action | Owner | Priority | Due | Status |
|----|--------|-------|----------|-----|--------|
| 1 | Configure container log rotation (10MB max, 3 files) | @bob | P1 | 06-22 | Open |
| 2 | Add node disk usage alert (>80% warning, >90% critical) | @alice | P1 | 06-22 | Open |
| 3 | Set Worker pod QoS to Guaranteed (requests == limits) | @carol | P2 | 06-28 | Open |
| 4 | Remove single-node affinity from Worker deployment | @carol | P2 | 06-28 | Open |
| 5 | Write runbook for node disk pressure | @alice | P2 | 06-30 | Open |
| 6 | Add disk usage to weekly operational review checklist | @dave | P3 | 07-05 | Open |

## Lessons Learned

1. Log rotation is infrastructure-level hygiene that must be configured
   at cluster bootstrap — not after the first disk-fill incident.
2. QoS class matters for eviction order. Stateful workloads that
   process queued work should be Guaranteed to survive node pressure.
3. Node affinity constraints that limit scheduling to a single node
   create a single point of failure.
```

</details>

### Challenge 3: Build an On-Call Runbook

Write a runbook for the DeployForge on-call engineer that covers the top 3 most likely alerts they'll receive, with step-by-step triage and mitigation for each.

<details>
<summary>Show solution</summary>

```markdown
# DeployForge On-Call Runbook

## Alert 1: DeployForgeHighErrorBurnRate (Critical)

**What it means:** API Gateway error rate is consuming error budget
at 14.4× the sustainable rate. At this pace, the 30-day budget
exhausts in ~50 hours.

**Triage steps:**

1. Check if a recent deployment happened:
   ```bash
   kubectl rollout history deployment/api-gateway -n deployforge | tail -3
   ```

2. Check pod health:
   ```bash
   kubectl get pods -n deployforge -l app=api-gateway
   ```

3. Check error logs:
   ```bash
   kubectl logs -n deployforge -l app=api-gateway --since=5m \
     | grep -i error | head -20
   ```

**Mitigation:**

- If recent deploy: `kubectl rollout undo deployment/api-gateway -n deployforge`
- If pod crash loop: `kubectl delete pod <pod-name> -n deployforge`
- If resource exhaustion: `kubectl scale deployment/api-gateway --replicas=5 -n deployforge`
- If unclear: Declare SEV2 incident and escalate

---

## Alert 2: WorkerHighBurnRate (Critical)

**What it means:** Worker job failure rate is burning error budget fast.

**Triage steps:**

1. Check worker pods:
   ```bash
   kubectl get pods -n deployforge -l app=worker
   ```

2. Check Redis connectivity (BullMQ backend):
   ```bash
   kubectl exec -n deployforge deploy/worker -- redis-cli -h redis ping
   ```

3. Check recent failed jobs:
   ```bash
   kubectl logs -n deployforge -l app=worker --since=5m \
     | grep -i "failed\|error" | head -20
   ```

**Mitigation:**

- If Redis unreachable: Check Redis pod, restart if needed
- If job-specific errors: Check job payload in logs
- If worker OOM: Increase memory limits, restart deployment
- If unclear: Declare incident

---

## Alert 3: NodeDiskPressure (Warning)

**What it means:** A cluster node is running low on disk space.

**Triage steps:**

1. Identify the affected node:
   ```bash
   kubectl get nodes -o custom-columns='NAME:.metadata.name,DISK_PRESSURE:.status.conditions[?(@.type=="DiskPressure")].status'
   ```

2. Check disk usage on the node:
   ```bash
   kubectl debug node/<node-name> -it --image=busybox -- df -h /
   ```

3. Check container log sizes:
   ```bash
   kubectl debug node/<node-name> -it --image=busybox -- du -sh /var/log/containers/ 2>/dev/null
   ```

**Mitigation:**

- Clear old container logs: Restart the log-rotation CronJob
- If image cache full: `crictl rmi --prune` on the node
- If PVC is full: Expand the PVC or clean up data
- Prevent recurrence: Verify log rotation is configured
```

</details>

---

## Capstone Connection

**DeployForge** implements a complete incident management lifecycle integrated with the SLO framework from Section 9.1:

- **Severity classification** — DeployForge alerts include a `severity` label (critical/warning/ticket) that maps directly to SEV1/SEV2/SEV3. Alertmanager routes critical alerts to PagerDuty and warning alerts to Slack, ensuring the right response for the right urgency.
- **Incident response runbook** — A per-service runbook lives in the `deployforge/runbooks/` directory, covering the top failure modes for the API Gateway, Worker Service, PostgreSQL, and Redis. Each runbook includes triage commands, mitigation steps, and escalation criteria.
- **Blameless postmortem process** — After every SEV1/SEV2 incident, the team writes a postmortem using a standardized template. Action items are tracked as GitHub Issues with `postmortem` labels and `P1`/`P2`/`P3` priority tags. A monthly review ensures completion.
- **Toil tracking** — DeployForge maintains a toil inventory (`deployforge/docs/toil-inventory.yaml`) that tracks repetitive operational tasks, their frequency, and automation ROI. The top-ROI items feed into each sprint's reliability work allocation.
- **Error budget integration** — The incident response process feeds back into the error budget framework. Every SEV1/SEV2 incident's error budget consumption is calculated and displayed on the Grafana dashboard. When an incident pushes the budget below a threshold, the error budget policy from Section 9.1 automatically adjusts deployment velocity.
- **Continuous improvement** — Quarterly incident theme analysis identifies recurring patterns. In Module 10, the CI/CD pipeline will incorporate lessons learned — automated canary analysis catches the kinds of issues that previously required human detection during incidents.
