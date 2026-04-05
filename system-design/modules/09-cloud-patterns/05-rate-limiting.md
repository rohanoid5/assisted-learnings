# 9.5 — Rate Limiting

## Concept

Rate limiting protects services from being overwhelmed — by a misbehaving client, a scraper, or a sudden traffic spike. Without it, a single user can exhaust your connection pool and degrade the experience for everyone else. Rate limiting is applied at two tiers: at the network edge (Nginx) to protect against IP-level floods, and inside the application (Redis) to enforce per-user or per-API-key quotas.

---

## Deep Dive

### Why Two Tiers?

```
  ┌────────────────────────────────────────────────────────────────┐
  │  Tier 1: Edge (Nginx)                                          │
  │  ─────────────────────────────────────────────────────────────│
  │  • Protects against IP floods / DDoS                          │
  │  • Handled in kernel space — almost zero CPU per request       │
  │  • Coarse-grained: 100 req/s per IP                           │
  │  • Enforced before any app code runs                          │
  │                                                               │
  │  Tier 2: Application (Redis)                                  │
  │  ─────────────────────────────────────────────────────────────│
  │  • Per user / per API key quotas                              │
  │  • Fine-grained: 100 URL creates per user per hour            │
  │  • Can vary by plan (free vs. paid)                           │
  │  • Returns contextual headers (X-RateLimit-Remaining)         │
  └────────────────────────────────────────────────────────────────┘
```

### Window Algorithm Comparison

```
  Fixed Window
  ─────────────
  bucket resets at t=0, t=60, t=120 ...
  
  t=59: user makes 100 requests  ← flushed at t=60
  t=61: user makes 100 requests  ← new window
  
  Result: 200 requests in 2 seconds — burst at window boundary!
  
  
  Sliding Window (counter in Redis)
  ──────────────────────────────────
  Always look back exactly N seconds from now.
  No window boundary — no burst.
  
  t=59:  100 requests recorded (timestamps t=0..t=59)
  t=61:  window is t=1..t=61 → 98 old timestamps expire → 2 remain
          user can make 98 more requests
  
  Cost: O(log N) per request (sorted set ops)
  
  
  Token Bucket
  ─────────────
  tokens = min(capacity, tokens + rate × elapsed_time)
  
  + Allows controlled bursting — good for API integrations
  - Harder to explain to users; requires last-refill timestamp
  
  Use case: SDK clients that process jobs in bursts
```

### Sliding Window in Redis: the Lua Script

```
  Per request:
  1. ZREMRANGEBYSCORE key 0 (now - windowMs)   ← remove expired entries
  2. ZADD key now <unique_member>               ← record this request
  3. ZCARD key                                  ← count in window
  4. EXPIRE key windowMs                        ← auto-cleanup
  
  Run as Lua script → atomic (single round trip, no race condition)
  
  Key: ratelimit:user:<userId>
  Score: epoch ms (allows range removal)
  Member: `${now}-${crypto.randomUUID()}` (unique per request)
```

### Response Headers

```
  X-RateLimit-Limit:      100         ← max allowed in window
  X-RateLimit-Remaining:  37          ← how many left
  X-RateLimit-Reset:      1715000060  ← epoch when oldest entry expires
  Retry-After:            47          ← seconds until next slot (on 429)
```

---

## Code Examples

### Application-Level RateLimiter

```typescript
// src/resilience/rate-limiter.ts
import { redis } from '../cache/redis.js';

export interface RateLimitResult {
  allowed: boolean;
  remaining: number;
  resetAt: number;    // epoch ms of oldest entry expiry
  retryAfterMs: number;
}

const SLIDING_WINDOW_LUA = `
local key       = KEYS[1]
local now       = tonumber(ARGV[1])
local window_ms = tonumber(ARGV[2])
local limit     = tonumber(ARGV[3])
local member    = ARGV[4]

-- Remove entries outside the window
redis.call('ZREMRANGEBYSCORE', key, 0, now - window_ms)

-- Count current entries
local count = redis.call('ZCARD', key)

if count < limit then
  -- Add this request
  redis.call('ZADD', key, now, member)
  redis.call('PEXPIRE', key, window_ms)
  return { 1, limit - count - 1, 0 }  -- allowed, remaining, retry_after_ms
else
  -- Fetch oldest entry score to compute reset time
  local oldest = redis.call('ZRANGE', key, 0, 0, 'WITHSCORES')
  local oldest_score = tonumber(oldest[2]) or now
  local retry_after  = oldest_score + window_ms - now
  return { 0, 0, retry_after }
end
`;

export class RateLimiter {
  constructor(
    private readonly limitPerWindow: number,
    private readonly windowMs: number,
  ) {}

  async check(identifier: string): Promise<RateLimitResult> {
    const key    = `ratelimit:${identifier}`;
    const now    = Date.now();
    const member = `${now}-${Math.random().toString(36).slice(2)}`;

    const [allowed, remaining, retryAfterMs] = (await redis.eval(
      SLIDING_WINDOW_LUA,
      1,
      key,
      String(now),
      String(this.windowMs),
      String(this.limitPerWindow),
      member,
    )) as [number, number, number];

    const resetAt = now + retryAfterMs;

    return {
      allowed:      allowed === 1,
      remaining:    remaining ?? 0,
      resetAt,
      retryAfterMs: retryAfterMs ?? 0,
    };
  }
}

// Create one instance per quota level:
export const urlCreationLimiter = new RateLimiter(
  100,         // 100 URL creates
  60 * 60 * 1000, // per hour
);
```

### Express Middleware

```typescript
// src/middleware/rate-limit.middleware.ts
import type { Request, Response, NextFunction } from 'express';
import { urlCreationLimiter } from '../resilience/rate-limiter.js';

export async function urlCreationRateLimit(
  req: Request,
  res: Response,
  next: NextFunction,
): Promise<void> {
  // userId is attached by auth middleware upstream
  const userId = res.locals.userId as string | undefined;
  if (!userId) {
    next();  // unauthenticated request — let auth middleware reject it
    return;
  }

  const result = await urlCreationLimiter.check(`user:${userId}`);

  // Always send rate limit headers (good API practice)
  res.setHeader('X-RateLimit-Limit',     String(urlCreationLimiter['limitPerWindow']));
  res.setHeader('X-RateLimit-Remaining', String(result.remaining));
  res.setHeader('X-RateLimit-Reset',     String(Math.ceil(result.resetAt / 1000)));

  if (!result.allowed) {
    res.setHeader('Retry-After', String(Math.ceil(result.retryAfterMs / 1000)));
    res.status(429).json({
      error:      'Too many requests',
      code:       'RATE_LIMIT_EXCEEDED',
      retryAfter: Math.ceil(result.retryAfterMs / 1000),
    });
    return;
  }

  next();
}
```

### Nginx Edge Rate Limiting

```nginx
# nginx/api-gateway.conf (extends the config from Module 07)

http {
  # Per-IP: 100 requests/second, 10MB zone
  limit_req_zone $binary_remote_addr zone=ip_zone:10m rate=100r/s;

  # Per-user (use $http_x_user_id injected by auth middleware)
  # Coarser limit — app layer enforces fine-grained quotas
  limit_req_zone $http_x_user_id   zone=user_zone:20m rate=20r/s;

  server {
    # Apply IP-level limit everywhere
    limit_req zone=ip_zone burst=200 nodelay;

    location /api/v1/urls {
      # Apply per-user limit on write endpoints
      limit_req zone=user_zone burst=10 nodelay;

      # Custom 429 response body
      limit_req_status 429;

      proxy_pass http://scaleforge_upstream;
    }
  }
}
```

### Testing the Rate Limiter

```typescript
// scripts/test-rate-limit.ts
// Sends 120 requests quickly and counts 429 responses

async function testRateLimit() {
  const results = { ok: 0, limited: 0, other: 0 };
  
  const requests = Array.from({ length: 120 }, (_, i) =>
    fetch('http://localhost:3000/api/v1/urls', {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'Authorization': 'Bearer <test-token>',
      },
      body: JSON.stringify({ longUrl: `https://example.com/${i}` }),
    })
  );

  const responses = await Promise.all(requests);

  for (const res of responses) {
    if (res.status === 201)      results.ok++;
    else if (res.status === 429) results.limited++;
    else                         results.other++;
  }

  console.table(results);
  // Expected: { ok: 100, limited: 20, other: 0 }
  
  // Check headers on a 429 response
  const limited = responses.find(r => r.status === 429);
  if (limited) {
    console.log('Retry-After header:', limited.headers.get('Retry-After'));
  }
}
```

---

## Try It Yourself

**Exercise:** Add per-user rate limiting to the `/api/v1/urls` route.

```typescript
// TODO: 
// 1. Apply `urlCreationRateLimit` middleware to POST /api/v1/urls
// 2. Start ScaleForge locally
// 3. Run the test-rate-limit.ts script above
// 4. Verify:
//    - First 100 requests succeed (201)
//    - 101st+ request immediately returns 429 with a Retry-After header
//    - After waiting `Retry-After` seconds, a new request succeeds
```

<details>
<summary>Show the rate limiting behavior trace</summary>

```
Request #1–100:   201 Created  (X-RateLimit-Remaining: 99 → 0)
Request #101:     429 Too Many Requests
                  X-RateLimit-Remaining: 0
                  Retry-After: 3599  ← seconds until oldest slot frees up

After 3600 seconds (or clear Redis key in dev):
Request #102:     201 Created  (X-RateLimit-Remaining: 99)

Redis inspection:
  ZCARD ratelimit:user:<userId>   → 100
  ZRANGE ratelimit:user:<userId> 0 -1 WITHSCORES
    → 100 members, scores = epoch timestamps in ms
```

</details>

---

## Capstone Connection

ScaleForge enforces a 100 URL-per-hour limit per user to prevent abuse (bulk link-farming, scraping campaigns). The limit lives in Redis — the same cluster used for redirect caching — so it's available in under 1ms without a database round trip. The Nginx layer adds a coarser IP-based limit (100 req/s) that activates even before the Node.js process is touched, protecting against raw traffic floods that could starve legitimate users.
