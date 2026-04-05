# 6.4 — REST API Design

## Concept

A well-designed REST API is predictable, self-documenting, and easy to evolve. This topic covers URL structure, HTTP method semantics, pagination, filtering, versioning, and the field envelope pattern used by APIs at scale (GitHub, Stripe, Twilio).

---

## Deep Dive

### URL Design

```
# Collections
GET    /api/v1/pipelines          List all pipelines
POST   /api/v1/pipelines          Create a pipeline

# Individual resources
GET    /api/v1/pipelines/:id      Get a pipeline
PATCH  /api/v1/pipelines/:id      Partial update
DELETE /api/v1/pipelines/:id      Delete

# Nested resources (owned by parent)
GET    /api/v1/pipelines/:id/jobs List jobs for a pipeline
POST   /api/v1/pipelines/:id/jobs Trigger a pipeline run

# Actions (non-CRUD operations)
POST   /api/v1/jobs/:id/cancel    Cancel a running job
POST   /api/v1/jobs/:id/retry     Retry a failed job
```

### Response Envelope

```typescript
// Consistent response shape across all endpoints
interface ApiResponse<T> {
  data: T;
  meta?: { total?: number; page?: number; limit?: number };
}

// Errors use RFC 7807 Problem Details
interface ProblemDetails {
  type: string;       // URI reference
  title: string;      // Short summary
  status: number;     // HTTP status code
  detail: string;     // Human-readable explanation
  instance?: string;  // Request ID
}

// Utility
function ok<T>(data: T, meta?: ApiResponse<T>['meta']): ApiResponse<T> {
  return { data, ...(meta ? { meta } : {}) };
}

res.json(ok({ id: pipeline.id, name: pipeline.name }));
res.json(ok(jobs, { total: 100, page: 2, limit: 20 }));
```

### Cursor-Based Pagination

```typescript
// Offset pagination breaks for large datasets and real-time data
// Cursor-based pagination is stable and efficient
router.get('/', asyncHandler(async (req, res) => {
  const schema = z.object({
    limit: z.coerce.number().int().min(1).max(100).default(20),
    cursor: z.string().optional(),
    status: z.enum(['ACTIVE', 'INACTIVE']).optional(),
  });

  const { limit, cursor, status } = schema.parse(req.query);

  const items = await db.pipeline.findMany({
    take: limit + 1, // fetch one extra to detect "has next page"
    where: status ? { status } : undefined,
    ...(cursor ? { cursor: { id: cursor }, skip: 1 } : {}),
    orderBy: { createdAt: 'desc' },
  });

  const hasNext = items.length > limit;
  const data = hasNext ? items.slice(0, limit) : items;
  const nextCursor = hasNext ? data[data.length - 1].id : undefined;

  res.json(ok(data, { limit, ...(nextCursor ? { nextCursor } : {}) }));
}));
```

### Versioning Strategy

```typescript
// URL versioning (simplest, most visible)
app.use('/api/v1', v1Router);
app.use('/api/v2', v2Router); // can introduce breaking changes

// Deprecation headers
v1Router.use((_req, res, next) => {
  res.setHeader('Deprecation', 'true');
  res.setHeader('Sunset', 'Sat, 01 Jan 2026 00:00:00 GMT');
  next();
});
```

---

## Try It Yourself

**Exercise:** Implement the `POST /api/v1/jobs/:id/cancel` action endpoint.

```typescript
router.post('/:id/cancel', authenticate, requireRole('ADMIN', 'OPERATOR'), asyncHandler(async (req, res) => {
  // TODO: find job, verify it's RUNNING, call engine.cancel(id), update status, return updated job
}));
```

<details>
<summary>Show solution</summary>

```typescript
router.post('/:id/cancel', authenticate, requireRole('ADMIN', 'OPERATOR'), asyncHandler(async (req, res) => {
  const job = await db.job.findUnique({ where: { id: req.params.id } });
  if (!job) throw new NotFoundError('Job', req.params.id);
  if (job.status !== 'RUNNING') {
    throw new ValidationError(`Cannot cancel a job with status ${job.status}`);
  }

  engine.cancel(job.id); // signals the AbortController

  const updated = await db.job.update({
    where: { id: job.id },
    data: { status: 'CANCELLED' },
  });

  res.json(ok(updated));
}));
```

</details>

---

## Capstone Connection

PipeForge's API follows these conventions exactly. The `GET /api/v1/jobs` endpoint supports cursor-based pagination. The `POST /api/v1/jobs/:id/cancel` action endpoint connects to the `AbortController` mechanism built in Module 02.
