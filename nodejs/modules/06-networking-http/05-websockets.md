# 6.5 — WebSockets

## Concept

WebSockets provide full-duplex, persistent communication between client and server — perfect for real-time job progress, live log tailing, and collaborative pipeline editing. The `ws` library is the production-standard WebSocket implementation for Node.js.

---

## Deep Dive

### WebSocket Upgrade Mechanics

```
Client                       Server
  |                              |
  |──── HTTP GET /ws ──────────► |  HTTP Upgrade request
  |     Upgrade: websocket       |
  |     Sec-WebSocket-Key: ...   |
  |                              |
  |◄─── 101 Switching Protocols  |  Server accepts upgrade
  |     Sec-WebSocket-Accept:... |
  |                              |
  |══════ WebSocket Frame ══════► |  Full-duplex binary frames
  |◄═════ WebSocket Frame ══════  |  Both sides can send anytime
  |                              |
```

The TCP connection stays open. Framing is binary, efficient, and low-overhead.

### Setting Up a WebSocket Server

```typescript
import { WebSocketServer, WebSocket } from 'ws';
import http from 'node:http';

const server = http.createServer(app);
const wss = new WebSocketServer({ server }); // share with HTTP

// Map of jobId → Set<WebSocket> for targeted broadcasts
const rooms = new Map<string, Set<WebSocket>>();

wss.on('connection', (ws, req) => {
  // Parse jobId from URL: ws://localhost:3000/ws?jobId=xyz
  const url = new URL(req.url!, `http://${req.headers.host}`);
  const jobId = url.searchParams.get('jobId');
  if (!jobId) { ws.close(1008, 'Missing jobId'); return; }

  // Join the job's room
  if (!rooms.has(jobId)) rooms.set(jobId, new Set());
  rooms.get(jobId)!.add(ws);

  ws.on('close', () => {
    rooms.get(jobId)?.delete(ws);
    if (rooms.get(jobId)?.size === 0) rooms.delete(jobId);
  });

  ws.on('error', (err) => console.error('WS error:', err));

  // Send initial state
  ws.send(JSON.stringify({ type: 'connected', jobId }));
});

// Broadcast a message to all clients watching a specific job
export function broadcastToJob(jobId: string, payload: unknown): void {
  const clients = rooms.get(jobId);
  if (!clients) return;
  const msg = JSON.stringify(payload);
  for (const client of clients) {
    if (client.readyState === WebSocket.OPEN) {
      client.send(msg);
    }
  }
}
```

### Authenticating WebSocket Connections

```typescript
import jwt from 'jsonwebtoken';

wss.on('connection', (ws, req) => {
  // Token passed as query param (headers not available in browser WS API)
  const token = new URL(req.url!, 'http://x').searchParams.get('token');
  if (!token) { ws.close(1008, 'Unauthorized'); return; }

  try {
    const payload = jwt.verify(token, env.JWT_SECRET);
    (ws as any).user = payload; // attach to ws instance
  } catch {
    ws.close(1008, 'Invalid token');
    return;
  }

  // ... proceed with authenticated connection
});
```

### Heartbeat / Ping-Pong

```typescript
function setupHeartbeat(wss: WebSocketServer, intervalMs = 30_000): void {
  const interval = setInterval(() => {
    wss.clients.forEach((ws) => {
      const extWs = ws as WebSocket & { isAlive?: boolean };
      if (!extWs.isAlive) { ws.terminate(); return; }
      extWs.isAlive = false;
      ws.ping();
    });
  }, intervalMs);

  wss.on('connection', (ws) => {
    (ws as any).isAlive = true;
    ws.on('pong', () => { (ws as any).isAlive = true; });
  });

  wss.on('close', () => clearInterval(interval));
}
```

---

## Try It Yourself

**Exercise:** Wire up the `engine` EventEmitter to broadcast job progress over WebSocket:

```typescript
// In your setup code:
engine.on('job:progress', (jobId, stepName, percent) => {
  broadcastToJob(jobId, { type: 'progress', stepName, percent });
});

engine.on('job:done', (jobId, result) => {
  broadcastToJob(jobId, { type: 'done', result });
  rooms.delete(jobId); // clean up room
});
```

Test with `wscat`:
```bash
npx wscat -c "ws://localhost:3000/ws?jobId=<job-id>&token=<jwt>"
# Then trigger a job run and watch progress messages arrive in real time
```

---

## Capstone Connection

PipeForge's WebSocket server at `/ws` enables the dashboard UI to show live job progress. The `broadcastToJob` function is called by the `PipelineEngine` every time a step completes, connecting the core engine (Module 02's EventEmitter) to real-time client updates.
