# 5.1 — fs/promises

## Concept

The `node:fs/promises` module is the modern, Promise-based API for all file system operations. Prefer it over the callback-based `fs` module or the synchronous `fs.*Sync` APIs (which block the event loop).

---

## Deep Dive

### Reading Files

```typescript
import { readFile, readdir } from 'node:fs/promises';

// Read entire file as string
const content = await readFile('pipelines/etl.json', 'utf8');
const config = JSON.parse(content);

// Read as Buffer (no encoding)
const bin = await readFile('artifact.gz');
console.log(bin.length); // size in bytes

// Parallel reads
const [configText, schemaText] = await Promise.all([
  readFile('config.json', 'utf8'),
  readFile('schema.json', 'utf8'),
]);
```

### Writing Files

```typescript
import { writeFile, appendFile, mkdir } from 'node:fs/promises';

// Write (overwrites if exists)
await writeFile('output.json', JSON.stringify(data, null, 2), 'utf8');

// Append
await appendFile('pipeline.log', `${new Date().toISOString()} — Step complete\n`);

// Create directory tree
await mkdir('pipelines/archive/2024', { recursive: true });
```

### Stat, Exists, Permissions

```typescript
import { stat, access, constants } from 'node:fs/promises';

// Check if file exists and is readable
async function isReadable(path: string): Promise<boolean> {
  try {
    await access(path, constants.R_OK);
    return true;
  } catch {
    return false;
  }
}

// Get file metadata
const info = await stat('artifact.gz');
console.log(info.size);         // size in bytes
console.log(info.isFile());     // true
console.log(info.isDirectory()); // false
console.log(info.mtimeMs);      // last modified (epoch ms)
```

### Recursive Directory Walking

```typescript
import { readdir } from 'node:fs/promises';
import { join } from 'node:path';

// Async generator — memory-efficient for deep file trees
async function* walk(dir: string): AsyncGenerator<string> {
  const entries = await readdir(dir, { withFileTypes: true });
  for (const entry of entries) {
    const fullPath = join(dir, entry.name);
    if (entry.isDirectory()) {
      yield* walk(fullPath);     // recurse
    } else {
      yield fullPath;             // emit file path
    }
  }
}

// List all .json files in a directory tree
for await (const file of walk('pipelines/')) {
  if (file.endsWith('.json')) {
    console.log(file);
  }
}
```

### Atomic File Writes

Writing to a temp file then renaming is atomic on most file systems — no partial writes visible to other readers:

```typescript
import { writeFile, rename } from 'node:fs/promises';
import { randomUUID } from 'node:crypto';
import { join, dirname } from 'node:path';

async function writeAtomic(filePath: string, data: string): Promise<void> {
  const tmpPath = join(dirname(filePath), `.tmp-${randomUUID()}`);
  try {
    await writeFile(tmpPath, data, 'utf8');
    await rename(tmpPath, filePath); // atomic on POSIX
  } catch (err) {
    // Clean up temp file on failure
    await unlink(tmpPath).catch(() => {});
    throw err;
  }
}
```

---

## Try It Yourself

**Exercise:** Walk a directory tree and return an object of `{ [relPath]: sizeBytes }` for all files.

```typescript
async function fileSizeMap(root: string): Promise<Record<string, number>> {
  // TODO: walk the tree, stat each file, return { relativePath: size }
}
```

<details>
<summary>Show solution</summary>

```typescript
import { readdir, stat } from 'node:fs/promises';
import { join, relative } from 'node:path';

async function* walk(dir: string): AsyncGenerator<string> {
  const entries = await readdir(dir, { withFileTypes: true });
  for (const e of entries) {
    const full = join(dir, e.name);
    if (e.isDirectory()) yield* walk(full);
    else yield full;
  }
}

async function fileSizeMap(root: string): Promise<Record<string, number>> {
  const result: Record<string, number> = {};
  for await (const file of walk(root)) {
    const info = await stat(file);
    result[relative(root, file)] = info.size;
  }
  return result;
}
```

</details>

---

## Capstone Connection

PipeForge uses `fs/promises` in its `import` CLI command to discover pipeline definition files (`walk()`), validate their structure, and atomically update the database snapshot file when a pipeline is modified.
