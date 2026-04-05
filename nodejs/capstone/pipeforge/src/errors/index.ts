// src/errors/index.ts
// Added in Module 03 — Error Handling & Debugging
// Stub: the full error hierarchy is built during exercises

// Base error class for all PipeForge errors
export class PipeForgeError extends Error {
  constructor(
    message: string,
    public readonly code: string,
    public readonly statusCode: number = 500,
    public readonly context?: Record<string, unknown>,
  ) {
    super(message);
    this.name = this.constructor.name;
    // TODO (Module 03): Add Error.captureStackTrace for cleaner traces
  }
}

// TODO (Module 03): Add ValidationError, JobError, NotFoundError, AuthError
