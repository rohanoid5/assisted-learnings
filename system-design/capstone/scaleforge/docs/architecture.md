# ScaleForge — Architecture Document

## 1. Functional Requirements
<!-- List 4–6 things the system must DO -->
- User creates a shortend URL
- Opening any shortend url will track clicks, ip, country, device, timestamp
- Creator of the url will be able to see aggregated reports on analytics

## 2. Non-Functional Requirements
| Property      | Target | Rationale |
|---------------|--------|-----------|
| Availability  | 99.99% |           |
| Redirect p99  | 50ms   |           |
| Write latency | 100ms  |           |
| Throughput    | 10k/s for read and 100/s for write|

## 3. Capacity Estimation
<!-- Run and paste the output of: node --loader ts-node/esm docs/capacity-estimates.ts -->
=== ScaleForge Capacity Estimates ===
Read QPS (avg):  1157 req/s
Read QPS (peak): 11574 req/s
Write QPS (avg): 11.57 req/s
URL storage:     182.5 GB/year
Click storage:   7.3 TB/year

=== Architectural Decisions ===
Need caching?    YES — 11574 peak req/s exceeds single-DB capacity
Need async writes? YES — 100M click rows/day blocks redirect if synchronous
Need partitioning? YES — clicks table grows 7.3 TB/year

## 4. High-Level Architecture
<!-- ASCII art or written description of components -->
Client → DNS → Nginx LB → App Servers (×3)
                                  ├── Redis Cache  (URL lookup)
                                  └── PostgreSQL   (source of truth)
                                  └── BullMQ Queue → Worker → Clicks DB

## 5. Data Model
<!-- Tables/entities and their key fields -->
- User(id, email, passwordhash, tier, createdAt)
- ShortURL(id, code, url, userId, expiredAt, clickCount, createdAt)
- Click(id, shortUrlId, ip, userAgent, country, device, timestamp)
- Report(id, shortUrlId, period, totalClicks, uniqueVisitors, createdAt)

## 6. Key Design Decisions
<!-- For each decision, state: what, why, what was considered -->
1. Redis for URL Lookup
2. BullMQ for Click tracking
3. HTTP 302 redirect instead of 301 as 301 is cached by browser. We will lose analytics.
