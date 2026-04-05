# Node.js Knowledge Checklist

Use this file to periodically self-assess. Review it monthly and update your ratings.

**Legend:** `[ ]` Not yet · `[~]` In progress · `[x]` Confident

---

## Module 1 — Node.js Internals & Architecture

### 1.1 What Is Node.js?
- [ ] Can describe Node.js as a JS runtime built on V8 + libuv
- [ ] Understands the single-threaded, non-blocking I/O model
- [ ] Can explain why Node.js is well-suited for I/O-heavy workloads and poor for CPU-heavy tasks

### 1.2 Node.js vs The Browser
- [ ] Knows what Node.js has that browsers don't: `fs`, `net`, `crypto`, `child_process`, no DOM/BOM
- [ ] Understands that `window` / `document` / `fetch` (pre-v18) don't exist in Node.js
- [ ] Knows the `global` object vs `window`

### 1.3 The Event Loop
- [ ] Can name and order the event loop phases: timers → pending callbacks → idle/prepare → poll → check → close callbacks
- [ ] Understands `process.nextTick()` runs before any I/O at the end of the current operation
- [ ] Understands microtasks (Promises, `queueMicrotask`) are drained after each phase and after `nextTick`
- [ ] Can predict the output order of `setTimeout`, `setImmediate`, `Promise.resolve`, `process.nextTick` in a code snippet

### 1.4 Module Systems
- [ ] Knows CommonJS: `require()`, `module.exports`, `exports` — synchronous, cached after first load
- [ ] Knows ESM: `import` / `export`, static analysis, top-level `await`, `.mjs` or `"type": "module"`
- [ ] Understands the interop challenges between CJS and ESM
- [ ] Knows how `require.resolve()` works and why `__filename` / `__dirname` don't exist in ESM

### 1.5 npm Advanced
- [ ] Understands `package.json` fields: `main`, `exports`, `types`, `engines`, `scripts`, `peerDependencies`
- [ ] Knows the `node_modules` resolution algorithm: walk up directories
- [ ] Can use `npm workspaces` for monorepos
- [ ] Understands `package-lock.json` vs `npm-shrinkwrap.json`; knows why it must be committed

### 1.6 Built-in Modules
- [ ] Comfortable with `path`, `os`, `url`, `util`, `events`, `crypto`, `zlib`
- [ ] Can use `util.promisify` to wrap callback-based APIs
- [ ] Knows `util.inspect` for deep object printing and `util.types` for type-checking

---

## Module 2 — Asynchronous Programming

### 2.1 Callbacks
- [ ] Understands the error-first callback convention: `(err, result) => {}`
- [ ] Can explain callback hell and how to mitigate it (named functions, modularization)
- [ ] Knows why you must not throw synchronously inside an async callback

### 2.2 Promises
- [ ] Can construct a `new Promise()` and explain resolve/reject
- [ ] Knows `Promise.prototype.then`, `.catch`, `.finally`
- [ ] Understands the microtask queue and why `.then` callbacks are always async

### 2.3 async/await
- [ ] Can convert a Promise chain to `async`/`await`
- [ ] Always wraps `await` calls in `try/catch` or handles rejection upstream
- [ ] Knows that `async function` always returns a Promise
- [ ] Can use `await` on non-Promise values (wraps in `Promise.resolve`)

### 2.4 Promise Combinators
- [ ] Knows all four: `Promise.all`, `Promise.allSettled`, `Promise.race`, `Promise.any`
- [ ] Can choose the right combinator for a given scenario (fail-fast vs wait-all vs first-success)

### 2.5 Async Iteration
- [ ] Can write `for await...of` loops over async iterables (streams, generators)
- [ ] Can write an async generator with `async function*` and `yield`
- [ ] Understands `Symbol.asyncIterator` and how to make an object async iterable

### 2.6 Cancellation with AbortController
- [ ] Can create an `AbortController` and pass its `signal` to fetch/streams
- [ ] Can listen for `signal.addEventListener('abort', ...)` to clean up resources
- [ ] Knows `AbortSignal.timeout(ms)` shorthand

---

## Module 3 — Error Handling

### 3.1 Error Taxonomy
- [ ] Can distinguish operational errors (expected, recoverable) from programmer errors (bugs, not recoverable)
- [ ] Knows `Error`, `TypeError`, `RangeError`, `SyntaxError`, `ReferenceError`, `EvalError` and typical causes
- [ ] Understands `error.code` (e.g., `ENOENT`, `ECONNREFUSED`, `ETIMEDOUT`) for system errors

### 3.2 Custom Error Classes
- [ ] Can create a custom error class that extends `Error` and sets `this.name` correctly
- [ ] Knows to call `Error.captureStackTrace(this, this.constructor)` in Node.js for a clean stack
- [ ] Can create a hierarchy of typed errors (e.g., `NotFoundError extends AppError`)

### 3.3 Async Error Handling
- [ ] Knows that an unhandled Promise rejection triggers `unhandledRejection` process event
- [ ] Can register `process.on('unhandledRejection', ...)` for logging before shutdown
- [ ] Understands `process.on('uncaughtException', ...)` and why recovering from it is dangerous

### 3.4 Express Error Handling
- [ ] Knows the 4-argument error middleware signature: `(err, req, res, next)`
- [ ] Knows to call `next(err)` from route handlers to trigger error middleware
- [ ] Can write a global error handler that formats and sends structured error responses
- [ ] Wraps async route handlers to catch promise rejections and forward to `next`

### 3.5 Graceful Shutdown
- [ ] Can listen for `SIGTERM` and `SIGINT` signals
- [ ] Knows the shutdown sequence: stop accepting connections → drain in-flight requests → close DB pool → exit
- [ ] Can implement a shutdown timeout to force-exit if drain takes too long

---

## Module 4 — Streams & Buffers

### 4.1 Buffers & Binary Data
- [ ] Can create buffers with `Buffer.alloc()`, `Buffer.from()`, `Buffer.concat()`
- [ ] Can convert between `Buffer`, UTF-8 strings, hex, and Base64
- [ ] Understands `Buffer` is allocated outside the V8 heap (fixed-size C++ native array)

### 4.2 Readable Streams
- [ ] Understands flowing vs paused mode and how to switch between them
- [ ] Can subscribe to `data`, `end`, `error`, `close` events
- [ ] Can use `readable.pipe()` and understands automatic backpressure

### 4.3 Writable Streams & Backpressure
- [ ] Can `.write()` to a writable and check the return value for backpressure
- [ ] Knows to pause the source on `false` return and resume on `drain` event
- [ ] Can `.end()` a writable and listen for `finish` vs `close`

### 4.4 Transform Streams
- [ ] Can implement a custom `Transform` by overriding `_transform(chunk, encoding, callback)`
- [ ] Knows `_flush(callback)` for emitting remaining data on stream end
- [ ] Can compose transforms (e.g., gunzip → parse CSV) using `.pipe()`

### 4.5 stream.pipeline
- [ ] Uses `stream.pipeline()` (or `require('stream/promises').pipeline`) instead of raw `.pipe()` chains
- [ ] Understands that `pipeline` handles error propagation and cleanup for all streams in the chain
- [ ] Can create a streaming HTTP response (file download) without buffering the entire file in memory

### 4.6 Practical Stream Patterns
- [ ] Can use `stream.Readable.from()` to create a readable from an array/generator
- [ ] Can read a stream to completion into a buffer/string with `stream.pipeline` into a `WritableStream` or collect chunks
- [ ] Knows `highWaterMark` and how to tune it for throughput vs memory

---

## Module 5 — File System & CLI

### 5.1 fs/promises
- [ ] Can `readFile`, `writeFile`, `appendFile`, `unlink`, `mkdir`, `rmdir`/`rm`, `readdir`, `stat`, `rename`
- [ ] Knows when to use the streaming `fs.createReadStream` instead of `readFile` (large files)
- [ ] Understands `fs.constants` (e.g., `F_OK`, `R_OK`, `W_OK`) for access checks

### 5.2 File Watching
- [ ] Can use `fs.watch()` and understands its portability caveats
- [ ] Knows `chokidar` as a robust cross-platform alternative
- [ ] Can debounce rapid change events

### 5.3 Path Resolution
- [ ] Can use `path.join`, `path.resolve`, `path.relative`, `path.dirname`, `path.basename`, `path.extname`
- [ ] Understands `__dirname` (CJS) vs `import.meta.url` + `fileURLToPath` (ESM)
- [ ] Can construct paths portably (no hardcoded `/` separators)

### 5.4 Commander.js CLI
- [ ] Can define commands, options, and arguments with `.command()`, `.option()`, `.argument()`
- [ ] Can set required options with `.requiredOption()` and customise help output
- [ ] Knows how to structure a multi-command CLI with subcommands

### 5.5 Environment & Configuration Management
- [ ] Can load `.env` files with `dotenv` and know when `dotenv` should not be in production
- [ ] Understands the 12-factor app config principle: config in environment, not in code
- [ ] Can use `zod` or `joi` to validate and type environment variables at startup

### 5.6 stdin, stdout, stderr & Terminal UX
- [ ] Can read from `process.stdin` in flowing mode and line-by-line
- [ ] Can detect TTY vs pipe: `process.stdout.isTTY`
- [ ] Knows how to use colors, progress bars (e.g., `cli-progress`), and clear-line escape codes

---

## Module 6 — Networking & HTTP

### 6.1 TCP & Socket Basics
- [ ] Can create a TCP server and client with `net.createServer()` / `net.createConnection()`
- [ ] Understands socket events: `data`, `end`, `close`, `error`
- [ ] Knows the three-way handshake and TIME_WAIT state relevance

### 6.2 node:http Internals
- [ ] Can create an HTTP server with `http.createServer((req, res) => {})`
- [ ] Understands `IncomingMessage` and `ServerResponse` as streams
- [ ] Knows keep-alive, chunked encoding, and how `Content-Length` / `Transfer-Encoding` work

### 6.3 Express Deep Dive
- [ ] Understands the middleware stack and how `next()` / `next(err)` flow works
- [ ] Can build and mount sub-routers with `express.Router()`
- [ ] Knows built-in middleware: `express.json()`, `express.urlencoded()`, `express.static()`
- [ ] Understands how Express wraps Node's `http.Server` and why `.listen()` returns that server

### 6.4 REST API Design
- [ ] Follows resource-oriented URL conventions (`/users/:id`, not `/getUser?id=1`)
- [ ] Uses correct HTTP verbs and status codes (201 Created, 204 No Content, 409 Conflict, etc.)
- [ ] Designs consistent error response bodies
- [ ] Implements pagination (cursor-based preferred over offset for large datasets)

### 6.5 WebSockets
- [ ] Can set up a WebSocket server with `ws` library
- [ ] Understands the HTTP Upgrade handshake
- [ ] Knows `socket.io` abstractions: rooms, namespaces, fallback to polling
- [ ] Can implement a basic pub/sub (chat, notifications) system with WebSockets

### 6.6 HTTP Client Patterns
- [ ] Can make requests with `fetch` (Node ≥18) and handle JSON/error responses
- [ ] Knows `axios` interceptors for auth headers and retry logic
- [ ] Can implement exponential backoff + jitter for retries
- [ ] Understands connection reuse (keep-alive) and can configure an `http.Agent` for pool tuning

---

## Module 7 — Design Patterns in Node.js

### 7.1 Factory & Builder Patterns
- [ ] Can implement a factory function that returns objects without `new`
- [ ] Can build a fluent builder (method chaining) for complex object construction
- [ ] Understands the benefit over constructors: flexible, testable, no `this` binding issues

### 7.2 Singleton Pattern in ESM
- [ ] Understands that ESM modules are cached — a module-level instance is a singleton
- [ ] Can explicitly create a singleton class with a private constructor pattern
- [ ] Knows the pitfalls: test isolation, module caching across different resolution paths

### 7.3 Observer Pattern & EventEmitter
- [ ] Can extend `EventEmitter` to create a typed event bus
- [ ] Understands `on`, `once`, `off`, `emit`, and the `newListener` / `removeListener` events
- [ ] Knows memory leak warning (default 10 listeners) and how to `setMaxListeners()`

### 7.4 Middleware Pattern
- [ ] Can implement a Koa-style middleware chain with `async (ctx, next) => { await next() }`
- [ ] Understands the "onion" model for pre/post-hook behaviour
- [ ] Can apply the middleware pattern outside of HTTP (pipeline, job processing)

### 7.5 Plugin Architecture
- [ ] Can design a plugin system where plugins register themselves against a host object
- [ ] Understands Fastify's encapsulation scope model as a reference
- [ ] Can implement plugin options/configuration and lifecycle hooks (register, start, stop)

### 7.6 Dependency Injection
- [ ] Can implement constructor injection for service dependencies
- [ ] Understands the testability benefit: inject mocks in tests
- [ ] Know lightweight DI containers like `awilix` and when they're worth adding

---

## Module 8 — Databases with Node.js

### 8.1 Prisma ORM
- [ ] Can define a `schema.prisma` with models, fields, and relations
- [ ] Can use `PrismaClient` for all CRUD operations: `create`, `findUnique`, `findMany`, `update`, `upsert`, `delete`
- [ ] Knows `include` for eager loading and `select` for projection
- [ ] Can write raw queries with `$queryRaw` and `$executeRaw`

### 8.2 Database Migrations
- [ ] Can run `prisma migrate dev` and `prisma migrate deploy`
- [ ] Understands the migration history table and how to handle drift
- [ ] Knows how to write a migration with a data backfill step
- [ ] Understands zero-downtime migration patterns: expand/contract

### 8.3 The N+1 Problem
- [ ] Can identify an N+1 in Prisma (selecting related records in a loop)
- [ ] Knows to use `include` / `select` or dataloader to batch queries
- [ ] Can explain why N+1 is unacceptable in production and how to detect it via query logs

### 8.4 Transactions
- [ ] Can use `prisma.$transaction([...operations])` for sequential transactional operations
- [ ] Can use interactive transactions: `prisma.$transaction(async (tx) => {})`
- [ ] Understands isolation levels and how to set them in Prisma

### 8.5 Connection Pooling
- [ ] Can configure Prisma's `connection_limit` URL parameter
- [ ] Understands why connection pooling matters (TCP handshake cost, PG backend memory)
- [ ] Can use Prisma Accelerate or PgBouncer to pool connections in serverless environments

### 8.6 Raw SQL with Prisma
- [ ] Knows when to escape to raw SQL vs use the query builder
- [ ] Uses `Prisma.sql` template tag to safely interpolate values (parameterized queries, no injection)
- [ ] Can parse raw query results into typed objects with Zod

---

## Module 9 — Testing

### 9.1 Testing Fundamentals
- [ ] Can explain the testing pyramid: unit > integration > e2e
- [ ] Knows AAA pattern: Arrange, Act, Assert
- [ ] Understands the difference between a test double, stub, mock, spy, and fake

### 9.2 Unit Testing
- [ ] Can write unit tests with Vitest or Jest
- [ ] Knows `describe`, `it`/`test`, `expect`, `beforeEach`, `afterEach`, `beforeAll`, `afterAll`
- [ ] Can test async code with `await expect(fn()).resolves.toBe(...)` / `.rejects.toThrow(...)`

### 9.3 Integration Testing
- [ ] Can write integration tests that spin up an HTTP server and make real requests using `supertest`
- [ ] Can use Testcontainers (Node) to run a real PostgreSQL in Docker during tests
- [ ] Knows how to reset database state between tests (truncate, rollback, re-seed)

### 9.4 Mocking
- [ ] Can use `vi.mock()` / `jest.mock()` to replace a module
- [ ] Can use `vi.spyOn()` to spy on a method and assert call count / arguments
- [ ] Can use `vi.fn()` to create standalone mock functions
- [ ] Knows to clear/reset/restore mocks between tests to avoid state leakage

### 9.5 Test Coverage
- [ ] Can run coverage reports with `--coverage` and understand line, branch, function coverage
- [ ] Knows a coverage threshold is a floor, not a goal — 100% coverage ≠ bug-free
- [ ] Can identify untested branches in coverage reports and write targeted tests

---

## Module 10 — Process & Performance

### 10.1 Child Processes
- [ ] Can spawn a process with `exec`, `execFile`, `spawn`, `fork` and knows the API differences
- [ ] Knows `fork` creates a Node.js process with an IPC channel for message passing
- [ ] Can stream stdout/stderr from a child process and detect non-zero exit codes

### 10.2 Worker Threads
- [ ] Knows when to use worker threads (CPU-bound tasks) vs child processes
- [ ] Can use `new Worker(filename, { workerData })` and `parentPort.postMessage()`
- [ ] Understands `SharedArrayBuffer` + `Atomics` for shared memory communication

### 10.3 Cluster
- [ ] Can set up a cluster with `cluster.fork()` across CPU cores
- [ ] Understands the master/worker lifecycle: spawn, `listening`, `disconnect`, `exit`
- [ ] Knows cluster vs PM2 vs container orchestration for production scaling

### 10.4 Memory Management
- [ ] Understands V8 heap layout: new space (minor GC) → old space (major GC)
- [ ] Can detect memory leaks with `process.memoryUsage()` trend and heap snapshots (Chrome DevTools)
- [ ] Knows common leak sources: forgotten event listeners, global caches without eviction, closures over large objects

### 10.5 Profiling
- [ ] Can generate a CPU profile with `--prof` and process it with `--prof-process`
- [ ] Can use the V8 inspector (Chrome DevTools) for heap snapshots and CPU flame graphs
- [ ] Knows `clinic.js` (Doctor, Flame) for automated bottleneck detection

### 10.6 Event Loop Monitoring
- [ ] Can measure event loop lag with `perf_hooks.monitorEventLoopDelay()`
- [ ] Knows that lag > 100ms in production is a red flag for blocking operations
- [ ] Can emit a metric or alert when event loop lag exceeds a threshold

---

## Capstone — PipeForge

- [ ] Can describe PipeForge's architecture and data flow
- [ ] Has run the application end-to-end successfully
- [ ] Can extend it with a new pipeline stage or transformation
- [ ] Has written integration tests covering at least one streaming or async flow
- [ ] Can profile the application under load and identify the bottleneck

---

## Review Log

| Date | Topics Reviewed | Gaps Identified |
|------|----------------|-----------------|
| | | |
| | | |
| | | |
