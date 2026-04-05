# 6.2 — node:http Internals

## Concept

Express is built on `node:http`. Understanding the raw HTTP module demystifies what Express does for you and equips you to work at the HTTP level when you need to — writing custom server-sent events, streaming responses, or debugging strange framework behaviour.

---

## Deep Dive

### A Minimal HTTP Server

```typescript
import http from 'node:http';

const server = http.createServer((req, res) => {
  // req: IncomingMessage (a Readable stream)
  // res: ServerResponse (a Writable stream)

  console.log(`${req.method} ${req.url}`);
  console.log('Headers:', req.headers);

  // Read request body (for POST/PUT)
  let body = '';
  req.on('data', (chunk: Buffer) => { body += chunk.toString(); });
  req.on('end', () => {
    const data = body ? JSON.parse(body) : null;

    res.writeHead(200, {
      'Content-Type': 'application/json',
      'X-Request-Id': crypto.randomUUID(),
    });
    res.end(JSON.stringify({ received: data }));
  });
});

server.listen(3000);
```

### Server-Sent Events (SSE) — Without ws Library

```typescript
// Send real-time updates using plain HTTP (one-way: server → client)
app.get('/api/jobs/:id/events', (req, res) => {
  res.writeHead(200, {
    'Content-Type': 'text/event-stream',
    'Cache-Control': 'no-cache',
    'Connection': 'keep-alive',
  });

  // Send a keepalive comment every 15s
  const keepalive = setInterval(() => res.write(': keepalive\n\n'), 15_000);

  // Emit events
  function sendEvent(event: string, data: unknown) {
    res.write(`event: ${event}\ndata: ${JSON.stringify(data)}\n\n`);
  }

  // Subscribe to engine events
  engine.on('job:progress', sendEvent.bind(null, 'progress'));
  engine.on('job:done', (job) => {
    sendEvent('done', job);
    cleanup();
  });

  function cleanup() {
    clearInterval(keepalive);
    engine.off('job:progress', sendEvent);
    res.end();
  }

  req.on('close', cleanup); // client disconnected
});
```

### Parsing Request Bodies (What Express Does)

```typescript
// express.json() is equivalent to this:
function jsonBodyParser(
  req: http.IncomingMessage,
  res: http.ServerResponse,
  next: () => void,
): void {
  if (!req.headers['content-type']?.startsWith('application/json')) {
    return next();
  }

  let body = '';
  req.setEncoding('utf8');
  req.on('data', (chunk) => { body += chunk; });
  req.on('end', () => {
    try {
      (req as any).body = JSON.parse(body);
      next();
    } catch {
      res.writeHead(400);
      res.end('Invalid JSON');
    }
  });
}
```

---

## Capstone Connection

PipeForge uses SSE for lightweight job progress streaming in browser clients that don't support WebSocket (e.g., corporate proxies). The WebSocket implementation in Topic 6.5 uses the same `req.socket` that `node:http` exposes — showing how `ws` upgrades HTTP connections.
