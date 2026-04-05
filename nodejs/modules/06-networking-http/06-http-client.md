# 6.6 — HTTP Client Patterns

## Concept

Every production service makes outbound HTTP requests — to external APIs, internal microservices, or webhook endpoints. The built-in `fetch` (Node 18+) is sufficient for basic use, but production code needs retry logic, timeouts, circuit breakers, and structured error handling around HTTP client calls.

---

## Deep Dive

### Using Native `fetch`

```typescript
// Basic usage
const res = await fetch('https://api.example.com/data');
if (!res.ok) {
  throw new Error(`HTTP ${res.status}: ${await res.text()}`);
}
const data = await res.json();

// With timeout (AbortSignal.timeout — Node 17.3+)
const res = await fetch('https://api.example.com/slow', {
  signal: AbortSignal.timeout(5000), // abort if no response in 5s
});

// POST with JSON body
const res = await fetch('https://api.example.com/jobs', {
  method: 'POST',
  headers: { 'Content-Type': 'application/json', Authorization: `Bearer ${token}` },
  body: JSON.stringify({ pipeline: 'etl', env: 'prod' }),
  signal: AbortSignal.timeout(10_000),
});
```

### Retry with Exponential Backoff

```typescript
interface RetryOptions {
  maxAttempts?: number;
  baseDelayMs?: number;
  maxDelayMs?: number;
  shouldRetry?: (res: Response) => boolean;
}

async function fetchWithRetry(
  url: string,
  init: RequestInit = {},
  opts: RetryOptions = {},
): Promise<Response> {
  const {
    maxAttempts = 3,
    baseDelayMs = 200,
    maxDelayMs = 5000,
    shouldRetry = (r) => r.status >= 500 || r.status === 429,
  } = opts;

  let lastError: Error | undefined;

  for (let attempt = 1; attempt <= maxAttempts; attempt++) {
    try {
      const res = await fetch(url, init);

      if (!shouldRetry(res) || attempt === maxAttempts) return res;

      const delay = Math.min(baseDelayMs * 2 ** (attempt - 1), maxDelayMs);
      const jitter = Math.random() * delay * 0.1; // ±10% jitter
      await new Promise((r) => setTimeout(r, delay + jitter));
    } catch (err) {
      lastError = err as Error;
      if (attempt === maxAttempts) throw lastError;
    }
  }

  throw lastError!;
}
```

### Circuit Breaker

```typescript
// Prevent cascading failures: stop sending requests if too many fail

type CircuitState = 'CLOSED' | 'OPEN' | 'HALF_OPEN';

class CircuitBreaker {
  private state: CircuitState = 'CLOSED';
  private failureCount = 0;
  private lastFailureTime = 0;

  constructor(
    private readonly threshold = 5,
    private readonly timeout = 60_000, // reset after 60s
  ) {}

  async execute<T>(fn: () => Promise<T>): Promise<T> {
    if (this.state === 'OPEN') {
      if (Date.now() - this.lastFailureTime > this.timeout) {
        this.state = 'HALF_OPEN'; // try one request
      } else {
        throw new Error('Circuit breaker OPEN — request blocked');
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

  private onSuccess() {
    this.failureCount = 0;
    this.state = 'CLOSED';
  }

  private onFailure() {
    this.failureCount++;
    this.lastFailureTime = Date.now();
    if (this.failureCount >= this.threshold) {
      this.state = 'OPEN';
    }
  }
}

// Usage with an external webhook service:
const webhookBreaker = new CircuitBreaker(5, 60_000);

await webhookBreaker.execute(() =>
  fetchWithRetry('https://hooks.example.com/job-done', {
    method: 'POST',
    body: JSON.stringify({ jobId, status: 'DONE' }),
  }),
);
```

---

## Try It Yourself

**Exercise:** Implement a `sendWebhook` function for PipeForge that:
1. POSTs a job completion event to a user-configured URL
2. Retries up to 3 times with exponential backoff on 5xx errors
3. Times out after 10 seconds per attempt

```typescript
async function sendWebhook(url: string, payload: unknown): Promise<void> {
  // TODO
}
```

<details>
<summary>Show solution</summary>

```typescript
async function sendWebhook(url: string, payload: unknown): Promise<void> {
  const res = await fetchWithRetry(
    url,
    {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(payload),
      signal: AbortSignal.timeout(10_000),
    },
    { maxAttempts: 3, baseDelayMs: 500 },
  );

  if (!res.ok) {
    throw new Error(`Webhook delivery failed: ${res.status} ${await res.text()}`);
  }
}
```

</details>

---

## Capstone Connection

PipeForge's pipeline steps can include a `WEBHOOK` step type that fires an HTTP request on job completion. The webhook call uses the circuit breaker to prevent a failing external service from blocking the job runner thread. Failed deliveries are persisted to `JobLog` for retry via the CLI (`pipeforge retry-webhook <jobId>`).
