# Module 07 — Exercises: Microservices

---

## Exercise 1: Correlation ID Tracing

**Goal:** Verify a single correlation ID flows through ScaleForge AND FlowForge logs for one user request.

**Setup:**
```bash
# Start both services
docker-compose up scaleforge flowforge

# In separate terminals, tail logs of each
docker-compose logs -f scaleforge | grep correlationId
docker-compose logs -f flowforge  | grep correlationId
```

**Steps:**
1. Make a request that triggers both services:
```bash
curl -X POST http://localhost:3001/api/v1/urls \
  -H "Content-Type: application/json" \
  -H "X-Correlation-Id: test-trace-001" \
  -d '{"url":"https://example.com","notifyOnCreate":true}'
```

2. Find `"test-trace-001"` in BOTH ScaleForge and FlowForge log streams.

**Expected:**
- ScaleForge logs: `{"correlationId":"test-trace-001","msg":"URL inserted",...}`
- FlowForge logs: `{"correlationId":"test-trace-001","msg":"Notification enqueued",...}`

**Verify it fails gracefully:**
- Remove the `correlationIdMiddleware` from FlowForge and repeat the test.
- What ID appears in FlowForge logs? (It should generate a new one — not break.)

---

## Exercise 2: Service Failure Isolation

**Goal:** Verify ScaleForge's redirect path keeps working when FlowForge is down.

**Steps:**
```bash
# 1. Create a short URL first (while FlowForge is alive)
curl -X POST http://localhost:3001/api/v1/urls \
  -d '{"url":"https://example.com"}' \
  -H "Content-Type: application/json"
# Note the shortCode returned

# 2. Stop FlowForge
docker-compose stop flowforge

# 3. Verify redirect STILL works (it doesn't call FlowForge)
curl -v http://localhost:3001/<shortCode>
# Expected: 302 Found — redirect works, FlowForge being down doesn't matter

# 4. Verify URL creation WITH notification fails gracefully
curl -X POST http://localhost:3001/api/v1/urls \
  -d '{"url":"https://example.com","notifyOnCreate":true}' \
  -H "Content-Type: application/json"
# Expected: 503 with Retry-After header (not 500, not a crash)
```

**Extend:**
- Add a feature flag `ALLOW_PARTIAL_SUCCESS=true` that creates the URL even when FlowForge is down (skips the compensation step). Note the tradeoff: user gets a URL, but no notification was queued.

---

## Exercise 3: Saga Rollback Verification

**Goal:** Force the compensation path and confirm no orphaned rows are left in the database.

**Steps:**
```typescript
// Temporarily modify FlowForgeClient.enqueueNotification()
// to always throw ServiceUnavailableError:

async enqueueNotification(_job: NotificationJobInput): Promise<string> {
  throw new ServiceUnavailableError('FlowForge');
}
```

```bash
# Make a request with notifyOnCreate: true
curl -X POST http://localhost:3001/api/v1/urls \
  -d '{"url":"https://example.com","notifyOnCreate":true}' \
  -H "Content-Type: application/json"
# Expected: 503

# Query the database — URL must NOT be there
psql -U app -d scaleforge -c "SELECT * FROM urls ORDER BY created_at DESC LIMIT 5;"
# Expected: the most recent row should NOT be from this request
```

**Watch the logs:**
```
{"msg":"Step 1 complete: URL inserted"}
{"msg":"Step 2 failed — running compensation"}
{"msg":"Compensation complete: URL deleted"}
```

**Bonus:** Wrap `compensateUrlInsert()` in a try/catch. Inject a failure there too (throw inside it). Verify the original error is still propagated and a FATAL log is emitted for the orphaned URL.

---

## Exercise 4: Prometheus Metrics Scraping

**Goal:** Observe real request latency histograms after a load test.

**Steps:**
```bash
# 1. Ensure /metrics endpoint is running on ScaleForge

# 2. Send 100 URL creation requests
for i in $(seq 1 100); do
  curl -s -X POST http://localhost:3001/api/v1/urls \
    -H "Content-Type: application/json" \
    -d '{"url":"https://example.com"}' > /dev/null
done

# 3. Fetch the metrics
curl http://localhost:3001/metrics | grep http_request_duration_seconds

# 4. Find the p99 value manually by reading the histogram buckets:
#    Look for the highest bucket with a count close to total request count * 0.99
```

**Install Prometheus locally (optional):**
```yaml
# prometheus.yml
scrape_configs:
  - job_name: scaleforge
    static_configs:
      - targets: ['host.docker.internal:3001']
    scrape_interval: 15s
```

```bash
docker run -p 9090:9090 \
  -v $(pwd)/prometheus.yml:/etc/prometheus/prometheus.yml \
  prom/prometheus
```

**Query in Prometheus UI (`http://localhost:9090`):**
```promql
# p99 redirect latency over last 5 minutes
histogram_quantile(0.99,
  rate(http_request_duration_seconds_bucket{route="/:code"}[5m])
)

# Cache hit rate (requires cacheHitsTotal counter)
rate(cache_hits_total{tier="l2"}[5m]) /
(rate(cache_hits_total{tier="l2"}[5m]) + rate(cache_misses_total{tier="l2"}[5m]))
```
