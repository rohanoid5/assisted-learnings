# 2.2 — REST vs. GraphQL vs. gRPC

## Concept

There is no universally best API paradigm — the right choice depends on who is calling, how data shapes vary across clients, and whether you need streaming. REST is the default for public HTTP APIs; GraphQL solves the over-fetching/under-fetching problem for complex client-driven queries; gRPC is optimised for high-throughput, low-latency inter-service communication.

---

## Deep Dive

### REST — Representational State Transfer

```
Design philosophy: Resources + uniform interface (HTTP verbs)
  GET    /urls/:code        → read
  POST   /urls              → create
  PATCH  /urls/:code        → update
  DELETE /urls/:code        → delete
  GET    /urls/:code/clicks → sub-resource

Strengths:
  ✓ Universal HTTP support (browsers, curl, any HTTP client)
  ✓ Stateless — scales horizontally
  ✓ HTTP caching works naturally (GET + proper Cache-Control)
  ✓ Easy to version (/v1/, /v2/), simple to document (OpenAPI)

Weaknesses:
  ✗ Over-fetching: GET /users returns ALL fields even if you need 2
  ✗ Under-fetching: GET /urls/:code + GET /users/:id = 2 round trips
  ✗ No type safety across service boundaries (JSON is untyped)
  ✗ REST "fatigue": 50+ endpoints for complex domains
```

### GraphQL — Query Language for APIs

```
Design philosophy: Client specifies EXACTLY the data shape needed

Query:
  query {
    shortUrl(code: "abc123") {
      longUrl
      clickCount
      owner { email }   ← joined in ONE request
    }
  }

Response:
  {
    "data": {
      "shortUrl": {
        "longUrl": "https://example.com",
        "clickCount": 42,
        "owner": { "email": "user@example.com" }
      }
    }
  }

Strengths:
  ✓ Single endpoint, client specifies shape — no over/under-fetch
  ✓ Strong typing via schema → code generation for TypeScript
  ✓ Introspection → auto-generated docs
  ✓ Great for complex nested queries (social feeds, dashboards)

Weaknesses:
  ✗ Complex queries can cause N+1 database queries (requires DataLoader)
  ✗ Caching harder (all requests are POST to /graphql)
  ✗ Higher learning curve to set up and secure
  ✗ Not suitable for public APIs (arbitrary query complexity = DoS risk)
```

### gRPC — Google Remote Procedure Call

```
Design philosophy: Typed function calls between services

// url_service.proto
service UrlService {
  rpc CreateUrl(CreateUrlRequest) returns (CreateUrlResponse);
  rpc LookupUrl(LookupUrlRequest) returns (LookupUrlResponse);
  rpc StreamClicks(StreamClicksRequest) returns (stream ClickEvent);
}

Encoding: Protocol Buffers (binary) — 3–10× smaller than JSON
Transport: HTTP/2 multiplexed streams
Code generation: client + server stubs generated from .proto

Strengths:
  ✓ Strongly typed, binary → fastest inter-service communication
  ✓ Built-in streaming (server, client, bidirectional)
  ✓ Code generation: no manual type syncing between services
  ✓ Deadline propagation built in (timeout cascades automatically)

Weaknesses:
  ✗ Binary: not human-readable; harder to debug with curl
  ✗ Requires HTTP/2 infrastructure everywhere
  ✗ Browser support requires gRPC-Web gateway (not native gRPC)
  ✗ Overkill for simple CRUD scenarios
```

### Decision Matrix

| Criterion | REST | GraphQL | gRPC |
|-----------|------|---------|------|
| Browser client | ✓ Best | ✓ Good | ✗ Needs gateway |
| Mobile client | ✓ Good | ✓ Best (bandwidth) | ✓ Good |
| Microservice-to-microservice | ✓ Good | ✗ Overkill | ✓ Best |
| Public API | ✓ Best | ✗ Risk | ✗ Not standard |
| Streaming | ✗ Polling/SSE | ✗ Subscriptions (WS) | ✓ Native |
| Caching | ✓ Native GET cache | ✗ Complex | ✗ No HTTP cache |
| Team learning curve | Low | Medium | High |

**ScaleForge uses:**
- REST for the public API (browser, mobile, 3rd party)
- gRPC for analytics-service → url-service in Module 07 (high-frequency, typed)
- SSE for real-time analytics dashboard updates (Module 02.3)

---

## Code Examples

### REST API for ScaleForge URL Management

```typescript
// src/routes/urls.router.ts

import { Router } from 'express';
import { z } from 'zod';
import { UrlService } from '../services/url.service.js';
import { authenticate } from '../middleware/auth.middleware.js';

export const urlsRouter = Router();

const CreateUrlSchema = z.object({
  longUrl: z.string().url({ message: 'Must be a valid URL' }),
  customCode: z.string().regex(/^[a-z0-9\-_]{4,20}$/).optional(),
  expiresIn: z.number().int().min(1).max(365).optional(), // days
});

// POST /api/v1/urls — Create a short URL
urlsRouter.post('/', authenticate, async (req, res) => {
  const result = CreateUrlSchema.safeParse(req.body);
  if (!result.success) {
    res.status(422).json({ errors: result.error.flatten().fieldErrors });
    return;
  }

  const { longUrl, customCode, expiresIn } = result.data;
  const { code } = await UrlService.create(longUrl, req.user.id, { customCode, expiresIn });
  res.status(201).json({ code, shortUrl: `${process.env.SHORT_URL_BASE}/${code}` });
});

// GET /api/v1/urls — List authenticated user's URLs
urlsRouter.get('/', authenticate, async (req, res) => {
  const page = Math.max(1, Number(req.query.page) || 1);
  const limit = Math.min(100, Math.max(1, Number(req.query.limit) || 20));

  const urls = await UrlService.listByUser(req.user.id, { page, limit });
  res.json(urls);
});

// DELETE /api/v1/urls/:code — Delete a URL
urlsRouter.delete('/:code', authenticate, async (req, res) => {
  const deleted = await UrlService.delete(req.params.code, req.user.id);
  if (!deleted) {
    res.status(404).json({ error: 'URL not found' });
    return;
  }
  res.status(204).end();
});
```

### GraphQL Schema for ScaleForge Analytics (comparison)

```typescript
// This is how the same API would look in GraphQL — for comparison only.
// ScaleForge uses REST, but this illustrates why GraphQL helps for dashboards.

// schema.graphql
/*
type Query {
  shortUrl(code: String!): ShortUrl
  myUrls(page: Int, limit: Int): UrlConnection!
}

type ShortUrl {
  code: String!
  longUrl: String!
  clickCount: Int!
  createdAt: String!
  clicks(last: Int): [Click!]!      # Sub-resource in same query
  owner: User!                       # Joined data in same query
  analytics(period: Period!): Report # Aggregated data
}

# A client wanting only code + clickCount for a list view:
query LightList {
  myUrls { edges { node { code clickCount } } }
}
# → Returns ONLY those 2 fields, not the full object
*/
```

---

## Try It Yourself

**Exercise:** Add pagination to the URL list endpoint and validate the response matches the expected contract.

```typescript
// The GET /api/v1/urls endpoint above returns a flat array.
// Upgrade it to return a paginated response:

interface PaginatedResponse<T> {
  data: T[];
  meta: {
    page: number;
    limit: number;
    total: number;
    hasNextPage: boolean;
  };
}

// TODO:
// 1. Update UrlService.listByUser to return { data, total } instead of just an array
// 2. Compute hasNextPage from total, page, and limit
// 3. Return the PaginatedResponse shape from the handler
// 4. Test with: curl 'http://localhost:3001/api/v1/urls?page=1&limit=5'
//    and verify the meta fields are correct
```

<details>
<summary>Show solution</summary>

```typescript
// In UrlService.listByUser:
async listByUser(
  userId: string,
  { page, limit }: { page: number; limit: number }
): Promise<{ data: ShortURL[]; total: number }> {
  const [data, total] = await Promise.all([
    prisma.shortURL.findMany({
      where: { userId },
      skip: (page - 1) * limit,
      take: limit,
      orderBy: { createdAt: 'desc' },
    }),
    prisma.shortURL.count({ where: { userId } }),
  ]);
  return { data, total };
}

// In the route handler:
const { data, total } = await UrlService.listByUser(req.user.id, { page, limit });
res.json({
  data,
  meta: {
    page,
    limit,
    total,
    hasNextPage: page * limit < total,
  },
});
```

</details>

---

## Capstone Connection

The REST API scaffolded here (with Zod validation, 422 error responses, and paginated list) is the contract the ScaleForge frontend will use through all subsequent modules. In Module 07, when `analytics-service` needs to call `url-service` to enrich click data with URL metadata, you'll add a gRPC interface to `UrlService` alongside the REST API — serving both internal (gRPC) and external (REST) callers from the same domain logic.
