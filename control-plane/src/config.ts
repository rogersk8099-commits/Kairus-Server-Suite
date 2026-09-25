import { z } from "zod";
import { validateConfiguredAuthUrls } from "./auth/redirect.js";
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
  DISCORD_OWNER_ROLE_ID: z.string().regex(/^\d+$/).optional(),
  MEMBERSHIP_ROLE_MAP: z.string().default("{}"),
  WEBSITE_API_SECRET: z.string().min(32).optional(),
  SESSION_SECRET: z.string().min(32).optional(),
  WEBSITE_URL: z.string().url().optional(),
  DISCORD_OAUTH_CLIENT_ID: z.string().regex(/^\d+$/).optional(),
  DISCORD_OAUTH_CLIENT_SECRET: z.string().min(1).optional(),
  DISCORD_OAUTH_REDIRECT_URI: z.string().url().optional()
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
  const authValues = [
    raw.WEBSITE_API_SECRET,
    raw.SESSION_SECRET,
    raw.WEBSITE_URL,
    raw.DISCORD_OAUTH_CLIENT_ID,
    raw.DISCORD_OAUTH_CLIENT_SECRET,
    raw.DISCORD_OAUTH_REDIRECT_URI
  ];
  if (authValues.some(Boolean) && !authValues.every(Boolean)) {
    throw new Error("Website authentication requires WEBSITE_API_SECRET, SESSION_SECRET, WEBSITE_URL, DISCORD_OAUTH_CLIENT_ID, DISCORD_OAUTH_CLIENT_SECRET, and DISCORD_OAUTH_REDIRECT_URI together");
  }
  if (raw.WEBSITE_URL && raw.DISCORD_OAUTH_REDIRECT_URI) {
    validateConfiguredAuthUrls(raw.WEBSITE_URL, raw.DISCORD_OAUTH_REDIRECT_URI);
  }
  if (raw.WEBSITE_API_SECRET && raw.SESSION_SECRET && raw.WEBSITE_API_SECRET === raw.SESSION_SECRET) {
    throw new Error("WEBSITE_API_SECRET and SESSION_SECRET must be different values");
  }
  if (raw.NODE_ENV === "production" && !raw.DATABASE_URL) {
    throw new Error("DATABASE_URL is required in production");
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
    discordOwnerRoleId: raw.DISCORD_OWNER_ROLE_ID,
    membershipRoleMap: parseRoleMap(raw.MEMBERSHIP_ROLE_MAP),
    websiteApiSecret: raw.WEBSITE_API_SECRET,
    sessionSecret: raw.SESSION_SECRET,
    websiteUrl: raw.WEBSITE_URL,
    discordOAuthClientId: raw.DISCORD_OAUTH_CLIENT_ID,
    discordOAuthClientSecret: raw.DISCORD_OAUTH_CLIENT_SECRET,
    discordOAuthRedirectUri: raw.DISCORD_OAUTH_REDIRECT_URI
  };
}
