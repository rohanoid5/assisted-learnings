// src/core/pipeline-engine.ts
// Added in Module 02 — Async Programming
// Stub: the full implementation is built during exercises

import { EventEmitter } from 'node:events';

export type JobStatus = 'PENDING' | 'RUNNING' | 'COMPLETED' | 'FAILED';

export interface Job {
  id: string;
  pipelineId: string;
  status: JobStatus;
  input: unknown;
  output?: unknown;
  progress: number;
  error?: string;
  startedAt?: Date;
  completedAt?: Date;
}

export interface StepConfig {
  id: string;
  name: string;
  type: 'TRANSFORM' | 'FILTER' | 'AGGREGATE' | 'CUSTOM';
  config: Record<string, unknown>;
  order: number;
}

// PipelineEngine is the core job runner — built step-by-step in Module 02
export class PipelineEngine extends EventEmitter {
  // TODO (Module 02): Implement execute(), pause(), cancel()
  // TODO (Module 03): Add custom error hierarchy
  // TODO (Module 04): Add stream-based step processing
  // TODO (Module 10): Wire to worker thread pool
}
