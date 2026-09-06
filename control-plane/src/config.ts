import { z } from "zod";
import type { AppConfig } from "./types.js";

const environmentSchema = z.object({
  NODE_ENV: z.enum(["development", "test", "production"]).default("development"),
  HOST: z.string().default("0.0.0.0"),
  PORT: z.coerce.number().int().min(1).max(65535).default(3000),
  LOG_LEVEL: z.string().default("info"),
  CORS_ORIGIN: z.string().default("http://localhost:5173,http://localhost:3000"),
  DATABASE_URL: z.string().url().optional(),
  PLUGIN_API_KEY: z.string().min(16).optional(),
  ADMIN_API_KEY: z.string().min(16).optional(),
  DISCORD_BOT_TOKEN: z.string().min(1).optional(),
  DISCORD_APPLICATION_ID: z.string().regex(/^\d+$/).optional(),
  DISCORD_GUILD_ID: z.string().regex(/^\d+$/).optional(),
  MEMBERSHIP_ROLE_MAP: z.string().default("{}")
});

function parseRoleMap(value: string): Record<string, string> {
  let parsed: unknown;
  try {
    parsed = JSON.parse(value);
  } catch {
    throw new Error("MEMBERSHIP_ROLE_MAP must be valid JSON");
  }
  const result = z.record(z.string().min(1), z.string().regex(/^\d+$/)).safeParse(parsed);
  if (!result.success) {
    throw new Error("MEMBERSHIP_ROLE_MAP must map rank names to Discord snowflake IDs");
  }
  return result.data;
}

export function loadConfig(env: NodeJS.ProcessEnv = process.env): AppConfig {
  const result = environmentSchema.safeParse(env);
  if (!result.success) {
    throw new Error(`Invalid environment configuration: ${result.error.issues.map((issue) => issue.message).join(", ")}`);
  }
  const raw = result.data;
  if (raw.DISCORD_BOT_TOKEN && !raw.DISCORD_APPLICATION_ID) {
    throw new Error("DISCORD_APPLICATION_ID is required when DISCORD_BOT_TOKEN is configured");
  }
  return {
    nodeEnv: raw.NODE_ENV,
    host: raw.HOST,
    port: raw.PORT,
    logLevel: raw.LOG_LEVEL,
    corsOrigins: raw.CORS_ORIGIN.split(",").map((origin) => origin.trim()).filter(Boolean),
    databaseUrl: raw.DATABASE_URL,
    pluginApiKey: raw.PLUGIN_API_KEY,
    adminApiKey: raw.ADMIN_API_KEY,
    discordBotToken: raw.DISCORD_BOT_TOKEN,
    discordApplicationId: raw.DISCORD_APPLICATION_ID,
    discordGuildId: raw.DISCORD_GUILD_ID,
    membershipRoleMap: parseRoleMap(raw.MEMBERSHIP_ROLE_MAP)
  };
}
