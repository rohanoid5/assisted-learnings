# 1.1 — What Is System Design?

## Concept

System design is the process of defining the **architecture, components, and data flows** of a system that meets a set of requirements. Unlike algorithm design (which asks "how do I solve this problem efficiently?"), system design asks "how do I build something that thousands of users can rely on?" — addressing reliability, scalability, maintainability, and cost simultaneously.

---

## Deep Dive

### The Two Layers of Requirements

Every system design starts with requirements gathering. A common mistake is jumping straight to drawing boxes. Instead, split requirements into two categories:

```
Requirements
├── Functional           "What does the system do?"
│   ├── User can shorten a URL
│   ├── User can visit a short URL and get redirected
│   └── User can see click analytics for their URLs
│
└── Non-Functional       "How well does the system do it?"
    ├── Availability    → 99.99% uptime (< 52 min downtime/year)
    ├── Latency         → Redirect responds in < 20ms at p99
    ├── Throughput      → Handle 10,000 redirects/second
    ├── Durability      → No URL data loss after creation
    ├── Consistency     → Analytics accurate within 5 seconds
    └── Scalability     → Architecture holds at 100× current load
```

The non-functional requirements (NFRs) are where system design actually lives. Functional requirements tell you what tables to create. NFRs tell you whether you need Redis, Kafka, or a CDN.

### What System Design Is NOT

| Misconception | Reality |
|---------------|---------|
| "Pick the right technology" | Technology follows requirements, not the other way around |
| "One correct answer" | All real designs involve tradeoffs — the goal is justified decisions |
| "Just draw a diagram" | A diagram without reasoning is decoration, not design |
| "Only relevant for big systems" | Systems thinking applies at any scale |

### The Core Tradeoffs You'll Revisit

Systems constantly trade off between competing properties. Learn to name these explicitly:

```
        Consistency ◀────────────────▶ Availability
                              │
                     Partition Tolerance
                    (you must have this in
                     any distributed system)
```

- **Consistency vs. Availability** — Can every node always return an answer? Is it guaranteed to be the latest data?
- **Latency vs. Throughput** — Can you serve more requests, or can you serve them faster? (Often both, but each has a ceiling.)
- **Read performance vs. Write performance** — Indexes speed up reads but slow down writes. Caches improve reads but add write complexity.
- **Simplicity vs. Resilience** — A simpler system is easier to reason about, but may have single points of failure.

### A Structured Approach to Any Design Problem

When asked to design a system — in an interview, in an RFC, or for a new feature — use this four-step structure (detailed in Topic 1.2):

```
1. Clarify requirements         ← 5–10 min
   - Functional: what it does
   - Non-functional: how it must perform

2. Estimate capacity            ← 3–5 min
   - Users, QPS, storage, bandwidth
   - Back-of-envelope math

3. High-level design            ← 10–15 min
   - Major components (API servers, DB, cache, queue)
   - Data model (entities, relationships)
   - Core flows (write path, read path)

4. Deep dive on critical parts  ← 15–20 min
   - The part that makes this design hard
   - Bottlenecks, single points of failure, scaling strategy
```

---

## Code Examples

### Expressing NFRs as Code (TypeScript Types)

Good system design translates NFRs into explicit contracts in code. Here's how ScaleForge captures its requirements:

```typescript
// src/config.ts — NFRs expressed as typed configuration

import { z } from 'zod';

const ConfigSchema = z.object({
  // Availability target: 99.99% = max 52 min downtime/year
  // Enforced externally via health checks + load balancer

  // Performance: redirect must complete in < 20ms (p99)
  // Enforced by: Redis cache (< 1ms lookup) + connection pooling
  CACHE_TTL_SECONDS: z.coerce.number().default(300),      // 5-min URL cache

  // Throughput: 10,000 redirects/second
  // Enforced by: horizontal scaling + rate limiting per user tier
  RATE_LIMIT_FREE: z.coerce.number().default(60),         // req/min FREE tier
  RATE_LIMIT_PRO: z.coerce.number().default(600),         // req/min PRO tier

  // Durability: no URL data loss
  // Enforced by: PostgreSQL WAL + read replicas
  DATABASE_URL: z.string().url(),

  // Consistency: analytics within 5 seconds (eventual)
  // Enforced by: async BullMQ queue — clicks tracked after redirect
  ANALYTICS_QUEUE_NAME: z.string().default('click-tracking'),
});

export const config = ConfigSchema.parse(process.env);
export type Config = z.infer<typeof ConfigSchema>;
```

Notice: every comment maps back to a specific NFR. This is the discipline of system design applied to code.

### The Redirect Path — Functional Requirement Made Explicit

```typescript
// src/routes/url.routes.ts — the core functional requirement

import { Router } from 'express';
import { UrlService } from '../services/url.service.js';
import { ClickQueue } from '../workers/click-tracker.worker.js';

const router = Router();

// Functional: "User can visit a short URL and get redirected"
// NFR: "Redirect < 20ms at p99" → read from Redis (< 1ms), not PostgreSQL (3-5ms)
router.get('/:code', async (req, res) => {
  const { code } = req.params;

  const longUrl = await UrlService.lookup(code);   // Redis-first, Postgres fallback
  if (!longUrl) {
    return res.status(404).json({ error: 'URL not found' });
  }

  // NFR: "Analytics within 5 seconds" → async, does NOT block the redirect
  ClickQueue.add('track', {
    code,
    ip: req.ip,
    userAgent: req.headers['user-agent'],
    timestamp: Date.now(),
  });

  // 301 = permanent (cached by browser, CDN) — fewer repeat hits
  // 302 = temporary (every visit hits server)  — more analytics data
  // Design decision: use 302 for analytics accuracy
  res.redirect(302, longUrl);
});

export { router as urlRouter };
```

---

## Try It Yourself

**Exercise:** Define requirements for ScaleForge.

Open `capstone/scaleforge/docs/architecture.md` and fill in:

```markdown
## Functional Requirements
1. ...
2. ...

## Non-Functional Requirements
| Property      | Target     | Rationale |
|---------------|------------|-----------|
| Availability  | 99.99%     | ...       |
| Redirect p99  | < 20ms     | ...       |
| Write latency | < 100ms    | ...       |
| Throughput    | 10k req/s  | ...       |
```

<details>
<summary>Show example solution</summary>

```markdown
## Functional Requirements
1. Authenticated users can create short URLs (with optional expiry)
2. Anyone can visit a short URL and be redirected to the original
3. URL owners can view click analytics (total clicks, by country, by device)
4. URLs expire after a configurable TTL (default: 90 days)

## Non-Functional Requirements
| Property        | Target      | Rationale |
|-----------------|-------------|-----------|
| Availability    | 99.99%      | < 52 min/year downtime; revenue-impacting during outages |
| Redirect p99    | < 20ms      | Core UX — users notice redirects > 100ms |
| Write latency   | < 500ms     | URL creation is not on the critical path |
| Read throughput | 10k req/s   | ~100M daily redirects ÷ 86,400s ≈ ~1,157 req/s avg; 10× for peak |
| Durability      | No data loss | Created URLs must survive any single node failure |
| Analytics lag   | ≤ 5 seconds | Near-real-time is enough; exact real-time adds complexity |
```

These become the success criteria for every architectural decision.

</details>

---

## Capstone Connection

ScaleForge begins here: before writing a single line of server code, create `capstone/scaleforge/docs/architecture.md`. The content you write in this file drives every subsequent technology choice — whether to use Redis, why BullMQ instead of a synchronous DB write for click tracking, and why the redirect uses HTTP 302 rather than 301.

Every module milestone will reference back to the requirements you define here. If a design decision seems arbitrary later, it means the relevant NFR wasn't written clearly enough.
