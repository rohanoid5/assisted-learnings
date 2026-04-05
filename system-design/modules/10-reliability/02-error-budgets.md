# 10.2 — Error Budgets

## Concept

An error budget is the amount of unreliability your SLO permits over a given window. If your SLO says 99.9% availability, your error budget is 0.1% — roughly 43 minutes of downtime per month. The critical insight is that this budget is a resource to be spent deliberately: on deploys, migrations, experiments, and chaos tests. When you run out, you stop spending. When you have plenty, you can move fast.

---

## Deep Dive

### Error Budget as a Balance Sheet

```
  Monthly error budget (30-day rolling):

  SLO: 99.9% availability
  Budget: 0.1% × 30 days × 24 hours × 60 min = 43.2 minutes

  ─────────────────────────────────────────────────────────────
  
  Day 5:  Deploy goes wrong, 12 minutes of elevated errors.
          Budget remaining: 43.2 - 12 = 31.2 min
  
  Day 12: Kafka migration, planned 10-minute degraded mode.
          Budget remaining: 31.2 - 10 = 21.2 min
  
  Day 18: Chaos experiment: kill a Redis node.
          3 minutes of cache miss storm before auto-recovery.
          Budget remaining: 21.2 - 3 = 18.2 min
  
  Day 22: Unexpected incident, 25 minutes.
          Budget remaining: 18.2 - 25 = -6.8 min ← BUDGET EXHAUSTED

  ─────────────────────────────────────────────────────────────
  Budget exhausted: freeze deploys, no new chaos experiments.
  Focus: incident review, fix root cause, harden system.
```

### Budget Burn Rate

Burn rate is how fast you're consuming the error budget **relative to a constant sustainable pace**.

```
  Burn rate 1.0 = consuming budget at exactly the SLO rate
    (use all budget in exactly 30 days — sustainable)
  
  Burn rate 2.0 = consuming budget 2× faster
    (30-day budget gone in 15 days — unsustainable)
  
  Burn rate 14.4 = the "1-hour alert" threshold
    (at this rate, you'll exhaust 1 hour's budget in ~5 minutes)
  
  Google SRE recommends multi-window alerts:
    Fast burn alert: burn_rate > 14.4 over 1h  → page on-call immediately
    Slow burn alert: burn_rate > 3   over 6h   → ticket (not yet an emergency)
```

#### PromQL Burn Rate

```
  # 1-hour burn rate for ScaleForge redirect availability
  
  error_rate_1h = 
    sum(rate(http_requests_total{status=~"5..", handler="/r/:code"}[1h]))
    /
    sum(rate(http_requests_total{handler="/r/:code"}[1h]))
  
  burn_rate_1h = error_rate_1h / (1 - 0.999)
  #                              └─────────┘
  #                              error budget fraction (0.001)
  
  Alert if burn_rate_1h > 14.4
```

### When to Stop Spending

```
  Budget policy (recommendation):
  
  Remaining budget > 50%:  Deploy freely, run experiments
  Remaining budget 20–50%: Deploy with extra caution (canary only)
  Remaining budget 5–20%:  Freeze experiments, review deploy process
  Remaining budget < 5%:   Freeze ALL deploys, incident review required
  Remaining budget = 0%:   Full stop. Fix before anything ships.
  
  This policy transforms reliability into a team negotiation:
    Product team: "We need this feature shipped tomorrow."
    Engineering: "Our error budget is at 3%. We deploy when it's > 20%."
    
  Everyone can see the budget. No one can ignore the math.
```

---

## Code Examples

### Computing Error Budget Remaining in Node.js

```typescript
// src/observability/error-budget.ts
// Queries Prometheus to compute remaining error budget.
// Intended for a monitoring dashboard or Slack digest.

interface ErrorBudgetStatus {
  slo: number;           // e.g. 0.999
  windowDays: number;    // e.g. 30
  budgetMinutes: number; // total budget in minutes
  consumedMinutes: number;
  remainingMinutes: number;
  remainingPercent: number;
  status: 'healthy' | 'caution' | 'critical' | 'exhausted';
}

async function getErrorBudget(
  prometheusUrl: string,
  slo: number,
  windowDays: number,
): Promise<ErrorBudgetStatus> {
  const windowSeconds = windowDays * 24 * 60 * 60;
  const budgetFraction = 1 - slo;
  const budgetSeconds = budgetFraction * windowSeconds;

  // Query actual error rate over the window
  const query = `
    sum(increase(http_requests_total{status=~"5..",handler="/r/:code"}[${windowDays}d]))
    /
    sum(increase(http_requests_total{handler="/r/:code"}[${windowDays}d]))
  `;

  const response = await fetch(
    `${prometheusUrl}/api/v1/query?` + new URLSearchParams({ query }),
    { signal: AbortSignal.timeout(5000) },
  );

  if (!response.ok) throw new Error(`Prometheus query failed: ${response.status}`);

  const data = (await response.json()) as {
    data: { result: Array<{ value: [number, string] }> };
  };

  const actualErrorRate = parseFloat(data.data.result[0]?.value[1] ?? '0');
  const consumedSeconds = actualErrorRate * windowSeconds;
  const remainingSeconds = Math.max(0, budgetSeconds - consumedSeconds);
  const remainingPercent = (remainingSeconds / budgetSeconds) * 100;

  let status: ErrorBudgetStatus['status'];
  if (remainingPercent > 50)      status = 'healthy';
  else if (remainingPercent > 20) status = 'caution';
  else if (remainingPercent > 0)  status = 'critical';
  else                            status = 'exhausted';

  return {
    slo,
    windowDays,
    budgetMinutes:    Math.round(budgetSeconds / 60),
    consumedMinutes:  Math.round(consumedSeconds / 60),
    remainingMinutes: Math.round(remainingSeconds / 60),
    remainingPercent: Math.round(remainingPercent * 10) / 10,
    status,
  };
}
```

### Multi-Window Burn Rate Alert

```yaml
# prometheus/slo-alerts.yml — burn rate rules
# Based on Google SRE workbook multi-window alerting

groups:
  - name: error_budget_burn
    rules:
      # Fast burn: page the on-call engineer immediately
      - alert: ErrorBudgetFastBurn
        expr: |
          (
            sum(rate(http_requests_total{status=~"5..",handler="/r/:code"}[1h]))
            /
            sum(rate(http_requests_total{handler="/r/:code"}[1h]))
          )
          / 0.001 > 14.4
        for: 2m
        labels:
          severity: page
        annotations:
          summary: "Fast error budget burn — 30-day budget gone in < 2 days"
          runbook: "https://wiki.internal/runbooks/scaleforge-redirect-slo"

      # Slow burn: create a ticket, review in stand-up
      - alert: ErrorBudgetSlowBurn
        expr: |
          (
            sum(rate(http_requests_total{status=~"5..",handler="/r/:code"}[6h]))
            /
            sum(rate(http_requests_total{handler="/r/:code"}[6h]))
          )
          / 0.001 > 3
        for: 30m
        labels:
          severity: ticket
        annotations:
          summary: "Slow error budget burn — review before next deploy"
```

---

## Try It Yourself

**Exercise:** Calculate the error budget for FlowForge notification delivery.

```typescript
// Given:
//   SLO: 99.5% of jobs complete successfully (no final failure state)
//   Window: 30 days
//   Current failure rate: 0.3% (measured over past 7 days)

// TODO:
// 1. Compute total error budget in minutes for a 30-day window
// 2. Compute consumed budget based on the current 0.3% failure rate
// 3. Compute the burn rate (current_rate / budget_fraction)
// 4. At this burn rate, how many days until the budget is exhausted?
// 5. Should FlowForge allow new deploys? (Use the policy from the Deep Dive)
```

<details>
<summary>Show the calculations</summary>

```
SLO:             99.5%
Budget fraction: 0.5% = 0.005
Window:          30 days = 43,200 minutes
Budget minutes:  0.005 × 43,200 = 216 minutes

Current failure rate: 0.3% = 0.003
Consumed rate relative to budget: 0.003 / 0.005 = 0.6
  → Consuming 60% of budget rate
  → Sustainable — budget won't be exhausted if rate holds

Burn rate: 0.003 / 0.005 = 0.6  (well below threshold of 3)
Days until exhausted: 30 / 0.6 = 50 days (more than the window → safe)

Policy status: HEALTHY (>50% budget remaining after 7 days extrapolated)
Deploy decision: Deploy freely ✓
```

</details>

---

## Capstone Connection

ScaleForge's error budget converts the abstract goal "be reliable" into concrete engineering decisions. When a Redis instance starts degrading and burning through the redirect availability budget, the on-call engineer doesn't need to guess whether it's "bad enough to escalate" — the burn rate alert fires at exactly the right threshold, and the team's deploy freeze policy kicks in automatically. The budget also protects Product: engineers can say "yes" to fast-moving deploys during healthy periods without accumulating silent reliability debt.
