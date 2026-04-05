# 4.2 — Readable Streams

## Concept

A Readable stream is a source of data that you consume as it becomes available. Files, HTTP request bodies, database cursors, stdout of child processes — all are Readable streams. Understanding the two consumption modes (flowing and paused) and how to use async iteration correctly is the foundation.

---

## Deep Dive

### The Two Modes

A Readable stream starts in **paused mode**. Attaching a `'data'` event listener switches it to **flowing mode** — data is emitted as fast as it arrives:

```typescript
// Flowing mode — data emitted via events
const readable = createReadStream('large-file.csv');

readable.on('data', (chunk: Buffer) => {
  process.stdout.write(chunk); // process each chunk
});

readable.on('end', () => console.log('\nDone'));
readable.on('error', (err) => console.error('Stream error:', err));
```

**Paused mode** — you pull data explicitly with `.read()`:

```typescript
readable.on('readable', () => {
  let chunk: Buffer | null;
  while ((chunk = readable.read()) !== null) {
    process(chunk);
  }
});
```

**Async iteration** — the cleanest consumption pattern (Node.js 12+):

```typescript
// Internally uses the readable iterator protocol
for await (const chunk of createReadStream('large-file.csv')) {
  process(chunk as Buffer);
}
```

### Key Readable Events

| Event | When | Notes |
|-------|------|-------|
| `'data'` | Chunk available (flowing mode) | Switches to flowing |
| `'readable'` | Data available to read (paused mode) | Must call `.read()` |
| `'end'` | All data consumed | No more data |
| `'close'` | Stream/resource closed | Resource released |
| `'error'` | Error occurred | Always handle! |

### Creating Readable Streams

```typescript
import { Readable } from 'node:stream';

// From an array (useful for testing)
const readable = Readable.from(['chunk 1', 'chunk 2', 'chunk 3']);
const readable2 = Readable.from(async function* () {
  yield Buffer.from('chunk 1');
  await delay(100);
  yield Buffer.from('chunk 2');
}());

// Custom Readable class
class NumberStream extends Readable {
  private current = 0;
  constructor(private readonly max: number) {
    super({ objectMode: true }); // emit objects, not Buffers
  }
  _read() {
    if (this.current <= this.max) {
      this.push(this.current++); // push data
    } else {
      this.push(null); // signal end of stream
    }
  }
}

const nums = new NumberStream(5);
for await (const n of nums) {
  console.log(n); // 0, 1, 2, 3, 4, 5
}
```

---

## Try It Yourself

**Exercise:** Count lines in a large file without loading it into memory.

```typescript
import { createReadStream } from 'node:fs';

async function countLines(filePath: string): Promise<number> {
  // TODO: Use Readable stream to count newline characters
  // without loading the entire file into a Buffer
}

console.log(await countLines('large-file.txt'));
```

<details>
<summary>Show solution</summary>

```typescript
async function countLines(filePath: string): Promise<number> {
  let count = 0;
  for await (const chunk of createReadStream(filePath)) {
    for (const byte of chunk as Buffer) {
      if (byte === 0x0a) count++; // 0x0a is '\n'
    }
  }
  return count;
}
```

</details>

---

## Capstone Connection

PipeForge's log streaming endpoint returns a Readable stream of `JobLog` records. The HTTP handler pipes this stream directly to the response, enabling the client to receive logs in real time as they're written to the database, without buffering all logs in memory.
