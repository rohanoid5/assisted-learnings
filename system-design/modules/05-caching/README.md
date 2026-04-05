# Module 05 — Caching

## Overview

Caching is the single most effective technique for reducing database load and improving read latency. ScaleForge's redirect path already uses Redis as a cache; this module makes that design rigorous — covering eviction policies, cache invalidation, multi-tier caching, and the patterns that prevent cache from becoming a source of bugs.

## Learning Objectives

By the end of this module you will be able to:

- [ ] Compare LRU, LFU, and TTL-based eviction and choose appropriately
- [ ] Implement cache-aside, read-through, and write-through patterns
- [ ] Solve cache invalidation on data mutations
- [ ] Prevent cache stampede with probabilistic refresh or locking
- [ ] Design a multi-tier cache (Nginx → Redis → Postgres)
- [ ] Choose the right Redis data structure for each use case

## Topics

| # | Topic | Est. Time |
|---|-------|-----------|
| 01 | [Caching Strategies](01-caching-strategies.md) | 45 min |
| 02 | [Redis Deep Dive](02-redis-deep-dive.md) | 60 min |
| 03 | [Cache Invalidation](03-cache-invalidation.md) | 45 min |
| 04 | [Cache Stampede and Thundering Herd](04-cache-stampede.md) | 30 min |
| 05 | [Multi-Tier Caching](05-multi-tier-caching.md) | 30 min |
| Exercises | [Hands-on exercises](exercises/README.md) | 2–3 hrs |

**Total estimated time:** 4–6 hours

## Prerequisites

- Module 01 — Foundations (consistency patterns)
- Module 04 — Databases (Redis as used alongside Postgres)

## Capstone Milestone

By the end of Module 05, ScaleForge has a complete multi-tier cache:

```
Request: GET /abc123

  Tier 1: Nginx proxy cache (memory)
  ├── HIT: return in ~0.1ms, app never reached
  └── MISS: forward to app

  Tier 2: Redis (app checks first)
  ├── HIT: return in ~0.5ms, Postgres not queried
  └── MISS: query Postgres

  Tier 3: PostgreSQL (source of truth)
  └── READ: ~2ms with index scan, populate Redis on miss

  Invalidation on URL update:
    PATCH /api/v1/urls/:code
    → DELETE redis key url:{code}
    → Nginx proxy cache evacuated (or TTL wait)
    → Next request repopulates Redis from Postgres
    
  Redis memory policy: allkeys-lru
  Cache key TTL: 5 minutes (300s)
  Expected redirect p99: < 1ms (Redis hit)
```
