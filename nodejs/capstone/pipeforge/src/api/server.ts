// src/api/server.ts
// Added in Module 06 — Networking & HTTP
// Stub: the full Express server is built during exercises

import express from 'express';
import { createServer } from 'node:http';

const app = express();
app.use(express.json());

// TODO (Module 06): Add helmet, cors, rate limiting
// TODO (Module 06): Mount route handlers (pipelines, jobs, auth)
// TODO (Module 06): Add global error handling middleware
// TODO (Module 06): Attach WebSocket server for job progress

app.get('/health', (_req, res) => {
  res.json({ status: 'ok', timestamp: new Date().toISOString() });
});

const PORT = process.env.PORT ?? 3000;
const httpServer = createServer(app);

httpServer.listen(PORT, () => {
  console.log(`PipeForge API running on http://localhost:${PORT}`);
});

export { app, httpServer };
