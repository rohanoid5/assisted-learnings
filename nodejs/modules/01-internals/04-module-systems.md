# 1.4 — Module Systems: CommonJS & ESM

## Concept

Node.js has two module systems: **CommonJS** (the original, synchronous `require()`) and **ESM** (the modern, standard `import`/`export`). You've probably used both. But do you know how CommonJS resolves paths, how the require cache works, what happens with circular dependencies, or why ESM is statically analyzable while CommonJS isn't?

These internals matter for build tools, bundling, tree-shaking, and avoiding subtle bugs when mixing the two systems.

---

## Deep Dive

### CommonJS: How `require()` Actually Works

When you call `require('./utils')`, Node.js runs this algorithm:

```
1. RESOLVE the path
   - Is it a core module (fs, path)? → return it directly
   - Does it start with ./ ../ /?  → resolve as file path
     - Try: ./utils.js
     - Try: ./utils.json
     - Try: ./utils.node
     - Try: ./utils/index.js
   - Otherwise: walk up node_modules/ directories
     - Try: ./node_modules/utils/
     - Try: ../node_modules/utils/
     - ... until filesystem root

2. CHECK the require cache (Module._cache)
   - If the resolved path is already cached → return the cached exports
   - This is how Node.js prevents infinite loops and ensures singletons

3. LOAD the file
   - Wrap it in a function: (function(exports, require, module, __filename, __dirname) { ... })
   - Execute the wrapped function
   - Cache module.exports at the resolved path

4. RETURN module.exports
```

### The Module Wrapper

Every CommonJS file is wrapped in this function before execution:

```typescript
(function(exports, require, module, __filename, __dirname) {
  // Your actual code goes here
  const result = 42;
  module.exports = { result };
});
```

This is why `__dirname` and `__filename` exist in CommonJS. It's also why `exports` (a reference to `module.exports`) works — but only until you reassign `module.exports`:

```typescript
// ✅ Works: mutating the exports object
exports.hello = () => 'hello';

// ❌ Breaks: reassigning exports breaks the reference
exports = { hello: () => 'hello' };  // now exports ≠ module.exports!

// ✅ Correct reassignment
module.exports = { hello: () => 'hello' };
```

### The Require Cache

```typescript
// moduleA.ts
console.log('moduleA loaded');
let count = 0;
export const increment = () => ++count;
export const getCount = () => count;
```

```typescript
// main.ts (CommonJS)
const a1 = require('./moduleA');
const a2 = require('./moduleA');

// 'moduleA loaded' prints ONCE — the second require is served from cache
console.log(a1 === a2);  // true — same object!

a1.increment();
console.log(a2.getCount()); // 1 — they share state!
```

You can inspect and manipulate the cache:

```typescript
console.log(require.cache);  // All cached modules
delete require.cache[require.resolve('./moduleA')];  // Force reload
```

### Circular Dependencies in CommonJS

```typescript
// a.js
const b = require('./b');
console.log('a sees b.name:', b.name);
exports.name = 'A';

// b.js
const a = require('./a');
console.log('b sees a.name:', a.name);  // undefined! 'A' not yet assigned
exports.name = 'B';

// main.js
require('./a');
```

CommonJS handles circular deps by returning the **partially-executed module** — whatever has been exported so far. This can lead to `undefined` values. ESM handles this differently with live bindings.

### ESM: Static Analysis & Live Bindings

ESM `import` statements are **statically analyzed** before any code runs. This enables:

- **Tree-shaking**: bundlers know exactly what's imported and can eliminate unused code
- **Circular dependency detection**: ESM creates live bindings, not snapshots
- **Top-level `await`**: ESM modules can `await` at the top level

```typescript
// ESM module structure
import { createReadStream } from 'node:fs';    // named import
import path from 'node:path';                   // default import
import * as utils from './utils.js';            // namespace import
import type { Job } from './types.js';          // type-only import (TypeScript)

export const VERSION = '1.0.0';                 // named export
export default class PipelineEngine { ... }     // default export
export type { Job };                            // type re-export
```

### ESM vs CommonJS: Key Differences

| Feature | CommonJS | ESM |
|---------|---------|-----|
| Syntax | `require()` / `module.exports` | `import` / `export` |
| Loading | Synchronous | Asynchronous (can `await`) |
| Analysis | Dynamic (at runtime) | Static (at parse time) |
| `__dirname` / `__filename` | ✅ Built-in | ❌ Use `import.meta.url` |
| Top-level `await` | ❌ | ✅ |
| Tree-shakeable | ❌ | ✅ |
| Circular deps | Partial exports | Live bindings |
| File extension | `.js` or implicit | `.js` or `.mjs` required |

### Interoperability

```typescript
// package.json: "type": "module" → all .js files are ESM
// package.json: "type": "commonjs" (default) → all .js files are CommonJS

// Force ESM regardless of package.json: use .mjs
// Force CJS regardless of package.json: use .cjs

// ESM can import CJS:
import cjsModule from './legacy.cjs';  // ✅ default import only

// CJS CANNOT require() ESM:
const esm = require('./modern.mjs');   // ❌ Error: ERR_REQUIRE_ESM

// CJS can dynamically import ESM:
const esm = await import('./modern.mjs');  // ✅ dynamic import works
```

### `__dirname` in ESM

```typescript
// CommonJS
const __dirname = __dirname;  // built-in

// ESM equivalent
import { fileURLToPath } from 'node:url';
import { dirname } from 'node:path';
const __filename = fileURLToPath(import.meta.url);
const __dirname = dirname(__filename);

// Or use import.meta directly:
const configPath = new URL('./config.json', import.meta.url);
```

---

## Code Examples

### Module Singleton Pattern

```typescript
// db/client.ts — module-level singleton via ESM cache
import { PrismaClient } from '@prisma/client';

const prisma = new PrismaClient();

// Because ESM modules are cached after first import,
// every file that imports from this module gets the SAME instance
export default prisma;
```

### Lazy Loading with Dynamic Import

```typescript
// Only load the plugin when needed — reduces startup time
async function loadPlugin(name: string) {
  try {
    const { default: plugin } = await import(`./plugins/${name}.js`);
    return plugin;
  } catch {
    throw new Error(`Plugin '${name}' not found`);
  }
}
```

---

## Try It Yourself

**Exercise:** Understand module caching.

```typescript
// counter.ts
let count = 0;
export const increment = () => ++count;
export const getCount = () => count;
```

```typescript
// main.ts
// 1. Import counter twice and verify they share state
// 2. Use dynamic import() to see how it compares to static import
// 3. Try: what happens when you import from a URL? (Node 20+)
import * as c1 from './counter.js';
import * as c2 from './counter.js';

c1.increment();
console.log('c2 sees:', c2.getCount()); // What does this print?
```

<details>
<summary>Show solution and explanation</summary>

```typescript
c2.getCount() === 1
// Because ESM modules are cached — c1 and c2 are the SAME module instance.
// c1.increment() mutates the shared state that c2.getCount() reads.

// To get fresh instances, create a factory function:
export function createCounter() {
  let count = 0;
  return { increment: () => ++count, getCount: () => count };
}
```

</details>

---

## Capstone Connection

PipeForge uses ESM throughout (`"type": "module"` in `package.json`):

- The **module-level singleton pattern** powers `src/db/client.ts` — Prisma is instantiated once and shared across all imports without any DI container overhead
- **Dynamic `import()`** is used in the plugin system (Module 07) to load step-processor plugins from the filesystem at runtime without bundling them into the core app
- Understanding `import.meta.url` is required to resolve config file paths relative to the current module in the CLI (Module 05)
- **ESM's static analysis** is what enables TypeScript's `tsc` to tree-shake unused code paths when building the production bundle
