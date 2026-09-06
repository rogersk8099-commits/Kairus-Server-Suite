import cors from "@fastify/cors";
import rateLimit from "@fastify/rate-limit";
import Fastify, { LogController, type FastifyInstance, type FastifyRequest } from "fastify";
import { z, ZodError } from "zod";
import { AppError } from "./errors.js";
import { getBearerToken, hashLinkCode, safeSecretEquals } from "./security.js";
import type { AppConfig, ControlPlaneStore, PlayerSnapshot } from "./types.js";

const uuid = z.string().uuid();
const serverId = z.string().trim().min(1).max(100).regex(/^[A-Za-z0-9_.-]+$/);
const discordUserId = z.string().trim().regex(/^\d{5,25}$/);
const nonNegativeInt = z.number().int().min(0);
const nonNegativeNumber = z.number().finite().min(0);

const heartbeatSchema = z.object({
  online: z.boolean(),
  tps: z.number().finite().min(0).max(100),
  playerCount: nonNegativeInt.max(100_000),
  worlds: z.array(z.string().trim().min(1).max(128)).max(256),
  players: z.array(z.string().trim().min(1).max(32)).max(100_000).default([]),
  version: z.string().trim().min(1).max(128),
  uptimeSeconds: nonNegativeInt
}).strict();

const snapshotSchema = z.object({
  minecraftUuid: uuid,
  name: z.string().trim().min(1).max(16).regex(/^[A-Za-z0-9_]{1,16}$/),
  playtimeSeconds: nonNegativeInt,
  blocksBroken: nonNegativeInt,
  kills: nonNegativeInt,
  deaths: nonNegativeInt,
  distanceMeters: nonNegativeNumber,
  balance: z.number().finite(),
  rankName: z.string().trim().min(1).max(64),
  worldName: z.string().trim().min(1).max(128).nullable().optional()
}).strict();

const completeLinkSchema = z.object({
  code: z.string().trim().min(20).max(128).regex(/^[A-Z0-9_-]+$/),
  minecraftUuid: uuid,
  javaUsername: z.string().trim().min(1).max(16).regex(/^[A-Za-z0-9_]{1,16}$/),
  bedrockXuid: z.string().trim().min(1).max(64).optional()
}).strict();

const ackSchema = z.object({
  status: z.enum(["completed", "failed"]),
  errorMessage: z.string().trim().min(1).max(1_000).optional()
}).strict().superRefine((value, context) => {
  if (value.status === "failed" && !value.errorMessage) context.addIssue({ code: z.ZodIssueCode.custom, message: "errorMessage is required when status is failed", path: ["errorMessage"] });
});

const playerName = z.string().trim().min(3).max(16).regex(/^[A-Za-z0-9_]{3,16}$/);
const queueCommandSchema = z.discriminatedUnion("commandType", [
  z.object({ serverId, commandType: z.literal("whitelist"), payload: z.object({ action: z.enum(["add", "remove"]), player: playerName }).strict() }).strict(),
  z.object({ serverId, commandType: z.literal("notification"), payload: z.object({ player: playerName, message: z.string().trim().min(1).max(512) }).strict() }).strict()
]);

function requireHeader(request: FastifyRequest, name: string): string {
  const value = request.headers[name] as string | string[] | undefined;
  const single = Array.isArray(value) ? value[0] : value;
  if (!single) throw new AppError(401, "UNAUTHORIZED", `Missing ${name} header`);
  return single;
}

function requireDiscordUser(request: FastifyRequest): string {
  const value = requireHeader(request, "x-discord-user-id");
  const result = discordUserId.safeParse(value);
  if (!result.success) throw new AppError(400, "INVALID_DISCORD_USER_ID", "X-Discord-User-Id must be a Discord snowflake");
  return result.data;
}

function requirePlugin(request: FastifyRequest, config: AppConfig): string {
  if (!config.pluginApiKey) throw new AppError(503, "PLUGIN_AUTH_NOT_CONFIGURED", "Plugin API authentication is not configured");
  const token = getBearerToken(request.headers.authorization);
  if (!safeSecretEquals(token, config.pluginApiKey)) throw new AppError(401, "UNAUTHORIZED", "Invalid plugin credentials");
  const result = serverId.safeParse(requireHeader(request, "x-kairu-server-id"));
  if (!result.success) throw new AppError(400, "INVALID_SERVER_ID", "X-Kairu-Server-Id is invalid");
  return result.data;
}

function requireAdmin(request: FastifyRequest, config: AppConfig): void {
  if (!config.adminApiKey) throw new AppError(503, "ADMIN_AUTH_NOT_CONFIGURED", "Administrative API authentication is not configured");
  const token = getBearerToken(request.headers.authorization);
  if (!safeSecretEquals(token, config.adminApiKey)) throw new AppError(401, "UNAUTHORIZED", "Invalid administrative credentials");
}

function achievements(snapshot: PlayerSnapshot | null) {
  if (!snapshot) return [];
  return [
    { id: "first-steps", name: "First Steps", unlocked: snapshot.playtimeSeconds >= 3_600, progress: Math.min(snapshot.playtimeSeconds, 3_600), target: 3_600, unit: "seconds" },
    { id: "stoneworker", name: "Stoneworker", unlocked: snapshot.blocksBroken >= 1_000, progress: Math.min(snapshot.blocksBroken, 1_000), target: 1_000, unit: "blocks" },
    { id: "hunter", name: "Hunter", unlocked: snapshot.kills >= 100, progress: Math.min(snapshot.kills, 100), target: 100, unit: "kills" }
  ];
}

export function buildApp(config: AppConfig, store: ControlPlaneStore): FastifyInstance {
  const app = Fastify({
    logger: {
      level: config.logLevel,
      redact: {
        paths: ["req.headers.authorization", "req.headers.x-discord-user-id", "req.body.code", "req.body.bedrockXuid", "config.pluginApiKey", "config.adminApiKey", "config.discordBotToken"],
        censor: "[REDACTED]"
      }
    },
    logController: new LogController({ disableRequestLogging: true }),
    requestIdHeader: "x-request-id"
  });

  void app.register(cors, {
    origin(origin, callback) {
      if (!origin || config.corsOrigins.includes("*") || config.corsOrigins.includes(origin)) return callback(null, true);
      return callback(null, false);
    },
    methods: ["GET", "POST", "OPTIONS"],
    allowedHeaders: ["Content-Type", "Authorization", "X-Discord-User-Id", "X-Kairu-Server-Id", "X-Request-Id"]
  });
  void app.register(rateLimit, { global: true, max: 300, timeWindow: "1 minute", keyGenerator: (request) => request.ip });

  app.addHook("onRequest", async (request) => {
    request.log.info({ requestId: request.id, method: request.method, path: request.url }, "request received");
  });
  app.addHook("onResponse", async (request, reply) => {
    request.log.info({ requestId: request.id, statusCode: reply.statusCode, responseTimeMs: reply.elapsedTime }, "request completed");
  });

  app.setErrorHandler((error, request, reply) => {
    if (error instanceof ZodError) {
      return reply.status(400).send({ error: { code: "VALIDATION_ERROR", message: "Request validation failed", details: error.flatten() }, requestId: request.id });
    }
    if (error instanceof AppError) {
      return reply.status(error.statusCode).send({ error: { code: error.code, message: error.message }, requestId: request.id });
    }
    request.log.error({ err: error, requestId: request.id }, "unhandled request error");
    return reply.status(500).send({ error: { code: "INTERNAL_ERROR", message: "Internal server error" }, requestId: request.id });
  });

  app.get("/health", async () => ({ status: "ok", storage: store.kind, timestamp: new Date().toISOString() }));

  app.get("/api/server/status", async () => {
    const heartbeat = await store.getLatestHeartbeat();
    return { online: heartbeat?.online ?? false, heartbeat };
  });
  app.get("/api/worlds", async () => {
    const heartbeat = await store.getLatestHeartbeat();
    return { worlds: heartbeat?.worlds ?? [] };
  });
  app.get("/api/streams", async () => ({ streams: await store.listStreams() }));
  app.get("/api/events", async () => ({ events: await store.listEvents(100) }));
  app.get("/api/leaderboard", async () => ({ players: await store.listPlayerSnapshots(100) }));
  app.get("/api/membership/tiers", async () => ({ tiers: await store.listMembershipTiers() }));

  app.get("/api/me", async (request) => {
    const userId = requireDiscordUser(request);
    const link = await store.getLinkByDiscordUser(userId);
    if (!link) throw new AppError(404, "NOT_LINKED", "No Minecraft identity is linked to this Discord user");
    const minecraft = await store.getPlayerSnapshot(link.minecraftUuid);
    return { profile: { ...link, minecraft } };
  });
  app.get("/api/me/achievements", async (request) => {
    const userId = requireDiscordUser(request);
    const link = await store.getLinkByDiscordUser(userId);
    if (!link) throw new AppError(404, "NOT_LINKED", "No Minecraft identity is linked to this Discord user");
    return { minecraftUuid: link.minecraftUuid, achievements: achievements(await store.getPlayerSnapshot(link.minecraftUuid)) };
  });
  app.get("/api/me/minecraft", async (request) => {
    const userId = requireDiscordUser(request);
    const link = await store.getLinkByDiscordUser(userId);
    if (!link) throw new AppError(404, "NOT_LINKED", "No Minecraft identity is linked to this Discord user");
    return { link, statistics: await store.getPlayerSnapshot(link.minecraftUuid) };
  });

  app.post("/api/plugin/heartbeat", { config: { rateLimit: { max: 120, timeWindow: "1 minute" } } }, async (request) => {
    const id = requirePlugin(request, config);
    const body = heartbeatSchema.parse(request.body);
    if (body.players.length > body.playerCount) throw new AppError(400, "VALIDATION_ERROR", "players cannot exceed playerCount");
    return { heartbeat: await store.recordHeartbeat({ serverId: id, ...body }) };
  });
  app.post("/api/plugin/player-snapshot", { config: { rateLimit: { max: 600, timeWindow: "1 minute" } } }, async (request) => {
    requirePlugin(request, config);
    const body = snapshotSchema.parse(request.body);
    return { snapshot: await store.recordPlayerSnapshot({ ...body, worldName: body.worldName ?? null }) };
  });
  app.post("/api/link-codes/complete", { config: { rateLimit: { max: 12, timeWindow: "1 minute" } } }, async (request, reply) => {
    requirePlugin(request, config);
    const body = completeLinkSchema.parse(request.body);
    const result = await store.completeLinkCode(hashLinkCode(body.code), { minecraftUuid: body.minecraftUuid, javaUsername: body.javaUsername, bedrockXuid: body.bedrockXuid });
    if (!result.ok) throw new AppError(409, result.reason === "invalid_or_expired" ? "INVALID_OR_EXPIRED_CODE" : "IDENTITY_ALREADY_LINKED", result.reason === "invalid_or_expired" ? "Link code is invalid, expired, or already used" : "A Discord or Minecraft identity is already linked");
    return reply.code(201).send({ link: result.link });
  });
  app.get("/api/plugin/commands", { config: { rateLimit: { max: 120, timeWindow: "1 minute" } } }, async (request) => {
    const id = requirePlugin(request, config);
    return { commands: await store.listQueuedPluginCommands(id) };
  });
  app.post("/api/plugin/commands/:id/ack", { config: { rateLimit: { max: 120, timeWindow: "1 minute" } } }, async (request) => {
    const serverId = requirePlugin(request, config);
    const commandId = uuid.parse((request.params as { id?: string }).id);
    const body = ackSchema.parse(request.body);
    const command = await store.acknowledgePluginCommand(serverId, commandId, body.status, body.errorMessage);
    if (!command) throw new AppError(404, "COMMAND_NOT_FOUND", "Queued command was not found or was already acknowledged");
    return { command };
  });

  app.post("/api/admin/plugin-commands", async (request, reply) => {
    requireAdmin(request, config);
    const body = queueCommandSchema.parse(request.body);
    return reply.code(201).send({ command: await store.queuePluginCommand(body) });
  });

  return app;
}
