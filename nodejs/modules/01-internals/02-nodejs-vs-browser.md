# 1.2 — Node.js vs the Browser

## Concept

JavaScript in the browser and JavaScript in Node.js share the same language specification (ECMAScript), but they run in **very different environments** with different globals, APIs, and security models. If you've only ever written browser JS, you'll notice missing APIs. If you've always worked server-side, you might not know what the browser provides that Node.js doesn't.

Knowing the difference prevents bugs and helps you write code that's intentionally environment-aware.

---

## Deep Dive

### Global Objects

| Browser | Node.js | Notes |
|---------|---------|-------|
| `window` | `global` / `globalThis` | `globalThis` works in both since Node.js 12 |
| `document`, `navigator` | ❌ Not available | DOM APIs don't exist in Node.js |
| `location`, `history` | ❌ Not available | Browser navigation APIs |
| `localStorage`, `sessionStorage` | ❌ Not available | Use files or a DB instead |
| `alert`, `confirm`, `prompt` | ❌ Not available | Node.js uses stdin/stdout |
| `fetch` | `fetch` (Node 18+) | Finally native in both! |
| `WebSocket` | `WebSocket` (Node 22+) / `ws` package | ws package for Node 18-21 |
| `console` | `console` | Same API, but Node adds `console.dir` depth options |
| `setTimeout`, `setInterval` | `setTimeout`, `setInterval` | Same API; Node adds `setImmediate` |
| `crypto` | `crypto` (Web Crypto API, Node 19+) | Node also has `node:crypto` with more algorithms |

### Node.js-Only Globals

```typescript
// Available everywhere in Node.js, not in browsers

process.env.NODE_ENV       // Environment variables
process.argv               // Command-line arguments: ['node', 'script.js', ...args]
process.exit(0)            // Exit with a code
process.stdout.write('..') // Direct stream write (no newline)
process.cwd()              // Current working directory

__dirname    // Directory of current file (CommonJS only)
__filename   // Full path of current file (CommonJS only)

// ESM equivalents:
import { fileURLToPath } from 'node:url';
const __filename = fileURLToPath(import.meta.url);
const __dirname = path.dirname(__filename);
```

### The Module System Difference

Browsers load scripts via `<script>` tags or ES modules loaded via `import`. Node.js has its own module loading system (CommonJS since v0.x, ESM since v12).

```typescript
// Browser ESM
import { format } from 'https://esm.sh/date-fns';  // URL imports work!
import data from './data.json' assert { type: 'json' };

// Node.js ESM
import { format } from 'date-fns';             // Resolved from node_modules
import data from './data.json' with { type: 'json' };  // Import assertions
import { createReadStream } from 'node:fs';    // Built-in module prefix
```

### Security Model

| Aspect | Browser | Node.js |
|--------|---------|---------|
| **File system access** | ❌ Sandboxed | ✅ Full access |
| **Network** | Restricted by CORS | Unrestricted |
| **Exec shell commands** | ❌ Impossible | ✅ Via `child_process` |
| **Environment variables** | ❌ | ✅ `process.env` |
| **OS-level APIs** | ❌ | ✅ `os`, signals, etc. |
| **Trust model** | Untrusted code (from internet) | Trusted code (you wrote it) |

The browser's sandbox protects users from malicious websites. Node.js has no such sandbox — your code runs with the same OS permissions as the user who launched it. This is why Node.js security matters (Module 11).

### APIs Available in Both (Since Node.js 18+)

```typescript
// These work identically in both environments:
fetch('https://api.example.com/data')
  .then((res) => res.json())
  .then(console.log);

// Web Crypto API
const key = await crypto.subtle.generateKey(
  { name: 'AES-GCM', length: 256 },
  true,
  ['encrypt', 'decrypt']
);

// URL API
const url = new URL('https://pipeforge.dev/api/jobs?status=RUNNING');
console.log(url.searchParams.get('status')); // 'RUNNING'

// Blob, File, FormData, Headers, Request, Response
const blob = new Blob(['hello world'], { type: 'text/plain' });
```

---

## Code Examples

### Environment Detection

```typescript
// Detect environment at runtime
const isBrowser = typeof window !== 'undefined';
const isNode = typeof process !== 'undefined' && process.versions?.node != null;

console.log({ isBrowser, isNode });

// Better: use import.meta.env (Vite) or process.env (Node)
const isDevelopment = process.env.NODE_ENV === 'development';
```

### process Object Essentials

```typescript
import process from 'node:process';

// Node.js version info
console.log(process.version);          // 'v20.11.0'
console.log(process.versions.v8);      // '11.3.244.8'
console.log(process.versions.uv);      // '1.46.0'

// Platform
console.log(process.platform);         // 'linux', 'darwin', 'win32'
console.log(process.arch);            // 'x64', 'arm64'

// Memory
console.log(process.memoryUsage());
// { rss: 25165824, heapTotal: 6250496, heapUsed: 4989152, external: 1048576 }

// Uptime
console.log(process.uptime());         // seconds since process started

// Signal handling
process.on('SIGTERM', () => {
  console.log('Received SIGTERM — shutting down gracefully');
  process.exit(0);
});
```

---

## Try It Yourself

**Exercise:** Write a utility that prints Node.js runtime info.

```typescript
// runtime-info.ts
import { cpus, totalmem, freemem, hostname } from 'node:os';
import { readFileSync } from 'node:fs';

// TODO: Print the following (use node:os and process):
// 1. Node.js version
// 2. V8 version
// 3. Platform and architecture
// 4. Number of CPU cores
// 5. Total and free memory (in MB)
// 6. Hostname
// 7. Process uptime (in seconds)
// 8. Current working directory
```

<details>
<summary>Show solution</summary>

```typescript
import { cpus, totalmem, freemem, hostname } from 'node:os';

const mb = (bytes: number) => (bytes / 1024 / 1024).toFixed(1);

console.log(`Node.js:     ${process.version}`);
console.log(`V8:          ${process.versions.v8}`);
console.log(`Platform:    ${process.platform} (${process.arch})`);
console.log(`CPU cores:   ${cpus().length}`);
console.log(`Memory:      ${mb(freemem())} MB free / ${mb(totalmem())} MB total`);
console.log(`Hostname:    ${hostname()}`);
console.log(`Uptime:      ${process.uptime().toFixed(1)}s`);
console.log(`CWD:         ${process.cwd()}`);
```

</details>

---

## Capstone Connection

PipeForge uses several Node.js-specific APIs unavailable in browsers:

- `process.env` for configuration (Module 05 — Environment Variables)
- `process.exit()` for clean shutdown in the CLI (Module 05)
- `process.on('SIGTERM')` for graceful API server shutdown (Module 11)
- `process.memoryUsage()` for memory monitoring in profiling (Module 10)
- `process.cwd()` for resolving pipeline config file paths relative to where the CLI is invoked
