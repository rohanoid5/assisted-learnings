# Module 10 — Process & Performance

## Overview

Node.js is single-threaded for JavaScript but exposes multiple mechanisms to exploit multi-core CPUs, offload CPU-bound work, and monitor process health. This module covers child processes, worker threads, clustering, memory management, profiling, and event loop health — everything needed to run PipeForge at production scale.

---

## Learning Objectives

- [ ] Spawn and communicate with child processes using `spawn`, `exec`, and `fork`
- [ ] Offload CPU-bound work to worker threads with shared memory
- [ ] Scale across CPU cores using the `cluster` module
- [ ] Interpret V8 heap metrics and prevent memory leaks
- [ ] Profile a Node.js process with `--prof` and Chrome DevTools
- [ ] Measure and alert on event loop lag using `perf_hooks`

---

## Topics

| # | Topic | Description |
|---|-------|-------------|
| 1 | [Child Processes](01-child-processes.md) | `spawn`, `exec`, `fork`, stdio piping, error handling |
| 2 | [Worker Threads](02-worker-threads.md) | `Worker`, `workerData`, `MessageChannel`, `SharedArrayBuffer`, worker pool |
| 3 | [Cluster](03-cluster.md) | Multi-core scaling, primary/worker pattern, graceful rolling restart |
| 4 | [Memory Management](04-memory-management.md) | V8 heap, `process.memoryUsage()`, leak detection, GC pressure |
| 5 | [Profiling](05-profiling.md) | `--prof`, `node --inspect`, Chrome DevTools, flame graphs |
| 6 | [Event Loop Monitoring](06-event-loop-monitoring.md) | `perf_hooks`, `monitorEventLoopDelay`, lag alerts, libuv threadpool |

---

## Estimated Time

6–8 hours

---

## Prerequisites

- Modules 01–09 (especially the event loop from Module 01 and streams from Module 04)
- Node.js 20+
- PipeForge capstone application running locally

---

## Capstone Milestone

By the end of this module, PipeForge will have:
- A `WorkerPool` that runs pipeline jobs in worker threads (no blocking the main thread)
- A `GET /api/v1/metrics` endpoint exposing heap usage, RSS, event loop delay, and worker pool queue depth
- Cluster mode in production (`src/cluster.ts`) for multi-core utilization
- A memory leak guard that restarts a worker if its heap exceeds 512 MB
