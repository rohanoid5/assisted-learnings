import express from 'express';
import { pinoHttp } from 'pino-http';
import logger from './telemetry/logger.js';

const app = express();
const PORT = Number(process.env.PORT ?? 3001);

app.use(express.json());
app.use(pinoHttp({ logger }));

app.get('/health', (_req, res) => {
  res.json({ status: 'ok', version: '0.1.0', timestamp: new Date().toISOString() });
});

// TODO: Mount URL routes (Module 02)
// app.use('/api', urlRouter);
// app.use('/api', statsRouter);

app.listen(PORT, () => {
  logger.info({ port: PORT }, 'ScaleForge listening');
});
