# 1.6 — Built-in Modules

## Concept

Node.js ships with a comprehensive standard library. These built-in modules cover OS interaction, cryptography, URL parsing, compression, DNS, and utilities. Using the `node:` prefix (e.g., `node:fs`, `node:crypto`) is the modern convention — it makes it immediately clear you're importing a built-in rather than an npm package.

This file is a practical reference for the most important built-in modules you'll reach for regularly.

---

## Deep Dive

### The `node:os` Module

```typescript
import os from 'node:os';

os.cpus()           // Array of CPU info objects (count = os.cpus().length)
os.totalmem()       // Total system memory in bytes
os.freemem()        // Free system memory in bytes
os.hostname()       // Machine hostname
os.platform()       // 'linux', 'darwin', 'win32'
os.arch()           // 'x64', 'arm64', 'ia32'
os.homedir()        // '/Users/username' or 'C:\Users\username'
os.tmpdir()         // '/tmp' or OS temp directory
os.EOL              // '\n' on POSIX, '\r\n' on Windows
os.uptime()         // System uptime in seconds
os.networkInterfaces()  // Network interfaces with IP addresses
```

### The `node:util` Module

```typescript
import { promisify, callbackify, inspect, format, types } from 'node:util';

// Convert callback-style functions to Promises
import { readFile } from 'node:fs';
const readFileAsync = promisify(readFile);
const data = await readFileAsync('README.md', 'utf8');

// Deep inspection of objects (better than console.log for nested objects)
console.log(inspect({ a: 1, b: { c: [1, 2, 3] } }, { depth: null, colors: true }));

// printf-style formatting
util.format('Hello %s, you are %d years old', 'Alice', 30);
// → 'Hello Alice, you are 30 years old'

// Type checking
util.types.isPromise(Promise.resolve());   // true
util.types.isRegExp(/abc/);               // true
util.types.isDate(new Date());            // true
```

### The `node:url` Module

```typescript
import { URL, URLSearchParams } from 'node:url';

const url = new URL('https://api.pipeforge.dev/jobs?status=RUNNING&limit=10');

url.hostname        // 'api.pipeforge.dev'
url.pathname        // '/jobs'
url.searchParams.get('status')    // 'RUNNING'
url.searchParams.get('limit')     // '10'
url.searchParams.set('page', '2')
url.toString()      // Updated URL string

// Build URLs safely (no string concatenation!)
const jobUrl = new URL(`/jobs/${jobId}`, 'https://api.pipeforge.dev');
```

### The `node:crypto` Module

```typescript
import {
  randomUUID,
  randomBytes,
  createHash,
  createHmac,
  scrypt,
  timingSafeEqual,
} from 'node:crypto';

// Generate a UUID
const id = randomUUID();  // 'a78b9c12-...'

// Random bytes (for tokens, salts)
const token = randomBytes(32).toString('hex');  // 64-char hex string

// Hash (for checksums, not passwords!)
const hash = createHash('sha256')
  .update('some data')
  .digest('hex');

// HMAC (for JWT signing, webhook verification)
const signature = createHmac('sha256', secretKey)
  .update(payload)
  .digest('hex');

// Password hashing (async, CPU-intensive — use bcrypt or argon2 in production)
import { promisify } from 'node:util';
const scryptAsync = promisify(scrypt);
const salt = randomBytes(16);
const derivedKey = await scryptAsync(password, salt, 64);

// Timing-safe equality (prevents timing attacks)
timingSafeEqual(Buffer.from(a), Buffer.from(b));  // Use this, not a === b!
```

### The `node:zlib` Module

```typescript
import { gzip, gunzip, brotliCompress } from 'node:zlib';
import { promisify } from 'node:util';

const gzipAsync = promisify(gzip);
const gunzipAsync = promisify(gunzip);

// Compress
const compressed = await gzipAsync(Buffer.from('Hello PipeForge!'));
console.log('Original:', 16, 'bytes → Compressed:', compressed.length, 'bytes');

// Decompress
const decompressed = await gunzipAsync(compressed);
console.log(decompressed.toString('utf8')); // 'Hello PipeForge!'

// Stream compression (Module 04 deep dive)
import { createGzip } from 'node:zlib';
import { createReadStream, createWriteStream } from 'node:fs';
import { pipeline } from 'node:stream/promises';

await pipeline(
  createReadStream('large-file.json'),
  createGzip(),
  createWriteStream('large-file.json.gz'),
);
```

### The `node:dns` Module

```typescript
import { lookup, resolve, resolve4, promises as dnsPromises } from 'node:dns';

// lookup: uses OS resolver (same as browser) — supports /etc/hosts
const { address, family } = await dnsPromises.lookup('api.pipeforge.dev');
// → { address: '93.184.216.34', family: 4 }

// resolve: queries DNS servers directly
const addresses = await dnsPromises.resolve4('api.pipeforge.dev');
// → ['93.184.216.34']

// Reverse DNS
const hostnames = await dnsPromises.reverse('8.8.8.8');
// → ['dns.google']
```

---

## Try It Yourself

**Exercise:** Build a system info endpoint for PipeForge's health check.

```typescript
// Create a function that returns system info for the /health endpoint
import os from 'node:os';
import { randomUUID } from 'node:crypto';

export function getSystemInfo() {
  // TODO: Return an object with:
  // - instanceId: a UUID (generated once, not per-call)
  // - platform, arch, nodeVersion
  // - cpuCount
  // - memoryMB: { total, free, used, usedPercent }
  // - uptimeSeconds: system uptime
}
```

<details>
<summary>Show solution</summary>

```typescript
import os from 'node:os';
import { randomUUID } from 'node:crypto';

// Generated once per process start — identifies this server instance
const instanceId = randomUUID();

export function getSystemInfo() {
  const totalMem = os.totalmem();
  const freeMem = os.freemem();
  const usedMem = totalMem - freeMem;

  return {
    instanceId,
    platform: os.platform(),
    arch: os.arch(),
    nodeVersion: process.version,
    cpuCount: os.cpus().length,
    memoryMB: {
      total: Math.round(totalMem / 1024 / 1024),
      free: Math.round(freeMem / 1024 / 1024),
      used: Math.round(usedMem / 1024 / 1024),
      usedPercent: Math.round((usedMem / totalMem) * 100),
    },
    uptimeSeconds: Math.floor(os.uptime()),
  };
}
```

</details>

---

## Capstone Connection

Built-in modules appear throughout PipeForge:

- **`node:crypto`** — JWT secret key generation, password hashing (Module 06), timing-safe token comparison (Module 06)
- **`node:url`** — Building WebSocket URLs and API endpoint URLs safely in the CLI client (Module 05/06)
- **`node:zlib`** — Compressing pipeline output files (Module 04 stream patterns)
- **`node:os`** — Determining the default worker thread pool size (`os.cpus().length / 2`) in Module 10
- **`node:util`** — `promisify` when wrapping a third-party callback API, `inspect` in debug logging
