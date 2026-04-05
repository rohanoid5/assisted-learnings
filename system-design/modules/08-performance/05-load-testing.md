# 8.5 — Load Testing with Autocannon

## Concept

Load testing is the practice of synthetically generating traffic against your system to discover its limits before real users do. A load test answers: "At what request rate does p99 latency exceed my SLO? At what rate do errors start appearing?" Without this data, every production capacity decision is a guess.

---

## Deep Dive

### Load Test Types

```
  ┌─────────────────────────────────────────────────────────────────┐
  │  Load test types (choose based on what you're measuring)        │
  │                                                                 │
  │  Smoke test         Low traffic for short duration              │
  │  (-c 5 -d 10)       → "Does it start and respond?"              │
  │                                                                 │
  │  Load test          Sustained expected load                     │
  │  (-c 200 -d 300)    → "Can it handle normal weekday traffic?"   │
  │                                                                 │
  │  Stress test        Ramp up beyond expected load                │
  │  (-c 2000 -d 60)    → "Where does it break? What breaks first?" │
  │                                                                 │
  │  Soak test          Sustained load for hours                    │
  │  (-c 100 -d 3600)   → "Does memory leak? Do connections leak?"  │
  │                                                                 │
  │  Spike test         Sudden burst, then return to normal         │
  │  (scripted)         → "Can the pool recover from stampede?"     │
  └─────────────────────────────────────────────────────────────────┘
```

### Understanding Autocannon Output

```
  Running 30s test @ http://localhost:3001/abc123
  200 connections

  ┌─────────┬──────┬──────┬───────┬──────┬─────────┬─────────┬───────┐
  │ Stat    │ 2.5% │ 50%  │ 97.5% │ 99%  │ Avg     │ Stdev   │ Max   │
  ├─────────┼──────┼──────┼───────┼──────┼─────────┼─────────┼───────┤
  │ Latency │ 2 ms │ 4 ms │ 12 ms │ 18 ms│ 4.3 ms  │ 3.1 ms  │ 340 ms│
  └─────────┴──────┴──────┴───────┴──────┴─────────┴─────────┴───────┘

  ┌───────────┬─────────┬─────────┬─────────┬────────┬──────────┐
  │ Stat      │ 1%      │ 2.5%    │ 50%     │ 97.5%  │ Avg      │
  ├───────────┼─────────┼─────────┼─────────┼────────┼──────────┤
  │ Req/Sec   │ 38,142  │ 40,123  │ 42,098  │ 43,211 │ 41,801   │
  └───────────┴─────────┴─────────┴─────────┴────────┴──────────┘

  41,801 requests in 30.04s, 7.28 MB read
  
  ─── Reading this ───────────────────────────────────────────────

  p99 latency = 18ms ✓ (under 50ms SLO)
  Max latency = 340ms ← that 1 request is the outlier — why?
                         (could be GC pause, cold cache miss, DNS lookup)
  
  Throughput = 41,801 req/s → excellent for a single Node.js process
  
  If you see:
    p99 starts increasing as concurrency increases → you've hit a 
    resource limit (pool exhaustion, Redis bandwidth, CPU)
    
    errors > 0 in output → the system returned 4xx/5xx
    
    "Req/Sec 1%" is very different from "Req/Sec 50%" → 
    throughput is unstable (likely GC pauses or pool waits)
```

### Finding the Knee of the Curve

```
  "Knee": the concurrency level where latency begins to spike
  nonlinearly — this is your practical capacity limit.

  Run at increasing concurrency levels and record p99:

  Concurrency  p99 latency  Throughput    Notes
  ───────────  ───────────  ────────────  ───────────────────────
  10           1ms          8,000 req/s   Comfortable
  50           3ms          35,000 req/s  Good
  100          8ms          39,000 req/s  Getting warm
  200          18ms         41,000 req/s  Near limit
  500          95ms         38,000 req/s  ← KNEE: latency spikes,
                                           throughput drops
  1000         800ms        20,000 req/s  Degraded
  2000         timeout      4,000 req/s   Broken

  Conclusion: plan your pool size and horizontal scale
  to keep sustained traffic well below 500 concurrent connections.
```

---

## Code Examples

### Basic Autocannon Load Test Script

```typescript
// scripts/load-test.ts
import autocannon from 'autocannon';

async function runLoadTest() {
  const result = await autocannon({
    url: 'http://localhost:3001/abc123',
    connections: 200,          // concurrent connections
    duration: 30,              // seconds
    pipelining: 1,             // HTTP/1.1 pipelining factor
    timeout: 10,               // request timeout in seconds
    headers: {
      'X-Correlation-Id': 'load-test',
    },
  });

  console.log('\n─── ScaleForge Redirect Load Test ───');
  console.log(`Requests: ${result.requests.total.toLocaleString()}`);
  console.log(`Throughput: ${result.requests.average.toFixed(0)} req/s`);
  console.log(`Latency p50: ${result.latency.p50}ms`);
  console.log(`Latency p99: ${result.latency.p99}ms`);
  console.log(`Latency max: ${result.latency.max}ms`);
  console.log(`Errors: ${result.errors + result.timeouts}`);

  // Fail if SLO is not met
  if (result.latency.p99 > 50) {
    console.error(`❌ p99 latency ${result.latency.p99}ms exceeds 50ms SLO`);
    process.exit(1);
  }
  if (result.errors + result.timeouts > 0) {
    console.error(`❌ ${result.errors + result.timeouts} request errors`);
    process.exit(1);
  }

  console.log('✓ All SLOs met');
}

runLoadTest().catch(console.error);
```

### Ramp Test (Find the Knee)

```typescript
// scripts/ramp-test.ts
import autocannon from 'autocannon';

const CONCURRENCY_LEVELS = [10, 50, 100, 200, 500, 1000];
const TARGET_URL = 'http://localhost:3001/abc123';

async function runRampTest() {
  console.log('\nConcurrency | p50 (ms) | p99 (ms) | Max (ms) | req/s  | Errors');
  console.log('─'.repeat(70));

  for (const connections of CONCURRENCY_LEVELS) {
    const result = await autocannon({
      url: TARGET_URL,
      connections,
      duration: 15,
      timeout: 5,
    });

    const pass = result.latency.p99 <= 50 && result.errors === 0;
    const status = pass ? '✓' : '✗';

    console.log(
      `${status} ${String(connections).padEnd(12)} | ` +
      `${String(result.latency.p50).padEnd(8)} | ` +
      `${String(result.latency.p99).padEnd(8)} | ` +
      `${String(result.latency.max).padEnd(8)} | ` +
      `${String(result.requests.average.toFixed(0)).padEnd(6)} | ` +
      `${result.errors + result.timeouts}`
    );
  }
}

runRampTest().catch(console.error);
```

### POST Request Load Test (URL Creation)

```typescript
// scripts/load-test-write.ts
import autocannon from 'autocannon';

async function runWriteLoadTest() {
  const result = await autocannon({
    url: 'http://localhost:3001/api/v1/urls',
    connections: 50,
    duration: 20,
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      'X-User-Id': 'load-test-user',
    },
    body: JSON.stringify({ url: 'https://example.com', notifyOnCreate: false }),
  });

  console.log(`Write throughput: ${result.requests.average.toFixed(0)} req/s`);
  console.log(`Write p99 latency: ${result.latency.p99}ms`);
  console.log(`Errors: ${result.errors}`);
  // Expect writes to be slower than reads (DB write + Redis SET)
}

runWriteLoadTest();
```

---

## Try It Yourself

**Exercise:** Find ScaleForge's throughput ceiling and identify the bottleneck.

```bash
# Step 1: Create a test URL (make sure it exists in the DB)
export SHORT_CODE=$(curl -s -X POST http://localhost:3001/api/v1/urls \
  -H "Content-Type: application/json" \
  -d '{"url":"https://example.com"}' | jq -r '.shortCode')

echo "Testing with short code: $SHORT_CODE"

# Step 2: Run the ramp test
npx ts-node scripts/ramp-test.ts

# Step 3: While the ramp test runs, watch these in separate terminals:
# Terminal A: Prometheus metrics
watch -n 2 'curl -s http://localhost:3001/metrics | grep -E "pool|cache"'

# Terminal B: System metrics
htop   # or: watch -n 1 'ps aux | grep node'

# Step 4: Identify the bottleneck by answering:
# - When does pg_pool_waiting_requests go above 0?
# - When does the Redis hit rate drop?
# - When does Node.js CPU usage saturate?
# - Which of these happens FIRST as concurrency increases?
#
# That's your primary bottleneck. Fix that one first.
```

<details>
<summary>Show what typically limits Node.js URL shorteners</summary>

```
Typical bottleneck order for a URL shortener under load:

1. Redis connection limits — ioredis default pool is a single connection.
   At ~10,000 req/s, Redis commands queue up. 
   Fix: ioredis cluster or multiple connections.

2. Node.js event loop — if any middleware does synchronous work
   (JSON.parse of large body, regex, crypto), it blocks everything.
   Fix: find with 0x flame graph, eliminate synchronous hot paths.

3. DB pool exhaustion — when cache miss rate is high and every
   request needs a Postgres query.
   Fix: increase pool size (up to Postgres max_connections limit),
        add read replica, improve cache warm-up.

4. Express router overhead — at 100k+ req/s, Express route matching
   becomes measurable. Fix: use fastify (3-5x less overhead).

5. TCP/OS limits — at 500k+ req/s, you hit kernel connection
   limits. Fix: tune net.core.somaxconn, use SO_REUSEDPORT.
```

</details>

---

## Capstone Connection

The ramp test is your pre-production SLO validation gate. Before deploying a new version of ScaleForge, run `npm run load-test` as part of the CI pipeline. If p99 latency for redirects regresses beyond 50ms at 200 concurrent connections, the deployment is blocked. This catches database query regressions (a developer dropped a critical index), Redis connection leaks (connections accumulate over time), and synchronous middleware additions (someone added a synchronous `crypto.createHash` to every request) before they affect real users.
