import { Router, Request, Response } from 'express';
import { randomUUID } from 'crypto';
import { Queue } from 'bullmq';
import IORedis from 'ioredis';

// TODO Module 04: replace with real cache-aside + Prisma implementation
const MOCK_URLS: Record<string, { id: string; longUrl: string }> = {
  abc123: { id: '1', longUrl: 'https://example.com' },
};
const lookupUrl = async (code: string) => MOCK_URLS[code] ?? null;

const redirectRouter = Router();

// BullMQ click queue — worker implemented in Module 06
const redis = new IORedis(process.env.REDIS_URL ?? 'redis://localhost:6379', {
  maxRetriesPerRequest: null,
});
const clickQueue = new Queue('clicks', { connection: redis });

// Alphanumeric, at least CODE_LENGTH chars (env default: 6), max 12
const CODE_LENGTH = Number(process.env.CODE_LENGTH ?? 6);
const CODE_REGEX = new RegExp(`^[a-zA-Z0-9]{${CODE_LENGTH},12}$`);

redirectRouter.get('/:code', async (req: Request, res: Response) => {
  const { code } = req.params;
  const requestId = randomUUID();

  // Applied to all responses — including 4xx
  res.setHeader('X-Request-Id', requestId);
  res.setHeader('Cache-Control', 'no-store');

  // 400 — reject malformed codes before touching the DB
  if (!CODE_REGEX.test(code as string)) {
    res.status(400).json({ error: 'Invalid code format', requestId });
    return;
  }

  const shortUrl = await lookupUrl(code as string);

  if (!shortUrl) {
    res.status(404).json({ error: 'Short URL not found', requestId });
    return;
  }

  // Extract real client IP from X-Forwarded-For set by Nginx.
  // Header may be a comma-separated list: "client, proxy1, proxy2"
  const forwarded = req.headers['x-forwarded-for'];
  const clientIp = Array.isArray(forwarded)
    ? forwarded[0]
    : (forwarded?.split(',')[0]?.trim() ?? req.socket.remoteAddress ?? 'unknown');

  // Fire-and-forget — never let analytics delay the redirect
  clickQueue
    .add('click', {
      shortUrlId: shortUrl.id,
      ip: clientIp,
      userAgent: req.headers['user-agent'] ?? '',
      timestamp: new Date().toISOString(),
    })
    .catch(() => {});

  // 302 Found — browser will not cache, preserving analytics accuracy
  res.redirect(302, shortUrl.longUrl);
});

export default redirectRouter;