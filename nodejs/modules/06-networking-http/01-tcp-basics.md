# 6.1 — TCP & Socket Basics

## Concept

Every HTTP request is a TCP connection. Understanding the TCP lifecycle — SYN/ACK handshake, data transfer, connection teardown, keep-alive — helps you reason about latency, connection pool sizing, and why WebSockets are efficient. This topic gives you the mental model you need to diagnose "why is my API slow?" at the network level.

---

## Deep Dive

### The TCP Three-Way Handshake

```
Client                    Server
  |                          |
  |──── SYN ───────────────► |  "I want to connect"
  |                          |
  |◄─── SYN-ACK ──────────── |  "OK, acknowledged"
  |                          |
  |──── ACK ───────────────► |  "Great, connected"
  |                          |
  |──── HTTP Request ──────► |  Data transfer
  |◄─── HTTP Response ─────  |
  |                          |
  |──── FIN ───────────────► |  "I'm done"
  |◄─── FIN-ACK ──────────── |  "Goodbye"
```

Each handshake = ~1 RTT (round trip). Over a high-latency network (e.g., 100ms RTT), this means 100ms before any data is sent.

### HTTP Keep-Alive (Persistent Connections)

HTTP/1.1 defaults to keep-alive — the TCP connection is reused for multiple requests:

```
Client                    Server
  |                          |
  |──── Request 1 ─────────► |
  |◄─── Response 1 ───────── |  (connection stays open)
  |──── Request 2 ─────────► |  (no new handshake!)
  |◄─── Response 2 ───────── |
  |...                       |
  |──── FIN ───────────────► |  (closed after keepAliveTimeout)
```

Node.js `http.Server` has `keepAliveTimeout: 5000` (5s) by default. Behind load balancers (ALB, nginx), you often need a higher value:

```typescript
import http from 'node:http';
import app from './app.js';

const server = http.createServer(app);

// Keep connections alive for 65s (> ALB idle timeout of 60s)
server.keepAliveTimeout = 65_000;
server.headersTimeout = 66_000;
```

### Raw TCP Server (Understanding the Primitives)

```typescript
import net from 'node:net';

const server = net.createServer((socket) => {
  console.log(`New connection: ${socket.remoteAddress}:${socket.remotePort}`);

  socket.on('data', (data) => {
    // Raw bytes — could be HTTP, WebSocket, custom protocol, anything
    console.log('Received:', data.toString());
    socket.write('HTTP/1.1 200 OK\r\nContent-Length: 5\r\n\r\nHello');
    socket.end();
  });

  socket.on('error', (err) => console.error('Socket error:', err));
});

server.listen(3000, () => console.log('TCP server on :3000'));
```

This is exactly what `node:http` builds on top of. Express builds on top of that.

---

## Capstone Connection

Understanding TCP keep-alive is directly relevant to PipeForge's production deployment (Module 11). The app server sits behind an AWS ALB — setting `keepAliveTimeout` correctly prevents "ECONNRESET" errors that occur when the ALB drops idle connections the Node.js server still considers open.
