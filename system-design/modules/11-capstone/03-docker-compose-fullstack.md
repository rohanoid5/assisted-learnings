# 11.3 — Full Stack: docker-compose.yml

## Overview

This `docker-compose.yml` starts the entire ScaleForge + FlowForge stack locally with a single command. It includes all dependencies, health checks, and service ordering so you can run end-to-end integration tests immediately after `docker compose up`.

---

## Running the Stack

```bash
# From the root of the project:
docker compose up

# Wait for all services to be healthy, then:
curl -X POST http://localhost:3000/api/v1/urls \
  -H 'Content-Type: application/json' \
  -H 'Authorization: Bearer <your-dev-token>' \
  -d '{"longUrl": "https://example.com/my-long-page"}'

# Follow the short link:
curl -L http://localhost:3000/r/<shortCode>
```

---

## docker-compose.yml

```yaml
# docker-compose.yml
# Full ScaleForge + FlowForge development stack.
# Services start in dependency order with health checks.

version: '3.9'

services:

  # ─────────────────────── Infrastructure ────────────────────────── #

  postgres-primary:
    image: postgres:16-alpine
    environment:
      POSTGRES_DB:       scaleforge
      POSTGRES_USER:     scaleforge
      POSTGRES_PASSWORD: password
    ports:
      - "5432:5432"
    volumes:
      - postgres_primary_data:/var/lib/postgresql/data
      - ./db/migrations:/docker-entrypoint-initdb.d:ro
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U scaleforge -d scaleforge"]
      interval: 5s
      timeout:  5s
      retries:  10

  postgres-replica:
    image: postgres:16-alpine
    environment:
      POSTGRES_DB:       scaleforge
      POSTGRES_USER:     scaleforge
      POSTGRES_PASSWORD: password
    ports:
      - "5433:5432"
    volumes:
      - postgres_replica_data:/var/lib/postgresql/data
    # In production this would be a streaming replica of the primary.
    # For local dev, it's a separate instance with the same schema (seeded separately).
    # Run: docker compose exec postgres-replica psql -U scaleforge -c "..."
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U scaleforge -d scaleforge"]
      interval: 5s
      timeout:  5s
      retries:  10

  redis:
    image: redis:7-alpine
    command: redis-server --appendonly yes
    ports:
      - "6379:6379"
    volumes:
      - redis_data:/data
    healthcheck:
      test: ["CMD", "redis-cli", "ping"]
      interval: 5s
      timeout:  3s
      retries:  10

  # ──────────────────────── Application Services ──────────────────── #

  scaleforge:
    build:
      context:    ./scaleforge
      dockerfile: Dockerfile
    ports:
      - "3000:3000"
    environment:
      NODE_ENV:                production
      PORT:                    3000
      DATABASE_URL:            postgresql://scaleforge:password@postgres-primary:5432/scaleforge
      DATABASE_READ_URL:       postgresql://scaleforge:password@postgres-replica:5432/scaleforge
      REDIS_URL:               redis://redis:6379
      FLOWFORGE_URL:           http://flowforge:4000
      JWT_SECRET:              dev-secret-at-least-32-chars-long!
      APP_BASE_URL:            http://localhost:3000
      LOG_LEVEL:               info
      DB_REDIRECT_POOL_MAX:    8
      DB_WRITE_POOL_MAX:       5
      DB_ADMIN_POOL_MAX:       2
      FLOWFORGE_TIMEOUT_MS:    5000
      CB_FAILURE_THRESHOLD:    5
      CB_RESET_TIMEOUT_MS:     30000
      URL_CREATION_LIMIT:      100
      URL_CREATION_WINDOW_MS:  3600000
    depends_on:
      postgres-primary:
        condition: service_healthy
      postgres-replica:
        condition: service_healthy
      redis:
        condition: service_healthy
    healthcheck:
      test: ["CMD-SHELL", "wget -qO- http://localhost:3000/health/live || exit 1"]
      interval: 10s
      timeout:  5s
      retries:  5

  flowforge:
    build:
      context:    ./flowforge
      dockerfile: Dockerfile
    ports:
      - "4000:4000"
    environment:
      NODE_ENV:         production
      PORT:             4000
      DATABASE_URL:     postgresql://scaleforge:password@postgres-primary:5432/scaleforge
      REDIS_URL:        redis://redis:6379
      SMTP_RELAY_URL:   http://mailhog:8025/api/v2/messages  # local mail catcher
      LOG_LEVEL:        info
    depends_on:
      postgres-primary:
        condition: service_healthy
      redis:
        condition: service_healthy
    healthcheck:
      test: ["CMD-SHELL", "wget -qO- http://localhost:4000/health/live || exit 1"]
      interval: 10s
      timeout:  5s
      retries:  5

  # ─────────────────────── Development Helpers  ───────────────────── #

  # Local SMTP catcher — view sent emails at http://localhost:8025
  mailhog:
    image: mailhog/mailhog:latest
    ports:
      - "1025:1025"   # SMTP port
      - "8025:8025"   # Web UI

  # BullMQ job dashboard — view queues at http://localhost:3001
  bull-board:
    image: deadly0/bull-board:latest
    ports:
      - "3001:3000"
    environment:
      REDIS_HOST: redis
      REDIS_PORT: 6379

  # ─────────────────────── Observability Stack ────────────────────── #

  prometheus:
    image: prom/prometheus:latest
    ports:
      - "9090:9090"
    volumes:
      - ./prometheus/prometheus.yml:/etc/prometheus/prometheus.yml:ro
      - ./prometheus/slo-alerts.yml:/etc/prometheus/slo-alerts.yml:ro
      - prometheus_data:/prometheus
    command:
      - '--config.file=/etc/prometheus/prometheus.yml'
      - '--storage.tsdb.path=/prometheus'
      - '--web.enable-lifecycle'
    depends_on:
      scaleforge:
        condition: service_healthy
      flowforge:
        condition: service_healthy

  grafana:
    image: grafana/grafana:latest
    ports:
      - "3002:3000"
    environment:
      GF_SECURITY_ADMIN_PASSWORD: admin
    volumes:
      - grafana_data:/var/lib/grafana
      - ./grafana/provisioning:/etc/grafana/provisioning:ro
    depends_on:
      - prometheus

volumes:
  postgres_primary_data:
  postgres_replica_data:
  redis_data:
  prometheus_data:
  grafana_data:
```

---

## Prometheus Configuration

```yaml
# prometheus/prometheus.yml
global:
  scrape_interval:     15s
  evaluation_interval: 15s

rule_files:
  - slo-alerts.yml

scrape_configs:
  - job_name: scaleforge
    static_configs:
      - targets: ['scaleforge:3000']
    metrics_path: /metrics

  - job_name: flowforge
    static_configs:
      - targets: ['flowforge:4000']
    metrics_path: /metrics
```

---

## Quick Verification

After `docker compose up`, run these smoke tests to verify each layer:

```bash
# 1. Create a URL
RESPONSE=$(curl -s -X POST http://localhost:3000/api/v1/urls \
  -H 'Content-Type: application/json' \
  -H 'Authorization: Bearer dev-token' \
  -d '{"longUrl": "https://example.com"}')
SHORT_CODE=$(echo $RESPONSE | jq -r '.shortCode')
echo "Short code: $SHORT_CODE"

# 2. Redirect
curl -I http://localhost:3000/r/$SHORT_CODE
# Expected: HTTP/1.1 302 Found
#           Location: https://example.com

# 3. Rate limiting
for i in {1..5}; do
  curl -s -o /dev/null -w "%{http_code}\n" \
    -X POST http://localhost:3000/api/v1/urls \
    -H 'Content-Type: application/json' \
    -H 'Authorization: Bearer dev-token' \
    -d '{"longUrl": "https://example.com/'$i'"}'
done
# First 4 (cumulative with step 1): 201
# After 100 total: 429

# 4. Metrics endpoint
curl -s http://localhost:3000/metrics | grep http_requests_total

# 5. FlowForge health
curl http://localhost:4000/health/live
# Expected: {"status":"ok"}

# 6. View queued emails at http://localhost:8025 (MailHog)
# 7. View queue depth at http://localhost:3001 (Bull Board)
# 8. View SLO dashboards at http://localhost:3002 (Grafana, admin/admin)
```
