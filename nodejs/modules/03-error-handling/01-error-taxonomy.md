# 3.1 — Error Taxonomy

## Concept

Node.js has multiple error channels, and most codebases only handle thrown exceptions. This leads to silent crashes, unhelpful error messages, and processes that die without explanation. A production Node.js service has to handle every channel deliberately.

---

## Deep Dive

### The Five Error Channels

```
1. Synchronous throws      ─── try/catch
2. Promise rejections      ─── .catch() or try/catch with await
3. Callback errors         ─── error-first: if (err) return handleError(err)
4. EventEmitter 'error'    ─── emitter.on('error', handler)
5. Uncaught exceptions     ─── process.on('uncaughtException', handler)
   Unhandled rejections    ─── process.on('unhandledRejection', handler)
```

Missing ANY of these means some errors are silently swallowed or crash your process without useful logs.

### Operational vs Programmer Errors

| Type | Description | How to Handle |
|------|-------------|--------------|
| **Operational** | Expected errors: user not found, network timeout, validation failure, disk full | Catch, handle, return appropriate response |
| **Programmer** | Bugs: `undefined` access, wrong type, logic error | Log, crash (or alert), DO NOT try to recover |

The key insight: **operational errors should be caught and handled**. **Programmer errors should crash fast** (because your process is in an unknown state and trying to recover makes things worse).

```typescript
// Operational error — expected, handle it
try {
  const user = await db.user.findUniqueOrThrow({ where: { id } });
} catch (err) {
  if (err instanceof Prisma.NotFoundError) {
    return res.status(404).json({ error: 'User not found' }); // handle it
  }
  throw err; // unexpected — re-throw
}

// Programmer error — don't catch it, let it crash
function processUser(user: User) {
  // If user is undefined here, it's a bug in the caller
  // Don't add: if (!user) return; — that hides the bug
  return user.name.toUpperCase();
}
```

### Process-Level Error Handlers

```typescript
// FINAL safety net — log and shut down
process.on('uncaughtException', (err, origin) => {
  console.error('Uncaught exception:', err);
  console.error('Origin:', origin);
  // Flush logs, then exit
  process.exit(1); // MUST exit — the process is in unknown state
});

// Node.js 15+: unhandled rejections crash by default
// In earlier versions, you had to handle this manually:
process.on('unhandledRejection', (reason, promise) => {
  console.error('Unhandled rejection at:', promise, 'reason:', reason);
  process.exit(1);
});
```

### EventEmitter Errors

If an `EventEmitter` emits `'error'` and no listener is registered, Node.js **throws the error** and crashes:

```typescript
const emitter = new EventEmitter();

// ⚠️ If no 'error' listener: unhandled error crashes the process
emitter.emit('error', new Error('Something went wrong'));

// ✅ Always add an 'error' listener to EventEmitters
emitter.on('error', (err) => {
  console.error('Stream error:', err);
});
```

---

## Try It Yourself

**Exercise:** Identify all missing error channels in this code:

```typescript
import net from 'node:net';
import { readFile } from 'node:fs/promises';
import { EventEmitter } from 'node:events';

const server = net.createServer((socket) => {
  socket.write('Hello!');
});

server.listen(8080);

const config = await readFile('config.json');

const emitter = new EventEmitter();
setTimeout(() => emitter.emit('error', new Error('crash!')), 100);
```

<details>
<summary>Show missing error handlers</summary>

1. `server` — no `'error'` event listener (EADDRINUSE crashes process)
2. `socket` — no `'error'` event listener (ECONNRESET crashes process)
3. `readFile` — no try/catch (file not found → unhandled rejection → crash)
4. `emitter` — no `'error'` listener (setTimeout fires, error emitted, crash)
5. No `process.on('uncaughtException')` or `process.on('unhandledRejection')` safety nets

</details>

---

## Capstone Connection

PipeForge registers all five error channels in `src/api/server.ts`:
- Express error middleware catches thrown errors from route handlers
- The `PipelineEngine` EventEmitter has an `'error'` listener
- Process-level handlers log and trigger graceful shutdown (Module 05)
- The Prisma client surfaces operational errors as typed exception subclasses
