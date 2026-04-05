# 7.2 — Service Communication

## Concept

Services communicate either synchronously (caller waits for response) or asynchronously (caller does not wait). Synchronous communication is simpler but creates temporal coupling — if the downstream service is slow or unavailable, the caller is affected directly. Asynchronous communication via message queues decouples availability at the cost of complexity.

---

## Deep Dive

### Synchronous: REST vs. gRPC

```
  REST (HTTP/JSON):
    + Human-readable, universally supported
    + Easy to test with curl/browser
    + Flexible schema evolution (additive changes backward compatible)
    - JSON parse overhead (vs. binary)
    - No built-in streaming, no bidirectional communication
    - Schema contract enforced only at runtime (unless using OpenAPI)
    
  gRPC (HTTP/2 + Protobuf):
    + 5-10x smaller payloads (binary Protobuf vs JSON)
    + Generated client/server stubs (contract enforced at build time)
    + Bidirectional streaming support
    + Multiplexed requests over single HTTP/2 connection
    - Browser support requires grpc-web proxy
    - Binary format: hard to inspect with curl
    - Service mesh required for advanced features
    
  Rule of thumb:
    External APIs (mobile, browser, third-party): REST
    Internal service-to-service (same network): gRPC or REST
    High-throughput internal calls (>10k/s): gRPC (latency + bandwidth savings)
    
  FlowForge uses REST for simplicity. ScaleForge internal calls are function
  calls (monolith) — no HTTP overhead at all.
```

### Synchronous: Request/Response Patterns

```
  Direct call (simple):
  
    Client ──► Service A ──► Service B ──► 200 OK
    
    Total latency: A_latency + B_latency
    Failure mode: B down → A down → Client sees 500
    
  Service with timeout + retry:
  
    Client ──► Service A ──[timeout: 5s]──► Service B
                         └──[retry 2x with backoff]──► Service B
                         
    Failure mode: B slow (>5s) → A times out → Client sees 503
                  A retries → risk of double-processing (if B is not idempotent)
    
  Service with circuit breaker:
  
    Client ──► Service A ──[circuit: CLOSED]──► Service B
    After N failures → circuit OPENS
    Client ──► Service A ──[circuit: OPEN]──► fast-fail 503
    (no requests reach B → B gets breathing room to recover)
```

### Asynchronous: When to Use It

```
  Choose async when:
  
  1. Response not immediately needed:
     "Send a notification" → user doesn't need notification ACK in the same request
     
  2. Receiver may be temporarily unavailable:
     Analytics service restarts → click events queue up → no events lost
     
  3. Fan-out to multiple receivers:
     URL updated → invalidate cache + update analytics + send webhook
     Sync: these three operations run sequentially (sum of latencies)
     Async: all three enqueued simultaneously, run in parallel
     
  4. Rate limiting downstream:
     Email provider: 100 emails/sec max
     Async queue with rate limiter: smooth traffic at 100/sec regardless of burst
     
  Choose sync when:
    - Immediate response required (user waiting for result)
    - Strong consistency required (read-after-write in same transaction)
    - Operation is fast and downstream is reliable (in-process function call)
```

---

## Code Examples

### REST Internal API with Type-Safe Client

```typescript
// src/clients/flowforge.client.ts — typed HTTP client for FlowForge API
// Used by ScaleForge to trigger notifications without coupling to FlowForge internals

import { z } from 'zod';
import pino from 'pino';

const log = pino({ name: 'flowforge-client' });

const SendNotificationSchema = z.object({
  channel: z.enum(['email', 'webhook']),
  recipient: z.string().min(1),
  subject: z.string().optional(),
  body: z.string().min(1),
});

type SendNotificationRequest = z.infer<typeof SendNotificationSchema>;

interface NotificationResponse {
  notificationId: string;
  jobId: string;
  status: 'queued';
}

export class FlowForgeClient {
  private readonly baseUrl: string;
  private readonly timeout: number;

  constructor(
    baseUrl = process.env.FLOWFORGE_URL ?? 'http://localhost:3002',
    timeout = 5000
  ) {
    this.baseUrl = baseUrl;
    this.timeout = timeout;
  }

  async sendNotification(req: SendNotificationRequest): Promise<NotificationResponse> {
    const parsed = SendNotificationSchema.parse(req); // validate before sending

    const controller = new AbortController();
    const timer = setTimeout(() => controller.abort(), this.timeout);

    try {
      const response = await fetch(`${this.baseUrl}/api/v1/notifications`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(parsed),
        signal: controller.signal,
      });

      if (!response.ok) {
        const errorBody = await response.text();
        throw new Error(`FlowForge error ${response.status}: ${errorBody}`);
      }

      return (await response.json()) as NotificationResponse;
    } catch (err) {
      if ((err as Error).name === 'AbortError') {
        throw new Error(`FlowForge request timed out after ${this.timeout}ms`);
      }
      log.error({ err }, 'FlowForge call failed');
      throw err;
    } finally {
      clearTimeout(timer);
    }
  }
}

export const flowForgeClient = new FlowForgeClient();
```

### Correlation IDs: Tracing Across Services

```typescript
// src/middleware/correlation-id.middleware.ts
// Every request gets a correlation ID that propagates to all downstream calls.
// This lets you grep logs across ScaleForge + FlowForge for one user action.

import type { Request, Response, NextFunction } from 'express';
import { randomUUID } from 'node:crypto';

// Extend Express Request to carry correlationId
declare global {
  namespace Express {
    interface Request {
      correlationId: string;
    }
  }
}

export function correlationIdMiddleware(req: Request, res: Response, next: NextFunction): void {
  // Accept from caller if present (for end-to-end tracing from browser/API gateway)
  req.correlationId = req.headers['x-correlation-id'] as string ?? randomUUID();
  res.setHeader('x-correlation-id', req.correlationId);
  next();
}

// Pass correlation ID to downstream calls
export function getDownstreamHeaders(req: Request): Record<string, string> {
  return {
    'x-correlation-id': req.correlationId,
    'x-forwarded-for': req.ip ?? '',
  };
}

// Usage in FlowForgeClient:
// await fetch(url, {
//   headers: {
//     ...getDownstreamHeaders(req),
//     'Content-Type': 'application/json',
//   }
// });
```

### Internal Service Contract with OpenAPI-Style Validation

```typescript
// src/internal/url-contract.ts
// Internal service contract: defines what ScaleForge's URL service accepts
// and returns. Both producer and consumer import this schema.

import { z } from 'zod';

// What ScaleForge publishes when a URL is updated
export const UrlUpdatedEventSchema = z.object({
  eventType: z.literal('url_updated'),
  code: z.string().min(1),
  newTarget: z.string().url(),
  updatedAt: z.string().datetime(),
  correlationId: z.string().uuid(),
});

export type UrlUpdatedEvent = z.infer<typeof UrlUpdatedEventSchema>;

// Publish to message queue (ScaleForge URL service)
export async function publishUrlUpdatedEvent(
  queue: Queue,
  event: UrlUpdatedEvent
): Promise<void> {
  const validated = UrlUpdatedEventSchema.parse(event);
  await queue.add('url_updated', validated, { jobId: `${event.code}-${event.updatedAt}` });
}

// Consume from message queue (any interested service)
export function validateUrlUpdatedEvent(payload: unknown): UrlUpdatedEvent {
  return UrlUpdatedEventSchema.parse(payload); // throws if schema mismatch
}

import type { Queue } from 'bullmq';
```

---

## Try It Yourself

**Exercise:** Implement cross-service request tracing.

```typescript
// tracing.exercise.ts

// TODO: 
// 1. Add correlationIdMiddleware to both ScaleForge and FlowForge Express apps
// 2. Modify FlowForgeClient to pass the correlation ID header in requests
// 3. In FlowForge, add a logger that includes correlationId in every log line
//    (use pino's child logger: const reqLog = log.child({ correlationId }))
//
// 4. Send a POST to ScaleForge /api/v1/urls  
//    Then trigger a URL update (PATCH) that calls FlowForge
//    Use the x-correlation-id header from the response to grep BOTH services' logs:
//
//    CORRELATION_ID="paste-id-here"
//    cat scaleforge.log | grep $CORRELATION_ID
//    cat flowforge.log  | grep $CORRELATION_ID
//
// 5. Verify the same correlation ID appears in both log streams
//    This is the foundation of distributed tracing.
```

<details>
<summary>Show middleware integration</summary>

```typescript
// In scaleforge/src/app.ts:
import { correlationIdMiddleware } from './middleware/correlation-id.middleware.js';

app.use(correlationIdMiddleware);

// In scaleforge logger setup, include correlation ID:
app.use((req, _res, next) => {
  req.log = log.child({ correlationId: req.correlationId });
  next();
});

// In flowforge/src/app.ts — read correlation ID from incoming request:
app.use((req, res, next) => {
  const correlationId = req.headers['x-correlation-id'] as string ?? randomUUID();
  req.correlationId = correlationId;
  res.setHeader('x-correlation-id', correlationId);
  req.log = log.child({ correlationId, service: 'flowforge' });
  next();
});
```

</details>

---

## Capstone Connection

Correlation IDs make ScaleForge and FlowForge behave like a single observable system. When a user reports "my notification didn't arrive", you grep the correlation ID from ScaleForge's URL-update log line — the same ID appears in FlowForge's delivery attempt log, including the failure reason. Without correlation IDs, tracing a request across services is a manual exercise in timestamp-based log archaeology.
