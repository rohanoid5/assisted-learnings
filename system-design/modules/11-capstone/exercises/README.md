# Module 11 Exercises — Capstone Integration

These exercises require the full stack running via `docker compose up`. They test end-to-end behavior across all layers and verify that the resilience patterns actually work together.

---

## Exercise 1 — Trace a Request Through All Layers

**Goal:** Follow a single redirect request through every system and annotate where each pattern activates.

**Steps:**

1. Enable verbose logging in ScaleForge: `LOG_LEVEL=debug docker compose up scaleforge`

2. Create a URL and note the short code:
   ```bash
   SHORT=$(curl -s -X POST http://localhost:3000/api/v1/urls \
     -H 'Content-Type: application/json' \
     -H 'Authorization: Bearer dev-token' \
     -d '{"longUrl": "https://example.com/exercise-1"}' | jq -r '.shortCode')
   ```

3. Hit the redirect:
   ```bash
   curl -v http://localhost:3000/r/$SHORT
   ```

4. In the log output, identify exactly where each of these occurred:
   - Correlation ID assigned
   - Deadline set (`X-Request-Deadline` header)
   - Redis cache hit (or miss)
   - Database query (if cache missed)
   - Response latency recorded for Prometheus

5. Now flush the Redis key and repeat:
   ```bash
   docker compose exec redis redis-cli DEL "url:$SHORT"
   curl -v http://localhost:3000/r/$SHORT
   ```
   Verify the log now shows a DB query and a cache set operation.

**Expected log structure (abbreviated):**
```json
{"level":"info","correlationId":"abc-123","msg":"Incoming request","method":"GET","path":"/r/abc123"}
{"level":"debug","correlationId":"abc-123","msg":"Cache hit","key":"url:abc123","tier":0}
{"level":"info","correlationId":"abc-123","msg":"Redirect served","shortCode":"abc123","tier":0,"durationMs":1}
```

---

## Exercise 2 — Full Saga: FlowForge Unavailable During URL Creation

**Goal:** Observe the saga compensation flow when FlowForge is down.

**Steps:**

1. Stop FlowForge:
   ```bash
   docker compose stop flowforge
   ```

2. Try to create a URL:
   ```bash
   curl -s -X POST http://localhost:3000/api/v1/urls \
     -H 'Content-Type: application/json' \
     -H 'Authorization: Bearer dev-token' \
     -d '{"longUrl": "https://example.com/saga-test"}' | jq .
   ```

3. You should see:
   - ScaleForge makes 5 attempts to call FlowForge (with jitter delays between them)
   - After the 5th failure, the circuit breaker opens
   - ScaleForge compensates: deletes the URL it just inserted
   - Returns `503` with `Retry-After: 30`

4. Verify the URL was deleted (not left as half-written data):
   ```bash
   # If the short code appeared in the 503 response...
   curl -I http://localhost:3000/r/<shortCode>
   # Expected: 404 (not 302 — the compensation ran correctly)
   ```

5. Wait 30 seconds (circuit breaker reset), then restart FlowForge:
   ```bash
   docker compose start flowforge
   sleep 30
   curl -s -X POST http://localhost:3000/api/v1/urls \
     -H 'Content-Type: application/json' \
     -H 'Authorization: Bearer dev-token' \
     -d '{"longUrl": "https://example.com/saga-test"}' | jq .
   # Expected: 201 (circuit half-open probe → success → CLOSED)
   ```

---

## Exercise 3 — Three-Tier Degradation Under Chaos

**Goal:** Walk ScaleForge through all four degradation tiers and observe the metrics change.

**Setup:**
```bash
# Start the steady-state metric watcher (requires the assert-steady-state.ts from Module 10.4)
npx tsx scripts/chaos/assert-steady-state.ts &

# Run constant redirect load
npx autocannon -c 10 -d 300 http://localhost:3000/r/abc123 &
```

**Steps:**

```bash
# Tier 0 → 1: Kill Redis
docker compose stop redis
# Watch: scaleforge_redirect_tier gauge changes from 0 to 1
# Watch: redirect p99 increases from ~1ms to ~8ms
sleep 60

# Tier 1 → 2: Kill primary DB
docker compose stop postgres-primary
# Watch: scaleforge_redirect_tier gauge changes from 1 to 2
# Watch: redirect p99 increases to ~15ms
# Watch: POST /api/v1/urls now returns 503
sleep 60

# Tier 2 → 3: Kill read replica
docker compose stop postgres-replica
# Watch: scaleforge_redirect_tier gauge changes from 2 to 3
# Watch: redirect p99 drops to <0.1ms (in-memory!)
# Watch: unknown short codes now return the degraded HTML page
sleep 60

# Full recovery
docker compose start postgres-primary postgres-replica redis
# Watch: tier gauge return to 0 within 2-3 minutes (cache repopulation)
```

**Record your observations:**

| Time | Tier | p99 Latency | Error Rate | Active services |
|---|---|---|---|---|
| Baseline | 0 | ~1ms | ~0% | All |
| +0s (Redis down) | 1 | ? | ? | App + Postgres + Replica |
| +60s (Primary down) | 2 | ? | ? | App + Replica |
| +120s (Replica down) | 3 | ? | ? | App only |
| +180s (All recovered) | 0 | ~1ms | ~0% | All |

---

## Exercise 4 — End-to-End SLO Verification

**Goal:** Run a 5-minute load test and verify ScaleForge meets its SLOs.

**Steps:**

1. Start the full stack: `docker compose up`

2. Prime the cache:
   ```bash
   # Create 50 URLs and warm the cache
   for i in {1..50}; do
     CODE=$(curl -s -X POST http://localhost:3000/api/v1/urls \
       -H 'Content-Type: application/json' \
       -H 'Authorization: Bearer dev-token' \
       -d "{\"longUrl\": \"https://example.com/$i\"}" | jq -r '.shortCode')
     curl -s -o /dev/null http://localhost:3000/r/$CODE  # warm cache
   done
   ```

3. Run a 5-minute load test:
   ```bash
   npx autocannon \
     -c 50 \
     -d 300 \
     --latency \
     http://localhost:3000/r/abc123
   ```

4. Verify against SLOs:

   | SLO | Target | Result | Pass? |
   |---|---|---|---|
   | Redirect availability | >= 99.9% | ? | ? |
   | Redirect p99 | <= 50ms | ? | ? |
   | Redirect p50 | <= 5ms | ? | ? |

5. Query Prometheus to verify:
   ```
   # Availability:
   1 - (
     sum(rate(http_requests_total{status=~"5..",handler="/r/:code"}[5m]))
     /
     sum(rate(http_requests_total{handler="/r/:code"}[5m]))
   )

   # p99 Latency:
   histogram_quantile(
     0.99,
     sum(rate(http_request_duration_seconds_bucket{handler="/r/:code"}[5m])) by (le)
   )
   ```

**If SLOs are missed:** Investigate which tier was active during the test (use the `scaleforge_redirect_tier` gauge in Grafana). Was the cache primed? Were there pool wait times?

---

## Exercise 5 — Design Your Own Feature

**Goal:** Apply the full course methodology to a new feature before writing any code.

**Feature to design:** ScaleForge wants to add link expiry — URLs can have an optional `expiresAt` timestamp. After that timestamp, the short link should return `410 Gone` instead of redirecting.

**Deliverable:**

```markdown
# Feature Design: Link Expiry

## SLOs
<!-- Define 2-3 SLIs and SLO targets for this feature -->

## Data Model Changes
<!-- What Postgres columns are added? -->

## Redis Cache Strategy
<!-- Does the cache TTL change? How does expiry interact with cached redirect responses? -->

## Redirect Handler Changes
<!-- Where in the tier-0..tier-3 flow does expiry get checked? -->

## API Changes
<!-- What does POST /api/v1/urls accept? What does GET /api/v1/urls/:code return? -->

## Graceful Degradation
<!-- If Redis is down, can you check expiry? From which tier? -->

## Rate Limiting Impact
<!-- Does expiry affect how you count URL creation toward the quota? -->

## Metrics to Add
<!-- What new Prometheus metrics would you add? -->

## Chaos Experiment
<!-- Design one chaos experiment to verify expiry behavior under failure -->
```
