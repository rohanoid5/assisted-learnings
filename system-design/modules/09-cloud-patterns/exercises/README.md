# Module 09 Exercises — Cloud Design Patterns

These exercises require ScaleForge and FlowForge running locally with the resilience patterns from this module applied. Each exercise is designed to be observable — you should see the pattern activate in logs, metrics, or HTTP responses.

---

## Exercise 1 — Trip and Recover the Circuit Breaker

**Goal:** Watch the circuit breaker move through all three states: CLOSED → OPEN → HALF_OPEN → CLOSED.

**Setup:**
- ScaleForge running on port 3000
- FlowForge mock server or the real service
- Prometheus/Grafana (optional, but great for observing the `flowforge_circuit_state` gauge)

**Steps:**

1. Stop FlowForge (or kill its network access).
2. Send 5+ notification requests through ScaleForge that require FlowForge.
   ```bash
   for i in {1..6}; do
     curl -s -o /dev/null -w "%{http_code}\n" \
       -X POST http://localhost:3000/api/v1/urls \
       -H 'Content-Type: application/json' \
       -d '{"longUrl": "https://example.com/'$i'"}'
   done
   ```
3. Observe that after 5 failures, responses change from 503 to "circuit open" errors (faster — no timeout wait).
4. Wait `resetTimeoutMs` (default 30 seconds).
5. Send one more request — this is the HALF_OPEN probe.
6. Start FlowForge again, then send one more request.
7. Confirm the circuit returns to CLOSED and requests succeed.

**Expected observations:**
- Requests 1–5: `503 Service Unavailable` (failure, circuit still CLOSED)
- Requests 6+: `503` but fast (no wait — circuit is OPEN, rejected immediately)
- After reset timeout + success probes: `201 Created` again
- In Prometheus: `flowforge_circuit_state` gauge: `0 → 2 → 1 → 0`

---

## Exercise 2 — Thundering Herd vs. Jitter

**Goal:** Visualize how full jitter spreads retry load vs. naive retries creating a thundering herd.

**Setup:**
- A mock server that fails 3 times then succeeds (use `express` with a counter in memory)
- A script that simulates 50 concurrent clients, each retrying on failure

**Steps:**

1. Create `scripts/herd-test.ts`:

```typescript
// Variation A: no jitter — fixed exponential backoff
async function naiveRetry(id: number): Promise<void> {
  for (let attempt = 0; attempt < 4; attempt++) {
    try {
      await fetch('http://localhost:4001/flaky');
      console.log(`[${Date.now()}] client ${id} succeeded on attempt ${attempt}`);
      return;
    } catch {
      const delay = 1000 * 2 ** attempt;  // 1s, 2s, 4s
      await new Promise(r => setTimeout(r, delay));
    }
  }
}

// Variation B: full jitter
async function jitterRetry(id: number): Promise<void> {
  for (let attempt = 0; attempt < 4; attempt++) {
    try {
      await fetch('http://localhost:4001/flaky');
      console.log(`[${Date.now()}] client ${id} succeeded on attempt ${attempt}`);
      return;
    } catch {
      const cap   = 1000 * 2 ** attempt;
      const delay = Math.random() * cap;
      await new Promise(r => setTimeout(r, delay));
    }
  }
}

// Run 50 concurrent clients, measure request arrival times per second
```

2. Look at the timestamp clustering with naive retry vs. jitter.
3. With naive retry: all 50 clients retry at the **same time** → mock server gets 50 requests in a 100ms window.
4. With jitter: retries are spread over multiple seconds → mock server sees a smooth arrival curve.

**Expected output (naive):**
```
All 50 "attempt 1" arrivals land at t≈1000ms
All 50 "attempt 2" arrivals land at t≈3000ms
Peak RPS: 50 × (1 / 0.1s) = 500 req/s in spikes
```

**Expected output (jitter):**
```
Retries spread over 0ms..1000ms, 0ms..2000ms, 0ms..4000ms
Peak RPS: ~5–10 req/s sustained
```

---

## Exercise 3 — Bulkhead: Protect Redirects from Admin Bulk Import

**Goal:** Run a heavy admin bulk import simultaneously with redirect traffic and confirm redirect latency doesn't degrade when bulkheads are configured correctly.

**Steps:**

1. Write a bulk import script that creates 1000 URLs back-to-back using the `adminPool`.
   ```bash
   for i in {1..1000}; do
     curl -s -o /dev/null http://localhost:3000/admin/bulk-import &
   done
   ```

2. Simultaneously, run redirect load:
   ```bash
   npx autocannon -c 50 -d 30 http://localhost:3000/r/abc123
   ```

3. Run this **twice**: once with all routes sharing a single pool (comment out separate pools), once with bulkheaded pools.

4. Compare redirect p99 latency between the two runs.

**Expected results:**
```
Without bulkhead:
  Redirect p99:     320ms  (admin queries consuming pool connections)
  
With bulkhead (separate pools):
  Redirect p99:     4ms    (redirectPool uncontested)
  Admin may queue:  some bulk import requests get 429 from Semaphore
```

---

## Exercise 4 — Compose All Four Patterns

**Goal:** Combine circuit breaker + retry with jitter + bulkhead + timeout on the FlowForge client and verify each pattern activates in the right scenario.

**Scenarios to test:**

| Scenario | Expected activating pattern |
|---|---|
| FlowForge returns 503 once, then 200 | Retry (1 retry, then success) |
| FlowForge returns 503 six times | Retry exhausted → Circuit opens |
| 20 concurrent requests hit FlowForge endpoint | Bulkhead: >10 concurrent rejected |
| FlowForge hangs (no response) | Timeout fires after 5s |
| All above simultaneously | Graceful degrades in order: timeout → retry → bulkhead → circuit |

**Implementation hint:**

```typescript
// The composed client call order should be:
// 1. Bulkhead (outer — checks concurrency limits first)
// 2. Circuit breaker (skips call if circuit open)
// 3. Timeout (wraps the actual fetch)
// 4. Retry (wraps timeout — retries on timeout OR 5xx)

async function callFlowForge(job: NotificationJobInput, signal: AbortSignal) {
  return await bulkhead.run(() =>         // 1. concurrency limit
    breaker.call(() =>                    // 2. circuit check
      withRetry(() =>                     // 3. retry with jitter
        withTimeout(                      // 4. per-call timeout
          () => doFetch(job, signal),
          5000,
        ),
        { maxAttempts: 3, baseDelayMs: 500 },
      )
    )
  );
}
```

For each scenario, check logs for the specific error name (`ConcurrencyLimitError`, `CircuitOpenError`, `AbortError`, `RetryExhaustedError`) to confirm the right guard activated.
