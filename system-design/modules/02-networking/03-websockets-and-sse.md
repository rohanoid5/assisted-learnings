# 2.3 — WebSockets and Server-Sent Events

## Concept

Standard HTTP is request-response: the client asks, the server answers. For real-time updates (live dashboards, notifications, collaborative tools), three patterns exist: **long polling** (repeated requests), **Server-Sent Events / SSE** (one-way server push over HTTP), and **WebSockets** (full-duplex bidirectional channel). Choosing the wrong one wastes resources or misses protocol capabilities.

---

## Deep Dive

### Long Polling — The Compatibility Hack

```
Client                      Server
  │── GET /events ─────────►│
  │                         │  (Server holds connection open
  │                         │   until an event occurs or timeout)
  │◄── 200 { event } ───────│  (Event occurred! Respond immediately)
  │── GET /events ─────────►│  (Client immediately reconnects)
  │                         │  (holds again...)
  │◄── 200 { event } ───────│

Pros:
  ✓ Works with any HTTP infrastructure (no WebSocket upgrade needed)
  ✓ Works behind all proxies and firewalls
  ✓ Graceful fallback

Cons:
  ✗ High latency (response → reconnect → server receives second request)
  ✗ Server must hold connections open — goroutines/threads per connection
  ✗ Extra HTTP overhead on every cycle
  
Use when: You need real-time-ish updates through corporate proxies/firewalls
          that block WebSocket upgrades (old enterprise environments)
```

### Server-Sent Events (SSE) — One-Way HTTP Push

```
Client                      Server
  │── GET /analytics/live ─►│
  │◄════ HTTP/1.1 200 ═══════│
  │◄═ Content-Type:          │
  │     text/event-stream ══►│
  │◄═══ data: {"clicks":1} ══│  (server pushes whenever it wants)
  │◄═══ data: {"clicks":2} ══│
  │◄═══ data: {"clicks":3} ══│
  │                          │  (connection stays open indefinitely)

The text/event-stream format:
  data: {"type":"click","country":"US"}\n\n
  event: url-deleted\n
  data: {"code":"abc123"}\n\n
  id: 42\n          ← client uses for reconnect (Last-Event-ID header)
  retry: 3000\n     ← reconnect delay in ms

Pros:
  ✓ Simple: built on plain HTTP (no protocol upgrade)
  ✓ Auto-reconnect built into browser EventSource API
  ✓ Works with HTTP/2 multiplexing
  ✓ Perfect for dashboards, notifications, live feeds (one direction)

Cons:
  ✗ One-way: server → client only
  ✗ Limited to 6 connections per domain in HTTP/1.1 browsers
     (HTTP/2 removes this limit via multiplexing)
```

### WebSockets — Full-Duplex Bidirectional

```
Client                      Server
  │── GET /ws ──────────────►│  (HTTP Upgrade request)
  │   Upgrade: websocket     │
  │◄── 101 Switching ────────│  (Protocol upgraded)
  │═══════ WS Frame ════════►│  (client → server, any time)
  │◄══════ WS Frame ══════════│  (server → client, any time)
  │═══════ WS Frame ════════►│
  │◄══════ WS Frame ══════════│

Pros:
  ✓ Bidirectional: client can send too (chat, gaming, collaborative editing)
  ✓ Low overhead: no HTTP headers on each message, just WS frames
  ✓ True real-time: sub-millisecond latency once connected

Cons:
  ✗ Stateful connection: harder to load-balance (sticky sessions needed)
  ✗ Some proxies/firewalls block WebSocket upgrade
  ✗ More complex: connection lifecycle, heartbeats, reconnection logic
  ✗ Doesn't use HTTP caching, CDN-friendly patterns
```

### Choosing the Right Pattern

```
                     Server → Client only?
                          │
               ┌──────────┼──────────┐
               │YES                   │NO
               ▼                      ▼
         SSE (EventSource)      WebSockets
              │                     (chat, games,
    ┌─────────┴─────────┐            collaborative tools)
    │                   │
  Simple?         Behind firewall /
  Dashboard?      old proxy?
    │YES               │YES
    ▼                  ▼
   SSE           Long polling
   ✓
```

**ScaleForge analytics dashboard** → **SSE**
- Server pushes click events to owner's browser
- No client→server messages needed for the feed
- Works with HTTP/2 (single multiplexed connection)

---

## Code Examples

### SSE Endpoint — Live Click Analytics

```typescript
// src/routes/analytics.router.ts

import { Router } from 'express';
import { authenticate } from '../middleware/auth.middleware.js';
import { UrlService } from '../services/url.service.js';
import { ClickEventBus } from '../events/click-event-bus.js';

export const analyticsRouter = Router();

// GET /api/v1/urls/:code/analytics/live
// Returns an SSE stream of click events for a URL owned by the authenticated user
analyticsRouter.get('/:code/analytics/live', authenticate, async (req, res) => {
  const { code } = req.params;

  // Verify ownership before subscribing
  const url = await UrlService.findOwnedByUser(code, req.user.id);
  if (!url) {
    res.status(404).json({ error: 'URL not found' });
    return;
  }

  // SSE headers
  res.setHeader('Content-Type', 'text/event-stream');
  res.setHeader('Cache-Control', 'no-cache');
  res.setHeader('Connection', 'keep-alive');
  res.setHeader('X-Accel-Buffering', 'no'); // Disable Nginx buffering for SSE
  res.flushHeaders(); // Send headers immediately before any events

  // Helper to send a typed SSE event
  const sendEvent = (type: string, data: unknown) => {
    res.write(`event: ${type}\n`);
    res.write(`data: ${JSON.stringify(data)}\n\n`);
  };

  // Send initial state
  sendEvent('connected', { code, message: 'Stream started' });

  // Subscribe to click events published by the click worker
  const unsubscribe = ClickEventBus.subscribe(code, (click) => {
    sendEvent('click', click);
  });

  // Send heartbeat every 30s to keep connection alive through proxies
  const heartbeat = setInterval(() => {
    res.write(': heartbeat\n\n'); // SSE comment — keeps connection alive
  }, 30_000);

  // Cleanup when client disconnects
  req.on('close', () => {
    clearInterval(heartbeat);
    unsubscribe();
    res.end();
  });
});
```

### In-Process Click Event Bus (Pub/Sub)

```typescript
// src/events/click-event-bus.ts
// Simple in-process event bus for broadcasting click events to SSE connections.
// In Module 07, this is replaced by Redis Pub/Sub across replicas.

type ClickEventHandler = (click: { country: string; device: string; timestamp: number }) => void;

export const ClickEventBus = {
  subscribers: new Map<string, Set<ClickEventHandler>>(),

  subscribe(code: string, handler: ClickEventHandler): () => void {
    if (!this.subscribers.has(code)) {
      this.subscribers.set(code, new Set());
    }
    this.subscribers.get(code)!.add(handler);

    // Return unsubscribe function
    return () => {
      this.subscribers.get(code)?.delete(handler);
      if (this.subscribers.get(code)?.size === 0) {
        this.subscribers.delete(code);
      }
    };
  },

  publish(code: string, click: Parameters<ClickEventHandler>[0]): void {
    this.subscribers.get(code)?.forEach(handler => handler(click));
  },
};
```

### Client-Side SSE Consumer (TypeScript browser code)

```typescript
// src/client/analytics-dashboard.ts (frontend TypeScript)

export function subscribeToLiveClicks(
  code: string,
  token: string,
  onClick: (click: { country: string; device: string; timestamp: number }) => void
): () => void {
  // EventSource is the browser's built-in SSE client
  const source = new EventSource(`/api/v1/urls/${code}/analytics/live`, {
    withCredentials: false,
  });

  // Note: EventSource doesn't support custom headers (like Authorization).
  // Pass token as query param or use a cookie-based auth strategy.
  // For demo purposes, token would go in URL (not ideal for production — use cookies):
  // const source = new EventSource(`/api/v1/urls/${code}/analytics/live?token=${token}`);

  source.addEventListener('click', (event) => {
    const click = JSON.parse(event.data);
    onClick(click);
  });

  source.addEventListener('error', (err) => {
    console.warn('SSE connection lost:', err);
    // EventSource automatically reconnects using the Retry-After header
  });

  // Return cleanup function
  return () => source.close();
}
```

---

## Try It Yourself

**Exercise:** Implement a simple long-polling endpoint as a fallback for environments that don't support SSE.

```typescript
// src/routes/polling.router.ts
// GET /api/v1/urls/:code/analytics/poll?after=<timestamp>
// Returns click events that occurred after the given timestamp.
// Client calls this repeatedly to simulate streaming.

pollRouter.get('/:code/analytics/poll', authenticate, async (req, res) => {
  const { code } = req.params;
  const after = Number(req.query.after) || Date.now() - 60_000;
  const timeout = 20_000; // Hold for 20 seconds max

  // TODO:
  // 1. Verify URL ownership (same as SSE endpoint)
  // 2. Poll the database for clicks after the `after` timestamp
  // 3. If clicks exist, return them immediately
  // 4. If none yet, hold the request open (setInterval or subscribe to ClickEventBus)
  // 5. Return when either: new events arrive OR timeout expires (return empty array)
  // 6. Include a `lastSeen` timestamp in the response so the client knows
  //    what `after` value to use on the next poll
});
```

<details>
<summary>Show solution</summary>

```typescript
pollRouter.get('/:code/analytics/poll', authenticate, async (req, res) => {
  const { code } = req.params;
  const after = Number(req.query.after) || Date.now() - 60_000;

  const url = await UrlService.findOwnedByUser(code, req.user.id);
  if (!url) { res.status(404).json({ error: 'Not found' }); return; }

  // Try immediate return first
  const recentClicks = await prisma.click.findMany({
    where: { shortUrlId: url.id, timestamp: { gt: new Date(after) } },
    orderBy: { timestamp: 'asc' },
    take: 50,
  });

  if (recentClicks.length > 0) {
    res.json({ events: recentClicks, lastSeen: Date.now() });
    return;
  }

  // Hold connection — wait for new events or timeout
  const timer = setTimeout(() => {
    unsubscribe();
    res.json({ events: [], lastSeen: Date.now() });
  }, 20_000);

  const unsubscribe = ClickEventBus.subscribe(code, (click) => {
    clearTimeout(timer);
    unsubscribe();
    res.json({ events: [click], lastSeen: Date.now() });
  });

  req.on('close', () => {
    clearTimeout(timer);
    unsubscribe();
  });
});
```

</details>

---

## Capstone Connection

The SSE endpoint built here powers the ScaleForge analytics dashboard in real time. The `ClickEventBus` in-process pub/sub works fine with one server instance (Module 01–06), but breaks when Module 07 introduces multiple replicas — a click processed by replica A won't reach a browser connected to replica B. Module 07 replaces `ClickEventBus` with **Redis Pub/Sub** so all replicas share the event stream. This is a concrete example of how horizontal scaling forces architectural evolution.
