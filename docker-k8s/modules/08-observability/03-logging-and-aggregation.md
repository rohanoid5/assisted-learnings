# 8.3 — Structured Logging & Log Aggregation

## Concept

Logs are the oldest observability signal — and the most abused. When an engineer writes `console.log("something went wrong")`, they've created a log. When they write a structured JSON entry with a timestamp, severity, correlation ID, trace ID, and contextual fields, they've created _useful_ information. The difference between the two is the difference between grepping through gigabytes of text and querying indexed fields in milliseconds.

Structured logging means emitting logs as machine-parseable records (JSON) with consistent field names. Log aggregation means collecting those records from hundreds of pods and sending them to a central store where they can be searched, filtered, and correlated. Together with metrics (what's broken) and traces (where it's broken), logs tell you _why_ it's broken — the exception stack trace, the validation error message, the unexpected input value.

This section covers structured logging best practices, correlation IDs, log collection with Fluent Bit, storage with Loki, and how to avoid the most common logging mistakes.

---

## Deep Dive

### Structured vs Unstructured Logging

```
┌─────────────────────────────────────────────────────────────────────┐
│              Unstructured vs Structured Logging                      │
│                                                                     │
│  Unstructured (BAD):                                                │
│  ┌─────────────────────────────────────────────────────────────┐    │
│  │ [2024-01-15 14:32:01] ERROR: Failed to deploy abc123 to    │    │
│  │ production - connection refused to postgres:5432             │    │
│  │                                                             │    │
│  │ How do you:                                                 │    │
│  │   - Filter by severity? → grep "ERROR" (fragile)            │    │
│  │   - Find all logs for deployment abc123? → grep (misses)    │    │
│  │   - Correlate with trace? → impossible                      │    │
│  │   - Count errors per service? → very difficult              │    │
│  └─────────────────────────────────────────────────────────────┘    │
│                                                                     │
│  Structured (GOOD):                                                 │
│  ┌─────────────────────────────────────────────────────────────┐    │
│  │ {                                                           │    │
│  │   "timestamp": "2024-01-15T14:32:01.123Z",                 │    │
│  │   "level": "error",                                         │    │
│  │   "message": "Failed to deploy",                            │    │
│  │   "service": "worker",                                      │    │
│  │   "deployment_id": "abc123",                                │    │
│  │   "environment": "production",                              │    │
│  │   "error": "connection refused",                            │    │
│  │   "target_host": "postgres:5432",                           │    │
│  │   "trace_id": "abc123def456789012345678",                   │    │
│  │   "span_id": "span001234567890",                            │    │
│  │   "request_id": "req-789xyz"                                │    │
│  │ }                                                           │    │
│  │                                                             │    │
│  │ Now you can:                                                │    │
│  │   - Filter: {level="error", service="worker"}               │    │
│  │   - Correlate: {deployment_id="abc123"} across all services │    │
│  │   - Link to trace: click trace_id → Jaeger                  │    │
│  │   - Aggregate: count_over_time({level="error"}[5m])         │    │
│  └─────────────────────────────────────────────────────────────┘    │
└─────────────────────────────────────────────────────────────────────┘
```

---

### Log Levels and When to Use Each

| Level | When to Use | Example |
|-------|-------------|---------|
| **fatal** | Process cannot continue. About to exit. | `Unrecoverable: database migration failed. Shutting down.` |
| **error** | Operation failed. Needs attention. | `Failed to process deployment job abc123: timeout` |
| **warn** | Unexpected but recoverable. Monitor for patterns. | `Redis connection retry 3/5. Queue depth growing.` |
| **info** | Normal operational events. Audit trail. | `Deployment abc123 started for project xyz` |
| **debug** | Diagnostic information. Off in production. | `Validating config: 12 rules checked, all passed` |
| **trace** | Extremely verbose. Only during active debugging. | `Entering function deployContainer, args: {...}` |

> **Production note:** Run `info` in production, `debug` in staging, `trace` only when actively debugging a specific issue. Each level below `info` can 10× your log volume — and your storage bill.

---

### Correlation IDs

A correlation ID (also called request ID) is a unique identifier that flows through every service involved in handling a single user request. Combined with `trace_id` from OpenTelemetry, it creates a searchable thread across all logs.

```
┌─────────────────────────────────────────────────────────────────────┐
│                  Correlation ID Flow                                  │
│                                                                     │
│  Client Request                                                     │
│  X-Request-ID: req-789xyz                                           │
│       │                                                             │
│       ▼                                                             │
│  ┌─────────────┐  All logs tagged with:                             │
│  │ API Gateway  │  request_id: req-789xyz                           │
│  │              │  trace_id:   abc123def456                         │
│  │ Generates ID │                                                   │
│  │ if missing   │                                                   │
│  └──────┬───────┘                                                   │
│         │ Forwarded in headers + job payload                        │
│         ▼                                                           │
│  ┌─────────────┐  All logs tagged with:                             │
│  │ Worker Svc   │  request_id: req-789xyz                           │
│  │              │  trace_id:   abc123def456                         │
│  └──────┬───────┘                                                   │
│         │                                                           │
│         ▼                                                           │
│  Search Loki: {request_id="req-789xyz"}                             │
│  → Returns logs from BOTH services, ordered by time                 │
│  → Click trace_id → Opens full trace in Jaeger                      │
└─────────────────────────────────────────────────────────────────────┘
```

```typescript
// src/middleware/request-id.ts — generate or forward correlation ID
import { Request, Response, NextFunction } from 'express';
import { randomUUID } from 'crypto';

export function requestIdMiddleware(req: Request, res: Response, next: NextFunction): void {
  // Use incoming ID if present (from upstream proxy or client)
  const requestId = req.headers['x-request-id'] as string || randomUUID();

  // Make it available to the application
  req.requestId = requestId;

  // Return it in the response for client-side correlation
  res.setHeader('X-Request-ID', requestId);

  next();
}

// Extend Express Request type
declare global {
  namespace Express {
    interface Request {
      requestId: string;
    }
  }
}
```

---

### Implementing Structured Logging with Pino

[Pino](https://github.com/pinojs/pino) is the fastest Node.js JSON logger. It outputs one JSON line per log entry — perfect for Fluent Bit and Loki.

```typescript
// src/logger.ts — structured logger with trace + request context
import pino from 'pino';
import { trace, context } from '@opentelemetry/api';

// Base logger configuration
export const logger = pino({
  level: process.env.LOG_LEVEL || 'info',
  formatters: {
    level(label) {
      return { level: label };  // "level": "info" instead of "level": 30
    },
  },
  // Always include these fields
  base: {
    service: process.env.SERVICE_NAME || 'api-gateway',
    version: process.env.npm_package_version || '0.0.0',
    environment: process.env.NODE_ENV || 'development',
  },
  // ISO timestamps
  timestamp: pino.stdTimeFunctions.isoTime,
  // Redact sensitive fields
  redact: {
    paths: ['req.headers.authorization', 'req.headers.cookie', 'password', 'token', 'secret'],
    censor: '[REDACTED]',
  },
});

// Create a child logger with trace context for the current request
export function createRequestLogger(requestId: string): pino.Logger {
  const span = trace.getSpan(context.active());
  const spanContext = span?.spanContext();

  return logger.child({
    request_id: requestId,
    ...(spanContext && {
      trace_id: spanContext.traceId,
      span_id: spanContext.spanId,
    }),
  });
}
```

```typescript
// src/middleware/logging.ts — request/response logging middleware
import { Request, Response, NextFunction } from 'express';
import { createRequestLogger } from '../logger';

export function loggingMiddleware(req: Request, res: Response, next: NextFunction): void {
  const log = createRequestLogger(req.requestId);

  // Attach logger to request for use in route handlers
  req.log = log;

  const start = process.hrtime.bigint();

  // Log the incoming request
  log.info({
    msg: 'request_started',
    http: {
      method: req.method,
      url: req.originalUrl,
      user_agent: req.headers['user-agent'],
    },
  });

  // Log the response when it finishes
  res.on('finish', () => {
    const durationMs = Number(process.hrtime.bigint() - start) / 1e6;

    const logMethod = res.statusCode >= 500 ? 'error'
      : res.statusCode >= 400 ? 'warn'
      : 'info';

    log[logMethod]({
      msg: 'request_completed',
      http: {
        method: req.method,
        url: req.originalUrl,
        status_code: res.statusCode,
        duration_ms: Math.round(durationMs * 100) / 100,
        response_size: res.getHeader('content-length'),
      },
    });
  });

  next();
}

// Extend Express Request type
declare global {
  namespace Express {
    interface Request {
      log: import('pino').Logger;
    }
  }
}
```

> **Key insight:** Attach the child logger (with request_id and trace_id) to the request object. Every route handler uses `req.log.info(...)` instead of importing a global logger. This ensures every log line is automatically correlated.

Sample output:
```json
{"level":"info","time":"2024-01-15T14:32:01.123Z","service":"api-gateway","version":"1.2.0","environment":"production","request_id":"req-789xyz","trace_id":"abc123def456789012345678","span_id":"span001234567890","msg":"request_started","http":{"method":"POST","url":"/api/deployments","user_agent":"curl/8.4.0"}}
{"level":"info","time":"2024-01-15T14:32:01.573Z","service":"api-gateway","version":"1.2.0","environment":"production","request_id":"req-789xyz","trace_id":"abc123def456789012345678","span_id":"span001234567890","msg":"request_completed","http":{"method":"POST","url":"/api/deployments","status_code":201,"duration_ms":450.12,"response_size":"1234"}}
```

---

### Log Collection with Fluent Bit

Fluent Bit runs as a **DaemonSet** — one pod per node — and tails container log files from `/var/log/containers/`. It enriches logs with Kubernetes metadata (pod name, namespace, labels) and forwards them to Loki.

```
┌─────────────────────────────────────────────────────────────────────┐
│                  Log Collection Pipeline                              │
│                                                                     │
│  ┌──── Node 1 ──────────────────────────────────────────────────┐  │
│  │                                                               │  │
│  │  ┌───────────┐  ┌───────────┐  ┌───────────┐                │  │
│  │  │ api-gw-0  │  │ worker-0  │  │ postgres-0│                │  │
│  │  │ stdout→   │  │ stdout→   │  │ stdout→   │                │  │
│  │  └─────┬─────┘  └─────┬─────┘  └─────┬─────┘                │  │
│  │        │              │              │                        │  │
│  │        ▼              ▼              ▼                        │  │
│  │  /var/log/containers/*.log (JSON per line)                    │  │
│  │        │                                                      │  │
│  │        ▼                                                      │  │
│  │  ┌───────────────────────────────────┐                        │  │
│  │  │ Fluent Bit (DaemonSet)            │                        │  │
│  │  │                                   │                        │  │
│  │  │ 1. Tail /var/log/containers/*.log │                        │  │
│  │  │ 2. Parse JSON                     │                        │  │
│  │  │ 3. Enrich with K8s metadata       │                        │  │
│  │  │ 4. Filter by namespace            │                        │  │
│  │  │ 5. Forward to Loki                │                        │  │
│  │  └──────────────┬────────────────────┘                        │  │
│  └─────────────────┼─────────────────────────────────────────────┘  │
│                    │                                                 │
│                    ▼                                                 │
│  ┌──── Loki ───────────────────────────────────────────────────┐    │
│  │  Label-indexed log storage                                   │    │
│  │  Labels: {namespace, pod, container, level, service}         │    │
│  │  Query: {namespace="deployforge", level="error"} |= "timeout"│    │
│  └──────────────────────────────────────────────────────────────┘    │
│                    │                                                 │
│                    ▼                                                 │
│  ┌──── Grafana ──────────────────────────────────────────────┐      │
│  │  Explore → Loki datasource → LogQL queries                 │      │
│  │  Dashboard panels with log volume + filtered log lines     │      │
│  └────────────────────────────────────────────────────────────┘      │
└─────────────────────────────────────────────────────────────────────┘
```

---

### Deploying Fluent Bit

```yaml
# fluent-bit-config.yaml
apiVersion: v1
kind: ConfigMap
metadata:
  name: fluent-bit-config
  namespace: deployforge
data:
  fluent-bit.conf: |
    [SERVICE]
        Flush        5
        Log_Level    info
        Daemon       off
        Parsers_File parsers.conf

    [INPUT]
        Name             tail
        Path             /var/log/containers/*.log
        Parser           cri
        Tag              kube.*
        Refresh_Interval 5
        Mem_Buf_Limit    10MB
        Skip_Long_Lines  On

    [FILTER]
        Name                kubernetes
        Match               kube.*
        Kube_URL            https://kubernetes.default.svc:443
        Kube_CA_File        /var/run/secrets/kubernetes.io/serviceaccount/ca.crt
        Kube_Token_File     /var/run/secrets/kubernetes.io/serviceaccount/token
        Merge_Log           On
        Merge_Log_Key       log_parsed
        K8S-Logging.Parser  On
        K8S-Logging.Exclude On

    [FILTER]
        Name    grep
        Match   kube.*
        Regex   $kubernetes['namespace_name'] ^deployforge$

    [OUTPUT]
        Name       loki
        Match      kube.*
        Host       loki.deployforge.svc.cluster.local
        Port       3100
        Labels     namespace=$kubernetes['namespace_name'], pod=$kubernetes['pod_name'], container=$kubernetes['container_name']
        Auto_Kubernetes_Labels Off
        Line_Format json

  parsers.conf: |
    [PARSER]
        Name   cri
        Format regex
        Regex  ^(?<time>[^ ]+) (?<stream>stdout|stderr) (?<logtag>[^ ]*) (?<log>.*)$
        Time_Key    time
        Time_Format %Y-%m-%dT%H:%M:%S.%L%z
```

```yaml
# fluent-bit-daemonset.yaml
apiVersion: apps/v1
kind: DaemonSet
metadata:
  name: fluent-bit
  namespace: deployforge
  labels:
    app: fluent-bit
spec:
  selector:
    matchLabels:
      app: fluent-bit
  template:
    metadata:
      labels:
        app: fluent-bit
    spec:
      serviceAccountName: fluent-bit
      tolerations:
      - key: node-role.kubernetes.io/control-plane
        operator: Exists
        effect: NoSchedule
      containers:
      - name: fluent-bit
        image: fluent/fluent-bit:2.2
        volumeMounts:
        - name: varlog
          mountPath: /var/log
          readOnly: true
        - name: config
          mountPath: /fluent-bit/etc/
        resources:
          requests:
            cpu: 50m
            memory: 64Mi
          limits:
            cpu: 200m
            memory: 128Mi
      volumes:
      - name: varlog
        hostPath:
          path: /var/log
      - name: config
        configMap:
          name: fluent-bit-config
```

---

### Loki and LogQL

Loki is a log aggregation system designed to work with Grafana. Unlike Elasticsearch, it indexes only labels (not full text), making it far cheaper to run. You query with **LogQL**.

```promql
# Stream selector — filter by labels
{namespace="deployforge", pod=~"api-gateway-.*"}

# Line filter — grep within matched streams
{namespace="deployforge"} |= "error"
{namespace="deployforge"} != "healthcheck"
{namespace="deployforge"} |~ "timeout|connection refused"

# JSON parsing — extract fields from JSON logs
{namespace="deployforge"} | json | level="error"
{namespace="deployforge"} | json | http_status_code >= 500

# Metric queries — turn logs into metrics
# Error rate from logs
sum(rate({namespace="deployforge"} | json | level="error" [5m]))

# Request count by status code
sum by (http_status_code) (
  count_over_time({namespace="deployforge", container="api-gateway"} | json | http_status_code != "" [5m])
)
```

> **Caution:** Loki is not a replacement for Prometheus metrics. Use Loki metric queries for ad-hoc investigation, not for alerting or dashboards. Prometheus is purpose-built for time-series aggregation; Loki metric queries are orders of magnitude slower.

---

### Avoiding Log Spam

Common anti-patterns that turn your logging into noise:

| Anti-Pattern | Problem | Fix |
|-------------|---------|-----|
| Logging inside tight loops | Millions of lines for one operation | Log at loop boundaries with aggregates |
| Logging request/response bodies | PII exposure, enormous volume | Log body size, content-type, and a truncated preview |
| Catch + log + rethrow | Same error logged at every layer | Log at the boundary, propagate errors without re-logging |
| Logging health checks | 50% of log volume is `/healthz` | Exclude health check paths in logging middleware |
| Missing log levels | Everything at `info` | Use levels correctly; `debug` for diagnostics, `error` for failures |
| Unbounded field values | Log queries become slow | Don't log full SQL queries or request bodies as indexed fields |

```typescript
// BAD — logging inside a loop
for (const item of items) {
  logger.info(`Processing item ${item.id}`);  // ✗ 10,000 log lines
  await process(item);
}

// GOOD — log at boundaries with aggregates
logger.info({ msg: 'batch_started', item_count: items.length });
let processed = 0, failed = 0;
for (const item of items) {
  try {
    await process(item);
    processed++;
  } catch (error) {
    failed++;
    // Only log individual failures — these are actionable
    logger.error({ msg: 'item_failed', item_id: item.id, error: (error as Error).message });
  }
}
logger.info({ msg: 'batch_completed', processed, failed, total: items.length });
```

---

### Log-Based Alerting

While Prometheus is the primary alerting source, some conditions are only visible in logs (e.g., specific error messages, security events). Loki supports alerting via its Ruler component:

```yaml
# loki-alert-rules.yaml
apiVersion: monitoring.coreos.com/v1
kind: PrometheusRule
metadata:
  name: deployforge-log-alerts
  namespace: deployforge
  labels:
    release: prometheus
spec:
  groups:
  - name: deployforge.log-alerts
    rules:
    - alert: HighErrorLogRate
      expr: |
        sum(rate({namespace="deployforge"} | json | level="error" [5m])) > 10
      for: 5m
      labels:
        severity: warning
      annotations:
        summary: "High error log rate in deployforge namespace"
        description: "More than 10 error logs per second for 5+ minutes."

    - alert: AuthenticationFailures
      expr: |
        sum(rate({namespace="deployforge", container="api-gateway"} |= "authentication_failed" [5m])) > 1
      for: 2m
      labels:
        severity: critical
      annotations:
        summary: "Authentication failure spike detected"
        description: "More than 1 auth failure per second. Possible brute force attack."
```

---

## Code Examples

### Complete Logging Setup for DeployForge

```bash
#!/bin/bash
set -euo pipefail

NS="deployforge"

echo "=== Step 1: Deploy Loki ==="
helm repo add grafana https://grafana.github.io/helm-charts
helm repo update

helm install loki grafana/loki-stack \
  --namespace deployforge \
  --set loki.persistence.enabled=false \
  --set promtail.enabled=false \
  --set grafana.enabled=false \
  --wait

echo "=== Step 2: Deploy Fluent Bit ==="
# Create service account
cat <<'EOF' | kubectl apply -f -
apiVersion: v1
kind: ServiceAccount
metadata:
  name: fluent-bit
  namespace: deployforge
---
apiVersion: rbac.authorization.k8s.io/v1
kind: ClusterRole
metadata:
  name: fluent-bit
rules:
- apiGroups: [""]
  resources: ["namespaces", "pods"]
  verbs: ["get", "list", "watch"]
---
apiVersion: rbac.authorization.k8s.io/v1
kind: ClusterRoleBinding
metadata:
  name: fluent-bit
roleRef:
  apiGroup: rbac.authorization.k8s.io
  kind: ClusterRole
  name: fluent-bit
subjects:
- kind: ServiceAccount
  name: fluent-bit
  namespace: deployforge
EOF

# Apply Fluent Bit ConfigMap and DaemonSet (from Deep Dive section)
kubectl apply -f fluent-bit-config.yaml
kubectl apply -f fluent-bit-daemonset.yaml

echo "=== Step 3: Verify ==="
kubectl wait --for=condition=Ready pod -l app=fluent-bit -n $NS --timeout=120s
kubectl get pods -n $NS -l app=fluent-bit
# → One fluent-bit pod per node, all Running

echo "=== Step 4: Test log flow ==="
# Generate a test log
kubectl run log-test --namespace=$NS --image=busybox:1.36 \
  --restart=Never -- sh -c 'echo "{\"level\":\"info\",\"msg\":\"test log entry\",\"service\":\"test\"}" && sleep 5'

# Wait, then query Loki
sleep 10
kubectl port-forward -n $NS svc/loki 3100:3100 &
sleep 2
curl -s 'http://localhost:3100/loki/api/v1/query_range' \
  --data-urlencode 'query={namespace="deployforge"}' \
  --data-urlencode 'limit=5' | jq '.data.result[].values[][1]'

kubectl delete pod log-test -n $NS --grace-period=0
```

---

## Try It Yourself

### Challenge 1: Implement a Structured Logger

Create a Pino-based logger for DeployForge that: outputs JSON, includes service name and version, redacts sensitive fields, and creates child loggers with request_id and trace_id.

<details>
<summary>Show solution</summary>

```typescript
// src/logger.ts
import pino from 'pino';
import { trace, context } from '@opentelemetry/api';

export const logger = pino({
  level: process.env.LOG_LEVEL || 'info',
  formatters: {
    level(label) {
      return { level: label };
    },
  },
  base: {
    service: process.env.SERVICE_NAME || 'api-gateway',
    version: process.env.npm_package_version || '0.0.0',
    env: process.env.NODE_ENV || 'development',
  },
  timestamp: pino.stdTimeFunctions.isoTime,
  redact: {
    paths: [
      'req.headers.authorization',
      'req.headers.cookie',
      'password',
      'token',
      'secret',
      'creditCard',
    ],
    censor: '[REDACTED]',
  },
});

export function createRequestLogger(requestId: string): pino.Logger {
  const span = trace.getSpan(context.active());
  const spanCtx = span?.spanContext();

  return logger.child({
    request_id: requestId,
    ...(spanCtx && {
      trace_id: spanCtx.traceId,
      span_id: spanCtx.spanId,
    }),
  });
}

// Usage in route handler:
// app.get('/api/deployments', (req, res) => {
//   req.log.info({ msg: 'listing_deployments', query: req.query });
//   ...
// });
```

</details>

### Challenge 2: Write LogQL Queries

Write LogQL queries that answer:
1. Show all error logs from the worker service in the last hour
2. Count errors per service per 5-minute window
3. Find all logs for a specific request_id across all services

<details>
<summary>Show solution</summary>

```promql
# 1. Error logs from worker service (last hour)
{namespace="deployforge", container="worker"} | json | level="error"

# 2. Error count per service per 5-minute window
sum by (service) (
  count_over_time(
    {namespace="deployforge"} | json | level="error" [5m]
  )
)

# 3. All logs for a specific request_id (cross-service)
{namespace="deployforge"} | json | request_id="req-789xyz"

# Bonus: combine with line filter for faster queries
{namespace="deployforge"} |= "req-789xyz" | json | request_id="req-789xyz"
# The |= pre-filter is faster because Loki checks the raw line before JSON parsing
```

</details>

### Challenge 3: Build a Log-to-Trace Correlation Flow

Deploy a test pod that generates structured JSON logs with trace_id fields. Query those logs in Loki and show how you'd configure Grafana to link trace_id fields to Jaeger.

<details>
<summary>Show solution</summary>

```bash
#!/bin/bash
set -euo pipefail

NS="deployforge"

echo "=== Step 1: Generate correlated logs ==="
cat <<'EOF' | kubectl apply -f -
apiVersion: v1
kind: Pod
metadata:
  name: log-trace-demo
  namespace: deployforge
spec:
  containers:
  - name: demo
    image: busybox:1.36
    command: ['sh', '-c']
    args:
    - |
      TRACE_ID="a1b2c3d4e5f6a7b8c9d0e1f2a3b4c5d6"
      SPAN_ID="1234567890abcdef"
      REQ_ID="req-demo-$(date +%s)"

      echo "{\"level\":\"info\",\"msg\":\"request_started\",\"service\":\"api-gateway\",\"request_id\":\"$REQ_ID\",\"trace_id\":\"$TRACE_ID\",\"span_id\":\"$SPAN_ID\",\"http\":{\"method\":\"POST\",\"url\":\"/api/deployments\"}}"
      sleep 1
      echo "{\"level\":\"info\",\"msg\":\"db_query\",\"service\":\"api-gateway\",\"request_id\":\"$REQ_ID\",\"trace_id\":\"$TRACE_ID\",\"span_id\":\"$SPAN_ID\",\"db\":{\"system\":\"postgresql\",\"duration_ms\":15}}"
      sleep 1
      echo "{\"level\":\"info\",\"msg\":\"job_enqueued\",\"service\":\"api-gateway\",\"request_id\":\"$REQ_ID\",\"trace_id\":\"$TRACE_ID\",\"span_id\":\"$SPAN_ID\",\"job\":{\"queue\":\"deploy\",\"id\":\"job-001\"}}"
      sleep 1
      echo "{\"level\":\"info\",\"msg\":\"request_completed\",\"service\":\"api-gateway\",\"request_id\":\"$REQ_ID\",\"trace_id\":\"$TRACE_ID\",\"span_id\":\"$SPAN_ID\",\"http\":{\"status_code\":201,\"duration_ms\":450}}"
      sleep 300
  restartPolicy: Never
EOF

kubectl wait --for=condition=Ready pod/log-trace-demo -n $NS --timeout=60s

echo "=== Step 2: Query logs by trace_id ==="
sleep 15  # wait for Fluent Bit to ship logs
kubectl port-forward -n $NS svc/loki 3100:3100 &
sleep 2

TRACE_ID="a1b2c3d4e5f6a7b8c9d0e1f2a3b4c5d6"
curl -s "http://localhost:3100/loki/api/v1/query_range" \
  --data-urlencode "query={namespace=\"deployforge\"} |= \"$TRACE_ID\"" \
  --data-urlencode "limit=10" | jq '.data.result[].values[][1]'

echo ""
echo "=== Step 3: Grafana derived field config ==="
echo "In Grafana → Configuration → Data Sources → Loki → Derived Fields:"
echo "  Name: TraceID"
echo "  Regex: \"trace_id\":\"([a-f0-9]+)\""
echo "  Query: \${__value.raw}"
echo "  Internal Link: Jaeger datasource"
echo ""
echo "This creates a clickable link from any trace_id in log lines"
echo "directly to the Jaeger trace view."

echo ""
echo "=== Cleanup ==="
kubectl delete pod log-trace-demo -n $NS --grace-period=0
```

</details>

---

## Capstone Connection

**DeployForge** uses structured logging as the third observability pillar alongside metrics and traces:

- **Pino JSON logger** — All services use Pino configured with JSON output, service metadata, and field redaction. The logger is created once at startup and child loggers (with request_id and trace_id) are attached to each request via middleware.
- **Correlation ID propagation** — The API Gateway generates an `X-Request-ID` header (or forwards one from the client). This ID is included in all log entries and propagated to the Worker Service via BullMQ job metadata. Combined with `trace_id`, any request can be traced across all services in seconds.
- **Fluent Bit DaemonSet** — Runs on every node in the kind cluster, tailing container log files. The Kubernetes metadata filter enriches logs with pod name, namespace, and labels. A namespace filter ensures only `deployforge` logs reach Loki.
- **Loki storage** — Stores logs indexed by labels (`namespace`, `pod`, `container`, `level`, `service`). In Module 08.4, Grafana dashboards query Loki alongside Prometheus for unified observability.
- **Trace-to-log linking** — Grafana's Loki derived fields are configured to detect `trace_id` values and create clickable links to Jaeger. This enables the "observe a latency spike → find the trace → read the logs" workflow that turns hours of debugging into minutes.
- **Log-based alerting** — Loki Ruler alerts fire on patterns invisible to metrics: specific error messages, authentication failures, and security events. These complement the PrometheusRule alerts from Module 08.1.
