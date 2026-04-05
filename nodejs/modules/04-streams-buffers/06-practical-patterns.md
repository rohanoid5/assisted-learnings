# 4.6 — Practical Stream Patterns

## Concept

Real-world stream usage comes down to a handful of patterns: collecting stream data, converting arrays to streams, chunking, throttling, and handling multipart uploads. This topic is a practical reference for the patterns you'll reach for repeatedly.

---

## Deep Dive

### Collect a Stream into a Buffer or String

```typescript
import { Readable } from 'node:stream';

// Using the stream utility
async function streamToBuffer(readable: Readable): Promise<Buffer> {
  const chunks: Buffer[] = [];
  for await (const chunk of readable) {
    chunks.push(Buffer.isBuffer(chunk) ? chunk : Buffer.from(chunk));
  }
  return Buffer.concat(chunks);
}

// Using node:stream built-in
import { text, buffer, json } from 'node:stream/consumers';
const str = await text(readable);     // UTF-8 decode to string
const buf = await buffer(readable);   // collect to Buffer
const obj = await json(readable);     // parse as JSON
```

### Array to Stream

```typescript
import { Readable } from 'node:stream';

// From an array
const stream = Readable.from(['a', 'b', 'c']);

// From an async generator (for database cursor, paginated API, etc.)
const jobStream = Readable.from(async function* () {
  let cursor: string | undefined;
  while (true) {
    const batch = await db.job.findMany({ take: 100, cursor });
    if (!batch.length) break;
    yield* batch;
    cursor = batch[batch.length - 1].id;
  }
}());
```

### Throttle a Stream

```typescript
import { Transform } from 'node:stream';
import { setTimeout as delay } from 'node:timers/promises';

class ThrottleTransform extends Transform {
  constructor(private readonly delayMs: number) {
    super({ objectMode: true });
  }

  async _transform(chunk: unknown, _enc: BufferEncoding, cb: () => void) {
    await delay(this.delayMs);
    this.push(chunk);
    cb();
  }
}

// Emit at most 10 records per second
const throttled = new ThrottleTransform(100);
```

### Handling Large File Uploads

```typescript
// Express + multipart: stream the file directly to disk
import multer from 'multer';

const upload = multer({
  storage: multer.diskStorage({
    destination: 'uploads/',
    filename: (_req, file, cb) => cb(null, `${Date.now()}-${file.originalname}`),
  }),
  limits: { fileSize: 100 * 1024 * 1024 }, // 100MB limit
});

app.post('/api/pipelines/import', upload.single('file'), asyncHandler(async (req, res) => {
  const filePath = req.file!.path;
  // File is already on disk — process it as a stream
  await pipeline(
    createReadStream(filePath),
    new CsvToJsonTransform(),
    new PipelineImportTransform(),
    new DevNullWritable(), // discard — side effects happen in the transform
  );
  res.json({ imported: true });
}));
```

---

## Try It Yourself

**Exercise:** Build a streaming NDJSON (Newline-Delimited JSON) serializer.

```typescript
// Input: ObjectMode Readable (e.g., from a DB cursor or array)
// Output: Text stream where each line is a JSON object followed by '\n'

class NdJsonSerializer extends Transform {
  constructor() { super({ objectMode: true, readableObjectMode: false }); }

  _transform(obj: unknown, _enc: BufferEncoding, cb: () => void): void {
    // TODO: push JSON stringified line with newline
  }
}

// Usage:
await pipeline(
  Readable.from([{ id: 1 }, { id: 2 }, { id: 3 }]),
  new NdJsonSerializer(),
  process.stdout,
);
// Output:
// {"id":1}
// {"id":2}
// {"id":3}
```

<details>
<summary>Show solution</summary>

```typescript
class NdJsonSerializer extends Transform {
  constructor() { super({ objectMode: true, readableObjectMode: false }); }

  _transform(obj: unknown, _enc: BufferEncoding, cb: () => void): void {
    this.push(JSON.stringify(obj) + '\n', 'utf8');
    cb();
  }
}
```

</details>

---

## Capstone Connection

The `NdJsonSerializer` is used directly in PipeForge's `GET /api/jobs/:id/logs` endpoint — Prisma records are streamed from the database via an async generator, serialized to NDJSON, and piped to the HTTP response. Clients can parse logs incrementally using the same `readline` pattern from Module 01.
