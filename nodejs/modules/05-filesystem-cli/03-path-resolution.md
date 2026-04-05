# 5.3 — Path Resolution

## Concept

Path handling is a surprisingly common source of bugs in Node.js applications — especially when mixing ESM and CJS, running tests from different working directories, or targeting Windows. Understanding `node:path`, `import.meta.url`, and `__dirname` equivalents in ESM is essential.

---

## Deep Dive

### The `path` Module

```typescript
import { join, resolve, relative, dirname, basename, extname, sep } from 'node:path';

// join — concatenate path segments (uses OS separator)
join('pipelines', 'etl', 'config.json'); // 'pipelines/etl/config.json' (Unix)
                                          // 'pipelines\\etl\\config.json' (Windows)

// resolve — absolute path from segments (relative to CWD or explicit root)
resolve('pipelines', 'etl.json');        // '/current/working/dir/pipelines/etl.json'
resolve('/base', 'pipelines', 'etl.json'); // '/base/pipelines/etl.json'

// dirname / basename / extname
dirname('/app/src/server.ts');   // '/app/src'
basename('/app/src/server.ts');  // 'server.ts'
basename('/app/src/server.ts', '.ts'); // 'server'
extname('/app/src/server.ts');   // '.ts'

// relative — relative path from one location to another
relative('/app/src', '/app/src/api/routes.ts'); // 'api/routes.ts'

// Platform separator
sep; // '/' on Unix, '\\' on Windows
```

### `__dirname` Equivalent in ESM

ESM modules don't have `__dirname` or `__filename`. Use `import.meta.url`:

```typescript
import { fileURLToPath } from 'node:url';
import { dirname, join } from 'node:path';

// ESM __dirname equivalent
const __filename = fileURLToPath(import.meta.url);
const __dirname = dirname(__filename);

// Load a file relative to the current module (not CWD!)
const schemaPath = join(__dirname, '..', 'db', 'schema.prisma');
const templatePath = join(__dirname, 'templates', 'pipeline.json');
```

### CWD vs Module-Relative Paths

```typescript
// ⚠️ process.cwd() — wherever the CLI was invoked from
// This CHANGES depending on where the user runs the command

// Use CWD for user-facing paths (files the user specified)
const outputPath = resolve(process.cwd(), 'output.json');

// Use __dirname for built-in app assets (templates, schemas, etc.)
const templatePath = join(__dirname, 'templates', 'default.json');
```

### Cross-Platform File URLs

```typescript
import { pathToFileURL, fileURLToPath } from 'node:url';

// When using dynamic import() with a file path
const modulePath = join(__dirname, 'plugins', 'csv-plugin.js');
const mod = await import(pathToFileURL(modulePath).href);
// NOT: await import(modulePath) — fails on Windows with backslashes!
```

---

## Try It Yourself

**Exercise:** Write a function that loads a JSON config file relative to the calling module, falling back to `~/.pipeforge/config.json` if not found.

```typescript
async function loadConfig(callerImportMetaUrl: string): Promise<Record<string, unknown>> {
  // TODO: look for config.json next to the caller's file,
  // fall back to ~/.pipeforge/config.json (using os.homedir())
}
```

<details>
<summary>Show solution</summary>

```typescript
import { readFile, access, constants } from 'node:fs/promises';
import { fileURLToPath } from 'node:url';
import { dirname, join } from 'node:path';
import { homedir } from 'node:os';

async function loadConfig(callerImportMetaUrl: string): Promise<Record<string, unknown>> {
  const callerDir = dirname(fileURLToPath(callerImportMetaUrl));
  const localPath = join(callerDir, 'config.json');
  const globalPath = join(homedir(), '.pipeforge', 'config.json');

  for (const p of [localPath, globalPath]) {
    try {
      await access(p, constants.R_OK);
      return JSON.parse(await readFile(p, 'utf8'));
    } catch {
      continue;
    }
  }

  return {}; // no config found — use defaults
}
```

</details>

---

## Capstone Connection

PipeForge's CLI uses `import.meta.url` to resolve the location of built-in pipeline template files bundled with the package, regardless of where the user runs the `pipeforge` command. User-provided paths (e.g., `--output ./reports`) are resolved relative to `process.cwd()`.
