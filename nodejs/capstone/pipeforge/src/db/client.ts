// src/db/client.ts
// Added in Module 08 — Databases
// Singleton Prisma client — module-level singleton pattern (Module 07)

import { PrismaClient } from '@prisma/client';

// Module-level singleton: the require/import cache ensures this runs once
const prisma = new PrismaClient({
  log: process.env.NODE_ENV === 'development' ? ['query', 'error', 'warn'] : ['error'],
});

export default prisma;
