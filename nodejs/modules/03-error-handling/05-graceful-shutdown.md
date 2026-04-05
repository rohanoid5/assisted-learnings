# 3.5 — Graceful Shutdown

## Concept

When a Node.js server receives SIGTERM (from Kubernetes, Docker, or `kill`), it should finish handling in-flight requests before exiting — not abruptly close all connections. This is **graceful shutdown**. Getting it wrong causes clients to receive connection reset errors, database transactions to be abandoned mid-flight, and jobs to be orphaned.

---

## Deep Dive

### The Shutdown Sequence

```
1. SIGTERM received
2. Stop accepting new connections (server.close())
3. Signal running jobs to cancel (AbortController.abort())
4. Wait for in-flight requests to complete (with timeout)
5. Close database connections (prisma.$disconnect())
6. Flush log buffers
7. Exit cleanly (process.exit(0))
```

### Implementation

```typescript
// src/api/server.ts

export function createGracefulShutdown(
  server: import('node:http').Server,
  engine: PipelineEngine,
) {
  let isShuttingDown = false;

  async function shutdown(signal: string) {
    if (isShuttingDown) return;
    isShuttingDown = true;

    logger.info({ signal }, 'Shutdown signal received');

    // Stop accepting new HTTP connections
    server.close(() => {
      logger.info('HTTP server closed');
    });

    // Cancel all running jobs
    engine.cancelAll();

    // Wait up to 30 seconds for in-flight requests and jobs to complete
    const timeout = setTimeout(() => {
      logger.error('Shutdown timeout — forcing exit');
      process.exit(1);
    }, 30_000);

    try {
      await engine.waitForAll(); // wait for all jobs to finish
      await db.$disconnect();
      logger.info('Graceful shutdown complete');
      clearTimeout(timeout);
      process.exit(0);
    } catch (err) {
      logger.error({ err }, 'Error during shutdown');
      process.exit(1);
    }
  }

  process.on('SIGTERM', () => shutdown('SIGTERM'));
  process.on('SIGINT',  () => shutdown('SIGINT'));  // Ctrl+C during development
}
```

---

## Try It Yourself

**Exercise:** Test graceful shutdown by running PipeForge and sending a SIGTERM:

```bash
npm run dev &
PID=$!
# Wait for it to start
curl -X POST http://localhost:3000/api/jobs   # start a job
sleep 1
kill -TERM $PID   # send SIGTERM
# Observe the logs — the job should complete before the process exits
```

<details>
<summary>What to observe</summary>

- The API stops accepting new requests immediately
- Any running job continues to execute
- Once all jobs complete (or timeout fires), the process exits cleanly with code 0
- No "ECONNRESET" or "ECONNREFUSED" errors for the in-flight job request

</details>

---

## Capstone Connection

The PipeForge graceful shutdown handler coordinates three things: the HTTP server (stop accepting connections), the `PipelineEngine` (cancel and wait for running jobs), and Prisma (disconnect from PostgreSQL). This is implemented in `src/api/server.ts` and tested in Module 09.
