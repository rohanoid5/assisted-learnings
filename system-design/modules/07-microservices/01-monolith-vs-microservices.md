# 7.1 — Monolith vs. Microservices

## Concept

Microservices are not inherently better than monoliths — they are better for specific organizational and scaling challenges. A monolith is a single deployable unit; a microservices architecture is multiple independently deployable units. The decision boundary is determined by team size, service independence, and scaling asymmetry.

---

## Deep Dive

### The Complexity Curve

```
  Complexity vs. Scale:
  
  Complexity
     ▲
     │                              Microservices
     │                            /
     │                           / ← distributed system complexity:
     │                          /    service discovery, network failures,
     │                         /     data consistency, deployment matrix
     │               ─────────/
     │ Monolith ────/   ← inflection point (~50-100 engineers)
     │            /       before this: monolith simpler
     │           /        after this: microservices manageable
     └────────────────────────────────────────► Team Size
  
  A 5-person team building a microservices architecture is paying:
    - Kubernetes overhead
    - Service mesh complexity
    - Distributed tracing infrastructure
    - Cross-service contract versioning
  
  For a problem that a monolith solves with:
    - One git repo
    - One deployment
    - Function call instead of HTTP call
```

### The "Distributed Monolith" Anti-Pattern

```
  Worst of both worlds:
  
  Services deployed separately BUT:
    - Share the same database
    - Must be deployed together (synchronized releases)
    - Cross-service transactions are common
    - One service change requires changes in 5 others
    
  Signs you have a distributed monolith:
    - "We can't deploy Service A without Service B"
    - Service A calls Service B calls Service C synchronously
    - Integration tests require running ALL services
    - Shared database schema causes cross-team conflicts
    
  Fix: either go back to a true monolith, or establish proper
  service boundaries with independent databases and async communication.
```

### When to Split a Service

```
  Good reasons to extract a service:
  
  1. Scaling asymmetry:
     URL redirect: 100k req/s → needs 20 pods
     URL management (create/update): 1k req/s → needs 2 pods
     → Splitting saves 18 unnecessary redirect pods
     
  2. Team autonomy:
     Analytics team deploys 5x/day; URL team deploys 1x/week
     → Shared deployment = analytics team blocked by URL team
     → Separate services = independent deployment cadences
     
  3. Technology fit:
     Click analytics: ClickHouse (columnar) for aggregations
     URL storage: Postgres (relational) for ACID compliance
     → One database can't optimally serve both workloads
     
  4. Failure isolation:
     Analytics dashboard down → URL redirect must still work
     → Same process = analytics crash kills redirects
     → Separate services = redirect service immune to analytics bugs
     
  Bad reasons to split:
    - "Because microservices are cool"
    - "Our manager wants microservices"
    - Service boundary unclear → leads to distributed monolith
    - Team too small to run separate CI/CD pipelines
```

### Monolith-to-Microservices Migration Patterns

```
  Strangler Fig Pattern (safest):
  
    1. Identify one bounded context to extract (e.g., FlowForge notifications)
    2. Build new service alongside monolith
    3. Route new traffic to new service via feature flag or API gateway
    4. Migrate existing traffic gradually (10% → 50% → 100%)
    5. Delete the code from monolith
    
  Anti-Corruption Layer:
  
    New service needs data from monolith's DB but shouldn't couple to its schema.
    Solution: expose a thin API on the monolith side that translates internal models
    to the new service's domain language.
    
  Parallel Run (safest for correctness):
  
    Run old code and new code simultaneously.
    Compare outputs.
    Switch over when outputs match consistently.
    Expensive but eliminates regression risk.
```

---

## Code Examples

### Modular Monolith as Stepping Stone

```typescript
// src/modules/url/url.service.ts — encapsulated module, can be extracted later

// The key discipline: this module communicates with other modules ONLY
// through defined interfaces — never by importing internal repositories directly.
// This lets you swap the module for an HTTP client without changing callers.

export interface UrlService {
  createUrl(data: CreateUrlInput): Promise<Url>;
  getTarget(code: string): Promise<string | null>;
  updateUrl(code: string, data: UpdateUrlInput): Promise<Url>;
  deleteUrl(code: string): Promise<void>;
}

// The actual implementation (monolith version — uses DB directly)
export class UrlServiceImpl implements UrlService {
  constructor(
    private readonly db: Pool,
    private readonly cache: Redis
  ) {}

  async getTarget(code: string): Promise<string | null> {
    const cached = await this.cache.get(`url:${code}`);
    if (cached !== null) return cached === '\x00' ? null : cached;

    const result = await this.db.query<{ target_url: string }>(
      'SELECT target_url FROM urls WHERE code = $1 AND deleted_at IS NULL',
      [code]
    );
    const target = result.rows[0]?.target_url ?? null;
    await this.cache.set(`url:${code}`, target ?? '\x00', 'EX', 300);
    return target;
  }

  // ... other methods
  async createUrl(_data: CreateUrlInput): Promise<Url> { throw new Error('not implemented'); }
  async updateUrl(_code: string, _data: UpdateUrlInput): Promise<Url> { throw new Error('not implemented'); }
  async deleteUrl(_code: string): Promise<void> { throw new Error('not implemented'); }
}

// Future microservice version (drop-in replacement — same interface):
export class UrlServiceClient implements UrlService {
  constructor(private readonly baseUrl: string) {}

  async getTarget(code: string): Promise<string | null> {
    const res = await fetch(`${this.baseUrl}/internal/urls/${code}/target`);
    if (res.status === 404) return null;
    if (!res.ok) throw new Error(`URL service error: ${res.status}`);
    const body = await res.json() as { target: string };
    return body.target;
  }

  // ... other methods
  async createUrl(_data: CreateUrlInput): Promise<Url> { throw new Error('not implemented'); }
  async updateUrl(_code: string, _data: UpdateUrlInput): Promise<Url> { throw new Error('not implemented'); }
  async deleteUrl(_code: string): Promise<void> { throw new Error('not implemented'); }
}

interface CreateUrlInput { url: string; customCode?: string; }
interface UpdateUrlInput { url?: string; }
interface Url { code: string; targetUrl: string; createdAt: Date; }
```

---

## Try It Yourself

**Exercise:** Identify service boundaries in a hypothetical feature.

```
  Scenario: You're adding user accounts to ScaleForge. Users can:
    - Sign up / log in (auth)
    - Create URLs (already exists)
    - See their URLs in a dashboard (read model)
    - Get a weekly email report of their URL statistics (notification)
    
  Questions:
  1. Which pieces are strong candidates to be separate services?
  2. For each candidate, what data store would you choose and why?
  3. What would happen if the "email report" service has an outage?
     Should it affect the ability to create URLs?
  4. Draw the service communication diagram showing sync vs async calls.
```

<details>
<summary>Show suggested architecture</summary>

```
  Services:
  
  ┌──────────────┐   ┌─────────────────────────────┐
  │  Auth Service│   │  ScaleForge URL Service      │
  │  (JWT issuer)│   │  (URL CRUD + redirect)       │
  │  Postgres    │   │  Postgres + Redis             │
  └──────────────┘   └───────────────┬──────────────┘
                                     │  (async event: url_click_recorded)
                                     ▼
  ┌───────────────────────────────┐  ┌─────────────────────────────────┐
  │  Analytics Service            │  │  FlowForge Notification Service  │
  │  (click aggregation, reports) │  │  (sends weekly report email)     │
  │  ClickHouse / TimescaleDB     │  │  Postgres + BullMQ               │
  └───────────────────────────────┘  └─────────────────────────────────┘
  
  Communication:
    Sync:  Auth → JWT validation (library, no network call)
    Sync:  URL Service calls Auth Service to check user exists on URL create
    Async: URL Service publishes click events → Analytics consumes
    Async: Analytics publishes weekly_report_ready → FlowForge consumes → Sends email
    
  Failure isolation:
    FlowForge down → weekly reports delayed, but URL creation + redirect unaffected
    Analytics down → no dashboards, but redirect still works (Click events queue until recovers)
    Auth down → cannot create new URLs or log in; existing redirects still work (no auth needed)
```

</details>

---

## Capstone Connection

ScaleForge and FlowForge are deliberately kept as separate services despite sharing a Docker Compose network. ScaleForge's redirect path never calls FlowForge synchronously — it only enqueues a click event (async). This means FlowForge can be stopped for maintenance with zero impact on URL redirect performance.
