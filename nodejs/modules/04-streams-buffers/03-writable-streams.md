# 4.3 — Writable Streams & Backpressure

## Concept

A Writable stream is a destination for data. `fs.createWriteStream`, `process.stdout`, HTTP response bodies, and WebSocket sockets are all Writable streams. The critical concept here is **backpressure**: when the consumer is slower than the producer, you must pause the producer or you'll exhaust memory.

---

## Deep Dive

### Writing to a Writable Stream

```typescript
const writable = createWriteStream('output.txt');

// .write() returns false when the internal buffer is full (backpressure!)
const ok = writable.write('some data\n');

if (!ok) {
  // The buffer is full — stop writing until 'drain' fires
  console.log('Backpressure! Waiting for drain...');
  await new Promise<void>((resolve) => writable.once('drain', resolve));
}

writable.end(() => {
  console.log('All data flushed, stream closed');
});
```

### Backpressure: Why It Matters

```typescript
// ⚠️ Ignoring backpressure — memory usage grows unboundedly
const readable = createReadStream('huge-file.bin');  // 10GB file
const writable = createWriteStream('output.bin');

readable.on('data', (chunk) => {
  writable.write(chunk); // write, ignoring return value
  // If writable is slower (e.g., network), internal buffer grows to OOM!
});

// ✅ Respect backpressure manually
readable.on('data', (chunk) => {
  const ok = writable.write(chunk);
  if (!ok) {
    readable.pause(); // stop reading until writable drains
  }
});

writable.on('drain', () => {
  readable.resume(); // writable buffer cleared — resume reading
});

// ✅✅ Best: use pipeline() which handles this automatically (Topic 4.5)
```

### The highWaterMark Buffer

Every stream has a `highWaterMark` — the buffer size threshold that triggers backpressure:

```typescript
// Byte streams: default 16KB
const readable = createReadStream('file.txt', { highWaterMark: 64 * 1024 }); // 64KB chunks

// Object mode streams: default 16 objects
const objectStream = new Writable({
  objectMode: true,
  highWaterMark: 100, // buffer up to 100 objects
  write(obj, _encoding, callback) {
    saveToDatabase(obj).then(() => callback()).catch(callback);
  },
});
```

---

## Try It Yourself

**Exercise:** Implement a file copy using streams with correct backpressure handling (without using `pipeline`):

```typescript
import { createReadStream, createWriteStream } from 'node:fs';

async function copyFile(src: string, dest: string): Promise<void> {
  // TODO: copy src → dest handling backpressure correctly
}
```

<details>
<summary>Show solution</summary>

```typescript
async function copyFile(src: string, dest: string): Promise<void> {
  const readable = createReadStream(src);
  const writable = createWriteStream(dest);

  for await (const chunk of readable) {
    const ok = writable.write(chunk as Buffer);
    if (!ok) {
      await new Promise<void>((resolve) => writable.once('drain', resolve));
    }
  }

  return new Promise<void>((resolve, reject) => {
    writable.end((err) => (err ? reject(err) : resolve()));
  });
}
// Note: This is essentially what pipeline() does, plus error propagation and cleanup.
// Always prefer pipeline() in production code.
```

</details>

---

## Capstone Connection

PipeForge's artifact export routes stream step output files directly from disk to the HTTP response (`res` is a Writable stream). Using `pipeline()` (Topic 4.5) ensures backpressure is respected — a slow client doesn't cause the server to buffer the entire file in memory.
