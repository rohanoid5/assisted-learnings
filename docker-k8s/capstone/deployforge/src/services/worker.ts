/**
 * DeployForge — Worker Service
 *
 * BullMQ-based job processor that consumes work from Redis queues.
 * Introduced in Module 03 (Docker Compose multi-service stack).
 *
 * Module progression:
 *   Module 03 — Basic BullMQ worker + Docker Compose
 *   Module 04 — Kubernetes Deployment manifest
 *   Module 05 — ConfigMap/Secret-driven Redis URL
 *   Module 07 — External secrets for Redis credentials
 *   Module 08 — Prometheus job-processing metrics
 *   Module 09 — Graceful shutdown for rolling deploys
 */

import { config } from "../config/index.js";

// TODO Module 03: Uncomment once bullmq + ioredis are installed
// import { Worker, Job } from "bullmq";
// import IORedis from "ioredis";

// ---------------------------------------------------------------------------
// Redis connection
// ---------------------------------------------------------------------------

// TODO Module 03: Create shared Redis connection
// const redisConnection = new IORedis(config.REDIS_URL, {
//   maxRetriesPerRequest: null, // required by BullMQ
// });

// ---------------------------------------------------------------------------
// Job processor
// ---------------------------------------------------------------------------

/**
 * Process incoming jobs from the "deployforge:jobs" queue.
 *
 * TODO Module 03: Implement actual job processing logic
 * TODO Module 08: Add histogram for job duration
 * TODO Module 08: Add counter for jobs processed / failed
 */
async function processJob(/* job: Job */): Promise<void> {
  console.log("[deployforge-worker] Processing job (stub)");
  // Simulate work
  await new Promise((resolve) => setTimeout(resolve, 100));
}

// ---------------------------------------------------------------------------
// Worker setup
// ---------------------------------------------------------------------------

// TODO Module 03: Uncomment to start the BullMQ worker
//
// const worker = new Worker("deployforge:jobs", processJob, {
//   connection: redisConnection,
//   concurrency: config.WORKER_CONCURRENCY,
// });
//
// worker.on("completed", (job) => {
//   console.log(`[deployforge-worker] Job ${job.id} completed`);
// });
//
// worker.on("failed", (job, err) => {
//   console.error(`[deployforge-worker] Job ${job?.id} failed:`, err.message);
// });

// ---------------------------------------------------------------------------
// Graceful shutdown
// ---------------------------------------------------------------------------

// TODO Module 09: Handle SIGTERM for graceful shutdown during rolling deploys
process.on("SIGTERM", async () => {
  console.log("[deployforge-worker] SIGTERM received — draining jobs");
  // TODO: await worker.close();
  // TODO: await redisConnection.quit();
  process.exit(0);
});

// ---------------------------------------------------------------------------
// Startup
// ---------------------------------------------------------------------------

console.log(`[deployforge-worker] starting (concurrency=${config.WORKER_CONCURRENCY})`);
console.log("[deployforge-worker] stub mode — no Redis connection yet (see Module 03)");

export { processJob };
