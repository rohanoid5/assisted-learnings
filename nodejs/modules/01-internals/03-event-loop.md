# 1.3 — The Event Loop

## Concept

The event loop is the **most important mental model in Node.js**. It's what enables a single JavaScript thread to handle thousands of concurrent operations without blocking. Yet most developers who've worked with Node.js for years can't accurately describe what order `setTimeout`, `setImmediate`, and `Promise.then` callbacks execute in — or why.

This topic fixes that. Once you understand event loop phases, you can reason precisely about execution order, avoid subtle bugs, and diagnose performance problems.

---

## Deep Dive

### The Six Phases

libuv's event loop has six phases. After each phase (and between the I/O phases), Node.js drains two microtask queues before moving to the next phase.

```
   ┌────────────────────────────────────────┐
   │            Event Loop Tick             │
   │                                        │
   │  ┌──────────┐                          │
   │  │  timers  │  setTimeout, setInterval │
   │  └────┬─────┘                          │
   │       │  [microtasks drained here]     │
   │  ┌────▼──────────┐                     │
   │  │ pending I/O   │  previous-cycle I/O │
   │  │  callbacks    │  error callbacks    │
   │  └────┬──────────┘                     │
   │       │  [microtasks drained here]     │
   │  ┌────▼──────────┐                     │
   │  │  idle/prepare │  internal use only  │
   │  └────┬──────────┘                     │
   │       │                                │
   │  ┌────▼──────────┐                     │
   │  │     poll      │  retrieve I/O events│
   │  │               │  execute I/O callbacks│
   │  └────┬──────────┘                     │
   │       │  [microtasks drained here]     │
   │  ┌────▼──────────┐                     │
   │  │     check     │  setImmediate        │
   │  └────┬──────────┘                     │
   │       │  [microtasks drained here]     │
   │  ┌────▼──────────┐                     │
   │  │ close callbacks│ 'close' events     │
   │  └────────────────┘                    │
   └────────────────────────────────────────┘
```

### Phase-by-Phase Explanation

**1. timers** — Executes callbacks registered by `setTimeout()` and `setInterval()` whose threshold has elapsed. Note: the delay is a *minimum*, not a guarantee.

**2. pending callbacks** — Executes I/O callbacks deferred to the next loop iteration (e.g., TCP errors).

**3. idle, prepare** — Internal to libuv. You won't interact with these.

**4. poll** — The heart of the loop. Node.js retrieves new I/O events and executes their callbacks. If no timers are ready and no `setImmediate` is scheduled, it *waits* here for I/O events. This is why Node.js doesn't consume 100% CPU while idle.

**5. check** — Executes callbacks registered with `setImmediate()`. These always run after the poll phase completes, before timers.

**6. close callbacks** — Executes `close` events (`socket.on('close', ...)`, `stream.on('close', ...)`).

### Microtasks: The Priority Queue

Between *every* phase (and after *every* timer/check callback in Node.js 11+), Node.js drains two microtask queues **completely** before moving on:

1. **`process.nextTick` queue** — drains first, completely
2. **Promise microtask queue** — drains second, completely

This means microtasks have **higher priority** than any event loop phase callback.

```
Phase callback runs
    ↓
process.nextTick queue drains (all of them)
    ↓
Promise microtask queue drains (all of them)
    ↓
Next phase begins
```

### The Execution Order

```typescript
console.log('1. sync');

setTimeout(() => console.log('5. setTimeout'), 0);
setImmediate(() => console.log('6. setImmediate'));

Promise.resolve().then(() => console.log('3. Promise.then'));
process.nextTick(() => console.log('2. nextTick'));

queueMicrotask(() => console.log('4. queueMicrotask'));

console.log('1b. sync end');

// Output:
// 1. sync
// 1b. sync end
// 2. nextTick          ← process.nextTick queue (highest priority)
// 3. Promise.then      ← Promise microtask queue
// 4. queueMicrotask    ← also Promise microtask queue (same as .then)
// 5. setTimeout        ← timers phase
// 6. setImmediate      ← check phase
```

### Event Loop Starvation

If you spam `process.nextTick` recursively, you can **starve** the event loop:

```typescript
// ⚠️ DANGER — this prevents any I/O from ever being processed
function recursiveNextTick() {
  process.nextTick(recursiveNextTick);
}
recursiveNextTick(); // Event loop is now starved — your server stops responding!
```

`setImmediate` is safer for recursive scheduling because it yields to I/O between iterations.

---

## Code Examples

### setTimeout vs setImmediate in I/O Context

```typescript
import { readFile } from 'node:fs';

readFile('README.md', () => {
  // Inside an I/O callback — we're in the poll phase

  setTimeout(() => {
    console.log('setTimeout'); // timers phase — NEXT iteration
  }, 0);

  setImmediate(() => {
    console.log('setImmediate'); // check phase — THIS iteration
  });
});

// Output (inside I/O callback): always setImmediate first, then setTimeout
// Outside an I/O callback: order is non-deterministic
```

### process.nextTick for Deferred Synchronous Work

```typescript
class EventEmitter {
  emit(event: string, data: unknown) {
    // If we emit synchronously in a constructor, listeners may not be attached yet
    // Deferring to nextTick ensures listeners are set up first
    process.nextTick(() => {
      this.listeners.get(event)?.forEach((fn) => fn(data));
    });
  }
}
```

### Measuring Event Loop Lag

```typescript
// Detect if the event loop is overloaded
function measureEventLoopLag(callback: (lagMs: number) => void): void {
  const start = performance.now();
  setImmediate(() => {
    const lag = performance.now() - start;
    callback(lag);
  });
}

setInterval(() => {
  measureEventLoopLag((lag) => {
    if (lag > 50) {
      console.warn(`⚠️ Event loop lag: ${lag.toFixed(1)}ms — possible blocking operation!`);
    }
  });
}, 1000);
```

---

## Try It Yourself

**Exercise:** Predict and verify execution order.

Before running the code, write down the order you expect the `console.log` statements to execute. Then run to verify.

```typescript
// order-prediction.ts
console.log('A');

setTimeout(() => {
  console.log('B');
  process.nextTick(() => console.log('C'));
  Promise.resolve().then(() => console.log('D'));
}, 0);

Promise.resolve()
  .then(() => {
    console.log('E');
    process.nextTick(() => console.log('F'));
  })
  .then(() => console.log('G'));

process.nextTick(() => {
  console.log('H');
  process.nextTick(() => console.log('I'));
});

console.log('J');
```

<details>
<summary>Show expected order with explanation</summary>

```
A   ← sync
J   ← sync
H   ← nextTick queue (first flush)
I   ← nextTick queue (added by H, flushed before Promise microtasks)
E   ← Promise microtask queue
F   ← nextTick (added by E — nextTick queue drains BEFORE Promise queue continues)
G   ← Promise microtask (.then chained after E)
B   ← timers phase (setTimeout)
C   ← nextTick queue (added inside setTimeout callback)
D   ← Promise microtask queue (added inside setTimeout callback)
```

Key insight: After each callback (even inside a phase), microtasks are fully drained, and `nextTick` always precedes Promise microtasks.

</details>

---

## Capstone Connection

The event loop is central to PipeForge's job execution model:

- **Job progress events**: when a pipeline step completes, progress is emitted via `EventEmitter`. The handler sends WebSocket messages — which are dispatched in the **poll phase** (I/O). Understanding this prevents you from accidentally blocking progress updates with synchronous computation.
- **Graceful shutdown** (Module 11): `process.on('SIGTERM')` triggers in the event loop. We use `process.nextTick` to ensure all in-flight operations flush before exiting.
- **Event loop lag monitoring** (Module 10): PipeForge uses `setImmediate`-based lag detection to alert when a CPU-intensive job step is blocking the main thread — a signal to move that work to a Worker Thread.
