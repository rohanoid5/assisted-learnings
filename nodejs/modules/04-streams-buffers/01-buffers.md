# 4.1 — Buffers and Binary Data

## Concept

Node.js represents binary data as `Buffer` — a fixed-size, raw memory allocation. Before you can work with streams (which traffic in Buffers), you need to understand how `Buffer`, `TypedArray`, and `ArrayBuffer` relate to each other, how to encode/decode data, and how to avoid the most common encoding bugs.

---

## Deep Dive

### The Data Model

```
ArrayBuffer      — Raw fixed-size binary memory allocation (Web standard)
  ↑
TypedArray       — Typed view: Int8Array, Uint8Array, Float64Array, etc.
  ↑
Buffer           — Node.js subclass of Uint8Array with extra methods
                   (encoding, hex, base64, copy, fill, etc.)
```

`Buffer` is a `Uint8Array`. Any API that accepts a `Uint8Array` accepts a `Buffer`.

### Creating Buffers

```typescript
// ✅ Always use Buffer.from() — never new Buffer() (deprecated, insecure)

// From a string
const buf1 = Buffer.from('Hello, PipeForge!', 'utf8');
const buf2 = Buffer.from('48656c6c6f', 'hex');
const buf3 = Buffer.from('SGVsbG8=', 'base64');

// From an array of bytes
const buf4 = Buffer.from([0x48, 0x65, 0x6c, 0x6c, 0x6f]);

// Allocate zero-filled memory (safe)
const buf5 = Buffer.alloc(1024);          // 1KB, zero-filled

// Allocate uninitialized memory (faster, but may contain old data — use carefully)
const buf6 = Buffer.allocUnsafe(1024);    // 1KB, NOT zero-filled
buf6.fill(0);                             // fill before use
```

### Encoding and Decoding

```typescript
const message = 'Hello, PipeForge! 🚀';

// String → Buffer (encode)
const encoded = Buffer.from(message, 'utf8');
console.log(encoded.length); // 22 bytes (emoji is 4 bytes in UTF-8)

// Buffer → String (decode)
const decoded = encoded.toString('utf8');
console.log(decoded); // 'Hello, PipeForge! 🚀'

// Other encodings:
encoded.toString('hex');    // 'efbfbd...' (hex representation)
encoded.toString('base64'); // 'SGVsbG8s...' (base64)

// Supported encodings: 'utf8', 'utf-8', 'ascii', 'latin1', 'binary',
//                      'base64', 'base64url', 'hex', 'ucs2', 'utf16le'
```

### Buffer Operations

```typescript
const buf = Buffer.alloc(10);

// Write methods
buf.writeUInt32BE(0x12345678, 0);  // write 4-byte big-endian uint at offset 0
buf.writeUInt16LE(0xabcd, 4);      // write 2-byte little-endian uint at offset 4

// Read methods
buf.readUInt32BE(0);               // read 4 bytes big-endian from offset 0
buf.readUInt16LE(4);               // read 2 bytes little-endian from offset 4

// Slice (shallow copy using same memory)
const slice = buf.subarray(2, 6);

// Copy (deep copy)
const copy = Buffer.allocUnsafe(buf.length);
buf.copy(copy);

// Concatenate
const a = Buffer.from('Hello');
const b = Buffer.from(' World');
const combined = Buffer.concat([a, b], a.length + b.length);
```

### TypedArrays and ArrayBuffer Interop

```typescript
// Node.js streams traffic in Buffers, but Web APIs use ArrayBuffer
const buf = Buffer.from('hello');

// Buffer → ArrayBuffer
const arrayBuffer = buf.buffer.slice(buf.byteOffset, buf.byteOffset + buf.byteLength);

// ArrayBuffer → Buffer
const backToBuffer = Buffer.from(arrayBuffer);

// Sharing memory (zero-copy)
const uint8 = new Uint8Array([1, 2, 3, 4]);
const sharedBuf = Buffer.from(uint8.buffer);  // same memory!
uint8[0] = 99;
console.log(sharedBuf[0]); // 99 — they share the ArrayBuffer
```

---

## Try It Yourself

**Exercise:** Implement a simple binary message framing protocol.

```typescript
// Protocol: [4-byte length (uint32 big-endian)] [data bytes]
// Encoder: given a string, return a framed Buffer
// Decoder: given a framed Buffer, return the string

function encodeMessage(message: string): Buffer {
  // TODO
}

function decodeMessage(frame: Buffer): string {
  // TODO
}

const frame = encodeMessage('Hello, PipeForge!');
console.log(decodeMessage(frame)); // 'Hello, PipeForge!'
```

<details>
<summary>Show solution</summary>

```typescript
function encodeMessage(message: string): Buffer {
  const data = Buffer.from(message, 'utf8');
  const frame = Buffer.allocUnsafe(4 + data.length);
  frame.writeUInt32BE(data.length, 0);
  data.copy(frame, 4);
  return frame;
}

function decodeMessage(frame: Buffer): string {
  const length = frame.readUInt32BE(0);
  return frame.subarray(4, 4 + length).toString('utf8');
}
```

This is exactly the kind of framing protocol used in WebSocket and TCP data streaming in Module 06.

</details>

---

## Capstone Connection

PipeForge uses Buffers in:
- **HTTP response bodies** — Express serializes JSON to a Buffer internally
- **WebSocket messages** (Module 06) — binary frames for efficient job progress updates
- **Gzip compression** — `zlib.gzip` accepts and returns Buffers
- **Crypto operations** (Module 06) — `createHmac` produces a Buffer digest
