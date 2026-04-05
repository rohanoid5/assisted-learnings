# Module 4 — Exercises

## Overview

These exercises build PipeForge's file processing pipeline — CSV parsing, artifact compression, and streaming log export.

---

## Exercise 1 — CSV Import Transform

**Goal:** Implement `CsvToJsonTransform` and wire it into a pipeline.

Create `src/transforms/csv-parser.ts`:
```typescript
import { Transform } from 'node:stream';

export class CsvToJsonTransform extends Transform {
  // Parse CSV text chunks → emit parsed row objects
  // Handle: headers from first line, partial lines across chunks
}
```

Test it:
```bash
echo "id,name,status\n1,Job1,RUNNING\n2,Job2,DONE" | \
  node --loader ts-node/esm -e "
  import { CsvToJsonTransform } from './src/transforms/csv-parser.js';
  const t = new CsvToJsonTransform();
  t.on('data', console.log);
  process.stdin.pipe(t);
  "
```

---

## Exercise 2 — Artifact Compression

**Goal:** Implement a `compressArtifact(srcPath: string): Promise<string>` function that:
1. Reads a file using a ReadStream
2. Compresses it with gzip
3. Writes to `srcPath + '.gz'`
4. Returns the compressed file path

```typescript
// src/core/artifact-store.ts
import { pipeline } from 'node:stream/promises';
import { createReadStream, createWriteStream } from 'node:fs';
import { createGzip } from 'node:zlib';

export async function compressArtifact(srcPath: string): Promise<string> {
  const destPath = `${srcPath}.gz`;
  // TODO: pipeline(read, gzip, write)
  return destPath;
}
```

---

## Exercise 3 — Streaming Log Endpoint

**Goal:** Implement `GET /api/jobs/:id/logs` that streams logs from the database.

```typescript
// In your router:
app.get('/api/jobs/:id/logs', asyncHandler(async (req, res) => {
  const jobId = req.params.id;

  // Set headers for streaming
  res.setHeader('Content-Type', 'application/x-ndjson');
  res.setHeader('Transfer-Encoding', 'chunked');

  const logStream = Readable.from(streamJobLogs(jobId));
  const serializer = new NdJsonSerializer();

  await pipeline(logStream, serializer, res);
}));

// Async generator that pages through JobLog records
async function* streamJobLogs(jobId: string) {
  let cursor: string | undefined;
  while (true) {
    const logs = await db.jobLog.findMany({
      where: { jobId },
      take: 50,
      ...(cursor ? { cursor: { id: cursor }, skip: 1 } : {}),
      orderBy: { timestamp: 'asc' },
    });
    if (!logs.length) break;
    yield* logs;
    cursor = logs[logs.length - 1].id;
  }
}
```

Test:
```bash
curl -N http://localhost:3000/api/jobs/<job-id>/logs
```

---

## Exercise 4 — Buffer Binary Protocol

**Goal:** Implement the binary framing protocol from Topic 4.1.

Create `src/lib/frame.ts` with `encode` and `decode` functions. Use them in the WebSocket message handler you'll add in Module 06.

---

## Capstone Checkpoint ✅

Before moving to Module 5, verify:

- [ ] Can explain backpressure and why ignoring it causes memory exhaustion
- [ ] Knows when to use `pipeline()` vs `.pipe()` (always pipeline!)
- [ ] Can implement a custom Transform stream with `_transform` and `_flush`
- [ ] Has a working CSV parser Transform stream
- [ ] Has a working artifact compression function using `stream/promises`
- [ ] The log streaming endpoint returns NDJSON line-by-line without buffering all records
