# 3.5 — CDNs and Edge Caching

## Concept

A CDN (Content Delivery Network) is a globally distributed network of servers that cache content close to users, reducing the physical distance data must travel. For ScaleForge, static assets (JS, CSS) belong on a CDN; the redirect path does not — because each redirect must be logged for analytics.

---

## Deep Dive

### CDN Architecture

```
Without CDN (Tokyo user → US-based origin):
  User (Tokyo) ──────────────────────────────────────► Origin (Virginia)
                     ~150ms one-way RTT
                     300ms round trip per request

With CDN:
  User (Tokyo) ──────► Edge PoP (Tokyo) ──────► Origin (Virginia)
               ~2ms            (first request, cold cache)
                                └──► Cache hit: all future requests
                                     served from Tokyo in ~2ms

  PoP = Point of Presence (a CDN server cluster in that region)

Edge PoP cache hierarchy:
  User
   │
   ▼
Edge PoP (Tokyo)        ← Closest to user — fastest, smallest cache
   │  cache miss
   ▼
Regional PoP (Singapore) ← "Mid-tier" cache — larger, less expensive
   │  cache miss
   ▼
Origin Shield (US-East)  ← Single point of contact for origin
   │  cache miss                (shields origin from repeat cache misses
   ▼                             from all global edges simultaneously)
Origin Server (Virginia) ← Your actual server
```

### Cache-Control Headers

```
Header directives that matter for web assets:

  public           — response may be cached by any cache (CDN, browser, proxy)
  private          — only the user's browser may cache it (not CDNs)
  no-store         — nothing may cache it anywhere
  max-age=N        — browser cache TTL in seconds
  s-maxage=N       — CDN/shared cache TTL (overrides max-age for CDNs)
  must-revalidate  — cache MUST check origin when TTL expires
  immutable        — browser won't revalidate until max-age expires
                     (saves conditional GET on every navigation)
  stale-while-revalidate=N — serve stale cached content while revalidating async

Route type → recommended header:
  ──────────────────────────────────────────────────────────────
  /static/app.a1b2c3.js        public, s-maxage=31536000, immutable
  (hashed filename)             CDN caches 1 year; browser downloads once
  ──────────────────────────────────────────────────────────────
  /                            public, s-maxage=300, stale-while-revalidate=60
  (HTML shell)                  CDN caches 5 min; serve stale for 60s while refreshing
  ──────────────────────────────────────────────────────────────
  /api/v1/urls/:code           private, no-store
  (URL metadata — auth)         no CDN caching; browser shouldn't cache either
  ──────────────────────────────────────────────────────────────
  /:code                       no-store
  (the redirect endpoint)       NEVER cache — analytics requires origin hit
  ──────────────────────────────────────────────────────────────
```

### Cache Invalidation Strategies

```
Problem: A URL was updated. Old redirect is cached in CDN for 5 min. Users get wrong destination.

Strategies:
  1. Immutable assets + content hashing (best approach for static files)
     - Filename contains hash of content: app.a1b2c3.js
     - When content changes, hash changes → new filename → old URL never needs invalidation
     
  2. TTL expiry (simplest, acceptable for low-churn content)
     - Set short TTL (e.g., 60s) → stale at most 60s
     - Trade-off: higher origin load vs lower staleness
     
  3. Tag-based purge / surrogate keys (best for dynamic content on CDN)
     - Attach tags to responses: Surrogate-Key: url:abc user:123
     - When URL "abc" changes, send purge-tag request to CDN API
     - CDN evicts all responses tagged "url:abc" across all PoPs
     - Used by: Fastly, Cloudflare (Cache-Tag), Varnish (X-Cache-Tags)
     
  4. URL-based purge
     - Explicitly purge specific URL paths via CDN API
     - Slower than tag-based but simpler to implement
```

---

## Code Examples

### Express Cache-Control Middleware

```typescript
// src/middleware/cache-headers.middleware.ts
// Centralize all cache header decisions in one place.

import type { Request, Response, NextFunction } from 'express';

// Immutable static assets (hashed filenames)
export function cacheImmutable(req: Request, res: Response, next: NextFunction): void {
  res.set('Cache-Control', 'public, s-maxage=31536000, max-age=31536000, immutable');
  next();
}

// Dynamic but publicly cacheable content (homepage, public URL stats)
export function cacheShort(req: Request, res: Response, next: NextFunction): void {
  res.set('Cache-Control', 'public, s-maxage=60, stale-while-revalidate=30');
  next();
}

// Never cache — analytics endpoints, redirect endpoint, auth routes
export function cacheNone(req: Request, res: Response, next: NextFunction): void {
  res.set('Cache-Control', 'no-store');
  next();
}

// Private — authenticated user data (URL management dashboard)
export function cachePrivate(req: Request, res: Response, next: NextFunction): void {
  res.set('Cache-Control', 'private, no-store');
  next();
}
```

```typescript
// src/app.ts — attach cache middleware per route group
import { cacheImmutable, cacheNone, cachePrivate, cacheShort } from './middleware/cache-headers.middleware.js';

// Static assets served by Express (in dev) — immutable + fingerprinted names
app.use('/static', cacheImmutable, express.static('public'));

// The redirect endpoint — MUST hit origin for analytics
app.get('/:code([a-zA-Z0-9_\\-]{4,12})', cacheNone, redirectHandler);

// Public stats page for a short URL
app.get('/api/v1/urls/:code/stats', cacheShort, statsHandler);

// Authenticated URL management
app.use('/api/v1/urls', cachePrivate, urlsRouter);
```

### CDN Surrogate Keys for Cache Purging

```typescript
// src/routes/redirect.router.ts
// Attach URL code as surrogate key on responses from the stats endpoint.
// When a URL is updated, purge all cached stats pages for that URL via CDN API.

// Tag responses — CDN reads this header and indexes by tag
app.get('/api/v1/urls/:code/stats', async (req, res) => {
  const { code } = req.params;
  const stats = await getStats(code);

  // Cloudflare uses Cache-Tag, Fastly uses Surrogate-Key
  res.set('Surrogate-Key', `url:${code}`);
  res.set('Cache-Control', 'public, s-maxage=60');
  res.json(stats);
});

// Purge when URL is updated
async function purgeUrlFromCDN(code: string): Promise<void> {
  const cloudflareZoneId = process.env.CF_ZONE_ID!;
  const cfToken = process.env.CF_API_TOKEN!;

  await fetch(`https://api.cloudflare.com/client/v4/zones/${cloudflareZoneId}/purge_cache`, {
    method: 'POST',
    headers: {
      Authorization: `Bearer ${cfToken}`,
      'Content-Type': 'application/json',
    },
    body: JSON.stringify({ tags: [`url:${code}`] }),
  });
}

app.patch('/api/v1/urls/:code', async (req, res) => {
  const { code } = req.params;
  const updatedUrl = await updateUrl(code, req.body);

  // Invalidate CDN cache for this URL's stats page
  await purgeUrlFromCDN(code);

  res.json(updatedUrl);
});
```

---

## Try It Yourself

**Exercise:** Write a function that returns the correct `Cache-Control` header value for each ScaleForge route type, and verify Nginx forwards headers to the CDN correctly.

```typescript
// cache-policy.exercise.ts

type RouteType =
  | 'static-hashed'       // /static/app.abc123.js — fingerprinted filename
  | 'redirect'            // /:code — actual redirect endpoint
  | 'url-stats-public'    // /api/v1/urls/:code/stats
  | 'url-management';     // /api/v1/urls (auth required)

// TODO:
// 1. Implement getCacheControlHeader(routeType: RouteType): string
//    - static-hashed → 1-year immutable
//    - redirect → no-store
//    - url-stats-public → 60s CDN cache + stale-while-revalidate=30
//    - url-management → private, no-store

// 2. Write a test using built-in Node assert:
//    assert.equal(getCacheControlHeader('redirect'), 'no-store')
//    assert.ok(getCacheControlHeader('static-hashed').includes('immutable'))

// 3. In nginx.conf, add proxy_hide_header Cache-Control and
//    proxy_pass_header Cache-Control so your Express headers
//    reach the CDN (Nginx strips them by default unless configured)
```

<details>
<summary>Show solution</summary>

```typescript
// cache-policy.solution.ts
import assert from 'node:assert/strict';

type RouteType = 'static-hashed' | 'redirect' | 'url-stats-public' | 'url-management';

function getCacheControlHeader(routeType: RouteType): string {
  switch (routeType) {
    case 'static-hashed':
      return 'public, s-maxage=31536000, max-age=31536000, immutable';
    case 'redirect':
      return 'no-store';
    case 'url-stats-public':
      return 'public, s-maxage=60, stale-while-revalidate=30';
    case 'url-management':
      return 'private, no-store';
  }
}

assert.equal(getCacheControlHeader('redirect'), 'no-store');
assert.equal(getCacheControlHeader('url-management'), 'private, no-store');
assert.ok(getCacheControlHeader('static-hashed').includes('immutable'));
assert.ok(getCacheControlHeader('url-stats-public').includes('stale-while-revalidate'));

console.log('All cache policy assertions passed ✓');
```

```nginx
# nginx.conf — ensure app cache headers pass through to CDN
location /api/ {
    proxy_pass http://app_backend;
    
    # Nginx buffers proxy responses. By default it may modify Cache-Control.
    # Use proxy_pass_header to explicitly forward headers to the client (CDN).
    proxy_pass_header Cache-Control;
    proxy_pass_header Surrogate-Key;
    
    # Remove X-Powered-By (security hygiene)
    proxy_hide_header X-Powered-By;
}
```

</details>

---

## Capstone Connection

ScaleForge's redirect path (`/:code`) must **never** be CDN-cached because every redirect must be counted for accurate analytics. This is why `Cache-Control: no-store` is set on that route. However, ScaleForge's frontend bundle (`/static/*.js`) is perfectly suited for CDN distribution with 1-year TTLs — the build pipeline generates content-hashed filenames so stale cache is never an issue. In Module 08 (Performance & Monitoring), you'll add a Prometheus counter for CDN cache hit rate (by scraping the `X-Cache-Status` header that Nginx injects).
