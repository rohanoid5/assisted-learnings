# 4.4 — Transform Streams

## Concept

A Transform stream is both Readable and Writable — it takes input, transforms it, and emits output. Gzip compressors, CSV parsers, encryption streams, and JSON serializers are all Transform streams. Writing your own gives you a reusable, composable unit that plugs into any stream pipeline.

---

## Deep Dive

### Implementing a Transform Stream

```typescript
import { Transform, TransformOptions } from 'node:stream';

class UpperCaseTransform extends Transform {
  constructor(opts?: TransformOptions) {
    super(opts); // opts may include objectMode, highWaterMark
  }

  // Called for each chunk
  _transform(
    chunk: Buffer | string,
    encoding: BufferEncoding,
    callback: (error?: Error | null, data?: Buffer | string) => void,
  ): void {
    // Transform the chunk
    const transformed = chunk.toString('utf8').toUpperCase();

    // Push the result downstream
    this.push(transformed, 'utf8');

    // Signal that we're ready for the next chunk
    callback();
  }

  // Called when the input stream ends — flush any remaining buffered data
  _flush(callback: (error?: Error | null, data?: Buffer | string) => void): void {
    // If we had buffered partial data, emit it here
    callback(); // signal done
  }
}
```

### Object Mode Transform

```typescript
interface RawJob { id: string; status: string; created_at: string; }
interface ParsedJob { id: string; status: string; createdAt: Date; }

class JobNormalizer extends Transform {
  constructor() {
    super({ objectMode: true }); // objects in, objects out
  }

  _transform(
    job: RawJob,
    _encoding: BufferEncoding,
    callback: () => void,
  ): void {
    this.push({
      id: job.id,
      status: job.status,
      createdAt: new Date(job.created_at),
    } satisfies ParsedJob);
    callback();
  }
}
```

### Line-by-Line Transform (Common Pattern)

```typescript
class LineBreaker extends Transform {
  private buffer = '';

  constructor() { super(); }

  _transform(chunk: Buffer, _enc: BufferEncoding, cb: () => void): void {
    this.buffer += chunk.toString('utf8');
    const lines = this.buffer.split('\n');
    this.buffer = lines.pop() ?? ''; // keep incomplete last line

    for (const line of lines) {
      this.push(line + '\n');
    }
    cb();
  }

  _flush(cb: () => void): void {
    if (this.buffer) this.push(this.buffer); // emit remaining
    cb();
  }
}
```

---

## Try It Yourself

**Exercise:** Implement a CSV-to-JSON Transform stream:

```typescript
// Input (text chunks):  "id,name,status\n1,Job1,RUNNING\n2,Job2,DONE\n"
// Output (objects):     { id: '1', name: 'Job1', status: 'RUNNING' }
//                       { id: '2', name: 'Job2', status: 'DONE' }

class CsvToJsonTransform extends Transform {
  private headers: string[] = [];
  private lineBuffer = '';

  constructor() { super({ objectMode: true }); }

  _transform(chunk: Buffer, _enc: BufferEncoding, cb: () => void): void {
    // TODO: parse lines, set headers from first line, emit objects for data lines
  }

  _flush(cb: () => void): void {
    // TODO: flush any remaining buffered partial line
    cb();
  }
}
```

<details>
<summary>Show solution</summary>

```typescript
class CsvToJsonTransform extends Transform {
  private headers: string[] = [];
  private lineBuffer = '';

  constructor() { super({ objectMode: true }); }

  _transform(chunk: Buffer, _enc: BufferEncoding, cb: () => void): void {
    this.lineBuffer += chunk.toString('utf8');
    const lines = this.lineBuffer.split('\n');
    this.lineBuffer = lines.pop() ?? '';

    for (const line of lines) {
      const trimmed = line.trim();
      if (!trimmed) continue;

      if (this.headers.length === 0) {
        this.headers = trimmed.split(',');
      } else {
        const values = trimmed.split(',');
        const obj: Record<string, string> = {};
        this.headers.forEach((h, i) => { obj[h] = values[i] ?? ''; });
        this.push(obj);
      }
    }
    cb();
  }

  _flush(cb: () => void): void {
    if (this.lineBuffer.trim() && this.headers.length > 0) {
      const values = this.lineBuffer.trim().split(',');
      const obj: Record<string, string> = {};
      this.headers.forEach((h, i) => { obj[h] = values[i] ?? ''; });
      this.push(obj);
    }
    cb();
  }
}
```

</details>

---

## Capstone Connection

PipeForge's `CSV_IMPORT` pipeline step type uses a `CsvToJsonTransform` to convert uploaded data files into records. The transform is a drop-in stage in the stream pipeline, making it easy to add or remove processing steps without rewriting the core logic.
