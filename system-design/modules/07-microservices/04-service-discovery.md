# 7.4 — Service Discovery

## Concept

Service discovery is how microservices find each other's network addresses when those addresses change — containers restart on new IPs, services scale horizontally, Kubernetes pods get reassigned. The solution is to name services rather than hardcode IPs, and let the infrastructure resolve names to healthy instances at request time.

---

## Deep Dive

### The Problem: Dynamic Addresses

```
  Static deployment (VMs with fixed IPs):
  
    ScaleForge knows: FlowForge is at 10.0.1.42:3002
    
    Works fine until:
      - FlowForge pod restarts → new IP: 10.0.1.87
      - FlowForge scales to 3 replicas → which IP to call?
      - Container host is replaced → all IPs change
    
  Solution: use names, not addresses
  
    ScaleForge calls: http://flowforge/api/v1/notifications
    DNS resolves "flowforge" to the current healthy IP(s)
    Load balancing happens at the DNS or proxy layer
```

### Client-Side vs. Server-Side Discovery

```
  Client-Side Discovery:
  
    ScaleForge → Registry ─── "flowforge instances?" ───► ["10.0.1.42:3002",
    ScaleForge ←────────────── returns list ──────────────   "10.0.1.87:3002"]
    ScaleForge picks one (round-robin, least-connections)
    ScaleForge ─────────────────────────────────────────────► 10.0.1.87:3002
  
    Pros: client controls load balancing strategy
    Cons: every service needs registry client code
  
  ─────────────────────────────────────────────────────────────
  
  Server-Side Discovery:
  
    ScaleForge ──────────────────────► Load Balancer ("flowforge")
                                       ↓ consults registry
                                       ↓ picks healthy instance
                                       └──────────────────────► 10.0.1.87:3002
  
    Pros: client is simple (just uses a hostname)
    Cons: load balancer is an extra hop
  
  ─────────────────────────────────────────────────────────────
  
  What you're already using at each environment:
  
  ┌──────────────────┬──────────────────────────────────────────────┐
  │ Environment      │ Discovery mechanism                          │
  ├──────────────────┼──────────────────────────────────────────────┤
  │ docker-compose   │ Docker DNS — service name = hostname         │
  │ localhost (dev)  │ ENV vars with hardcoded ports                │
  │ Kubernetes       │ kube-dns — service.namespace.svc.cluster.local│
  │ AWS ECS          │ Cloud Map + Route 53                         │
  └──────────────────┴──────────────────────────────────────────────┘
```

### Docker Compose DNS (Current Setup)

```
  docker-compose.yml defines services. Docker creates a virtual network
  where each service's name becomes a hostname.

  ┌──────────────────────────────────────────────────────────┐
  │  Docker Compose internal network: "app_default"          │
  │                                                          │
  │   ┌──────────────┐   http://flowforge:3002               │
  │   │  scaleforge  │ ──────────────────────► ┌──────────┐  │
  │   │  :3001       │                         │flowforge │  │
  │   └──────────────┘                         │ :3002    │  │
  │                                            └──────────┘  │
  │   "flowforge" resolves to the flowforge container's IP   │
  │   automatically — no /etc/hosts editing needed           │
  └──────────────────────────────────────────────────────────┘
```

### Health-Check-Aware Routing

```
  Problem: DNS resolves to all instances, including unhealthy ones.
  
  Docker Compose healthcheck:
  
    flowforge:
      healthcheck:
        test: ["CMD", "curl", "-f", "http://localhost:3002/health"]
        interval: 10s
        timeout: 5s
        retries: 3
  
  Kubernetes liveness + readiness probes:
  
    livenessProbe:
      httpGet: { path: /health/live, port: 3002 }
      initialDelaySeconds: 10
  
    readinessProbe:
      httpGet: { path: /health/ready, port: 3002 }
      initialDelaySeconds: 5
  
  Kubernetes Service only routes to pods where readinessProbe passes.
  Nginx upstream health_check module polls registered backends.
```

---

## Code Examples

### Environment-Based Service Registry

```typescript
// src/config/services.ts
// All inter-service URLs defined in one place.
// Default values match docker-compose service names.
// Override with env vars for Kubernetes DNS or cloud environments.

export function getServiceUrls() {
  return {
    flowforge: {
      baseUrl: process.env.FLOWFORGE_URL ?? 'http://flowforge:3002',
      timeoutMs: Number(process.env.FLOWFORGE_TIMEOUT_MS ?? 5000),
    },
    analytics: {
      baseUrl: process.env.ANALYTICS_URL ?? 'http://analytics:3003',
      timeoutMs: Number(process.env.ANALYTICS_TIMEOUT_MS ?? 3000),
    },
  } as const;
}

// Usage:
// const { flowforge } = getServiceUrls();
// const client = new FlowForgeClient(flowforge.baseUrl, flowforge.timeoutMs);
```

### Express Health Endpoint

```typescript
// src/routes/health.router.ts
// Kubernetes readinessProbe hits /health/ready.
// Returns 503 if DB or Redis are not reachable — probe fails,
// Kubernetes removes pod from Service endpoints (stops routing traffic).

import { Router } from 'express';
import { primaryPool } from '../db/pool.js';
import { redis } from '../cache/redis.js';

export const healthRouter = Router();

// Liveness: is the process alive? (just return 200)
healthRouter.get('/live', (_req, res) => {
  res.json({ status: 'alive' });
});

// Readiness: can the process serve traffic?
healthRouter.get('/ready', async (_req, res) => {
  try {
    await primaryPool.query('SELECT 1');
    await redis.ping();
    res.json({ status: 'ready', db: 'ok', redis: 'ok' });
  } catch (err) {
    res.status(503).json({
      status: 'not ready',
      error: (err as Error).message,
    });
  }
});
```

### docker-compose.yml with Service Discovery

```yaml
# docker-compose.yml
version: "3.9"

services:
  postgres:
    image: postgres:16-alpine
    environment:
      POSTGRES_DB: scaleforge
      POSTGRES_USER: app
      POSTGRES_PASSWORD: secret
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U app -d scaleforge"]
      interval: 5s
      timeout: 3s
      retries: 5

  redis:
    image: redis:7-alpine
    healthcheck:
      test: ["CMD", "redis-cli", "ping"]
      interval: 5s
      timeout: 3s
      retries: 5

  scaleforge:
    build: ./scaleforge
    environment:
      # "postgres" and "redis" resolve to the containers above
      DATABASE_URL: postgres://app:secret@postgres:5432/scaleforge
      REDIS_URL: redis://redis:6379
      FLOWFORGE_URL: http://flowforge:3002   # service DNS
      PORT: "3001"
    ports:
      - "3001:3001"
    depends_on:
      postgres: { condition: service_healthy }
      redis:    { condition: service_healthy }
      flowforge: { condition: service_healthy }
    healthcheck:
      test: ["CMD", "curl", "-f", "http://localhost:3001/health/ready"]
      interval: 10s
      timeout: 5s
      retries: 3

  flowforge:
    build: ./flowforge
    environment:
      DATABASE_URL: postgres://app:secret@postgres:5432/scaleforge
      REDIS_URL: redis://redis:6379
      PORT: "3002"
    ports:
      - "3002:3002"
    depends_on:
      postgres: { condition: service_healthy }
      redis:    { condition: service_healthy }
    healthcheck:
      test: ["CMD", "curl", "-f", "http://localhost:3002/health/ready"]
      interval: 10s
      timeout: 5s
      retries: 3
```

### Kubernetes Service DNS (Production Reference)

```yaml
# k8s/flowforge-service.yaml
# After applying this, any pod in the cluster can reach FlowForge at:
#   http://flowforge.default.svc.cluster.local:3002
# or just:
#   http://flowforge:3002  (if caller is in the same namespace)

apiVersion: v1
kind: Service
metadata:
  name: flowforge
  namespace: default
spec:
  selector:
    app: flowforge
  ports:
    - name: http
      port: 3002
      targetPort: 3002
  # ClusterIP (default) = internal-only. Use "LoadBalancer" for external.
  type: ClusterIP
```

---

## Try It Yourself

**Exercise:** Verify service discovery in Docker Compose and handle missing services gracefully.

```typescript
// service-discovery.exercise.ts

// TODO:
// 1. Add the docker-compose.yml above to your project
// 2. Run: docker-compose up --build
// 3. Verify ScaleForge can reach FlowForge:
//    docker-compose exec scaleforge curl http://flowforge:3002/health/ready
//
// 4. Simulate FlowForge being unavailable:
//    docker-compose stop flowforge
//
// 5. Make a POST to ScaleForge that triggers a FlowForge call.
//    Expected: ScaleForge returns a 503 with "Retry-After: 30" header
//    (not a crash, not a 500 — a graceful degraded response)
//
// 6. Update FlowForgeClient to catch connection errors and throw
//    a ServiceUnavailableError that the route handler converts to 503.

class ServiceUnavailableError extends Error {
  constructor(service: string) {
    super(`${service} is unavailable`);
    this.name = 'ServiceUnavailableError';
  }
}

// TODO: Wrap FlowForgeClient.enqueueNotification() to catch ECONNREFUSED
// and throw ServiceUnavailableError instead.
```

<details>
<summary>Show solution</summary>

```typescript
// src/clients/flowforge.client.ts (updated error handling)
import type { NotificationJobInput } from './types.js';

export class ServiceUnavailableError extends Error {
  constructor(service: string) {
    super(`${service} is unavailable`);
    this.name = 'ServiceUnavailableError';
  }
}

export class FlowForgeClient {
  constructor(
    private readonly baseUrl: string,
    private readonly timeoutMs = 5000,
  ) {}

  async enqueueNotification(job: NotificationJobInput): Promise<string> {
    const controller = new AbortController();
    const timer = setTimeout(() => controller.abort(), this.timeoutMs);

    try {
      const res = await fetch(`${this.baseUrl}/api/v1/notifications`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(job),
        signal: controller.signal,
      });
      if (!res.ok) throw new Error(`FlowForge error: ${res.status}`);
      const body = (await res.json()) as { jobId: string };
      return body.jobId;
    } catch (err) {
      const cause = err as NodeJS.ErrnoException;
      if (cause.code === 'ECONNREFUSED' || cause.name === 'AbortError') {
        throw new ServiceUnavailableError('FlowForge');
      }
      throw err;
    } finally {
      clearTimeout(timer);
    }
  }
}

// In the route handler:
// try { await flowforgeClient.enqueueNotification(job); }
// catch (err) {
//   if (err instanceof ServiceUnavailableError) {
//     res.setHeader('Retry-After', '30');
//     res.status(503).json({ error: err.message });
//     return;
//   }
//   throw err;
// }
```

</details>

---

## Capstone Connection

ScaleForge calls FlowForge synchronously (HTTP) when creating a URL with notification preferences. Docker Compose DNS resolves `http://flowforge:3002` automatically at runtime. This same pattern works identically in Kubernetes — you only swap the service name with the Kubernetes DNS FQDN in the environment variable. The application code never changes between environments.
