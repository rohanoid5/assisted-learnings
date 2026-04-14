# 8.2 — Distributed Tracing with OpenTelemetry

## Concept

Metrics tell you _what_ is broken. Traces tell you _where_ and _why_. When a user's request traverses the API Gateway, hits PostgreSQL, enqueues a job in Redis, and gets processed by the Worker Service, a single metric like "p99 latency = 3s" doesn't tell you which hop is slow. Distributed tracing solves this by following a request end-to-end across service boundaries, recording the timing and metadata of every operation along the way.

**OpenTelemetry** (OTel) is the CNCF standard for instrumentation — a vendor-neutral SDK that produces traces, metrics, and logs. It replaces the earlier OpenTracing and OpenCensus projects. You instrument once with OpenTelemetry and send data to any backend: Jaeger, Tempo, Zipkin, Datadog, or Honeycomb. This section covers the trace data model, SDK instrumentation in Node.js/TypeScript, context propagation, sampling strategies, and backend deployment.

---

## Deep Dive

### The Trace Data Model

A trace is a tree of **spans**. Each span represents a unit of work — an HTTP request, a database query, a queue publish/consume — and carries timing information, metadata, and a parent-child relationship.

```
┌─────────────────────────────────────────────────────────────────────┐
│                    Distributed Trace Anatomy                         │
│                                                                     │
│  Trace ID: abc123def456                                             │
│                                                                     │
│  ┌─── Span A: POST /api/deployments (API Gateway) ──────────────┐  │
│  │  trace_id: abc123def456                                       │  │
│  │  span_id:  span_001                                           │  │
│  │  parent:   (none — root span)                                 │  │
│  │  start:    14:32:01.000    end: 14:32:01.450                  │  │
│  │  duration: 450ms                                              │  │
│  │  attributes: http.method=POST, http.url=/api/deployments      │  │
│  │                                                                │  │
│  │  ┌─── Span B: SELECT * FROM projects (PostgreSQL) ──────┐    │  │
│  │  │  span_id:  span_002                                   │    │  │
│  │  │  parent:   span_001                                   │    │  │
│  │  │  start:    14:32:01.010    end: 14:32:01.025          │    │  │
│  │  │  duration: 15ms                                       │    │  │
│  │  │  attributes: db.system=postgresql, db.statement=SELECT│    │  │
│  │  └───────────────────────────────────────────────────────┘    │  │
│  │                                                                │  │
│  │  ┌─── Span C: PUBLISH deploy.queue (Redis/BullMQ) ──────┐    │  │
│  │  │  span_id:  span_003                                   │    │  │
│  │  │  parent:   span_001                                   │    │  │
│  │  │  start:    14:32:01.030    end: 14:32:01.035          │    │  │
│  │  │  duration: 5ms                                        │    │  │
│  │  │  attributes: messaging.system=redis                   │    │  │
│  │  └───────────────────────────────────────────────────────┘    │  │
│  │                                                                │  │
│  └────────────────────────────────────────────────────────────────┘  │
│                                                                     │
│  ┌─── Span D: PROCESS deploy.queue (Worker Service) ────────────┐  │
│  │  span_id:  span_004                                           │  │
│  │  parent:   span_003   ← linked across service boundary        │  │
│  │  start:    14:32:01.040    end: 14:32:01.440                  │  │
│  │  duration: 400ms                                              │  │
│  │                                                                │  │
│  │  ┌─── Span E: docker build (shell exec) ────────────────┐    │  │
│  │  │  duration: 350ms                                      │    │  │
│  │  └──────────────────────────────────────────────────────┘    │  │
│  │                                                                │  │
│  │  ┌─── Span F: UPDATE deployments SET status (PostgreSQL) ┐    │  │
│  │  │  duration: 8ms                                         │    │  │
│  │  └───────────────────────────────────────────────────────┘    │  │
│  └────────────────────────────────────────────────────────────────┘  │
│                                                                     │
│  Timeline:                                                          │
│  |──────── Span A (450ms) ──────────────────────────────|           │
│  | B (15ms) |                                            |           │
│  |           | C (5ms) |                                 |           │
│  |                      |──── Span D (400ms) ──────────|           │
│  |                      | E (350ms)            |F(8ms)|           │
└─────────────────────────────────────────────────────────────────────┘
```

**Key terms:**

- **Trace** — A collection of spans sharing a `trace_id`. Represents one end-to-end operation.
- **Span** — A single unit of work with a name, start/end time, attributes, events, and status.
- **Context** — The `trace_id` + `span_id` + trace flags that propagate across boundaries.
- **Attributes** — Key-value metadata on a span (e.g., `http.method`, `db.statement`).
- **Events** — Timestamped log entries within a span (e.g., "cache miss", "retry attempt").
- **Status** — OK, ERROR, or UNSET. Only set ERROR explicitly on failure.
- **Links** — Connect spans from different traces (e.g., batch processing).

---

### Context Propagation

For traces to work across services, the trace context must propagate through network boundaries. The **W3C TraceContext** standard defines HTTP headers for this:

```
traceparent: 00-<trace_id>-<parent_span_id>-<trace_flags>
tracestate: <vendor-specific key=value pairs>
```

Example:
```
traceparent: 00-abc123def456789012345678abcdef00-span001234567890-01
tracestate: deployforge=sampled
```

```
┌─────────────────────────────────────────────────────────────────────┐
│                  Context Propagation Flow                             │
│                                                                     │
│  ┌─────────────┐  HTTP + traceparent  ┌─────────────┐              │
│  │ API Gateway  │───────────────────▶│ Worker Svc   │              │
│  │              │    header           │              │              │
│  │ OTel SDK     │                     │ OTel SDK     │              │
│  │ creates root │                     │ extracts     │              │
│  │ span + ctx   │                     │ parent ctx   │              │
│  └──────┬───────┘                     └──────┬───────┘              │
│         │                                     │                     │
│         │ inject ctx                          │ extract ctx         │
│         │ into headers                        │ from headers        │
│         ▼                                     ▼                     │
│  ┌──────────────┐                     ┌──────────────┐              │
│  │ Outgoing HTTP │                     │ Incoming HTTP │              │
│  │ traceparent:  │                     │ traceparent:  │              │
│  │ 00-abc...-01  │                     │ 00-abc...-01  │              │
│  └──────────────┘                     └──────────────┘              │
│                                                                     │
│  For message queues (BullMQ/Redis):                                 │
│  Context is serialized into the job payload metadata                │
│  rather than HTTP headers.                                          │
└─────────────────────────────────────────────────────────────────────┘
```

> **Key insight:** Context propagation is what separates "we have tracing" from "we have _distributed_ tracing." Without it, you get isolated span islands per service with no way to correlate them.

---

### OpenTelemetry SDK Setup (Node.js/TypeScript)

The OTel SDK should be initialized _before_ any other imports so auto-instrumentation can patch libraries.

```typescript
// src/instrumentation.ts — MUST be loaded before all other imports
import { NodeSDK } from '@opentelemetry/sdk-node';
import { OTLPTraceExporter } from '@opentelemetry/exporter-trace-otlp-http';
import { OTLPMetricExporter } from '@opentelemetry/exporter-metrics-otlp-http';
import { PeriodicExportingMetricReader } from '@opentelemetry/sdk-metrics';
import { getNodeAutoInstrumentations } from '@opentelemetry/auto-instrumentations-node';
import { Resource } from '@opentelemetry/resources';
import {
  ATTR_SERVICE_NAME,
  ATTR_SERVICE_VERSION,
  ATTR_DEPLOYMENT_ENVIRONMENT_NAME,
} from '@opentelemetry/semantic-conventions';

const sdk = new NodeSDK({
  resource: new Resource({
    [ATTR_SERVICE_NAME]: process.env.OTEL_SERVICE_NAME || 'api-gateway',
    [ATTR_SERVICE_VERSION]: process.env.npm_package_version || '0.0.0',
    [ATTR_DEPLOYMENT_ENVIRONMENT_NAME]: process.env.NODE_ENV || 'development',
  }),

  // Trace exporter — sends spans to OTel Collector or Jaeger
  traceExporter: new OTLPTraceExporter({
    url: process.env.OTEL_EXPORTER_OTLP_TRACES_ENDPOINT || 'http://otel-collector:4318/v1/traces',
  }),

  // Metric exporter — sends metrics to OTel Collector
  metricReader: new PeriodicExportingMetricReader({
    exporter: new OTLPMetricExporter({
      url: process.env.OTEL_EXPORTER_OTLP_METRICS_ENDPOINT || 'http://otel-collector:4318/v1/metrics',
    }),
    exportIntervalMillis: 15000,
  }),

  // Auto-instrumentation: HTTP, Express, pg, ioredis, BullMQ, dns, etc.
  instrumentations: [
    getNodeAutoInstrumentations({
      '@opentelemetry/instrumentation-http': {
        ignoreIncomingPaths: ['/healthz', '/readyz', '/metrics'],
      },
      '@opentelemetry/instrumentation-express': { enabled: true },
      '@opentelemetry/instrumentation-pg': { enabled: true },
      '@opentelemetry/instrumentation-ioredis': { enabled: true },
    }),
  ],
});

sdk.start();

// Graceful shutdown
process.on('SIGTERM', () => {
  sdk.shutdown().then(() => process.exit(0));
});
```

```json
// package.json — load instrumentation before app
{
  "scripts": {
    "start": "node --require ./dist/instrumentation.js ./dist/index.js",
    "dev": "tsx --require ./src/instrumentation.ts ./src/index.ts"
  }
}
```

> **Warning:** The instrumentation file must be loaded first via `--require` (or `--import` for ESM). If you import it inside `index.ts`, auto-instrumentation will miss libraries that were imported before it.

---

### Auto-Instrumentation vs Manual Spans

**Auto-instrumentation** patches popular libraries (Express, pg, ioredis, http) to create spans automatically. You get HTTP request spans, database query spans, and Redis command spans with zero code changes.

**Manual spans** capture business logic that auto-instrumentation can't see: "validate deployment config," "build Docker image," "run health checks."

```typescript
// src/services/deployment.ts — manual spans for business logic
import { trace, SpanStatusCode, SpanKind } from '@opentelemetry/api';

const tracer = trace.getTracer('deployforge-api-gateway');

export async function createDeployment(config: DeploymentConfig): Promise<Deployment> {
  return tracer.startActiveSpan('createDeployment', { kind: SpanKind.INTERNAL }, async (span) => {
    try {
      // Add business context as attributes
      span.setAttribute('deployment.project_id', config.projectId);
      span.setAttribute('deployment.environment', config.environment);
      span.setAttribute('deployment.strategy', config.strategy);

      // Validation — child span for timing
      const validated = await tracer.startActiveSpan('validateConfig', async (validateSpan) => {
        const result = await validateDeploymentConfig(config);
        validateSpan.setAttribute('validation.rules_checked', result.rulesChecked);
        if (!result.valid) {
          validateSpan.setStatus({ code: SpanStatusCode.ERROR, message: result.error });
          validateSpan.addEvent('validation_failed', { 'error.message': result.error });
        }
        validateSpan.end();
        return result;
      });

      if (!validated.valid) {
        throw new Error(`Validation failed: ${validated.error}`);
      }

      // Enqueue the deployment job — auto-instrumented by BullMQ plugin
      const job = await deployQueue.add('deploy', {
        config,
        requestedBy: config.requestedBy,
      });

      span.setAttribute('deployment.job_id', job.id!);
      span.addEvent('job_enqueued', { 'job.queue': 'deploy', 'job.id': job.id! });

      const deployment = await saveDeployment(config, job.id!);
      span.setStatus({ code: SpanStatusCode.OK });
      return deployment;

    } catch (error) {
      span.setStatus({ code: SpanStatusCode.ERROR, message: (error as Error).message });
      span.recordException(error as Error);
      throw error;
    } finally {
      span.end();
    }
  });
}
```

> **Production note:** Favor auto-instrumentation for infrastructure spans (HTTP, DB, cache) and add manual spans only for business-significant operations. Over-instrumenting makes traces noisy and expensive.

---

### Sampling Strategies

At scale, tracing every request is prohibitively expensive. Sampling reduces volume while preserving signal.

| Strategy | How It Works | When to Use |
|----------|-------------|-------------|
| **AlwaysOn** | Record every trace | Development, low-traffic services |
| **AlwaysOff** | Record nothing | Disable tracing per-service |
| **TraceIdRatio** | Sample X% of traces by trace ID hash | High-traffic services (1-10%) |
| **ParentBased** | Follow the parent's sampling decision | Cross-service consistency |
| **Tail-based** | Decide after the trace completes | Capture all errors + sample normal |

```typescript
// Sampling configuration examples
import { ParentBasedSampler, TraceIdRatioBasedSampler, AlwaysOnSampler } from '@opentelemetry/sdk-trace-base';

// Production: sample 10% of traces, but always sample if parent says so
const sampler = new ParentBasedSampler({
  root: new TraceIdRatioBasedSampler(0.1),  // 10% of root spans
});

// Development: sample everything
const devSampler = new AlwaysOnSampler();
```

> **Key insight:** Always use `ParentBasedSampler` as the wrapper. Without it, Service B might drop a trace that Service A decided to keep, creating gaps in your trace tree.

---

### Deploying the OTel Collector and Jaeger

The **OpenTelemetry Collector** is a vendor-neutral proxy that receives, processes, and exports telemetry data. It decouples your application from the backend — you can switch from Jaeger to Tempo without changing application code.

```
┌─────────────────────────────────────────────────────────────────────┐
│                  OTel Collector Pipeline                              │
│                                                                     │
│  ┌─────────────┐     ┌──────────────────────────────────────────┐  │
│  │ API Gateway  │────▶│            OTel Collector                 │  │
│  │ (OTLP)       │     │                                          │  │
│  └─────────────┘     │  Receivers    Processors    Exporters    │  │
│  ┌─────────────┐     │  ┌────────┐  ┌──────────┐  ┌─────────┐  │  │
│  │ Worker Svc   │────▶│  │ OTLP   │─▶│ Batch    │─▶│ Jaeger  │  │  │
│  │ (OTLP)       │     │  │ gRPC   │  │ (buffer) │  │ (OTLP)  │  │  │
│  └─────────────┘     │  │ HTTP   │  │          │  │         │  │  │
│                       │  └────────┘  │ Memory   │  ├─────────┤  │  │
│                       │              │ limiter  │  │ Prom    │  │  │
│                       │              └──────────┘  │ (remote │  │  │
│                       │                            │  write)  │  │  │
│                       │                            └─────────┘  │  │
│                       └──────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────────────────┘
```

```yaml
# otel-collector-config.yaml
apiVersion: v1
kind: ConfigMap
metadata:
  name: otel-collector-config
  namespace: deployforge
data:
  config.yaml: |
    receivers:
      otlp:
        protocols:
          grpc:
            endpoint: 0.0.0.0:4317
          http:
            endpoint: 0.0.0.0:4318

    processors:
      batch:
        timeout: 5s
        send_batch_size: 1024
      memory_limiter:
        check_interval: 1s
        limit_mib: 512
        spike_limit_mib: 128

    exporters:
      otlp/jaeger:
        endpoint: jaeger-collector:4317
        tls:
          insecure: true
      prometheus:
        endpoint: 0.0.0.0:8889

    service:
      pipelines:
        traces:
          receivers: [otlp]
          processors: [memory_limiter, batch]
          exporters: [otlp/jaeger]
        metrics:
          receivers: [otlp]
          processors: [memory_limiter, batch]
          exporters: [prometheus]
```

---

### Trace-to-Log Correlation

The power of observability multiplies when pillars connect. By injecting `trace_id` and `span_id` into your structured logs, you can jump from a slow trace directly to its associated log entries:

```typescript
// src/logger.ts — inject trace context into every log line
import { trace, context } from '@opentelemetry/api';
import pino from 'pino';

const baseLogger = pino({ level: process.env.LOG_LEVEL || 'info' });

export function getLogger(module: string) {
  return new Proxy(baseLogger.child({ module }), {
    get(target, prop) {
      if (typeof target[prop as keyof typeof target] === 'function') {
        return (...args: unknown[]) => {
          const span = trace.getSpan(context.active());
          const spanContext = span?.spanContext();

          const traceFields = spanContext ? {
            trace_id: spanContext.traceId,
            span_id: spanContext.spanId,
            trace_flags: spanContext.traceFlags,
          } : {};

          // Merge trace context into the log entry
          if (typeof args[0] === 'object' && args[0] !== null) {
            args[0] = { ...args[0] as object, ...traceFields };
          } else {
            args.unshift(traceFields);
          }

          return (target[prop as keyof typeof target] as Function)(...args);
        };
      }
      return target[prop as keyof typeof target];
    },
  });
}
```

> **Key insight:** In Grafana, you can configure Loki to link `trace_id` values directly to Jaeger/Tempo. One click takes you from a log line to the full distributed trace. This is the "observability holy grail."

---

## Code Examples

### Deploying Jaeger and OTel Collector

```bash
#!/bin/bash
set -euo pipefail

NS="deployforge"

echo "=== Step 1: Deploy Jaeger (all-in-one for development) ==="
cat <<'EOF' | kubectl apply -f -
apiVersion: apps/v1
kind: Deployment
metadata:
  name: jaeger
  namespace: deployforge
spec:
  replicas: 1
  selector:
    matchLabels:
      app: jaeger
  template:
    metadata:
      labels:
        app: jaeger
    spec:
      containers:
      - name: jaeger
        image: jaegertracing/all-in-one:1.53
        ports:
        - containerPort: 16686   # UI
          name: ui
        - containerPort: 4317    # OTLP gRPC
          name: otlp-grpc
        - containerPort: 4318    # OTLP HTTP
          name: otlp-http
        env:
        - name: COLLECTOR_OTLP_ENABLED
          value: "true"
        resources:
          requests:
            memory: 256Mi
            cpu: 100m
          limits:
            memory: 512Mi
            cpu: 500m
---
apiVersion: v1
kind: Service
metadata:
  name: jaeger-collector
  namespace: deployforge
spec:
  selector:
    app: jaeger
  ports:
  - name: otlp-grpc
    port: 4317
    targetPort: 4317
  - name: otlp-http
    port: 4318
    targetPort: 4318
---
apiVersion: v1
kind: Service
metadata:
  name: jaeger-query
  namespace: deployforge
spec:
  selector:
    app: jaeger
  ports:
  - name: ui
    port: 16686
    targetPort: 16686
EOF

echo "=== Step 2: Deploy OTel Collector ==="
kubectl apply -f - <<'EOF'
apiVersion: v1
kind: ConfigMap
metadata:
  name: otel-collector-config
  namespace: deployforge
data:
  config.yaml: |
    receivers:
      otlp:
        protocols:
          grpc:
            endpoint: 0.0.0.0:4317
          http:
            endpoint: 0.0.0.0:4318
    processors:
      batch:
        timeout: 5s
        send_batch_size: 1024
      memory_limiter:
        check_interval: 1s
        limit_mib: 512
        spike_limit_mib: 128
    exporters:
      otlp/jaeger:
        endpoint: jaeger-collector:4317
        tls:
          insecure: true
      prometheus:
        endpoint: 0.0.0.0:8889
    service:
      pipelines:
        traces:
          receivers: [otlp]
          processors: [memory_limiter, batch]
          exporters: [otlp/jaeger]
        metrics:
          receivers: [otlp]
          processors: [memory_limiter, batch]
          exporters: [prometheus]
---
apiVersion: apps/v1
kind: Deployment
metadata:
  name: otel-collector
  namespace: deployforge
spec:
  replicas: 1
  selector:
    matchLabels:
      app: otel-collector
  template:
    metadata:
      labels:
        app: otel-collector
    spec:
      containers:
      - name: collector
        image: otel/opentelemetry-collector-contrib:0.92.0
        args: ["--config=/conf/config.yaml"]
        ports:
        - containerPort: 4317
          name: otlp-grpc
        - containerPort: 4318
          name: otlp-http
        - containerPort: 8889
          name: prometheus
        volumeMounts:
        - name: config
          mountPath: /conf
        resources:
          requests:
            memory: 256Mi
            cpu: 100m
          limits:
            memory: 512Mi
            cpu: 500m
      volumes:
      - name: config
        configMap:
          name: otel-collector-config
---
apiVersion: v1
kind: Service
metadata:
  name: otel-collector
  namespace: deployforge
spec:
  selector:
    app: otel-collector
  ports:
  - name: otlp-grpc
    port: 4317
    targetPort: 4317
  - name: otlp-http
    port: 4318
    targetPort: 4318
  - name: prometheus
    port: 8889
    targetPort: 8889
EOF

echo "=== Step 3: Verify ==="
kubectl wait --for=condition=Ready pod -l app=jaeger -n $NS --timeout=120s
kubectl wait --for=condition=Ready pod -l app=otel-collector -n $NS --timeout=120s

echo "Jaeger UI: kubectl port-forward -n deployforge svc/jaeger-query 16686:16686"
echo "Then open http://localhost:16686"
```

---

## Try It Yourself

### Challenge 1: Add a Manual Span to a Service

Write a function that wraps a "deploy container" operation with a manual span. The span should include attributes for project ID, image tag, and environment. On failure, the span should record the exception and set error status.

<details>
<summary>Show solution</summary>

```typescript
// src/services/container-deploy.ts
import { trace, SpanStatusCode, SpanKind } from '@opentelemetry/api';

const tracer = trace.getTracer('deployforge-worker');

interface DeployParams {
  projectId: string;
  imageTag: string;
  environment: string;
}

export async function deployContainer(params: DeployParams): Promise<void> {
  return tracer.startActiveSpan(
    'deployContainer',
    {
      kind: SpanKind.INTERNAL,
      attributes: {
        'deployment.project_id': params.projectId,
        'deployment.image_tag': params.imageTag,
        'deployment.environment': params.environment,
      },
    },
    async (span) => {
      try {
        span.addEvent('pulling_image', { 'image.tag': params.imageTag });

        // Simulate image pull
        await pullImage(params.imageTag);
        span.addEvent('image_pulled');

        // Simulate container start
        await startContainer(params);
        span.addEvent('container_started');

        // Health check
        const healthy = await waitForHealthy(params);
        span.setAttribute('deployment.healthy', healthy);

        if (!healthy) {
          throw new Error(`Container failed health check: ${params.imageTag}`);
        }

        span.setStatus({ code: SpanStatusCode.OK });
      } catch (error) {
        span.setStatus({
          code: SpanStatusCode.ERROR,
          message: (error as Error).message,
        });
        span.recordException(error as Error);
        throw error;
      } finally {
        span.end();
      }
    },
  );
}
```

</details>

### Challenge 2: Configure Sampling for Production

Write an OpenTelemetry SDK configuration that:
- Samples 5% of normal traffic
- Always samples if the parent trace was sampled
- Always samples traces that contain errors (hint: use tail-based sampling on the Collector)

<details>
<summary>Show solution</summary>

```typescript
// src/instrumentation.ts — application-side head sampling
import { NodeSDK } from '@opentelemetry/sdk-node';
import { ParentBasedSampler, TraceIdRatioBasedSampler } from '@opentelemetry/sdk-trace-base';
import { OTLPTraceExporter } from '@opentelemetry/exporter-trace-otlp-http';

const sdk = new NodeSDK({
  sampler: new ParentBasedSampler({
    root: new TraceIdRatioBasedSampler(0.05),  // 5% of root spans
    // Inherits parent decision for child spans
  }),
  traceExporter: new OTLPTraceExporter({
    url: 'http://otel-collector:4318/v1/traces',
  }),
});
sdk.start();
```

```yaml
# otel-collector-config.yaml — tail-based sampling on the Collector
# The collector buffers traces and decides after seeing all spans
apiVersion: v1
kind: ConfigMap
metadata:
  name: otel-collector-config
  namespace: deployforge
data:
  config.yaml: |
    receivers:
      otlp:
        protocols:
          grpc:
            endpoint: 0.0.0.0:4317
          http:
            endpoint: 0.0.0.0:4318

    processors:
      tail_sampling:
        decision_wait: 10s
        num_traces: 100000
        policies:
          # Always keep traces with errors
          - name: errors-policy
            type: status_code
            status_code:
              status_codes: [ERROR]
          # Always keep slow traces (>2s)
          - name: latency-policy
            type: latency
            latency:
              threshold_ms: 2000
          # Sample 5% of the rest
          - name: probabilistic-policy
            type: probabilistic
            probabilistic:
              sampling_percentage: 5
      batch:
        timeout: 5s

    exporters:
      otlp/jaeger:
        endpoint: jaeger-collector:4317
        tls:
          insecure: true

    service:
      pipelines:
        traces:
          receivers: [otlp]
          processors: [tail_sampling, batch]
          exporters: [otlp/jaeger]
```

</details>

### Challenge 3: Propagate Context Through BullMQ

BullMQ doesn't automatically propagate trace context. Write a helper that injects the current trace context into a BullMQ job's data and extracts it on the worker side.

<details>
<summary>Show solution</summary>

```typescript
// src/tracing/bullmq-propagation.ts
import { context, propagation, trace, SpanKind } from '@opentelemetry/api';
import { Queue, Worker, Job } from 'bullmq';

// Inject: serialize current trace context into job data
export function injectTraceContext<T extends Record<string, unknown>>(data: T): T & { _traceContext: Record<string, string> } {
  const carrier: Record<string, string> = {};
  propagation.inject(context.active(), carrier);
  return { ...data, _traceContext: carrier };
}

// Extract: deserialize trace context from job data and create a linked span
export function createWorkerSpan<T extends { _traceContext?: Record<string, string> }>(
  job: Job<T>,
  spanName: string,
): ReturnType<typeof trace.getTracer> extends infer Tracer ? ReturnType<Tracer['startActiveSpan']> : never {
  const tracer = trace.getTracer('deployforge-worker');
  const carrier = job.data._traceContext || {};

  // Extract the parent context from the job payload
  const parentContext = propagation.extract(context.active(), carrier);

  return context.with(parentContext, () => {
    return tracer.startActiveSpan(
      spanName,
      {
        kind: SpanKind.CONSUMER,
        attributes: {
          'messaging.system': 'bullmq',
          'messaging.operation': 'process',
          'messaging.destination': job.queueName,
          'job.id': job.id || 'unknown',
          'job.name': job.name,
          'job.attempt': job.attemptsMade,
        },
      },
      (span) => span,
    );
  });
}

// Usage — Producer side (API Gateway)
const deployQueue = new Queue('deploy', { connection: redisConnection });

async function enqueueDeployment(config: DeploymentConfig) {
  const tracedData = injectTraceContext({ config });
  await deployQueue.add('deploy', tracedData);
}

// Usage — Consumer side (Worker Service)
const worker = new Worker('deploy', async (job) => {
  const span = createWorkerSpan(job, 'process-deployment');
  try {
    await runDeployment(job.data.config);
    span.setStatus({ code: 0 });  // OK
  } catch (error) {
    span.recordException(error as Error);
    span.setStatus({ code: 2, message: (error as Error).message });  // ERROR
    throw error;
  } finally {
    span.end();
  }
}, { connection: redisConnection });
```

</details>

---

## Capstone Connection

**DeployForge** uses OpenTelemetry as the tracing backbone across all services:

- **API Gateway instrumentation** — The Express/TypeScript API Gateway loads `instrumentation.ts` via `--require` before any other imports. Auto-instrumentation covers HTTP, Express routes, pg queries, and ioredis commands. Manual spans wrap business logic like `createDeployment` and `validateConfig`.
- **Worker Service instrumentation** — The BullMQ Worker uses the same OTel SDK. A custom propagation helper injects trace context into job payloads so traces flow seamlessly from the API request through the job queue to the worker processing.
- **OTel Collector** — Deployed as a Deployment in the `deployforge` namespace, the Collector receives OTLP from all services and exports to Jaeger (traces) and Prometheus (metrics). This decouples applications from backend choice.
- **Jaeger backend** — Running in all-in-one mode for development. In production (Module 11), you'd switch to Grafana Tempo with S3 storage — a one-line change in the Collector config.
- **Sampling strategy** — Head-based `ParentBasedSampler` with 10% ratio for root spans in production, plus tail-based sampling on the Collector to capture 100% of error traces. Development uses `AlwaysOn`.
- **Trace-to-log correlation** — Every structured log entry includes `trace_id` and `span_id` from the active span context. In Module 08.4, Grafana links Loki logs to Jaeger traces for one-click correlation.
