/**
 * DeployForge — Configuration Module
 *
 * Validates and exports environment variables using Zod schemas.
 * Fail-fast on startup if required config is missing.
 *
 * Module progression:
 *   Module 02 — Basic PORT config
 *   Module 03 — Redis + PostgreSQL connection strings
 *   Module 05 — ConfigMap/Secret references in K8s
 *   Module 07 — External secrets operator (AWS SSM, Vault, etc.)
 *   Module 08 — OTEL_* tracing variables
 */

import { z } from "zod";

const envSchema = z.object({
  /** HTTP port for the API Gateway */
  PORT: z
    .string()
    .default("3000")
    .transform((v) => parseInt(v, 10)),

  /** Node environment */
  NODE_ENV: z
    .enum(["development", "production", "test"])
    .default("development"),

  /** PostgreSQL connection string */
  DATABASE_URL: z
    .string()
    .default("postgresql://deployforge:password@localhost:5432/deployforge_dev"),

  /** Redis connection URL */
  REDIS_URL: z
    .string()
    .default("redis://localhost:6379"),

  /** BullMQ worker concurrency */
  WORKER_CONCURRENCY: z
    .string()
    .default("5")
    .transform((v) => parseInt(v, 10)),

  /** Application log level */
  LOG_LEVEL: z
    .enum(["debug", "info", "warn", "error"])
    .default("info"),

  // TODO Module 07: Add external secrets config
  // SECRETS_PROVIDER: z.enum(["env", "aws-ssm", "vault"]).default("env"),
  // AWS_REGION: z.string().optional(),
  // VAULT_ADDR: z.string().optional(),

  // TODO Module 08: Add OpenTelemetry config
  // OTEL_EXPORTER_OTLP_ENDPOINT: z.string().optional(),
  // OTEL_SERVICE_NAME: z.string().default("deployforge-api"),
});

export type Config = z.infer<typeof envSchema>;

function loadConfig(): Config {
  const result = envSchema.safeParse(process.env);

  if (!result.success) {
    console.error("[deployforge] ❌ Invalid environment configuration:");
    console.error(result.error.format());
    process.exit(1);
  }

  return result.data;
}

export const config = loadConfig();
