# Module 1 — Exercises

## Overview

Complete all exercises before moving to Module 2. They reinforce the internals concepts and produce the PipeForge project scaffold you'll build on throughout the rest of the tutorial.

---

## Exercise 1 — Bootstrap PipeForge

**Goal:** Create the PipeForge project from scratch using the plan's directory structure.

The `capstone/pipeforge/` directory already contains a starter skeleton. Your job is to understand and verify every configuration choice.

1. Navigate to `capstone/pipeforge/`

2. Review `package.json` and answer these questions:
   - Why is `"type": "module"` set?
   - What does the `--loader ts-node/esm` flag do in the `dev` script?
   - Why is `prisma` in `devDependencies` but `@prisma/client` in `dependencies`?

3. Review `tsconfig.json`:
   - What does `"moduleResolution": "bundler"` mean vs `"node16"`?
   - Why is `"esModuleInterop": true` needed?

4. Install dependencies:
   ```bash
   npm install
   ```

5. Verify TypeScript compiles cleanly:
   ```bash
   npm run typecheck
   ```

6. Start the dev server and verify the health check works:
   ```bash
   npm run dev
   # In another terminal:
   curl http://localhost:3000/health
   ```

**Verification:** `{"status":"ok","timestamp":"..."}` response.

---

## Exercise 2 — ESM vs CJS Experiment

**Goal:** Observe module caching and the `require` algorithm directly.

Create a temporary experiment file (delete after the exercise):

```typescript
// experiments/module-cache.ts
// 1. Create two imports of the same module and verify they're identical
// 2. Observe that module code runs only once
// 3. Use import.meta.url to get the current file path

console.log('Module URL:', import.meta.url);

// Create a counter module inline using a dynamic import
const counter1 = await import('../src/core/pipeline-engine.js');
const counter2 = await import('../src/core/pipeline-engine.js');

// Are they the same reference?
console.log('Same module instance?', counter1 === counter2);
```

Run it:
```bash
node --loader ts-node/esm experiments/module-cache.ts
```

<details>
<summary>Show expected output and explanation</summary>

```
Module URL: file:///path/to/pipeforge/experiments/module-cache.ts
Same module instance? true
```

ESM modules are cached by their resolved file URL. Two imports of the same path return the same module namespace object. This is what enables the singleton pattern in `src/db/client.ts`.

</details>

---

## Exercise 3 — Event Loop Order Verification

**Goal:** Write and run an event loop ordering experiment to validate your understanding from Topic 1.3.

```typescript
// experiments/event-loop.ts
import { readFile } from 'node:fs/promises';

async function main() {
  console.log('1. sync start');

  // Schedule a setTimeout (timers phase)
  setTimeout(() => console.log('6. setTimeout(0)'), 0);

  // Schedule a setImmediate (check phase)
  setImmediate(() => console.log('7. setImmediate'));

  // Schedule a nextTick
  process.nextTick(() => console.log('3. nextTick'));

  // Promise (microtask)
  Promise.resolve().then(() => console.log('4. Promise.then'));

  // Async I/O — which phase does the callback land in?
  readFile('package.json').then(() => {
    console.log('5. fs.readFile resolved');
    setTimeout(() => console.log('8. setTimeout inside I/O callback'), 0);
    setImmediate(() => console.log('8b. setImmediate inside I/O callback'));
  });

  console.log('2. sync end');
}

main();
```

Before running, write your predicted order. Then run and compare.

---

## Exercise 4 — System Info Health Endpoint

**Goal:** Implement the `/health` endpoint in `src/api/server.ts` using built-in modules.

Update the health check route to return meaningful system information:

```typescript
// Update src/api/server.ts
import os from 'node:os';
import { randomUUID } from 'node:crypto';

const instanceId = randomUUID();

app.get('/health', (_req, res) => {
  const totalMem = os.totalmem();
  const freeMem = os.freemem();

  res.json({
    status: 'ok',
    timestamp: new Date().toISOString(),
    instance: instanceId,
    node: process.version,
    platform: os.platform(),
    memory: {
      totalMB: Math.round(totalMem / 1024 / 1024),
      freeMB: Math.round(freeMem / 1024 / 1024),
      usedPercent: Math.round(((totalMem - freeMem) / totalMem) * 100),
    },
    uptime: {
      processSeconds: Math.floor(process.uptime()),
      systemSeconds: Math.floor(os.uptime()),
    },
  });
});
```

**Verification:**
```bash
curl http://localhost:3000/health | json_pp
```

---

## Exercise 5 — npm Workspace Understanding

**Goal:** Understand how `npm install` and the lockfile interact.

1. Check the current state:
   ```bash
   npm ls --depth=0
   npm outdated
   ```

2. Check for vulnerabilities:
   ```bash
   npm audit
   ```

3. Understand why a transitive dep exists:
   ```bash
   npm why typescript
   ```

4. Simulate what `npm ci` does differently from `npm install`:
   ```bash
   # npm ci: clean install, uses lockfile exactly, fails if lockfile is outdated
   # npm install: may update lockfile
   # In CI: always use npm ci
   ```

---

## Capstone Checkpoint ✅

Before moving to Module 2, verify you can answer these questions:

- [ ] Can explain in plain language what V8 and libuv each do, and how they interact
- [ ] Can describe what happens at the OS level when `fs.readFile()` is called
- [ ] Can predict the output of a snippet mixing `setTimeout`, `setImmediate`, `process.nextTick`, and `Promise.then`
- [ ] Can explain why `require()` always returns the same object for the same module path
- [ ] Can explain the difference between CommonJS and ESM, and when you'd use each
- [ ] Has the PipeForge project running locally with `npm run dev` and responding to `GET /health`
- [ ] Understands why `"type": "module"` was added to `package.json` and what it does
