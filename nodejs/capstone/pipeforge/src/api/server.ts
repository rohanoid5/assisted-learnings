// src/api/server.ts
// Added in Module 06 — Networking & HTTP
// Stub: the full Express server is built during exercises

import express from 'express';
import { createServer } from 'node:http';
import os from 'node:os';
import { randomUUID } from 'node:crypto';

const instanceId = randomUUID();

const app = express();
app.use(express.json());

// TODO (Module 06): Add helmet, cors, rate limiting
// TODO (Module 06): Mount route handlers (pipelines, jobs, auth)
// TODO (Module 06): Add global error handling middleware
// TODO (Module 06): Attach WebSocket server for job progress

app.get('/health', (_req, res) => {
  const totalMem = os.totalmem();
  const freeMem = os.freemem();

  res.json({
    status: 'ok',
    timestamp: new Date().toISOString(),
    instance: instanceId,
    node: process.version,
    platform: os.platform(),
    memory: {
      totalMB: Math.round(totalMem / 1024 / 1024),
      freeMB: Math.round(freeMem / 1024 / 1024),
      usedPercent: Math.round(((totalMem - freeMem) / totalMem) * 100),
    },
    uptime: {
      processSeconds: Math.floor(process.uptime()),
      systemSeconds: Math.floor(os.uptime()),
    },
  });
});

const PORT = process.env.PORT ?? 3000;
const httpServer = createServer(app);

httpServer.listen(PORT, () => {
  console.log(`PipeForge API running on http://localhost:${PORT}`);
});

export { app, httpServer };
