# 1.2 — How to Approach System Design

## Concept

System design problems are open-ended by nature — there is no algorithm to run, no test suite to pass. The skill is knowing how to **structure ambiguity into a concrete design** while making each tradeoff explicit. A repeatable framework prevents you from either over-designing upfront or missing critical constraints.

---

## Deep Dive

### The Four-Step Framework

```
┌─────────────────────────────────────────────────────────────────┐
│  Step 1: CLARIFY (5–10 min)                                     │
│  ─────────────────────────────────────────────────────────────  │
│  Ask before you draw.                                           │
│  • Who are the users? (consumers, businesses, both?)            │
│  • What are the core features? (MVP only)                       │
│  • What volumes? (users, QPS, data size)                        │
│  • What constraints? (latency SLA, budget, existing systems)    │
│  • What does "done" look like?                                  │
├─────────────────────────────────────────────────────────────────┤
│  Step 2: ESTIMATE (3–5 min)                                     │
│  ─────────────────────────────────────────────────────────────  │
│  Back-of-envelope math to size the system.                      │
│  • QPS (queries per second) — avg and peak                      │
│  • Storage (how much data, how fast it grows)                   │
│  • Bandwidth (inbound and outbound bytes/sec)                   │
│  • Derived: do we need caching? sharding? CDN?                  │
├─────────────────────────────────────────────────────────────────┤
│  Step 3: HIGH-LEVEL DESIGN (10–15 min)                          │
│  ─────────────────────────────────────────────────────────────  │
│  Draw the boxes. Be non-committal on technologies at first.     │
│  • Core components (API, DB, cache, queue, workers)             │
│  • Data model (tables / documents / keys)                       │
│  • Key flows: write path and read path                          │
├─────────────────────────────────────────────────────────────────┤
│  Step 4: DEEP DIVE (15–20 min)                                  │
│  ─────────────────────────────────────────────────────────────  │
│  Zoom in on the hardest problem.                                │
│  • What breaks at scale?                                        │
│  • Single points of failure?                                    │
│  • How does the bottleneck component scale?                     │
│  • Failure modes and recovery                                   │
└─────────────────────────────────────────────────────────────────┘
```

### Step 2 — Capacity Estimation in Practice

Estimation is not about precision. It's about finding **order-of-magnitude constraints** that drive architecture decisions.

**Useful numbers to internalize:**

| Unit | Value |
|------|-------|
| Seconds in a day | ~86,400 (≈ 10⁵) |
| Seconds in a month | ~2.5M (≈ 2.5 × 10⁶) |
| 1 KB | 10³ bytes |
| 1 MB | 10⁶ bytes |
| 1 GB | 10⁹ bytes |
| 1 TB | 10¹² bytes |
| SSD read latency | ~100 µs |
| Network round-trip (same DC) | ~0.5 ms |
| PostgreSQL query (index hit) | ~1–5 ms |
| Redis GET | ~0.1–1 ms |

**ScaleForge Estimation Example:**

```
Assumptions:
  - 100M redirects / day
  - Read:Write ratio = 100:1  →  1M URL creations / day
  - Average URL metadata = 500 bytes
  - Average click record = 200 bytes

QPS (Reads):
  100M / 86,400 ≈ 1,157 req/s average
  Peak (10×)      ≈ 11,570 req/s  ← need horizontal scaling + caching

QPS (Writes):
  1M / 86,400 ≈ 12 URL creations/s  ← single PostgreSQL node is fine

Storage:
  URLs:   1M/day × 365 days × 500 bytes ≈ 182 GB / year
  Clicks: 100M/day × 365 days × 200 bytes ≈ 7.3 TB / year
  → Clicks table needs partitioning or archival strategy

Bandwidth:
  Outbound: 11,570 req/s × 500 bytes (avg redirect response) ≈ 5.5 MB/s  ← fine
  Inbound:  12 writes/s × 500 bytes ≈ trivial

Derived decisions:
  ✅ Redis cache  — 11k req/s at < 20ms needs sub-millisecond lookup
  ✅ Async clicks — 100M writes/day can't block the redirect response
  ✅ Partitioned clicks table — 7.3 TB/year needs archival
  ? CDN           — depends on whether HTML/JS assets are served
```

### What to Draw in High-Level Design

A good high-level diagram shows components, not implementation:

```
  Client
    │
    ▼
  DNS / CDN
    │
    ▼
  Load Balancer (Nginx)
    │
    ├──▶ API Servers (×N stateless replicas)
    │       │               │
    │    Redis Cache     PostgreSQL
    │    (URL lookup)    (source of truth)
    │
    └──▶ Analytics Queue (BullMQ)
              │
              ▼
         Worker Pool
              │
              ▼
         Clicks Table (PostgreSQL)
```

Use prose too: "The API server checks Redis first; on a miss it reads PostgreSQL and populates the cache. Click tracking is pushed to a BullMQ queue and processed asynchronously to avoid adding latency to the redirect."

---

## Code Examples

### Back-of-Envelope Math as Tests

Good engineers write their capacity assumptions as executable code so they can be revisited:

```typescript
// docs/capacity-estimates.ts — runnable capacity document

const SECONDS_PER_DAY = 86_400;

const assumptions = {
  dailyRedirects: 100_000_000,          // 100M/day
  dailyUrlCreations: 1_000_000,         // 1M/day (100:1 read:write ratio)
  urlSizeBytes: 500,
  clickSizeBytes: 200,
  peakMultiplier: 10,                   // peak = 10× average
};

const qps = {
  readAvg: assumptions.dailyRedirects / SECONDS_PER_DAY,
  readPeak: (assumptions.dailyRedirects * assumptions.peakMultiplier) / SECONDS_PER_DAY,
  writeAvg: assumptions.dailyUrlCreations / SECONDS_PER_DAY,
};

const storageBytesPerYear = {
  urls: assumptions.dailyUrlCreations * 365 * assumptions.urlSizeBytes,
  clicks: assumptions.dailyRedirects * 365 * assumptions.clickSizeBytes,
};

console.log('=== ScaleForge Capacity Estimates ===');
console.log(`Read QPS (avg):  ${qps.readAvg.toFixed(0)} req/s`);
console.log(`Read QPS (peak): ${qps.readPeak.toFixed(0)} req/s`);
console.log(`Write QPS (avg): ${qps.writeAvg.toFixed(2)} req/s`);
console.log(`URL storage:     ${(storageBytesPerYear.urls / 1e9).toFixed(1)} GB/year`);
console.log(`Click storage:   ${(storageBytesPerYear.clicks / 1e12).toFixed(1)} TB/year`);

// Derived decisions
console.log('\n=== Architectural Decisions ===');
console.log(`Need caching?    ${qps.readPeak > 1000 ? 'YES — ' + qps.readPeak.toFixed(0) + ' peak req/s exceeds single-DB capacity' : 'No'}`);
console.log(`Need async writes? YES — 100M click rows/day blocks redirect if synchronous`);
console.log(`Need partitioning? ${storageBytesPerYear.clicks > 1e12 ? 'YES — clicks table grows ' + (storageBytesPerYear.clicks / 1e12).toFixed(1) + ' TB/year' : 'No'}`);

// Output:
// === ScaleForge Capacity Estimates ===
// Read QPS (avg):  1157 req/s
// Read QPS (peak): 11574 req/s
// Write QPS (avg): 11.57 req/s
// URL storage:     182.5 GB/year
// Click storage:   7.3 TB/year
//
// === Architectural Decisions ===
// Need caching?    YES — 11574 peak req/s exceeds single-DB capacity
// Need async writes? YES — 100M click rows/day blocks redirect if synchronous
// Need partitioning? YES — clicks table grows 7.3 TB/year
```

---

## Try It Yourself

**Exercise:** Walk through the estimation framework for a different system.

Estimate capacity for a **Twitter-scale timeline service**:

```typescript
// Assumptions to calculate:
const twitterAssumptions = {
  dailyActiveUsers: 200_000_000,
  tweetsPerUserPerDay: 0.1,           // most users read, few tweet
  timelineReadsPerUserPerDay: 50,
  avgTweetSizeBytes: 300,
  // TODO: calculate QPS, storage/year, bandwidth
};

// Then answer:
// 1. What is the peak read QPS?
// 2. How much storage is needed per year for tweets?
// 3. Does this need caching? Why?
```

<details>
<summary>Show solution</summary>

```typescript
const SECONDS_PER_DAY = 86_400;
const DAU = 200_000_000;

// QPS
const writesPerDay = DAU * 0.1;             // 20M tweets/day
const readsPerDay = DAU * 50;               // 10B timeline reads/day
const writeQPS = writesPerDay / SECONDS_PER_DAY;   // ~231 req/s
const readQPS = readsPerDay / SECONDS_PER_DAY;     // ~115,740 req/s
const readQPSpeak = readQPS * 10;                  // ~1.16M req/s peak

// Storage
const tweetStoragePerYear = writesPerDay * 365 * 300;  // ~2.2 TB/year
const mediaStoragePerYear = writesPerDay * 0.3 * 365 * 1_000_000; // ~2.2 PB (images!)

// Decisions:
// Caching: YES — 115k–1.16M read QPS is impossible on a single DB
// CDN: CRITICAL — media is most of the bandwidth
// Fan-out strategy needed: push vs pull for timeline generation
```

Key insight: the read:write ratio (500:1) means every write must trigger many reads. This is why Twitter uses a **fan-out-on-write** model (pre-compute timelines) + Redis cache.

</details>

---

## Capstone Connection

The `capstone/scaleforge/docs/architecture.md` file is the deliverable for Module 1. Structure it with the four-step framework:

1. **Clarify**: functional and non-functional requirements
2. **Estimate**: run the capacity estimation script to derive actual numbers
3. **High-level design**: draw the component diagram in ASCII art
4. **Key risks**: note the hardest parts and which modules address them

Returning to this document in later modules — and updating it as decisions change — is a sign of mature system design practice.
