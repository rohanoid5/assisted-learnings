# 1.1 — What Is Node.js?

## Concept

You've been using Node.js. But what *is* it?

Node.js is not a programming language. It's not a framework. It's a **JavaScript runtime** — a program that executes JavaScript code outside of a browser. Under the hood, it combines two major components: **V8** (Google's JavaScript engine) and **libuv** (a C library for asynchronous I/O). Understanding how these two interact explains *everything* about Node.js behavior — why it's fast for I/O, why it can't do CPU-heavy work on the main thread, and how "non-blocking" actually works.

---

## Deep Dive

### The Two-Component Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                      Node.js Process                        │
│                                                             │
│  ┌──────────────────────────┐  ┌─────────────────────────┐ │
│  │           V8             │  │         libuv           │ │
│  │  (JavaScript engine)     │  │  (async I/O + event     │ │
│  │                          │  │   loop)                 │ │
│  │  • Parses JS/TS          │  │                         │ │
│  │  • JIT compilation       │  │  • Thread pool (4-128)  │ │
│  │  • Garbage collection    │  │  • Timers               │ │
│  │  • Heap & stack mgmt     │  │  • File system          │ │
│  │  • Call stack            │  │  • Network (TCP/UDP)    │ │
│  └──────────────────────────┘  │  • DNS                  │ │
│                                │  • Signals              │ │
│  ┌──────────────────────────┐  └─────────────────────────┘ │
│  │   Node.js Bindings       │                               │
│  │  (C++ bridge layer)      │                               │
│  │  • fs, net, http, crypto │                               │
│  └──────────────────────────┘                               │
└─────────────────────────────────────────────────────────────┘
```

### V8: The JavaScript Engine

V8 is Google's open-source JavaScript engine, written in C++. It powers both Chrome and Node.js. Key responsibilities:

- **Parsing**: Converts your JavaScript source code into an Abstract Syntax Tree (AST)
- **Compilation**: JIT (Just-In-Time) compiles hot code paths to native machine code via the Turbofan compiler
- **Garbage collection**: Manages memory automatically (more on this in Module 10)
- **Call stack**: Tracks which function is currently executing

V8 is entirely single-threaded. It executes one JavaScript operation at a time. This is why CPU-intensive work blocks everything.

### libuv: The Async I/O Engine

libuv is a C library that provides Node.js with:

- **The event loop** — the heartbeat of Node.js
- **A thread pool (4 by default)** — for operations that can't be non-blocking at the OS level (file system, DNS, crypto, zlib)
- **Non-blocking network I/O** — using OS-level facilities (epoll on Linux, kqueue on macOS, IOCP on Windows)

### The Critical Insight: What "Non-Blocking" Actually Means

When you call `fs.readFile()`:

1. Node.js calls a C++ binding
2. The binding hands the work to **libuv's thread pool**
3. V8's main thread is **immediately freed** — it continues executing other JavaScript
4. When the OS finishes reading the file, libuv puts a callback on the **event queue**
5. When the call stack is empty, the event loop picks up that callback and runs it

```
Main Thread (V8)          libuv Thread Pool          OS
      │                         │                     │
      │─ fs.readFile() ────────▶│                     │
      │                         │─ read syscall ─────▶│
      │  (continues running      │                     │
      │   other JS code)         │◀── data ready ──────│
      │                         │                     │
      │◀── callback queued ─────│                     │
      │                         │                     │
      │─ execute callback()     │                     │
```

**Network I/O** (HTTP requests, TCP connections) is different: it uses OS-level async I/O (`epoll`/`kqueue`) on a dedicated polling thread — no thread pool needed. That's why Node.js can handle thousands of concurrent connections with minimal overhead.

### Single-Threaded ≠ Single-Process

Node.js JavaScript is single-threaded, but the Node process uses multiple threads:

| Thread | Purpose |
|--------|---------|
| Main thread | Executes JavaScript (V8) + event loop (libuv) |
| Thread pool (4 by default) | Async file I/O, DNS lookups, crypto, zlib |
| Extra libuv threads | Timer management, signal handling |

The thread pool size can be increased: `UV_THREADPOOL_SIZE=8 node app.js`

### Why Not Multi-Threaded JavaScript?

JavaScript was designed for browsers where shared mutable state across threads causes UI corruption bugs. The single-threaded model eliminates **race conditions** and **deadlocks** for JavaScript code. Worker Threads (Module 10) provide true multithreading when you need it, using message passing to avoid shared state issues.

---

## Code Examples

### Demonstrating Non-Blocking I/O

```typescript
import { readFile } from 'node:fs/promises';

console.log('1. Start');

// This does NOT block — libuv hands it to the thread pool
readFile('package.json', 'utf8').then((data) => {
  console.log('3. File read complete:', data.length, 'bytes');
});

console.log('2. Immediately after readFile call');

// Output:
// 1. Start
// 2. Immediately after readFile call
// 3. File read complete: 847 bytes
```

### Blocking vs Non-Blocking: The Difference

```typescript
import { readFileSync, readFile } from 'node:fs';

// BLOCKING — freezes the event loop until complete
const data = readFileSync('large-file.csv');  // ← bad in servers!
console.log('Got', data.length, 'bytes');

// NON-BLOCKING — delegates to libuv, main thread stays free
readFile('large-file.csv', (err, data) => {
  if (err) throw err;
  console.log('Got', data.length, 'bytes');
});
console.log('This runs immediately, before the file is read');
```

### Checking the Thread Pool

```typescript
import { cpus } from 'node:os';

console.log(`CPU cores: ${cpus().length}`);
console.log(`Default UV_THREADPOOL_SIZE: 4`);
console.log(`Max UV_THREADPOOL_SIZE: 1024`);
console.log(`Current UV_THREADPOOL_SIZE: ${process.env.UV_THREADPOOL_SIZE ?? 4}`);
```

---

## Try It Yourself

**Exercise:** Observe non-blocking I/O in action.

1. Create a file `test.ts`:

```typescript
import { readFile } from 'node:fs/promises';
import { performance } from 'node:perf_hooks';

const start = performance.now();

// Fire 10 file reads simultaneously
const reads = Array.from({ length: 10 }, (_, i) =>
  readFile('package.json', 'utf8').then((data) => {
    console.log(`Read ${i + 1} complete: ${(performance.now() - start).toFixed(1)}ms`);
    return data;
  })
);

await Promise.all(reads);
console.log(`Total: ${(performance.now() - start).toFixed(1)}ms`);
```

2. Run it: `node --loader ts-node/esm test.ts`
3. Notice: all 10 reads complete in roughly the same time as one (they run in the thread pool concurrently, not sequentially).

<details>
<summary>Expected output</summary>

```
Read 1 complete: 3.2ms
Read 3 complete: 3.4ms
Read 2 complete: 3.5ms
...
Total: 4.1ms
```

The reads complete in parallel (order is non-deterministic) and the total time is approximately the time for one read — not 10x.

</details>

---

## Capstone Connection

The PipeForge pipeline engine (built in Module 02) relies entirely on this mental model:

- Each **job** is handed to libuv for async execution — the API server stays responsive
- **Progress events** flow back through the event loop to WebSocket clients
- **File-based pipeline steps** (Module 04) use non-blocking stream APIs — no thread pool, pure async I/O
- **Worker threads** (Module 10) are used for CPU-intensive step processing (aggregation, transformation of large datasets) to avoid blocking the event loop

Understanding what happens in `libuv` vs what happens in `V8` tells you exactly *when* to use streams, when to use worker threads, and when plain async/await is sufficient.
