# 7.3 — API Gateway Pattern

## Concept

An API gateway is a single entry point for all client requests that routes them to the appropriate backend service. It handles cross-cutting concerns — authentication, rate limiting, request routing, response transformation, and observability — in one place rather than duplicating this logic across every service.

---

## Deep Dive

### Why a Gateway?

```
  Without API gateway (client talks directly to services):
  
    Mobile App ──────────────────────► ScaleForge :3001
    Mobile App ──────────────────────► FlowForge  :3002
    Mobile App ──────────────────────► Analytics  :3003
    
    Problems:
      - Client knows private network topology
      - Each service implements its own auth separately
      - Rate limiting must be duplicated in every service
      - Adding a new service = client update required
      - Cannot do canary deployments without client-side logic
  
  With API gateway:
  
    Mobile App ──► API Gateway :443 ──────► ScaleForge :3001
                                      ├──► FlowForge  :3002
                                      └──► Analytics  :3003
    
    Benefits:
      - Single auth check at the gateway
      - Single rate limiter
      - Clients never see internal topology
      - Service address changes are transparent to clients
      - Canary routing (5% traffic to v2) at the gateway level
```

### Gateway Responsibilities

```
  Request pipeline (in order):

    ┌─────────────────────────────────────────────────────┐
    │  1. TLS Termination                                  │
    │     Client connects with HTTPS → gateway handles SSL │
    │     Internal services use plain HTTP (same VPC)      │
    │                                                      │
    │  2. Authentication                                   │
    │     Validate JWT bearer token                        │
    │     Inject user context as header (X-User-Id)        │
    │                                                      │
    │  3. Rate Limiting                                    │
    │     Per-IP: 1000 req/min global                      │
    │     Per-user: 100 req/min authenticated              │
    │                                                      │
    │  4. Request Routing                                  │
    │     /api/v1/urls/*       → ScaleForge                │
    │     /api/v1/notifications/* → FlowForge              │
    │     /api/v1/analytics/*  → Analytics Service         │
    │                                                      │
    │  5. Response Transformation                          │
    │     Add CORS headers                                 │
    │     Normalize error response shape                   │
    │     Strip internal headers (X-Powered-By, etc.)      │
    │                                                      │
    │  6. Observability                                    │
    │     Request logging (with correlation ID)            │
    │     Timing metrics                                   │
    │     Error rate alerting                              │
    └─────────────────────────────────────────────────────┘
```

### Gateway vs. Reverse Proxy vs. Service Mesh

```
  Tool           Operates at     Responsibilities
  ─────────────  ─────────────   ─────────────────────────────────────────
  Nginx          L7 (HTTP)       Static routing, TLS, basic rate limiting
  API Gateway    L7 (HTTP)       Auth, rate limit, routing, transforms
  (Kong, APISIX)
  Service Mesh   L4/L7 (TCP)     mTLS, circuit breaker, retries, tracing
  (Istio, Linkerd) betw. pods   
  
  For ScaleForge/FlowForge at learning scale:
    Nginx serves as the API gateway (simple routing + rate limiting)
    No service mesh needed until you have 10+ services
```

---

## Code Examples

### Nginx as API Gateway

```nginx
# nginx/api-gateway.conf

upstream scaleforge {
    least_conn;
    server scaleforge:3001;
    keepalive 50;
}

upstream flowforge {
    least_conn;
    server flowforge:3002;
    keepalive 20;
}

# Rate limit zones
limit_req_zone $binary_remote_addr zone=global:10m rate=100r/s;
limit_req_zone $http_x_user_id    zone=per_user:10m rate=20r/s;

server {
    listen 443 ssl http2;
    server_name api.scaleforge.local;

    ssl_certificate     /etc/nginx/ssl/cert.pem;
    ssl_certificate_key /etc/nginx/ssl/key.pem;

    # Global rate limit (unauthenticated)
    limit_req zone=global burst=200 nodelay;

    # Correlation ID: accept from client or generate
    set $corr_id $http_x_correlation_id;
    if ($corr_id = "") {
        set $corr_id $request_id;  # Nginx auto-generated UUID
    }
    add_header X-Correlation-Id $corr_id always;

    # Proxy common settings
    proxy_set_header Host              $host;
    proxy_set_header X-Real-IP         $remote_addr;
    proxy_set_header X-Correlation-Id  $corr_id;
    proxy_set_header X-Forwarded-For   $proxy_add_x_forwarded_for;
    proxy_connect_timeout              5s;
    proxy_read_timeout                 30s;

    # ScaleForge: URL management + redirect
    location /api/v1/urls {
        proxy_pass http://scaleforge;
    }

    # ScaleForge: redirect handler (no auth required)
    location ~ ^/[a-zA-Z0-9]{4,10}$ {
        proxy_cache off;            # analytics requires every hit to reach app
        proxy_pass http://scaleforge;
    }

    # FlowForge: notifications
    location /api/v1/notifications {
        # Apply per-user rate limit when auth header present
        limit_req zone=per_user burst=50 nodelay;
        proxy_pass http://flowforge;
    }

    # Normalized error response for gateway-level errors
    error_page 429 = @rate_limit_error;
    location @rate_limit_error {
        default_type application/json;
        return 429 '{"error":"Rate limit exceeded","code":"RATE_LIMITED","retryAfter":60}';
    }

    error_page 502 503 504 = @upstream_error;
    location @upstream_error {
        default_type application/json;
        return 503 '{"error":"Service temporarily unavailable","code":"SERVICE_UNAVAILABLE"}';
    }
}

# HTTP → HTTPS redirect
server {
    listen 80;
    return 301 https://$host$request_uri;
}
```

### JWT Auth Middleware at the Gateway (Node.js Gateway)

```typescript
// src/gateway/auth.middleware.ts
// For a Node.js-based gateway (e.g., Express + http-proxy-middleware)
// In production, use Kong or AWS API Gateway — this illustrates the logic.

import jwt from 'jsonwebtoken';
import type { Request, Response, NextFunction } from 'express';

interface JwtPayload {
  sub: string;   // userId
  email: string;
  iat: number;
  exp: number;
}

const PUBLIC_PATHS = [
  /^\/[a-zA-Z0-9]{4,10}$/, // redirect handler — no auth
  /^\/health$/,
];

export function authMiddleware(req: Request, res: Response, next: NextFunction): void {
  // Skip auth for public routes
  if (PUBLIC_PATHS.some((p) => p.test(req.path))) {
    next();
    return;
  }

  const authHeader = req.headers.authorization;
  if (!authHeader?.startsWith('Bearer ')) {
    res.status(401).json({ error: 'Authorization header required' });
    return;
  }

  const token = authHeader.slice(7);
  try {
    const payload = jwt.verify(token, process.env.JWT_SECRET!) as JwtPayload;

    // Inject user context as headers for downstream services
    // Services never need to verify the JWT themselves — trust the gateway
    req.headers['x-user-id'] = payload.sub;
    req.headers['x-user-email'] = payload.email;

    next();
  } catch (err) {
    if ((err as Error).name === 'TokenExpiredError') {
      res.status(401).json({ error: 'Token expired' });
    } else {
      res.status(401).json({ error: 'Invalid token' });
    }
  }
}
```

---

## Try It Yourself

**Exercise:** Implement path-based routing with authentication.

```typescript
// gateway-routing.exercise.ts

// TODO: Build a minimal API gateway using Express + http-proxy-middleware
// that:
// 1. Routes /api/v1/urls/* → http://localhost:3001
// 2. Routes /api/v1/notifications/* → http://localhost:3002
// 3. Applies authMiddleware to ALL /api/v1/* routes (not / redirects)
// 4. Adds X-Correlation-Id to every response
// 5. Returns a normalized 503 JSON when a backend service is unavailable

import express from 'express';
import { createProxyMiddleware } from 'http-proxy-middleware';
import { authMiddleware } from './src/gateway/auth.middleware.js';
import { correlationIdMiddleware } from './src/middleware/correlation-id.middleware.js';

const app = express();
app.use(correlationIdMiddleware);

// TODO: Apply authMiddleware to /api/v1/* routes only
// TODO: Add proxy routes for ScaleForge and FlowForge
// TODO: Handle proxy error events to return 503

app.listen(8080, () => console.log('Gateway running on :8080'));
```

<details>
<summary>Show solution</summary>

```typescript
import express from 'express';
import { createProxyMiddleware } from 'http-proxy-middleware';
import { authMiddleware } from './src/gateway/auth.middleware.js';
import { correlationIdMiddleware } from './src/middleware/correlation-id.middleware.js';

const app = express();

app.use(correlationIdMiddleware);
app.use('/api/v1', authMiddleware);

// ScaleForge proxy
app.use('/api/v1/urls', createProxyMiddleware({
  target: 'http://localhost:3001',
  changeOrigin: true,
  on: {
    error: (_err, _req, res) => {
      (res as express.Response).status(503).json({ error: 'ScaleForge unavailable' });
    },
  },
}));

// FlowForge proxy
app.use('/api/v1/notifications', createProxyMiddleware({
  target: 'http://localhost:3002',
  changeOrigin: true,
  on: {
    error: (_err, _req, res) => {
      (res as express.Response).status(503).json({ error: 'FlowForge unavailable' });
    },
  },
}));

// Redirect handler — no auth
app.use(createProxyMiddleware({
  target: 'http://localhost:3001',
  changeOrigin: true,
  pathFilter: (path) => /^\/[a-zA-Z0-9]{4,10}$/.test(path),
}));

app.listen(8080, () => console.log('Gateway on :8080'));
```

</details>

---

## Capstone Connection

The API gateway is where ScaleForge and FlowForge become one system from the client's perspective. A mobile app calls `https://api.scaleforge.com/api/v1/notifications` and never knows that FlowForge is a separate service on a different port. When you add a third service (analytics), you add one routing rule to the gateway — the mobile app requires zero changes.
