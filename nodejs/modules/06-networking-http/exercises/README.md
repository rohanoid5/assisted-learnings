# Module 6 — Exercises

## Overview

These exercises complete PipeForge's full API and add real-time WebSocket job progress.

---

## Exercise 1 — Pipelines Router

**Goal:** Implement `src/api/routes/pipelines.ts` with full CRUD.

```typescript
// GET    /api/v1/pipelines      — list with cursor pagination
// POST   /api/v1/pipelines      — create (validate with Zod)
// GET    /api/v1/pipelines/:id  — get by id
// PATCH  /api/v1/pipelines/:id  — partial update
// DELETE /api/v1/pipelines/:id  — soft delete (set status=INACTIVE)
```

Apply `authenticate` to all routes. Apply `requireRole('ADMIN')` to DELETE.

Test:
```bash
curl -X POST http://localhost:3000/api/v1/pipelines \
  -H "Authorization: Bearer <token>" \
  -H "Content-Type: application/json" \
  -d '{"name":"Daily ETL","steps":[{"name":"extract","type":"CSV_IMPORT"}]}'
```

---

## Exercise 2 — Jobs Router with Trigger

**Goal:** Implement `src/api/routes/jobs.ts`.

```typescript
// POST /api/v1/pipelines/:id/jobs  — trigger a new pipeline run
// GET  /api/v1/jobs                — list jobs with pagination
// GET  /api/v1/jobs/:id            — get job detail
// POST /api/v1/jobs/:id/cancel     — cancel running job
// POST /api/v1/jobs/:id/retry      — retry failed job
// GET  /api/v1/jobs/:id/logs       — stream NDJSON logs (Module 04 pattern)
```

---

## Exercise 3 — JWT Auth

**Goal:** Implement `POST /api/v1/auth/login` that:
1. Finds the user by email
2. Verifies password with `bcrypt.compare`
3. Returns a signed JWT with `sub` (userId) and `role`

```typescript
const token = jwt.sign({ sub: user.id, role: user.role }, env.JWT_SECRET, {
  expiresIn: '24h',
  algorithm: 'HS256',
});
res.json(ok({ token, expiresIn: 86400 }));
```

---

## Exercise 4 — WebSocket Job Progress

**Goal:** Wire up the WebSocket server to broadcast job progress events.

1. Set up `WebSocketServer` sharing the HTTP server
2. Implement room management (jobId → Set<WebSocket>)
3. Listen for `engine.on('job:progress', ...)` and call `broadcastToJob`
4. Implement heartbeat with ping/pong

Test with wscat:
```bash
npx wscat -c "ws://localhost:3000/ws?jobId=<id>&token=<jwt>"
# Trigger a job run and watch progress messages arrive
```

---

## Capstone Checkpoint ✅

Before moving to Module 7, verify:

- [ ] All pipeline CRUD endpoints return consistent `{ data, meta }` envelope
- [ ] `POST /api/v1/auth/login` returns a valid JWT
- [ ] Protected routes return 401 without a valid token
- [ ] `POST /api/v1/jobs/:id/cancel` correctly signals the engine's AbortController
- [ ] WebSocket clients receive progress events in real time during a pipeline run
- [ ] Rate limiter blocks requests after 100/minute and returns `429 Too Many Requests`
