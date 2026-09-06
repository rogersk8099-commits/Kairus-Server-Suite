import { createHash, createHmac, timingSafeEqual } from "node:crypto";
import Fastify, { type FastifyRequest } from "fastify";
import { z } from "zod";
import type { PrismaClient } from "@prisma/client";
import type { AppConfig } from "../config.js";
import type { IdempotencyStore } from "../idempotency.js";
import type { AppLogger } from "../logger.js";

const bodyLimit = 64 * 1024;
const common = { id: z.string().min(1).max(200), occurredAt: z.string().datetime({ offset: true }) };
const schemas = {
  minecraft: z.object({ ...common, type: z.enum(["server_online", "server_offline", "player_join", "player_leave"]), server: z.string().min(1).max(100), player: z.string().min(1).max(100).optional() }).strict(),
  stream: z.object({ ...common, type: z.enum(["live", "ended"]), platform: z.enum(["TWITCH", "YOUTUBE"]), channel: z.string().min(1).max(200), url: z.string().url().optional(), guildId: z.string().regex(/^\d+$/).optional() }).strict(),
  membership: z.object({ ...common, discordUserId: z.string().regex(/^\d+$/), status: z.enum(["verified", "revoked"]), membershipId: z.string().min(1).max(200).optional(), guildId: z.string().regex(/^\d+$/).optional() }).strict(),
  events: z.object({ ...common, type: z.enum(["event_created", "event_updated", "event_cancelled", "reminder"]), eventId: z.string().min(1).max(200), title: z.string().min(1).max(200), startsAt: z.string().datetime({ offset: true }), guildId: z.string().regex(/^\d+$/).optional(), channelId: z.string().regex(/^\d+$/).optional() }).strict()
} as const;
type WebhookName = keyof typeof schemas;

class WindowLimiter {
  private readonly windows = new Map<string, { at: number; count: number }>();
  public constructor(private readonly maximum: number) {}
  public allow(key: string): boolean { const now = Date.now(), current = this.windows.get(key); if (!current || now - current.at >= 60_000) { this.windows.set(key, { at: now, count: 1 }); return true; } if (current.count >= this.maximum) return false; current.count += 1; return true; }
}

export function signWebhook(secret: string, timestamp: string, body: Buffer): string { return `sha256=${createHmac("sha256", secret).update(timestamp).update(".").update(body).digest("hex")}`; }
export function verifyWebhook(secret: string, timestamp: string, body: Buffer, signature: string | undefined): boolean { if (!signature?.startsWith("sha256=")) return false; const expected = Buffer.from(signWebhook(secret, timestamp, body)); const supplied = Buffer.from(signature); return expected.length === supplied.length && timingSafeEqual(expected, supplied); }
function timestampValid(value: string | undefined, seconds: number): boolean { if (!value || !/^\d{10}(?:\d{3})?$/.test(value)) return false; const time = value.length === 10 ? Number(value) * 1_000 : Number(value); return Number.isSafeInteger(time) && Math.abs(Date.now() - time) <= seconds * 1_000; }

declare module "fastify" { interface FastifyRequest { rawBody?: Buffer } }

export interface HealthState { gateway: "dormant" | "connecting" | "ready" | "disconnected"; shuttingDown: boolean; }
interface ServerOptions { config: AppConfig; database: PrismaClient; idempotency: IdempotencyStore; logger: AppLogger; state: HealthState; onMembership?: (discordUserId: string) => Promise<void>; }

export function buildHttpServer(options: ServerOptions) {
  const app = Fastify({ loggerInstance: options.logger, bodyLimit });
  const limiter = new WindowLimiter(options.config.WEBHOOK_RATE_LIMIT_PER_MINUTE);
  app.addContentTypeParser("application/json", { parseAs: "buffer", bodyLimit }, (request, body, done) => { const rawBody = Buffer.isBuffer(body) ? body : Buffer.from(body); request.rawBody = rawBody; try { done(null, JSON.parse(rawBody.toString("utf8"))); } catch (error) { done(error as Error, undefined); } });
  app.get("/health", async (_request, reply) => { const database = await options.database.$queryRaw`SELECT 1`.then(() => "ready" as const).catch(() => "unavailable" as const); const ok = !options.state.shuttingDown && database === "ready"; return reply.code(ok ? 200 : 503).send({ status: ok ? "ok" : "degraded", database, gateway: options.state.gateway, shuttingDown: options.state.shuttingDown }); });
  app.get("/ready", async (_request, reply) => reply.code(options.state.shuttingDown ? 503 : 200).send({ ready: !options.state.shuttingDown, gateway: options.state.gateway }));
  for (const name of Object.keys(schemas) as WebhookName[]) app.post(`/webhooks/${name}`, async (request, reply) => {
    if (!options.config.WEBHOOK_SECRET) return reply.code(503).send({ error: "webhook_unavailable" });
    if (!limiter.allow(`${name}:${request.ip}`)) return reply.code(429).send({ error: "rate_limited" });
    const timestamp = request.headers["x-kairu-timestamp"] as string | undefined;
    const signature = request.headers["x-kairu-signature"] as string | undefined;
    if (!timestampValid(timestamp, options.config.WEBHOOK_MAX_AGE_SECONDS)) return reply.code(401).send({ error: "invalid_timestamp" });
    const rawBody = request.rawBody ?? Buffer.alloc(0);
    if (!verifyWebhook(options.config.WEBHOOK_SECRET, timestamp!, rawBody, signature)) return reply.code(401).send({ error: "invalid_signature" });
    const parsed = schemas[name].safeParse(request.body);
    if (!parsed.success) return reply.code(400).send({ error: "invalid_payload" });
    const event = parsed.data;
    const key = `webhook:${name}:${event.id}`;
    if (!await options.idempotency.claim(key, options.config.WEBHOOK_MAX_AGE_SECONDS * 2_000, { source: name, id: event.id })) return reply.code(202).send({ accepted: true, duplicate: true });
    try {
      const guildId = "guildId" in event && event.guildId ? event.guildId : options.config.DISCORD_GUILD_ID;
      await options.database.discordGuild.upsert({ where: { guildId }, create: { guildId }, update: {} });
      await options.database.discordWebhookReceipt.create({ data: { guildId, source: name, externalEventId: event.id, signatureHash: createHash("sha256").update(signature ?? "").digest("hex"), payload: event, processedAt: new Date() } });
      if (name === "membership") { const item = event as z.infer<typeof schemas.membership>; await options.database.discordMembership.upsert({ where: { guildId_discordUserId: { guildId, discordUserId: item.discordUserId } }, create: { guildId, discordUserId: item.discordUserId, status: item.status === "verified" ? "ACTIVE" : "CANCELLED", externalRef: item.membershipId }, update: { status: item.status === "verified" ? "ACTIVE" : "CANCELLED", externalRef: item.membershipId } }); await options.onMembership?.(item.discordUserId); }
      if (name === "minecraft") { const item = event as z.infer<typeof schemas.minecraft>; await options.database.serverHeartbeat.upsert({ where: { serverId: item.server }, create: { serverId: item.server, online: item.type !== "server_offline", tps: 0, playerCount: 0, version: "webhook", uptimeSeconds: 0n }, update: { online: item.type !== "server_offline", receivedAt: new Date() } }); }
      if (name === "stream") { const item = event as z.infer<typeof schemas.stream>; await options.database.legacyStream.upsert({ where: { id: item.id }, create: { id: item.id, channelName: item.channel, url: item.url ?? "https://example.invalid", platform: item.platform, title: `${item.channel} live`, live: item.type === "live", startedAt: new Date(item.occurredAt) }, update: { live: item.type === "live", url: item.url ?? undefined } }); }
      return reply.code(202).send({ accepted: true });
    } catch (error) { await options.idempotency.release(key); options.logger.error({ err: error, route: name, eventId: event.id }, "webhook processing failed"); return reply.code(500).send({ error: "internal_error" }); }
  });
  app.setErrorHandler((error, request: FastifyRequest, reply) => { options.logger.warn({ err: error, path: request.url }, "HTTP request rejected"); void reply.code((error as { statusCode?: number }).statusCode === 413 ? 413 : 400).send({ error: (error as { statusCode?: number }).statusCode === 413 ? "payload_too_large" : "invalid_request" }); });
  return app;
}
