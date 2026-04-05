# 10.5 — Profiling

## Concept

Profiling tells you where CPU time is being spent. Without profiling, performance optimization is guesswork. Node.js ships a `--prof` flag that generates V8 profiler output, and `--inspect` opens the full Chrome DevTools profiler.

---

## Deep Dive

### `--prof` Static Profiling

```bash
# 1. Run the process with the profiler
node --prof src/api/server.js

# 2. Simulate load (or run the slow operation)
npx autocannon -c 50 -d 10 http://localhost:3000/api/v1/pipelines

# 3. Process the profile log generated (isolate-*.log)
node --prof-process isolate-0x*.log > processed-profile.txt

# 4. Read the profile
# Look for "Bottom up (heavy) profile" section:
# ticks   total  nonlib   name
#   823   41.2%  41.2%   /path/to/heavy-function.js:45:transform
```

### `--inspect` + Chrome DevTools

```bash
# Start with inspector (waits for debugger to connect)
node --inspect src/api/server.js
# or auto-break on start:
node --inspect-brk src/api/server.js

# Open: chrome://inspect → "Open dedicated DevTools for Node"
# Performance tab → Record → do the slow operation → Stop
# Flame chart shows where CPU time goes
```

### Identifying Event Loop Blocking

```typescript
// Find synchronous code that blocks the event loop
// Classic culprits:
JSON.parse(hugeString);          // O(n) in JS, blocks
JSON.stringify(hugeObject);
crypto.pbkdf2Sync(...)           // Use async version!
fs.readFileSync(...)             // Use fs.promises.readFile
Array.sort() on 100k elements    // Consider offloading to worker
```

### `clinic.js` Flame Graphs (Recommended for Production)

```bash
npm install -g clinic

# Flame graph (best for CPU profiling)
clinic flame -- node src/api/server.js

# Doctor (event loop, I/O, memory overview)
clinic doctor -- node src/api/server.js

# Opens an HTML report in the browser automatically
```

### Benchmarking a Function

```typescript
// Micro-benchmark with native perf_hooks
import { performance } from 'node:perf_hooks';

async function benchmark(name: string, fn: () => Promise<void>, iterations = 1000) {
  const start = performance.now();
  for (let i = 0; i < iterations; i++) {
    await fn();
  }
  const elapsed = performance.now() - start;
  console.log(`${name}: ${(elapsed / iterations).toFixed(3)}ms/op  (total: ${elapsed.toFixed(0)}ms)`);
}

await benchmark('transform()', () => transform(testData));
```

---

## Try It Yourself

**Exercise:** Profile PipeForge's `POST /api/v1/pipelines` endpoint under load:

```bash
# Start with profiler
node --prof src/api/server.js &

# Load test
npx autocannon -c 20 -d 5 -m POST \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -b '{"name":"test","steps":[]}' \
  http://localhost:3000/api/v1/pipelines

# Stop server (Ctrl+C), then process the profile
node --prof-process isolate-0x*.log | head -80
```

What function takes the most ticks? (Hint: likely bcrypt or Zod parsing)

---

## Capstone Connection

After profiling, PipeForge found that Zod validation in hot paths was a significant overhead due to unnecessary deep schema re-compilation. The fix: hoist schema definitions to module-level (compile once, validate many times) — a common pattern in Express middleware.
