# Module 10 Exercises — Reliability Engineering

These exercises reinforce the SLO/error-budget/degradation concepts from this module. Some require a running ScaleForge instance; others are calculation/design exercises.

---

## Exercise 1 — Define SLOs for a New Feature

**Goal:** Practice writing user-centric SLOs before writing code.

**Scenario:** ScaleForge is adding a new "custom domain" feature: users can map their own domain (e.g., `go.mycompany.com`) to ScaleForge short links.

**Tasks:**

1. Identify 3 user-visible behaviors for this feature. For each, write an SLI (what to measure) and an SLO value (what threshold is acceptable).

2. For each SLI, write the Prometheus metric name you'd emit:

```typescript
// TODO: Fill in the SLO table

const customDomainSLOs = [
  {
    behavior:   'Custom domain resolves to the correct long URL',
    sli:        '???',  // e.g. "fraction of requests returning 302"
    sloTarget:  '???',  // e.g. ">= 99.9%"
    prometheus: '???',  // e.g. "http_requests_total{handler=...}"
  },
  {
    behavior:   'Custom domain setup completes within 60 minutes of configuration',
    sli:        '???',
    sloTarget:  '???',
    prometheus: '???',
  },
  {
    behavior:   'Latency: custom domain redirect completes in under ???ms at p99',
    sli:        '???',
    sloTarget:  '???',
    prometheus: '???',
  },
];
```

<details>
<summary>Show sample SLO definitions</summary>

```typescript
const customDomainSLOs = [
  {
    behavior:   'Custom domain resolves to the correct long URL',
    sli:        'fraction of GET requests to custom domain handler returning 302 (not 404/5xx)',
    sloTarget:  '>= 99.9%',
    prometheus: 'http_requests_total{handler="/custom/:domain/:code",status="302"}',
  },
  {
    behavior:   'Domain setup propagation within 60 minutes',
    sli:        'fraction of domains where DNS resolves within 60 min of creation',
    sloTarget:  '>= 99%',
    prometheus: 'custom_domain_propagation_seconds (histogram, alert if p95 > 3600s)',
  },
  {
    behavior:   'Custom domain redirect latency',
    sli:        'p99 latency of GET /custom/:domain/:code',
    sloTarget:  '<= 50ms (same as standard redirect)',
    prometheus: 'http_request_duration_seconds{handler="/custom/:domain/:code"}',
  },
];
```

</details>

---

## Exercise 2 — Error Budget Burn Rate Calculation

**Goal:** Practice burn rate math before writing alerting rules.

**Given:**
- ScaleForge `POST /api/v1/urls` SLO: 99.5% availability
- Over the past 2 hours, the error rate is 1.5%
- Window: 30-day rolling

**Calculate:**

```
// TODO:
// 1. What is the error budget fraction? (1 − SLO)
// 2. What is the current burn rate?
//    burn_rate = current_error_rate / error_budget_fraction
// 3. At this burn rate, how many hours until the 30-day budget is exhausted?
// 4. Should the "fast burn" alert fire?
//    (Threshold: burn_rate > 14.4 over 1 hour)
// 5. What action should the team take based on the deploy policy?
```

<details>
<summary>Show the calculations</summary>

```
1. Error budget fraction:
   1 - 0.995 = 0.005 (0.5%)

2. Current burn rate:
   0.015 / 0.005 = 3.0
   (burning 3× the sustainable rate)

3. Time until budget exhausted:
   30 days / 3.0 = 10 days
   (at this rate, you'd exhaust the month's budget in 10 days)

4. Fast burn alert (> 14.4 over 1 hour)?
   Burn rate = 3.0 < 14.4 → fast burn alert does NOT fire

5. Slow burn alert (> 3 over 6 hours)?
   Burn rate = 3.0 — borderline, would fire if it sustains for 6h

6. Deploy policy:
   Budget remaining after 2 hours of 1.5% error rate:
     Budget seconds = 0.005 × 30 × 86400 = 12,960 seconds
     Consumed in 2h = 0.015 × 7200 = 108 seconds
     Remaining = 12,960 - 108 = 12,852 seconds (~214 min)
     % remaining = 12,852 / 12,960 = 99.2% → still HEALTHY
   
   Action: No freeze needed. But investigate the 1.5% error rate —
           if it continues for 6h, slow burn alert fires.
```

</details>

---

## Exercise 3 — Design a Degradation Tier for FlowForge

**Goal:** Apply the graceful degradation pattern to FlowForge notifications.

**Scenario:** FlowForge's database becomes unavailable. The notification queue in BullMQ is backed by Redis (separate from the app's Redis). The email/webhook workers read from BullMQ.

**Tasks:**

1. Draw the degradation tiers for FlowForge's `POST /api/v1/notifications` endpoint:
   - Tier 0: Everything works
   - Tier 1: Postgres unavailable (Redis + BullMQ still up)
   - Tier 2: Postgres + Redis unavailable
   - Tier 3: Everything unavailable

2. For each tier, answer:
   - What does the endpoint return? (201, 202, 503?)
   - Are notifications delivered? Immediately? Eventually? Not at all?
   - What happens to jobs already in the queue?

3. Implement Tier 1: If the Postgres `INSERT` to the `notifications` table fails, enqueue the job to BullMQ anyway (write-behind), and return `202 Accepted` with a job ID.

```typescript
// TODO: Implement Tier 1 handler
// async function handleNotificationWithFallback(payload: NotificationPayload): Promise<{ jobId: string; tier: number }>
```

<details>
<summary>Show the tier design and Tier 1 implementation</summary>

```
Tier 0 (all up):
  POST /api/v1/notifications
    → INSERT into Postgres (audit log)
    → ZADD to BullMQ (Redis)
    → Return 201 with jobId
  Delivery: guaranteed, full audit trail

Tier 1 (Postgres down, Redis up):
  → Postgres INSERT fails
  → Fall back: enqueue to BullMQ only (no audit log)
  → Return 202 with jobId (not 201 — we don't have DB confirmation)
  Delivery: job is queued, delivered when workers process it
  Risk: audit log has a gap — reconcile on Postgres recovery

Tier 2 (Postgres + Redis down):
  → BullMQ enqueue fails
  → Fall back: write to local in-process queue (bounded buffer, max 1000 entries)
  → Return 202 with a synthetic ID
  Delivery: delayed — will flush to BullMQ when Redis recovers
  Risk: data loss if process restarts before Redis recovers

Tier 3 (everything down):
  → Buffer is full OR process was restarted with lost buffer
  → Return 503 with Retry-After: 30
  Delivery: not guaranteed — caller must retry

// Tier 1 implementation:
async function handleNotificationWithFallback(
  payload: NotificationPayload
): Promise<{ jobId: string; tier: number }> {
  // Try Tier 0: DB + queue
  try {
    const row = await db.query(
      'INSERT INTO notifications (payload) VALUES ($1) RETURNING id',
      [JSON.stringify(payload)],
    );
    const jobId = await notificationQueue.add('send', payload);
    return { jobId: jobId.id, tier: 0 };
  } catch (dbErr) {
    logger.warn({ err: dbErr }, 'Postgres down, falling back to queue-only (Tier 1)');
  }

  // Tier 1: Queue only (no DB audit)
  try {
    const jobId = await notificationQueue.add('send', payload);
    return { jobId: jobId.id, tier: 1 };
  } catch (queueErr) {
    logger.error({ err: queueErr }, 'BullMQ unavailable, using in-memory buffer (Tier 2)');
    throw queueErr;  // handle Tier 2 at caller level
  }
}
```

</details>

---

## Exercise 4 — Chaos Experiment Design

**Goal:** Design (but don't run) a chaos experiment that validates ScaleForge's graceful degradation from Module 10.3.

**Deliverable:** Fill out the experiment template:

```markdown
# Chaos Experiment Design

**Hypothesis:** [Fill in]

**Failure to inject:** [e.g. stop Redis container]

**Blast radius:** [production / staging / local dev only?]

**Duration:** [how long to keep the failure active]

**Steady-state metrics to watch:**
1. [metric name] — alert if [condition]
2. [metric name] — alert if [condition]

**Abort condition:** [what would cause you to immediately stop the experiment]

**Expected behavior during experiment:**
- Redirect tier gauge: [expected value]
- Redirect p99: [expected range]
- HTTP error rate: [expected range]

**Expected behavior after recovery:**
- Redirect tier gauge: [back to?]
- Redirect p99: [back to?]
- Time to full recovery: [estimated]

**What this experiment proves:** [one sentence]
```

<details>
<summary>Show a completed experiment design</summary>

```markdown
# Chaos Experiment Design

**Hypothesis:** If the primary Postgres database becomes unavailable,
ScaleForge redirects fall back to the read replica within 3 seconds,
and redirect p99 stays below 25ms.

**Failure to inject:** Stop the primary Postgres container
  (`docker compose stop postgres-primary`)

**Blast radius:** Local development environment only.
  Do not run on staging without prior approval.

**Duration:** 60 seconds

**Steady-state metrics to watch:**
1. scaleforge_redirect_tier — abort if it reaches 3 (in-memory) within first 10s
2. http error rate — abort if rate > 5% at any point
3. Redirect p99 — warn (don't abort) if > 25ms

**Abort condition:**
  HTTP error rate > 5% for more than 30 consecutive seconds.
  (Run `assert-steady-state.ts` script; it will throw on this condition.)

**Expected behavior during experiment:**
- Redirect tier gauge: 0 → 1 (DB fallback) within 3 seconds
- Redirect p99: increases from ~1ms to ~15ms (DB vs. cache latency)
- HTTP error rate: < 0.5% (a few in-flight queries may fail on disconnect)
- ScaleForgeRunningDegraded alert: fires within 1 minute of tier change

**Expected behavior after recovery:**
- Redirect tier gauge: returns to 0 within 2 minutes (Redis repopulated)
- Redirect p99: returns to ~1ms
- Time to full recovery: ~2 minutes (cache repopulation period)

**What this experiment proves:**
Primary DB failure gracefully degrades to read-replica serving,
with no user-visible errors and p99 remaining within 2× of normal.
```

</details>
