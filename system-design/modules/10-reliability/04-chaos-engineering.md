# 10.4 — Chaos Engineering

## Concept

Chaos engineering is the practice of intentionally injecting failures into a system to verify that it behaves as designed under real-world conditions. Rather than hoping your circuit breaker, fallback logic, and alerting work correctly — chaos engineering proves it, before a real incident does. The goal isn't destruction; it's confidence.

---

## Deep Dive

### The Chaos Engineering Mindset

```
  Traditional testing:
    "Verify the system does the right thing when inputs are correct."

  Chaos engineering:
    "Verify the system does the right thing when infrastructure fails."

  These are complementary. Unit/integration tests prove correctness.
  Chaos experiments prove resilience.
```

### The Chaos Experiment Lifecycle

```
  1. Define the steady state
     ─────────────────────────
     Identify a measurable output that indicates "normal":
       • redirect p99 < 10ms
       • error rate < 0.1%
       • BullMQ queue depth < 500 jobs

  2. Hypothesize
     ─────────────
     "If we kill one Redis node, the system will fall back to the DB
      and redirect p99 will remain < 25ms."

  3. Design the experiment
     ──────────────────────
     What failure to inject, how long, with what blast radius.
     Start with the smallest possible blast radius.
       Smallest: kill one process on one host, observe for 60s
       Larger: cut network between two services, observe for 5 min
       Largest: kill an entire availability zone (don't start here)

  4. Run the experiment
     ───────────────────
     Inject the failure. Watch the steady-state metrics in real time.
     Have a clear abort condition: if error rate > 5%, stop immediately.

  5. Observe and document
     ──────────────────────
     Did the system behave as hypothesized?
     If yes → confidence gained. Document this as a "confirmed resilience property."
     If no  → you found a real bug. Fix it before a real incident finds it first.
```

### What to Chaos Test (and in What Order)

```
  Level 1 — Process failures (safe to start here):
    • Kill a single app replica (requires load balancer failover)
    • Kill Redis (requires cache fallback — Module 10.3)
    • Kill a BullMQ worker (requires retry logic — Module 06)

  Level 2 — Network failures:
    • Inject latency on DB connection (requires timeout — Module 09.4)
    • Drop packets between ScaleForge and FlowForge (requires circuit breaker — Module 09.1)
    • Return 5xx responses from FlowForge (requires retry — Module 09.2)

  Level 3 — Resource exhaustion:
    • Fill the disk on the app server
    • Consume all DB connections (pool exhaustion — Module 09.3)
    • Saturate CPU

  Level 4 — Infrastructure failures (only in staging):
    • Kill a Postgres replica
    • Kill the primary database (fail over to replica)
    • Network partition between app and DB
```

### Tools

| Tool | What it tests | Scale |
|---|---|---|
| `kill -9` + shell scripts | Any process | Single host |
| `tc netem` (Traffic Control) | Network latency, packet loss | Single host |
| `toxiproxy` | Network faults at TCP level | Any host, controlled API |
| `chaos-monkey` approach | Random instance termination | Multi-host |
| Kubernetes `PodDisruptionBudget` | Controlled pod killing in K8s | K8s clusters |

---

## Code Examples

### Toxiproxy-Based Chaos Helper

```typescript
// scripts/chaos/toxiproxy-helper.ts
// toxiproxy intercepts TCP connections and can inject:
//   latency, bandwidth limits, packet loss, connection reset, slow close
// Run toxiproxy as a proxy in front of Redis and Postgres in your dev/staging docker-compose.

interface ToxiproxyToxic {
  name:       string;
  type:       'latency' | 'bandwidth' | 'slow_close' | 'timeout' | 'reset_peer';
  stream:     'upstream' | 'downstream';
  toxicity:   number;  // 0.0–1.0, fraction of requests affected
  attributes: Record<string, number>;
}

const TOXIPROXY_URL = process.env['TOXIPROXY_URL'] ?? 'http://localhost:8474';

async function addToxic(proxy: string, toxic: Omit<ToxiproxyToxic, 'name'> & { name?: string }) {
  const res = await fetch(`${TOXIPROXY_URL}/proxies/${proxy}/toxics`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ name: toxic.name ?? `chaos-${Date.now()}`, ...toxic }),
  });
  if (!res.ok) throw new Error(`Failed to add toxic: ${await res.text()}`);
  return res.json();
}

async function removeToxic(proxy: string, toxicName: string) {
  await fetch(`${TOXIPROXY_URL}/proxies/${proxy}/toxics/${toxicName}`, {
    method: 'DELETE',
  });
}

// Experiment: 500ms latency on Redis for 60 seconds
// Then verify redirect p99 stays < 50ms (cache miss falls back to DB)
export async function experimentRedisLatency() {
  const toxicName = 'redis-latency-500ms';
  console.log('[chaos] Injecting 500ms Redis latency...');

  await addToxic('redis', {
    name:       toxicName,
    type:       'latency',
    stream:     'downstream',
    toxicity:   1.0,   // 100% of requests
    attributes: { latency: 500, jitter: 50 },
  });

  console.log('[chaos] Waiting 60s — check Grafana: redirect p99 should stay < 50ms');
  await new Promise(r => setTimeout(r, 60_000));

  await removeToxic('redis', toxicName);
  console.log('[chaos] Toxic removed. Verifying recovery...');
}
```

### Simple Kill-and-Recover Script

```bash
#!/usr/bin/env bash
# scripts/chaos/kill-redis.sh
# Stops Redis, waits 30s, restarts it.
# Watch Grafana during the window: redirect tier gauge should go 0→1→0

set -euo pipefail

echo "[chaos] Stopping Redis..."
docker compose stop redis

echo "[chaos] Redis down for 30 seconds. Check Grafana now."
sleep 30

echo "[chaos] Restarting Redis..."
docker compose start redis

echo "[chaos] Waiting for Redis to become healthy..."
until docker compose exec redis redis-cli ping | grep -q PONG; do
  sleep 1
done

echo "[chaos] Redis recovered. Verify:"
echo "  1. redirect p99 returned to < 5ms"
echo "  2. scaleforge_redirect_tier gauge is back at 0"
echo "  3. No HTTP 5xx errors during the outage window"
```

### Validating Steady State with Assertions

```typescript
// scripts/chaos/assert-steady-state.ts
// Poll Prometheus every 5s and assert steady-state conditions.
// Abort the chaos experiment if conditions are violated.

const PROMETHEUS_URL = process.env['PROMETHEUS_URL'] ?? 'http://localhost:9090';

async function queryPromQL(query: string): Promise<number> {
  const url = `${PROMETHEUS_URL}/api/v1/query?` + new URLSearchParams({ query });
  const res  = await fetch(url, { signal: AbortSignal.timeout(5000) });
  const data = (await res.json()) as { data: { result: Array<{ value: [number, string] }> } };
  return parseFloat(data.data.result[0]?.value[1] ?? 'NaN');
}

async function assertSteadyState(): Promise<void> {
  const [errorRate, p99Latency] = await Promise.all([
    queryPromQL(`sum(rate(http_requests_total{status=~"5.."}[1m])) / sum(rate(http_requests_total[1m]))`),
    queryPromQL(`histogram_quantile(0.99, sum(rate(http_request_duration_seconds_bucket{handler="/r/:code"}[1m])) by (le))`),
  ]);

  console.log(`[steady state] error_rate=${(errorRate * 100).toFixed(3)}% p99=${(p99Latency * 1000).toFixed(1)}ms`);

  if (errorRate > 0.05) {  // > 5% error rate — abort experiment
    throw new Error(`[chaos ABORT] Error rate ${(errorRate * 100).toFixed(1)}% exceeds abort threshold 5%`);
  }
  if (p99Latency > 0.5) {  // > 500ms p99 — warn but don't abort redirect chaos
    console.warn(`[chaos WARN] p99 latency ${(p99Latency * 1000).toFixed(0)}ms exceeds 500ms`);
  }
}

// Call assertSteadyState() on a 5-second interval during the experiment
```

---

## Try It Yourself

**Exercise:** Run a controlled chaos experiment on ScaleForge.

```typescript
// TODO:
// 1. Start ScaleForge + all dependencies locally (docker compose up)
// 2. Define the steady state: 
//    - Run a background autocannon test: npx autocannon -c 10 -d 120 http://localhost:3000/r/abc123
//    - Note baseline p99 latency and error rate
//
// 3. Hypothesis: "If Redis is killed, redirect p99 stays < 25ms"
//
// 4. Run the experiment:
//    - Start steady-state assertion loop (every 5 seconds)
//    - Execute: docker compose stop redis
//    - Wait 30 seconds
//    - Execute: docker compose start redis
//
// 5. Verify:
//    - Did the p99 increase? By how much?
//    - Did any 5xx errors appear?
//    - Did the redirect tier gauge update correctly?
//
// 6. Document the result (see template below)
```

<details>
<summary>Show a completed experiment report template</summary>

```markdown
# Chaos Experiment Report

**Date:** [YYYY-MM-DD]
**Hypothesis:** If Redis is killed, ScaleForge continues to serve redirects with p99 < 25ms (fallback to Postgres DB)
**Blast radius:** Single Redis instance (dev environment)
**Duration:** 30 seconds

## Steady State (before experiment)
- Redirect error rate: 0.02%
- Redirect p99 latency: 1.2ms
- Redirect tier gauge: 0 (cache)

## During Experiment (t=0..30s)
- Redirect error rate: 0.05% (slight increase — some in-flight requests failed on Redis)
- Redirect p99 latency: 8.3ms (increased but < 25ms threshold ✓)
- Redirect tier gauge: 1 (DB fallback)
- ScaleForgeRunningDegraded alert fired at t=+62s ✓

## After Recovery (t=60s)
- Redirect error rate: 0.02% ✓
- Redirect p99 latency: 1.1ms ✓
- Redirect tier gauge: 0 ✓

## Result: ✅ HYPOTHESIS CONFIRMED
Redirect latency stayed well below 25ms during Redis outage.
Cache fallback activated correctly. Alerting fired as expected.

## Confidence Gained
- Redis single-node failure → graceful degradation to Postgres ✓
- Alerting > database fallback confirms monitoring is connected ✓
- No error budget consumed (< 0.1% error rate throughout) ✓
```

</details>

---

## Capstone Connection

ScaleForge's multi-tier architecture (Module 10.3) only works if you've actually verified each tier activates under the right conditions. Chaos experiments turn those conditional code paths — the `} catch (cacheErr) {` blocks that almost never fire in production — into verified, documented behaviors. Over time, a library of passing chaos experiments becomes the most honest form of reliability documentation: not "we designed it this way," but "we tested this, on this date, and here's what we observed."
