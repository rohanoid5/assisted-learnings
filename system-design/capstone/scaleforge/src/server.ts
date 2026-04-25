import express from 'express';
import { pinoHttp } from 'pino-http';
import helmet from 'helmet';
// import logger from './telemetry/logger.js';
import redirectRouter from './routes/redirect.router.js'; // Ensure routes are included in the bundle for dynamic mounting below

const app = express();
const PORT = Number(process.env.PORT ?? 3001);

// TODO: Add helmet() middleware to the Express app.
// Configure it to:
// 1. Enable HSTS (Strict-Transport-Security) with 1 year max-age
// 2. Disable X-Powered-By (don't reveal it's Express)
// 3. Set Content-Security-Policy to only allow same-origin resources
app.use(helmet({
  hsts: {
    maxAge: 31536000, // 1 year in seconds
    includeSubDomains: true,
    preload: true,
  },
  hidePoweredBy: true,
  contentSecurityPolicy: {
    directives: {
      defaultSrc: ["'self'"],
    },
  },
}));

app.use(express.json());
// app.use(pinoHttp({ logger }));

app.get('/health', (_req, res) => {
  res.json({ status: 'ok', version: '0.1.0', timestamp: new Date().toISOString() });
});

// TODO: Mount URL routes (Module 02)
app.use('/api', redirectRouter);
// app.use('/api', statsRouter);

app.listen(PORT, () => {
  // logger.info({ port: PORT }, 'ScaleForge listening');
  console.log(`ScaleForge server is running on port ${PORT}`);
});
