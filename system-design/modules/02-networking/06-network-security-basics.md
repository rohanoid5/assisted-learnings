# 2.6 — Network Security Basics

## Concept

Network security isn't optional — it's a set of concrete, implementable controls that prevent credentials from being stolen in transit, protect your API from cross-origin abuse, and stop content injection attacks. This topic covers the minimal security baseline that every production system must have.

---

## Deep Dive

### TLS — Transport Layer Security

TLS encrypts all data in transit, preventing:
- **Eavesdropping**: ISPs, coffee shop Wi-Fi operators, government surveillance
- **Man-in-the-Middle (MitM) attacks**: Intercepting and modifying HTTP traffic
- **Session hijacking**: Stealing auth cookies from clear-text HTTP

```
TLS 1.3 Handshake (simplified):
  Client                     Server
    │── ClientHello ────────►│  (supported TLS versions, cipher suites)
    │◄── ServerHello ─────────│  (chosen cipher, cert, server's public key)
    │── [verify cert] ────────│  (Client verifies cert against CA trust store)
    │── Finished ────────────►│  (Encrypted with session key derived from handshake)
    │◄── Finished ────────────│
    │═══ Encrypted data ══════│  (All subsequent traffic encrypted)

TLS 1.3 improvements over 1.2:
  ✓ 1-RTT handshake (vs 2-RTT in 1.2)
  ✓ 0-RTT resumption for returning connections
  ✓ Removed weak cipher suites (RC4, DES, SHA-1)
  ✓ Forward secrecy by default (Diffie-Hellman key exchange)
```

### HTTPS and Certificate Management

```
Server certificate chain:
  Your cert: scaleforge.io
    ↑ signed by
  Intermediate CA: Let's Encrypt R3
    ↑ signed by
  Root CA: ISRG Root X1 (trusted by browsers/OS)

Practical setup options:
  1. Let's Encrypt (free) + Certbot → valid 90-day cert, auto-renew
  2. Cloudflare proxy → Cloudflare manages TLS, terminates at edge
  3. AWS ACM → Free managed certs for ALB/CloudFront
  4. Nginx cert termination → Cert on Nginx, plain HTTP to app servers
     (acceptable if internal network is private/VPC)
```

### CORS — Cross-Origin Resource Sharing

```
Browser security rule: JavaScript on origin-A cannot read responses from origin-B
unless origin-B explicitly permits it.

Without CORS (browser blocks):
  Attacker's site: https://evil.com
    ↓ JavaScript: fetch('https://api.scaleforge.io/user/me', { credentials: 'include' })
    ← Browser receives response but BLOCKS JavaScript from reading it
    (But the request DID reach the server! CORS is read protection, not request prevention)

With CORS (server tells browser who's allowed):
  api.scaleforge.io response headers:
    Access-Control-Allow-Origin: https://app.scaleforge.io
    Access-Control-Allow-Credentials: true
    Access-Control-Allow-Methods: GET, POST, DELETE, PATCH
    Access-Control-Allow-Headers: Content-Type, Authorization

  Now the browser allows JavaScript at app.scaleforge.io to read the response.
  evil.com still cannot.

Preflight (OPTIONS) request:
  For non-simple requests (JSON body, Authorization header), browser sends:
    OPTIONS /api/v1/urls HTTP/1.1
    Origin: https://app.scaleforge.io
    Access-Control-Request-Method: POST
    Access-Control-Request-Headers: Authorization, Content-Type
  
  Server must respond with CORS headers — THEN browser sends the actual request.
```

### Security Headers Reference

| Header | Purpose | ScaleForge value |
|--------|---------|-----------------|
| `Strict-Transport-Security` | Force HTTPS for N seconds | `max-age=31536000; includeSubDomains; preload` |
| `Content-Security-Policy` | Restrict resource loading origins | `default-src 'self'` |
| `X-Content-Type-Options` | Prevent MIME-type sniffing | `nosniff` |
| `X-Frame-Options` | Prevent clickjacking via `<iframe>` | `DENY` |
| `Referrer-Policy` | Control Referer header leakage | `strict-origin-when-cross-origin` |
| `Permissions-Policy` | Disable browser APIs | `camera=(), microphone=()` |

---

## Code Examples

### CORS Configuration for ScaleForge API

```typescript
// src/middleware/cors.middleware.ts

import cors from 'cors';  // npm install cors @types/cors

const ALLOWED_ORIGINS = process.env.ALLOWED_ORIGINS?.split(',') ?? [
  'http://localhost:5173',        // Vite dev server
  'https://app.scaleforge.io',    // Production frontend
];

export const corsMiddleware = cors({
  origin: (requestOrigin, callback) => {
    // Allow requests with no origin (mobile apps, Postman, curl)
    if (!requestOrigin) {
      callback(null, true);
      return;
    }

    if (ALLOWED_ORIGINS.includes(requestOrigin)) {
      callback(null, true);
    } else {
      callback(new Error(`Origin ${requestOrigin} not allowed`));
    }
  },
  credentials: true,    // Allow cookies (for session auth if used)
  methods: ['GET', 'POST', 'PATCH', 'DELETE', 'OPTIONS'],
  allowedHeaders: ['Content-Type', 'Authorization', 'X-Request-Id'],
  exposedHeaders: ['X-Request-Id', 'X-RateLimit-Remaining'],
  maxAge: 600,  // Cache preflight response for 10 minutes
});
```

### Nginx TLS Termination Configuration

```nginx
# capstone/scaleforge/nginx/default.conf
# Nginx handles TLS; plain HTTP internally to Node.js app

server {
    listen 443 ssl http2;
    server_name scaleforge.io;

    # TLS configuration
    ssl_certificate     /etc/nginx/certs/fullchain.pem;
    ssl_certificate_key /etc/nginx/certs/privkey.pem;
    ssl_protocols       TLSv1.2 TLSv1.3;
    ssl_ciphers         ECDHE-ECDSA-AES128-GCM-SHA256:ECDHE-RSA-AES128-GCM-SHA256;
    ssl_prefer_server_ciphers on;
    ssl_session_cache   shared:SSL:10m;
    ssl_session_timeout 10m;

    # Security headers
    add_header Strict-Transport-Security "max-age=31536000; includeSubDomains; preload" always;
    add_header X-Content-Type-Options nosniff always;
    add_header X-Frame-Options DENY always;
    add_header Referrer-Policy strict-origin-when-cross-origin always;

    # Proxy to Node.js app (plain HTTP internal)
    location / {
        proxy_pass http://app:3001;
        proxy_http_version 1.1;
        proxy_set_header Upgrade $http_upgrade;
        proxy_set_header Connection ''; # Keep-alive for upstream
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto https;
        proxy_cache_bypass $http_upgrade;
    }
}

# Redirect HTTP to HTTPS
server {
    listen 80;
    server_name scaleforge.io;
    return 301 https://$host$request_uri;
}
```

---

## Try It Yourself

**Exercise:** Add a CSRF protection mechanism for state-changing endpoints.

CORS prevents cross-origin reads but doesn't stop a malicious site from submitting a form (or fetching with cookies) to your API. CSRF tokens add a second layer.

```typescript
// src/middleware/csrf.middleware.ts

// The double-submit cookie pattern:
// 1. Server sets a random token as a cookie
// 2. Client reads the cookie and sends it as a request header (X-CSRF-Token)
// 3. Server compares cookie value to header value — a cross-origin attacker
//    can't read the cookie (same-origin policy), so they can't copy it to the header.

import { randomBytes } from 'node:crypto';
import type { Request, Response, NextFunction } from 'express';

const CSRF_COOKIE = 'csrf-token';
const CSRF_HEADER = 'x-csrf-token';

export function setCsrfToken(req: Request, res: Response, next: NextFunction) {
  // TODO:
  // 1. If no csrf-token cookie exists, generate one (randomBytes(32).toString('hex'))
  // 2. Set it as a cookie with SameSite=Strict, Secure=true, HttpOnly=false
  //    (HttpOnly=false so JavaScript can read it to put in the header)
  // 3. Call next()
  throw new Error('Not implemented');
}

export function verifyCsrfToken(req: Request, res: Response, next: NextFunction) {
  // TODO:
  // 1. Read cookie value: req.cookies[CSRF_COOKIE]
  // 2. Read header value: req.headers[CSRF_HEADER]
  // 3. Compare them using timingSafeEqual (to prevent timing attacks)
  // 4. If they match: call next()
  // 5. If not: res.status(403).json({ error: 'CSRF token invalid' })
  throw new Error('Not implemented');
}
```

<details>
<summary>Show solution</summary>

```typescript
import { randomBytes, timingSafeEqual } from 'node:crypto';

export function setCsrfToken(req: Request, res: Response, next: NextFunction) {
  if (!req.cookies?.[CSRF_COOKIE]) {
    const token = randomBytes(32).toString('hex');
    res.cookie(CSRF_COOKIE, token, {
      httpOnly: false,  // JS must be able to read it
      secure: process.env.NODE_ENV === 'production',
      sameSite: 'strict',
      maxAge: 24 * 60 * 60 * 1000,  // 1 day
    });
  }
  next();
}

export function verifyCsrfToken(req: Request, res: Response, next: NextFunction) {
  const cookieToken = req.cookies?.[CSRF_COOKIE];
  const headerToken = req.headers[CSRF_HEADER];

  if (!cookieToken || !headerToken || typeof headerToken !== 'string') {
    res.status(403).json({ error: 'CSRF token missing' });
    return;
  }

  // Use timingSafeEqual to prevent timing attacks
  const cookieBuf = Buffer.from(cookieToken);
  const headerBuf = Buffer.from(headerToken);

  if (cookieBuf.length !== headerBuf.length || !timingSafeEqual(cookieBuf, headerBuf)) {
    res.status(403).json({ error: 'CSRF token invalid' });
    return;
  }

  next();
}
```

</details>

---

## Capstone Connection

The CORS configuration here restricts ScaleForge's API to requests from `app.scaleforge.io` only — preventing a malicious site from stealing user URLs by making authenticated requests with the victim's cookies. The Nginx TLS config establishes the end-to-end encryption that module 08's Prometheus metrics endpoint must also be protected behind — metrics endpoints must never be publicly accessible (they reveal internal service topology). Module 05 (Security) dives deeper into JWT, OAuth, and role-based access control.
