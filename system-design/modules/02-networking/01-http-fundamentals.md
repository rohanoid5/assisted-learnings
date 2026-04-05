# 2.1 — HTTP Fundamentals

## Concept

HTTP is the backbone of the web and most inter-service communication. Understanding its evolution from HTTP/1.1 to HTTP/2 and HTTP/3 explains why your API feels fast or slow, which headers control caching, and why connection management matters at scale.

---

## Deep Dive

### HTTP/1.1 — The Baseline

Released 1997. Still widely used. Key characteristics:

```
Client                         Server
  │──── TCP Handshake ──────────►│  (1.5 × RTT overhead)
  │──── GET /api/urls ──────────►│
  │◄─── 200 OK ─────────────────│
  │──── GET /api/user ──────────►│  (must wait for first response)
  │◄─── 200 OK ─────────────────│
  │──── GET /api/clicks ────────►│
  │◄─── 200 OK ─────────────────│
  
Problem: HEAD-OF-LINE BLOCKING
  Requests must be pipelined serially on a single connection.
  One slow response blocks all subsequent responses.
  
Workaround: browsers open 6 connections per domain.
  Cost: 6× TCP overhead, 6× memory on server.
```

**Persistent connections**: HTTP/1.1 introduced `Connection: keep-alive` to reuse connections, but still serial within a connection.

### HTTP/2 — Multiplexing

Released 2015. Major improvements:

```
Client                         Server
  │──── Single TCP Connection ──►│
  │═══ Stream 1: GET /api/urls ═►│
  │═══ Stream 2: GET /api/user ═►│  (concurrent, no waiting)
  │═══ Stream 3: GET /api/clicks►│
  │◄══ Stream 2: 200 OK ═════════│  (responses can come back in any order)
  │◄══ Stream 1: 200 OK ═════════│
  │◄══ Stream 3: 200 OK ═════════│

Key improvements:
  ✓ Multiplexing: Multiple requests over ONE TCP connection
  ✓ Header compression (HPACK): Reduces overhead for similar headers
  ✓ Server Push: Server can proactively send resources
  ✓ Stream prioritisation: Important responses sent first
```

**For ScaleForge:** HTTP/2 means the browser can send the analytics dashboard API calls (get URL details + get click data + get report) concurrently over one connection, halving the effective latency vs. HTTP/1.1.

### HTTP/3 — QUIC (UDP-based)

Released 2022 (RFC 9114). Solves HTTP/2's remaining problem:

```
HTTP/2 over TCP:
  TCP Head-of-Line Blocking:
  If one TCP packet is lost, ALL streams are blocked until retransmission.
  (Multiplexing streams doesn't help if the transport layer blocks)

HTTP/3 over QUIC (UDP):
  Streams are independent at the transport layer.
  A lost packet only delays its own stream — others continue.
  Also: 0-RTT handshake for reconnections (mobile network switches)
  
When it matters:
  - High packet loss networks (mobile, satellite, congested WiFi)
  - Users who frequently switch networks (commuters)
  - Large file transfers with parallel streams
```

### Status Codes — The Contract

| Range | Meaning | Key Codes |
|-------|---------|-----------|
| 2xx | Success | 200 OK, 201 Created, 204 No Content |
| 3xx | Redirect | **301 Permanent** (cached by browser), **302 Found** (not cached), 304 Not Modified |
| 4xx | Client error | 400 Bad Request, 401 Unauthorized, 403 Forbidden, 404 Not Found, 422 Unprocessable, **429 Too Many Requests** |
| 5xx | Server error | 500 Internal Server Error, 502 Bad Gateway, 503 Service Unavailable, 504 Gateway Timeout |

**ScaleForge redirect decision:**

```
301 Permanent Redirect:
  Browser caches this FOREVER (or until explicit cache clear)
  ✓ Pro: User's browser never calls ScaleForge again — zero load
  ✗ Con: Click analytics impossible. Cannot update or expire URLs.
  ✗ Con: Once sent, irreversible for that user/browser combination
  
302 Found (Temporary Redirect):
  Browser does NOT cache (unless Cache-Control says otherwise)
  ✓ Pro: Full analytics. Can update/delete URLs.
  ✗ Con: Every click hits ScaleForge servers
  
307 Temporary Redirect:
  Same as 302 but guarantees method preservation (POST stays POST)
  Use for redirecting form submissions or non-GET requests.

ScaleForge uses 302 with Cache-Control: no-store
  → Analytics preserved
  → No stale redirects after URL update/deletion
```

### Key Headers

```
Request headers for ScaleForge to read:
  User-Agent: browser/bot/app identification
  X-Forwarded-For: real IP behind a proxy/LB
  Accept: content negotiation (API versioning)
  Authorization: Bearer <JWT>

Response headers ScaleForge should send:
  Location: https://actual-destination.com  (on 302)
  Cache-Control: no-store  (disable caching of redirect)
  X-Request-Id: <uuid>     (tracing — correlate logs across services)
  Strict-Transport-Security: max-age=31536000  (force HTTPS)
  X-Content-Type-Options: nosniff
  X-Frame-Options: DENY
  Content-Security-Policy: default-src 'self'
```

---

## Code Examples

### Implementing the Redirect Endpoint — Correct HTTP Semantics

```typescript
// src/routes/redirect.router.ts

import { Router, Request, Response } from 'express';
import { UrlService } from '../services/url.service.js';
import { ClickQueue } from '../workers/click-tracker.worker.js';
import { logger } from '../logger.js';

export const redirectRouter = Router();

// GET /:code — the performance-critical redirect
redirectRouter.get('/:code', async (req: Request, res: Response): Promise<void> => {
  const { code } = req.params;

  if (!isValidCode(code)) {
    res.status(400).json({ error: 'Invalid short URL code' });
    return;
  }

  const longUrl = await UrlService.lookup(code);

  if (!longUrl) {
    res.status(404).json({ error: 'Short URL not found or expired' });
    return;
  }

  // Enqueue click BEFORE sending response — fire and forget
  // Using void + .catch to avoid blocking on queue errors
  ClickQueue.add('click', {
    code,
    ip: getClientIp(req),
    userAgent: req.headers['user-agent'] ?? 'unknown',
    timestamp: Date.now(),
  }).catch(err => logger.warn({ err }, 'Failed to enqueue click'));

  res
    .status(302)  // Temporary redirect — not cached; analytics work
    .setHeader('Location', longUrl)
    .setHeader('Cache-Control', 'no-store')  // Prevent redirect caching
    .setHeader('X-Request-Id', req.id ?? crypto.randomUUID())
    .end();
});

function isValidCode(code: string): boolean {
  // Only allow alphanumeric + hyphens, 4-12 chars
  return /^[a-zA-Z0-9\-_]{4,12}$/.test(code);
}

function getClientIp(req: Request): string {
  // X-Forwarded-For may contain a comma-separated list when behind multiple proxies.
  // Trust only the first (leftmost) address, set by Nginx.
  const forwarded = req.headers['x-forwarded-for'];
  if (typeof forwarded === 'string') {
    return forwarded.split(',')[0]?.trim() ?? req.ip ?? 'unknown';
  }
  return req.ip ?? 'unknown';
}
```

### HTTP/2 Setup with Node.js (for inter-service communication)

```typescript
// src/clients/http2.client.ts
// For microservices communication (Module 07), HTTP/2 eliminates per-request
// TCP handshake overhead in service-to-service calls.

import http2 from 'node:http2';

export class Http2Client {
  private session: http2.ClientHttp2Session;

  constructor(authority: string) {
    this.session = http2.connect(authority);
    this.session.on('error', (err) => console.error('HTTP/2 session error:', err));
  }

  request(path: string, method = 'GET'): Promise<{ status: number; body: string }> {
    return new Promise((resolve, reject) => {
      const req = this.session.request({
        ':path': path,
        ':method': method,
      });

      let body = '';
      req.on('data', (chunk) => (body += chunk.toString()));
      req.on('end', () => resolve({ status: req.sentHeaders[':status'] as number, body }));
      req.on('error', reject);
      req.end();
    });
  }

  close(): void {
    this.session.close();
  }
}
```

---

## Try It Yourself

**Exercise:** Add security headers to ScaleForge's Express app using the `helmet` middleware.

```typescript
// src/server.ts  — add security headers to all responses

import helmet from 'helmet';
// npm install helmet @types/helmet

// TODO: Add helmet() middleware to the Express app.
// Configure it to:
// 1. Enable HSTS (Strict-Transport-Security) with 1 year max-age
// 2. Disable X-Powered-By (don't reveal it's Express)
// 3. Set Content-Security-Policy to only allow same-origin resources
// 4. Verify with: curl -I http://localhost:3001/health

// What headers do you expect to see?
```

<details>
<summary>Show solution</summary>

```typescript
// In src/server.ts, before route registration:
app.use(helmet({
  hsts: {
    maxAge: 31536000,        // 1 year in seconds
    includeSubDomains: true,
    preload: true,
  },
  contentSecurityPolicy: {
    directives: {
      defaultSrc: ["'self'"],
      scriptSrc: ["'self'"],
      objectSrc: ["'none'"],
      upgradeInsecureRequests: [],
    },
  },
}));

// Expected headers:
// Strict-Transport-Security: max-age=31536000; includeSubDomains; preload
// X-Content-Type-Options: nosniff
// X-Frame-Options: SAMEORIGIN
// Content-Security-Policy: default-src 'self'; script-src 'self'; ...
```

</details>

---

## Capstone Connection

The redirect endpoint using `302 + Cache-Control: no-store` is a direct result of the HTTP/1.1 status code semantics covered here. In Module 03, Nginx will be configured to route traffic to ScaleForge app servers — understanding keep-alive and connection management from this topic is what lets you configure `upstream keepalive 32` correctly in the Nginx config. In Module 06, when FlowForge posts click events via webhooks, the webhook destination must respond within 5 seconds — HTTP timeout configuration is directly from this module's request lifecycle section.
