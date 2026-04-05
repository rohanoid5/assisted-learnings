# 9.1 — Circuit Breaker

## Concept

A circuit breaker wraps external calls and monitors their failure rate. When failures exceed a threshold, it "opens" and immediately rejects calls without attempting them — giving the failing service time to recover without being hammered by a flood of failing requests. After a timeout, it half-opens and allows one probe call through to check if the dependency has recovered.

---

## Deep Dive

### State Machine

```
  ┌─────────────────────────────────────────────────────────────────┐
  │  States:                                                        │
  │                                                                 │
  │  CLOSED  — Normal operation. Calls pass through.               │
  │            Failure counter incremented on each error.          │
  │            When failures >= threshold in window:               │
  │              → transition to OPEN                              │
  │                                                                 │
  │  OPEN    — All calls fail immediately (CallNotPermittedError)   │
  │            No calls reach the downstream service.              │
  │            After resetTimeout (e.g., 30s):                     │
  │              → transition to HALF_OPEN                         │
  │                                                                 │
  │  HALF_OPEN — One probe call allowed through.                    │
  │              If it succeeds: → CLOSED (reset failure count)    │
  │              If it fails:   → OPEN again (another resetTimeout) │
  └─────────────────────────────────────────────────────────────────┘
  
  Visual timeline:
  
  t=0    t=10s  t=15s  t=45s  t=60s  t=75s
  CLOSED ────►│OPEN──────────►│HALF──►│CLOSED
              5 failures       30s     probe
              in 10s           passes  succeeds
```

### Failure Counting Strategies

```
  Count-based:
    Open after N consecutive failures.
    Simple but doesn't account for success rate.
    "5 failures in last N calls"
  
  Rate-based (more sophisticated):
    Open when failure rate > X% in a time window.
    Protects against high-volume paths better.
    "30% error rate in last 10 seconds"
  
  For ScaleForge → FlowForge calls:
    Low volume (URL creates) → count-based is fine
    "Open after 5 failures in any 10s window"
```

---

## Code Examples

### Circuit Breaker Implementation

```typescript
// src/resilience/circuit-breaker.ts

type CircuitState = 'CLOSED' | 'OPEN' | 'HALF_OPEN';

interface CircuitBreakerOptions {
  name: string;
  failureThreshold: number;    // open after this many failures
  windowMs: number;            // count failures within this window
  resetTimeoutMs: number;      // how long to stay OPEN before probing
  onStateChange?: (name: string, from: CircuitState, to: CircuitState) => void;
}

export class CircuitBreaker {
  private state: CircuitState = 'CLOSED';
  private failures: number[] = [];  // timestamps of recent failures
  private openedAt: number | null = null;

  constructor(private readonly opts: CircuitBreakerOptions) {}

  async call<T>(fn: () => Promise<T>): Promise<T> {
    if (this.state === 'OPEN') {
      if (this.shouldAttemptReset()) {
        this.transition('HALF_OPEN');
      } else {
        throw new CircuitOpenError(this.opts.name);
      }
    }

    try {
      const result = await fn();
      this.onSuccess();
      return result;
    } catch (err) {
      this.onFailure();
      throw err;
    }
  }

  getState(): CircuitState {
    return this.state;
  }

  private onSuccess(): void {
    if (this.state === 'HALF_OPEN') {
      this.failures = [];
      this.openedAt = null;
      this.transition('CLOSED');
    }
    // In CLOSED: success doesn't change state (only failures do)
  }

  private onFailure(): void {
    const now = Date.now();
    // Remove failures outside the window
    this.failures = this.failures.filter((t) => now - t < this.opts.windowMs);
    this.failures.push(now);

    if (this.state === 'HALF_OPEN' || this.failures.length >= this.opts.failureThreshold) {
      this.openedAt = now;
      this.transition('OPEN');
    }
  }

  private shouldAttemptReset(): boolean {
    return this.openedAt !== null && Date.now() - this.openedAt >= this.opts.resetTimeoutMs;
  }

  private transition(to: CircuitState): void {
    const from = this.state;
    if (from !== to) {
      this.state = to;
      this.opts.onStateChange?.(this.opts.name, from, to);
    }
  }
}

export class CircuitOpenError extends Error {
  constructor(name: string) {
    super(`Circuit '${name}' is OPEN — call rejected`);
    this.name = 'CircuitOpenError';
  }
}
```

### Integrating with FlowForgeClient

```typescript
// src/clients/flowforge.client.ts (updated)
import { CircuitBreaker, CircuitOpenError } from '../resilience/circuit-breaker.js';
import { Gauge, Counter } from 'prom-client';
import { metricsRegistry } from '../metrics/registry.js';
import { logger } from '../logger.js';

// Prometheus metrics for circuit state
const circuitStateGauge = new Gauge({
  name: 'circuit_breaker_state',
  help: '0=CLOSED, 1=HALF_OPEN, 2=OPEN',
  labelNames: ['circuit'] as const,
  registers: [metricsRegistry],
});

const circuitRejectCounter = new Counter({
  name: 'circuit_breaker_rejected_calls_total',
  help: 'Calls rejected by an open circuit breaker',
  labelNames: ['circuit'] as const,
  registers: [metricsRegistry],
});

const STATE_VALUES = { CLOSED: 0, HALF_OPEN: 1, OPEN: 2 } as const;

export class FlowForgeClient {
  private readonly breaker: CircuitBreaker;

  constructor(
    private readonly baseUrl: string,
    private readonly timeoutMs = 5000,
  ) {
    this.breaker = new CircuitBreaker({
      name: 'flowforge',
      failureThreshold: 5,
      windowMs: 10_000,      // 5 failures in any 10s window → OPEN
      resetTimeoutMs: 30_000, // probe after 30s
      onStateChange: (name, from, to) => {
        logger.warn({ name, from, to }, 'Circuit breaker state change');
        circuitStateGauge.set({ circuit: name }, STATE_VALUES[to]);
      },
    });

    // Initialize gauge
    circuitStateGauge.set({ circuit: 'flowforge' }, 0);
  }

  async enqueueNotification(job: NotificationJobInput): Promise<string> {
    try {
      return await this.breaker.call(async () => {
        const controller = new AbortController();
        const timer = setTimeout(() => controller.abort(), this.timeoutMs);
        try {
          const res = await fetch(`${this.baseUrl}/api/v1/notifications`, {
            method: 'POST',
            headers: { 'Content-Type': 'application/json' },
            body: JSON.stringify(job),
            signal: controller.signal,
          });
          if (!res.ok) throw new Error(`HTTP ${res.status}`);
          return ((await res.json()) as { jobId: string }).jobId;
        } finally {
          clearTimeout(timer);
        }
      });
    } catch (err) {
      if (err instanceof CircuitOpenError) {
        circuitRejectCounter.inc({ circuit: 'flowforge' });
        throw new ServiceUnavailableError('FlowForge (circuit open)');
      }
      // Connection refused or timeout → also circuit-tracked
      throw new ServiceUnavailableError('FlowForge');
    }
  }
}
```

---

## Try It Yourself

**Exercise:** Trip the circuit breaker and observe automatic recovery.

```bash
# 1. Start ScaleForge with FlowForge running
docker-compose up scaleforge flowforge

# 2. Make a successful URL+notification request
curl -X POST http://localhost:3001/api/v1/urls \
  -d '{"url":"https://example.com","notifyOnCreate":true}' \
  -H "Content-Type: application/json"
# Expected: 201

# 3. Stop FlowForge (simulates crash)
docker-compose stop flowforge

# 4. Make 5 URL+notification requests quickly (will all fail and trip the circuit)
for i in $(seq 1 5); do
  curl -X POST http://localhost:3001/api/v1/urls \
    -d '{"url":"https://example.com","notifyOnCreate":true}' \
    -H "Content-Type: application/json"
  echo ""
done
# First 5: 503 (FlowForge unreachable)

# 5. Make one more request immediately
# Expected: 503 — circuit is now OPEN (fast fail, doesn't hit network)
# Check ScaleForge logs: "Circuit breaker state change: CLOSED → OPEN"
curl -X POST http://localhost:3001/api/v1/urls \
  -d '{"url":"https://example.com","notifyOnCreate":true}' \
  -H "Content-Type: application/json"

# 6. Wait 30 seconds, then restart FlowForge
sleep 30
docker-compose start flowforge

# 7. Make another request
# Expected: circuit half-opens, probe succeeds → circuit CLOSES
# Logs: "Circuit breaker state change: OPEN → HALF_OPEN → CLOSED"
```

<details>
<summary>Show how to expose circuit state via health endpoint</summary>

```typescript
// In health.router.ts, add circuit state to the readiness check
import { flowforgeClient } from '../clients/flowforge.client.js';

healthRouter.get('/ready', async (_req, res) => {
  const circuitState = flowforgeClient.getCircuitState();

  try {
    await primaryPool.query('SELECT 1');
    await redis.ping();
    res.json({
      status: 'ready',
      db: 'ok',
      redis: 'ok',
      flowforge: circuitState,  // "CLOSED" | "OPEN" | "HALF_OPEN"
    });
  } catch (err) {
    res.status(503).json({ status: 'not ready', error: (err as Error).message });
  }
});
```

</details>

---

## Capstone Connection

ScaleForge's `createUrlAndNotify()` function calls FlowForge over HTTP. Without a circuit breaker, a FlowForge outage causes ScaleForge's URL creation endpoint to hang for 5 seconds per request (timeout), exhausting the connection pool, making the entire ScaleForge service unavailable. With the circuit breaker, after 5 failures the circuit opens — FlowForge calls fail immediately (no network I/O), URL creation returns a clear 503, and the redirect path (which never calls FlowForge) keeps working at full throughput.
