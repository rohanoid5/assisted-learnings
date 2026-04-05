# 6.3 — Express Deep Dive

## Concept

Express's core abstraction is the **middleware chain** — a series of functions that process a request from left to right, each deciding whether to pass control to the next or send a response. Mastering this mental model lets you build any Express application correctly, regardless of complexity.

---

## Deep Dive

### The Middleware Chain

```
Request ─────────────────────────────────────────────────────► Response
         │          │           │           │          │
      logger    cors()     auth()      route()    errorHandler()
      (next)    (next)     (next/err)   (res)       (res)
```

```typescript
import express, { Request, Response, NextFunction } from 'express';

const app = express();

// ─── Global Middleware (runs on every request) ──────────────
app.use(express.json());
app.use(express.urlencoded({ extended: false }));

// ─── Security Middleware ────────────────────────────────────
import helmet from 'helmet';
import cors from 'cors';
import rateLimit from 'express-rate-limit';

app.use(helmet()); // sets 11 security headers (CSP, HSTS, X-Frame-Options, etc.)
app.use(cors({
  origin: process.env.ALLOWED_ORIGINS?.split(',') ?? ['http://localhost:5173'],
  credentials: true,
}));

const limiter = rateLimit({
  windowMs: 60_000,    // 1 minute
  max: 100,            // 100 requests per window
  standardHeaders: true,
  legacyHeaders: false,
});
app.use('/api/', limiter); // rate limit all API routes

// ─── Routes ────────────────────────────────────────────────
import { pipelinesRouter } from './routes/pipelines.js';
app.use('/api/v1/pipelines', pipelinesRouter);
```

### JWT Authentication Middleware

```typescript
import jwt from 'jsonwebtoken';
import { env } from '../config/env.js';
import { UnauthorizedError } from '../errors/index.js';

interface JwtPayload {
  sub: string;   // userId
  role: 'ADMIN' | 'OPERATOR' | 'VIEWER';
}

// Extend Request type
declare global {
  namespace Express {
    interface Request {
      user?: JwtPayload;
    }
  }
}

export function authenticate(req: Request, _res: Response, next: NextFunction): void {
  const header = req.headers.authorization;
  if (!header?.startsWith('Bearer ')) {
    return next(new UnauthorizedError('Missing Bearer token'));
  }

  const token = header.slice(7);
  try {
    req.user = jwt.verify(token, env.JWT_SECRET) as JwtPayload;
    next();
  } catch (err) {
    next(new UnauthorizedError('Invalid or expired token'));
  }
}

// Role-based access control (RBAC)
export function requireRole(...roles: JwtPayload['role'][]) {
  return (req: Request, _res: Response, next: NextFunction) => {
    if (!req.user || !roles.includes(req.user.role)) {
      return next(new ForbiddenError(`Requires one of: ${roles.join(', ')}`));
    }
    next();
  };
}

// Usage:
router.delete('/pipelines/:id', authenticate, requireRole('ADMIN'), asyncHandler(deletePipeline));
```

### Request Validation with Zod

```typescript
import { z } from 'zod';
import { ValidationError } from '../errors/index.js';

export function validate<T>(schema: z.ZodSchema<T>) {
  return (req: Request, _res: Response, next: NextFunction) => {
    const result = schema.safeParse(req.body);
    if (!result.success) {
      return next(new ValidationError('Invalid request body', result.error));
    }
    req.body = result.data; // replace with parsed+typed data
    next();
  };
}

const createPipelineSchema = z.object({
  name: z.string().min(1).max(255),
  steps: z.array(z.object({ name: z.string(), type: z.string() })).min(1),
});

router.post(
  '/',
  authenticate,
  validate(createPipelineSchema),
  asyncHandler(createPipeline),
);
```

---

## Try It Yourself

**Exercise:** Implement a `requestId` middleware that attaches a UUID to each request and includes it in the response:

```typescript
export function requestId(req: Request, res: Response, next: NextFunction): void {
  // TODO: generate UUID, attach to req, set X-Request-Id header
}
```

<details>
<summary>Show solution</summary>

```typescript
import { randomUUID } from 'node:crypto';

declare global {
  namespace Express { interface Request { id: string; } }
}

export function requestId(req: Request, res: Response, next: NextFunction): void {
  req.id = req.headers['x-request-id']?.toString() ?? randomUUID();
  res.setHeader('X-Request-Id', req.id);
  next();
}
```

</details>

---

## Capstone Connection

PipeForge's `src/api/server.ts` applies middleware in this order: `requestId` → `helmet` → `cors` → `rateLimit` → `authenticate` → routes → `errorHandler`. Ordering matters — error handler must be last, CORS before auth so OPTIONS preflight requests pass through.
