/**
 * DeployForge — API Gateway
 *
 * Express HTTP server that serves as the main entry point for DeployForge.
 * Introduced in Module 02 (Dockerized Node.js service).
 *
 * Module progression:
 *   Module 02 — Basic Express server + Dockerfile
 *   Module 03 — Docker Compose integration
 *   Module 05 — ConfigMap-driven environment variables
 *   Module 07 — External secrets for DB credentials
 *   Module 08 — Prometheus metrics + OpenTelemetry
 *   Module 09 — Graceful shutdown for CI/CD rolling deploys
 */

import express, { Request, Response } from "express";
import { config } from "../config/index.js";

// TODO Module 08: Import and register prom-client default metrics
// import { collectDefaultMetrics, register } from "prom-client";
// collectDefaultMetrics();

// TODO Module 08: Import OpenTelemetry SDK
// import { NodeSDK } from "@opentelemetry/sdk-node";

const app = express();
app.use(express.json());

// ---------------------------------------------------------------------------
// Health & readiness probes (used by Kubernetes liveness/readiness checks)
// ---------------------------------------------------------------------------

/** Liveness probe — is the process alive? */
app.get("/health", (_req: Request, res: Response) => {
  res.status(200).json({ status: "ok", timestamp: new Date().toISOString() });
});

/**
 * Readiness probe — can the service handle traffic?
 * TODO Module 03: Check Redis connection
 * TODO Module 05: Check PostgreSQL connection
 */
app.get("/ready", (_req: Request, res: Response) => {
  // TODO: Add dependency checks (DB, Redis) before reporting ready
  const ready = true;
  res.status(ready ? 200 : 503).json({ ready });
});

// ---------------------------------------------------------------------------
// Prometheus metrics endpoint
// ---------------------------------------------------------------------------

/**
 * TODO Module 08: Expose collected metrics for Prometheus scraping
 * Uncomment once prom-client is wired up:
 *
 * app.get("/metrics", async (_req, res) => {
 *   res.set("Content-Type", register.contentType);
 *   res.end(await register.metrics());
 * });
 */
app.get("/metrics", (_req: Request, res: Response) => {
  res.status(501).json({ message: "Metrics not yet configured — see Module 08" });
});

// ---------------------------------------------------------------------------
// Application routes
// ---------------------------------------------------------------------------

app.get("/", (_req: Request, res: Response) => {
  res.json({
    service: "deployforge-api",
    version: "0.1.0",
    docs: "/health, /ready, /metrics",
  });
});

// TODO Module 03: Add POST /jobs endpoint to enqueue work via BullMQ
// TODO Module 05: Add CRUD routes backed by PostgreSQL
// TODO Module 07: Add /admin routes protected by RBAC

// ---------------------------------------------------------------------------
// Graceful shutdown
// ---------------------------------------------------------------------------

function startServer(): void {
  const server = app.listen(config.PORT, () => {
    console.log(`[deployforge-api] listening on :${config.PORT}`);
  });

  // TODO Module 09: Handle SIGTERM for graceful shutdown during rolling deploys
  process.on("SIGTERM", () => {
    console.log("[deployforge-api] SIGTERM received — shutting down");
    server.close(() => {
      console.log("[deployforge-api] HTTP server closed");
      process.exit(0);
    });
  });
}

startServer();

export { app };
