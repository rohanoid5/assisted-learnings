# 2.4 — DNS and Routing

## Concept

DNS (Domain Name System) translates human-readable names into IP addresses, but it also plays a critical role in routing traffic between regions, enabling CDN edge nodes, and managing failover. Understanding DNS TTLs, propagation, and routing strategies is essential for designing globally available systems.

---

## Deep Dive

### DNS Resolution Chain

```
User types: https://scaleforge.io/abc123

Browser cache miss
       │
       ▼
OS cache miss (local hosts file)
       │
       ▼
Recursive Resolver (ISP or 8.8.8.8)
       │
       ▼
Root Name Server (.)
  → "I don't know .io, ask Verisign"
       │
       ▼
TLD Name Server (.io)
  → "scaleforge.io is at ns1.cloudflare.com"
       │
       ▼
Authoritative Name Server (Cloudflare DNS for scaleforge.io)
  → "scaleforge.io → 104.21.55.1 (TTL: 300)"
       │
       ▼
Browser connects to 104.21.55.1

Total time: first lookup ~100-200ms, cached: ~0ms
```

### TTL — Time to Live

TTL (in seconds) tells resolvers how long to cache the DNS record:

| TTL | Effect | Use Case |
|-----|--------|----------|
| 60 | Changes propagate in ~1 min | Active deployment or failover in progress |
| 300 (5 min) | Good balance | Normal operations |
| 3600 (1 hr) | Stable records | Records that rarely change |
| 86400 (1 day) | Maximum cache | Static infrastructure (mail servers) |

**Before a DNS change**: lower TTL to 60 seconds, 24 hours in advance. After the change is confirmed, raise TTL back.

**Why this matters for ScaleForge**: If your primary server's IP changes (new load balancer, failover), the world still routes to the old IP until TTL expires. A 24-hour TTL means 24 hours of downtime risk.

### DNS Record Types

```
A Record:    scaleforge.io          → 104.21.55.1      (IPv4)
AAAA Record: scaleforge.io          → 2606:4700::       (IPv6)
CNAME:       www.scaleforge.io      → scaleforge.io     (alias)
MX:          scaleforge.io          → mail.scaleforge.io (email routing)
TXT:         scaleforge.io          → "v=spf1 include:..."  (SPF for email)
NS:          scaleforge.io          → ns1.cloudflare.com (delegation)
SRV:         _grpc._tcp.api.scaleforge.io → host:port  (gRPC service discovery)
```

### Routing Strategies via DNS

**Round-robin DNS**: Multiple A records for the same name — resolver cycles through them:
```
scaleforge.io → 104.21.55.1
scaleforge.io → 104.21.55.2
scaleforge.io → 104.21.55.3
```
Problem: No health checking — a failed server still receives traffic. Use a proper load balancer instead.

**Anycast routing**: Multiple globally distributed servers share the **same IP address**. BGP routing automatically sends users to the topographically nearest server.
```
User in Tokyo → IP 104.16.1.1 → Cloudflare Tokyo PoP
User in London → IP 104.16.1.1 → Cloudflare London PoP
(Same IP, different physical servers — BGP magic)
```
Used by: Cloudflare, Fastly CDN, all major DNS providers. This is how `1.1.1.1` works — same IP, 250+ global endpoints.

**GeoDNS**: DNS server returns different IP based on query source region:
```
Query from EU → 185.60.100.1  (EU data center)
Query from US → 104.21.55.1   (US data center)
```
Used for: data residency requirements (GDPR), latency optimisation.

---

## Code Examples

### DNS Lookup with Node.js

```typescript
// experiments/dns-lookup.ts
// Explore DNS resolution programmatically

import dns from 'node:dns/promises';

async function inspectDns(hostname: string) {
  console.log(`\n=== DNS Inspection: ${hostname} ===`);

  try {
    // A records (IPv4)
    const addresses = await dns.resolve4(hostname);
    console.log('IPv4 A records:', addresses);

    // All records with TTL
    const addressesWithTtl = await dns.resolve4(hostname, { ttl: true });
    addressesWithTtl.forEach(r => console.log(`  ${r.address} TTL: ${r.ttl}s`));

    // Reverse lookup (PTR record)
    if (addresses[0]) {
      const hostnames = await dns.reverse(addresses[0]).catch(() => ['(no PTR)']);
      console.log('Reverse lookup:', hostnames);
    }

    // Name servers
    const ns = await dns.resolveNs(hostname);
    console.log('Name servers:', ns);

  } catch (err) {
    console.error('DNS error:', err);
  }
}

// Compare DNS resolution times across providers
async function benchmarkDnsProviders(hostname: string) {
  const providers = [
    { name: 'Cloudflare', ip: '1.1.1.1' },
    { name: 'Google', ip: '8.8.8.8' },
    { name: 'Default (ISP)', ip: 'default' },
  ];

  for (const provider of providers) {
    const resolver = new dns.Resolver();
    if (provider.ip !== 'default') {
      resolver.setServers([provider.ip]);
    }

    const start = performance.now();
    await resolver.resolve4(hostname).catch(() => []);
    const elapsed = performance.now() - start;
    console.log(`${provider.name}: ${elapsed.toFixed(2)}ms`);
  }
}

await inspectDns('cloudflare.com');
await benchmarkDnsProviders('github.com');
```

### Implementing DNS-Based Health Check in Docker Compose

```yaml
# For local development, use Docker's internal DNS to route between services
# capstone/scaleforge/docker-compose.yml excerpt:

# Docker Compose provides automatic DNS for service names:
# "postgres" resolves to the container's IP
# "redis" resolves to the Redis container's IP
# This simulates service discovery in Kubernetes (CoreDNS)

services:
  app:
    # environment:
    #   DATABASE_URL: postgresql://user:pass@postgres:5432/scaleforge
    #                                          ^^^^^^^^^ Docker DNS name
    #   REDIS_URL: redis://redis:6379
    #                       ^^^^^ Docker DNS name
    depends_on:
      postgres:
        condition: service_healthy
      redis:
        condition: service_healthy

  postgres:
    healthcheck:
      test: ["CMD-SHELL", "pg_isready -U $$POSTGRES_USER"]
      interval: 5s
      timeout: 5s
      retries: 5

  redis:
    healthcheck:
      test: ["CMD", "redis-cli", "ping"]
      interval: 5s
      timeout: 3s
      retries: 5
```

---

## Try It Yourself

**Exercise:** Simulate a DNS TTL scenario — what happens to ScaleForge users if the long URL changes after a `301` redirect was cached?

```typescript
// experiments/ttl-simulation.ts

// Simulates two scenarios:
// A) 301 Permanent Redirect (browser caches)
// B) 302 Temporary Redirect (no browser cache)

// TTLs involved:
// - DNS TTL: 300s (5 min) — how long the IP of scaleforge.io is cached
// - 301 cache: Permanent — browser caches redirect URL forever (until cleared)
// - 302 cache: 0 — browser re-queries ScaleForge on every visit
// - Redis URL cache TTL: 300s — how long the longUrl is cached after DB lookup

// Question: A user visits scaleforge.io/abc123 at T=0.
//           Domain owner changes longUrl at T=100s.
//           DNS TTL is 300s. Redis TTL is 300s.
//           Browser uses 302.
//
// At T=200s, the user visits scaleforge.io/abc123 again.
// Which URL do they get?
// List every cache involved and whether it still holds stale data.
```

<details>
<summary>Show answer</summary>

```
At T=200s with 302 redirects:

1. DNS cache: DNS TTL 300s, T=200s → still cached (100s remaining)
   → "scaleforge.io resolves to 104.21.55.1" — same server, no issue

2. ScaleForge browser cache: 302 → Cache-Control: no-store → NOT cached
   → Browser makes a new GET /:code request

3. Redis URL cache: Set at T=0 with 300s TTL → still cached (100s remaining)
   → Returns OLD longUrl ← STALE!

4. Result: User gets the OLD destination until Redis TTL expires at T=300s

To fix: When longUrl is updated, ALSO invalidate the Redis cache key (del url:abc123).
This is "cache invalidation on write" — covered in Module 05.
```

</details>

---

## Capstone Connection

ScaleForge's custom domain support (future feature) would use CNAME records pointing to ScaleForge's infrastructure. The DNS TTL analysis from this topic explains why deleting a short URL with a 300s Redis TTL means users may still be redirected for up to 5 minutes after deletion — an acceptable eventual consistency tradeoff that's documented in the architecture decision record from Module 01 Exercises.
