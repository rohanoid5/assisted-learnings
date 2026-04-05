// src/workers/job-runner.ts
// Added in Module 10 — Process Management & Performance
// Worker thread script: runs a single pipeline job off the main thread

import { workerData, parentPort } from 'node:worker_threads';

if (!parentPort) {
  throw new Error('This script must be run as a worker thread');
}

const { jobId, pipelineId, input } = workerData as {
  jobId: string;
  pipelineId: string;
  input: unknown;
};

// TODO (Module 10): Implement actual step execution
// Post progress updates back to the main thread
parentPort.postMessage({ type: 'progress', jobId, progress: 0 });

// Simulate work
await new Promise((resolve) => setTimeout(resolve, 100));

parentPort.postMessage({ type: 'complete', jobId, output: { processed: true, input } });
