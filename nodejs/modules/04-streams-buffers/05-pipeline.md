# 4.5 — stream.pipeline

## Concept

Connecting streams with `.pipe()` is the old way. It doesn't propagate errors correctly and can leave resources unclosed on failure. `stream.pipeline()` (and the Promise version `stream/promises`) handles error propagation, cleanup, and backpressure automatically. Always use `pipeline`.

---

## Deep Dive

### Why `.pipe()` Fails

```typescript
// ⚠️ OLD WAY — .pipe() has multiple failure modes:
readable
  .pipe(transform)
  .pipe(writable);

// Problems:
// 1. If readable errors, writable is NOT automatically closed
// 2. If transform errors, writable is NOT automatically closed
// 3. You need to manually listen for 'error' on EVERY stream
// 4. Resources leak on error
```

### `stream/promises.pipeline` — The Right Way

```typescript
import { pipeline } from 'node:stream/promises';
import { createReadStream, createWriteStream } from 'node:fs';
import { createGzip } from 'node:zlib';

// Compress a file — errors cleaned up automatically
await pipeline(
  createReadStream('input.json'),   // Readable
  createGzip(),                      // Transform
  createWriteStream('output.json.gz'), // Writable
);

// On any error:
// - Both streams are destroyed
// - Writable is closed without flushing partial data
// - The Promise rejects with the error
```

### Pipeline with Abort Signal

```typescript
import { pipeline } from 'node:stream/promises';

const controller = new AbortController();

// Cancel the pipeline after 5 seconds
setTimeout(() => controller.abort(), 5000);

try {
  await pipeline(
    createReadStream('huge-file.bin'),
    processTransform,
    createWriteStream('output.bin'),
    { signal: controller.signal }, // pipeline aborts on signal
  );
} catch (err) {
  if (err.name === 'AbortError') {
    console.log('File copy cancelled');
  } else {
    throw err;
  }
}
```

### Building Complex Pipelines

```typescript
import { pipeline } from 'node:stream/promises';
import { createReadStream, createWriteStream } from 'node:fs';
import { createGzip } from 'node:zlib';
import { CsvToJsonTransform } from './transforms/csv.js';
import { JsonSerializerTransform } from './transforms/serialize.js';

// CSV file → parse → gzip → output
await pipeline(
  createReadStream('jobs.csv'),
  new CsvToJsonTransform(),
  new JsonSerializerTransform(),
  createGzip(),
  createWriteStream('jobs.ndjson.gz'),
);
```

---

## Try It Yourself

**Exercise:** Stream a large file through gzip compression and write to a new file.

```typescript
// Compress src file → dest file, report bytes in/out
async function compressFile(src: string, dest: string): Promise<void> {
  // TODO: use stream/promises.pipeline with createReadStream, createGzip, createWriteStream
}
```

<details>
<summary>Show solution</summary>

```typescript
import { pipeline } from 'node:stream/promises';
import { createReadStream, createWriteStream, statSync } from 'node:fs';
import { createGzip } from 'node:zlib';

async function compressFile(src: string, dest: string): Promise<void> {
  const originalSize = statSync(src).size;

  await pipeline(
    createReadStream(src),
    createGzip({ level: 6 }),
    createWriteStream(dest),
  );

  const compressedSize = statSync(dest).size;
  const ratio = ((1 - compressedSize / originalSize) * 100).toFixed(1);
  console.log(`${originalSize} → ${compressedSize} bytes (${ratio}% reduction)`);
}
```

</details>

---

## Capstone Connection

PipeForge uses `pipeline()` in two places:
1. **Artifact compression**: step output files are gzip-compressed when stored, using `pipeline(readStream, createGzip(), writeStream)`
2. **Log export endpoint**: `GET /api/jobs/:id/logs` streams records through `pipeline(dbCursorStream, jsonSerializerTransform, res)` — the HTTP response is the writable endpoint
