# Module 4 — Streams & Buffers

## Overview

Streams are one of Node.js's most powerful — and most misunderstood — primitives. They let you process data as it arrives instead of loading everything into memory at once. This matters when you're handling large files, real-time data, HTTP request/response bodies, or any pipeline of data transformations. This module covers the full stream API, backpressure, custom transforms, and the `Buffer`/`TypedArray` data model that underpins it all.

---

## Learning Objectives

By the end of this module you will be able to:

- [ ] Explain the difference between **Readable**, **Writable**, **Duplex**, and **Transform** streams
- [ ] Correctly use `pipeline()` to connect streams with proper error propagation and cleanup
- [ ] Explain **backpressure** and why ignoring it causes memory exhaustion
- [ ] Implement a **custom Transform stream** for data transformation
- [ ] Work with **Buffers** and understand the relationship with `TypedArray` and `ArrayBuffer`
- [ ] Use **object mode** streams for non-binary data pipelines

---

## Topics

| # | File | Concept |
|---|------|---------|
| 1 | [01-buffers.md](01-buffers.md) | Buffer, TypedArray, ArrayBuffer — the binary data model |
| 2 | [02-readable-streams.md](02-readable-streams.md) | Readable streams, events, async iteration |
| 3 | [03-writable-streams.md](03-writable-streams.md) | Writable streams, backpressure, drain event |
| 4 | [04-transform-streams.md](04-transform-streams.md) | Custom Transform streams, object mode |
| 5 | [05-pipeline.md](05-pipeline.md) | stream.pipeline, composing stream chains |
| 6 | [06-practical-patterns.md](06-practical-patterns.md) | Compression, CSV parsing, chunking patterns |

---

## Estimated Time

**5–7 hours** (including exercises)

---

## Prerequisites

- Module 02 — Async Programming
- Module 03 — Error Handling (for pipeline error propagation)

---

## Capstone Milestone

By the end of this module you will implement PipeForge's **file processing pipeline**:

- A Transform stream that parses CSV pipeline step output
- A compression pipeline that gzip-compresses step artifacts
- A streaming log exporter that reads `JobLog` records and streams them to a response

See [exercises/README.md](exercises/README.md) for the step-by-step tasks.
