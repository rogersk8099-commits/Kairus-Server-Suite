import "dotenv/config";
import { z } from "zod";

const optionalString = z.preprocess((value) => typeof value === "string" && value.trim() === "" ? undefined : value, z.string().trim().min(1).optional());
const optionalSecret = z.preprocess((value) => typeof value === "string" && value.trim() === "" ? undefined : value, z.string().min(16).optional());
const snowflake = z.string().regex(/^\d{5,32}$/, "must be a Discord snowflake");
const postgresUrl = z.string().url().refine((value) => value.startsWith("postgres://") || value.startsWith("postgresql://"), "must be a PostgreSQL URL");

const schemaShape = {
  DISCORD_TOKEN: optionalString,
  DISCORD_CLIENT_ID: snowflake,
  DISCORD_GUILD_ID: snowflake,
  DATABASE_URL: postgresUrl,
  API_URL: z.string().url().refine((value) => value.startsWith("https://") || value.startsWith("http://"), "must be an HTTP(S) URL"),
  API_SECRET: z.string().min(16),
  NODE_ENV: z.enum(["development", "test", "production"]).default("development"),
  LOG_LEVEL: z.enum(["fatal", "error", "warn", "info", "debug", "trace", "silent"]).default("info"),
  HOST: z.string().default("0.0.0.0"),
  PORT: z.coerce.number().int().min(1).max(65535).default(8080),
  API_TIMEOUT_MS: z.coerce.number().int().min(100).max(120_000).default(10_000),
  API_MAX_RETRIES: z.coerce.number().int().min(0).max(10).default(3),
  WEBHOOK_SECRET: optionalSecret,
  WEBHOOK_MAX_AGE_SECONDS: z.coerce.number().int().min(30).max(3_600).default(300),
  WEBHOOK_RATE_LIMIT_PER_MINUTE: z.coerce.number().int().min(1).max(1_000).default(60),
  MEMBERSHIP_INTERVAL_MS: z.coerce.number().int().min(30_000).default(900_000),
  STREAM_INTERVAL_MS: z.coerce.number().int().min(30_000).default(300_000),
  REMINDER_INTERVAL_MS: z.coerce.number().int().min(10_000).default(60_000),
  STATUS_INTERVAL_MS: z.coerce.number().int().min(30_000).default(60_000),
  TWITCH_CLIENT_ID: optionalString,
  TWITCH_CLIENT_SECRET: optionalSecret,
  YOUTUBE_API_KEY: optionalSecret
};
const schema = z.object(schemaShape).strict();

export type AppConfig = z.infer<typeof schema>;

export function loadConfig(source: NodeJS.ProcessEnv = process.env): AppConfig {
  const exactSource = Object.fromEntries(Object.keys(schemaShape).map((name) => [name, source[name]]));
  const parsed = schema.safeParse(exactSource);
  if (!parsed.success) {
    const fields = parsed.error.issues.map((issue) => issue.path.join(".")).filter(Boolean);
    throw new Error(`Invalid environment configuration: ${[...new Set(fields)].join(", ")}`);
  }
  return Object.freeze({ ...parsed.data, API_URL: parsed.data.API_URL.replace(/\/+$/, "") });
}
