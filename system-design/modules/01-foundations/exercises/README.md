# Module 1 — Exercises

## Overview

These exercises solidify the foundational concepts from Module 1 and produce the first real deliverable of ScaleForge: a structured architecture document. Complete all exercises before moving to Module 2.

---

## Exercise 1 — Write the ScaleForge Architecture Document

**Goal:** Apply the four-step design framework to ScaleForge and produce a structured requirements + architecture document.

### Steps

1. Create the file `capstone/scaleforge/docs/architecture.md`

2. Fill in the following sections:

```markdown
# ScaleForge — Architecture Document

## 1. Functional Requirements
<!-- List 4–6 things the system must DO -->

## 2. Non-Functional Requirements
| Property      | Target | Rationale |
|---------------|--------|-----------|
| Availability  |        |           |
| Redirect p99  |        |           |
| Write latency |        |           |
| Throughput    |        |           |

## 3. Capacity Estimation
<!-- Run and paste the output of: node --loader ts-node/esm docs/capacity-estimates.ts -->

## 4. High-Level Architecture
<!-- ASCII art or written description of components -->

## 5. Data Model
<!-- Tables/entities and their key fields -->

## 6. Key Design Decisions
<!-- For each decision, state: what, why, what was considered -->
```

3. Run the capacity estimation to get real numbers:

```bash
cd capstone/scaleforge
node --loader ts-node/esm docs/capacity-estimates.ts
```

**Verification:** Your document should have all 6 sections filled with content that justifies  architectural choices made in later modules.

<details>
<summary>Show example architecture document</summary>

```markdown
# ScaleForge — Architecture Document

## 1. Functional Requirements
1. Authenticated users can create short URLs with optional expiry
2. Anyone can visit a short URL and be redirected to the destination
3. URL owners can view click analytics (total clicks, by country, by device)
4. Short URLs expire after a configurable TTL (default 90 days)
5. Users can delete their own short URLs

## 2. Non-Functional Requirements
| Property         | Target      | Rationale |
|------------------|-------------|-----------|
| Availability     | 99.99%      | Revenue at risk during outage; < 53 min/year downtime |
| Redirect p99     | < 20ms      | UX — users notice latency on direct links |
| URL creation p99 | < 500ms     | Not on critical path |
| Read throughput  | 10k req/s   | 100M/day ÷ 86,400 ≈ 1,157 avg; 10× peak headroom |
| Write throughput | 100 req/s   | 1M URLs/day, with burst headroom |
| Data durability  | No URL loss | Replication: PostgreSQL primary + standby |
| Analytics lag    | ≤ 5 seconds | Real-time isn't needed; eventual is fine |

## 3. Capacity Estimation
Read QPS (avg):  1157 req/s
Read QPS (peak): 11574 req/s
Write QPS (avg): 11.57 req/s
URL storage:     182.5 GB/year
Click storage:   7.3 TB/year

→ Need Redis cache (peak 11k req/s impossible on single Postgres)
→ Need async click writes (100M/day synchronous = blocks redirect)
→ Need click table partitioning (7.3 TB/year)

## 4. High-Level Architecture
  Client → DNS → Nginx LB → App Servers (×3)
                                  ├── Redis Cache  (URL lookup)
                                  └── PostgreSQL   (source of truth)
                                  └── BullMQ Queue → Worker → Clicks DB

## 5. Data Model
- User(id, email, passwordHash, tier, createdAt)
- ShortURL(id, code, longUrl, userId, expiresAt, clickCount, createdAt)
- Click(id, shortUrlId, ip, userAgent, country, device, timestamp)
- Report(id, shortUrlId, period, totalClicks, uniqueVisitors, createdAt)

## 6. Key Design Decisions
1. Redis for URL lookup (not direct Postgres read)
   → Why: 11k peak req/s, p99 < 20ms — Redis at ~0.3ms vs Postgres at ~4ms
   → Rejected: Postgres read replicas alone (still ~4ms + connection limits)

2. Async click tracking via BullMQ
   → Why: 100M writes/day cannot block the 20ms redirect SLA
   → Rejected: Synchronous click write (adds 4ms to critical path)

3. HTTP 302 redirect (not 301)
   → Why: 301 is cached by browsers forever — we'd lose analytics
   → Rejected: 301 (performance win, but kills analytics)
```

</details>

---

## Exercise 2 — Availability Calculator

**Goal:** Calculate ScaleForge's effective availability and find the weakest link.

Create `experiments/availability.ts` and calculate:

```typescript
// Implement these two functions (from Topic 1.5):
function availabilityInSequence(...availabilities: number[]): number {
  // TODO
}

function availabilityInParallel(...availabilities: number[]): number {
  // TODO
}

function toDowntimePerYear(availability: number): string {
  const minutes = (1 - availability) * 365 * 24 * 60;
  return minutes < 1 ? `${(minutes * 60).toFixed(1)}s/year` : `${minutes.toFixed(1)} min/year`;
}

// Given these individual availabilities:
const NGINX = 0.9999;
const APP_REPLICA = 0.999;   // one replica
const REDIS_NODE = 0.9999;   // one Redis node
const POSTGRES_NODE = 0.9995; // one Postgres node

// Calculate and print:
// 1. Availability with 3 app replicas
// 2. Availability with Redis primary + replica
// 3. Availability with Postgres primary + standby
// 4. End-to-end availability of the critical path
// 5. Which component is still the weakest link?
```

Run with:
```bash
node --loader ts-node/esm experiments/availability.ts
```

<details>
<summary>Show expected output</summary>

```
App layer (3 replicas):   99.9999% (0.5 min/year)
Redis (primary+replica):  99.999998% (0.01 min/year)
Postgres (primary+standby): 99.999975% (0.01 min/year)

End-to-end: 99.9989% (5.7 min/year)

Weakest link: Nginx at 99.99% (52 min/year)
→ To reach 99.999%, use managed LB (AWS ALB) or add second Nginx
```

</details>

---

## Exercise 3 — Classify Consistency Requirements

**Goal:** Map each ScaleForge operation to a consistency pattern. No code needed — this is a design thinking exercise.

For each operation below, answer:
1. What consistency level is required? (weak / eventual / strong)
2. Which data store enforces it? (Redis / PostgreSQL / BullMQ queue)
3. What failure mode occurs if you pick a weaker level than needed?

| Operation | Consistency Level | Store | Failure Mode if Wrong |
|-----------|------------------|-------|----------------------|
| Redirect visitor to long URL | ? | ? | ? |
| Create short URL code | ? | ? | ? |
| Record a click | ? | ? | ? |
| Generate analytics report | ? | ? | ? |
| Enforce rate limit | ? | ? | ? |
| Authenticate user via JWT | ? | ? | ? |

<details>
<summary>Show answers</summary>

| Operation | Level | Store | Failure if Weaker |
|-----------|-------|-------|-------------------|
| Redirect visitor | Eventual | Redis (cache) | 5-min stale redirect — acceptable |
| Create URL code | Strong | PostgreSQL | **Two users get same code** — fatal |
| Record a click | Eventual | BullMQ Queue | Lose a few clicks — acceptable |
| Analytics report | Eventual | PostgreSQL read replica | Report is 5s stale — acceptable |
| Rate limit | Strong | Redis INCR | Users exceed quota across replicas |
| Authenticate JWT | Stateless | No store (verify sig) | n/a — no distributed state |

</details>

---

## Exercise 4 — CAP Classification Game

**Goal:** Given a network partition, decide what each system should do.

For each scenario, you are the on-call engineer. A network partition has occurred between nodes. What is the correct behavior?

**Scenario A:** A Redis primary can't reach its replica. A user is trying to GET a URL.
- Serve from primary (consistent, but primary could be overloaded)?
- Return an error (unavailable)?
- Serve from replica with stale data (available, but possibly stale)?

**Scenario B:** PostgreSQL primary can't sync to standby. A user is creating a new URL.
- Accept the write (available, but standby is now behind)?
- Reject the write until sync resumes (consistent, but unavailable)?

**Scenario C:** BullMQ queue (Redis) is partitioned. A visitor is redirecting.
- Block redirect until queue is available?
- Skip click tracking and complete redirect immediately?

<details>
<summary>Show recommended decisions and rationale</summary>

**A:** Serve from primary. Redis is AP by default — serving the primary maintains availability. The replica is for read scaling, not the primary fallback.

**B:** Accept the write (modern PostgreSQL asynchronous default). Strong consistency mode (`synchronous_commit = remote_apply`) would block, but for URL creation the availability loss isn't worth it. PostgreSQL replication lag is typically < 1ms — risk of losing the write on primary failure is very low.

**C:** Skip click tracking. The redirect is the core product. A lost click event is acceptable. This is exactly why clicks are enqueued async — so a queue partition doesn't affect the redirect path (AP design).

</details>
