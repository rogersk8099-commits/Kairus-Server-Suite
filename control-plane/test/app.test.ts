import { describe, expect, it, afterEach } from "vitest";
import { buildApp } from "../src/app.js";
import { MemoryStore } from "../src/db/memory-store.js";
import { hashLinkCode } from "../src/security.js";
import type { AppConfig } from "../src/types.js";

const config: AppConfig = {
  nodeEnv: "test",
  host: "127.0.0.1",
  port: 0,
  logLevel: "silent",
  corsOrigins: ["http://localhost:5173"],
  pluginApiKey: "test-plugin-key-which-is-long",
  adminApiKey: "test-admin-key-which-is-long-",
  membershipRoleMap: {}
};
const pluginHeaders = { authorization: `Bearer ${config.pluginApiKey}`, "x-kairu-server-id": "primary" };
const uuid = "123e4567-e89b-42d3-a456-426614174000";

function setup() {
  const store = new MemoryStore({
    events: [{ id: "build-night", title: "Build Night", description: "Create together.", startsAt: "2099-01-01T18:00:00.000Z", endsAt: null, location: "Spawn", status: "scheduled" }],
    streams: [{ id: "stream-1", channelName: "KairuTV", url: "https://example.invalid/live", platform: "Twitch", title: "SMP Live", viewerCount: 12, live: true, startedAt: "2099-01-01T17:00:00.000Z" }],
    tiers: [{ slug: "supporter", name: "Supporter", description: "Help the server", priceMonthlyCents: 500, benefits: ["Discord badge"] }]
  });
  return { store, app: buildApp(config, store) };
}

describe("Kairu control plane API", () => {
  const apps: ReturnType<typeof setup>["app"][] = [];
  afterEach(async () => { await Promise.all(apps.splice(0).map((app) => app.close())); });

  it("serves health and all public directory endpoints with safe empty/default data", async () => {
    const { app } = setup(); apps.push(app);
    expect((await app.inject("/health")).json()).toMatchObject({ status: "ok", storage: "memory" });
    expect((await app.inject("/api/server/status")).json()).toEqual({ online: false, heartbeat: null });
    expect((await app.inject("/api/worlds")).json()).toEqual({ worlds: [] });
    expect((await app.inject("/api/events")).json().events).toHaveLength(1);
    expect((await app.inject("/api/streams")).json().streams).toHaveLength(1);
    expect((await app.inject("/api/membership/tiers")).json().tiers[0].slug).toBe("supporter");
    expect((await app.inject("/api/leaderboard")).json()).toEqual({ players: [] });
  });

  it("requires plugin credentials, validates telemetry, and exposes its public projection", async () => {
    const { app } = setup(); apps.push(app);
    const forbidden = await app.inject({ method: "POST", url: "/api/plugin/heartbeat", payload: {} });
    expect(forbidden.statusCode).toBe(401);
    const invalid = await app.inject({ method: "POST", url: "/api/plugin/heartbeat", headers: pluginHeaders, payload: { online: true } });
    expect(invalid.statusCode).toBe(400);
    const response = await app.inject({ method: "POST", url: "/api/plugin/heartbeat", headers: pluginHeaders, payload: { online: true, tps: 19.98, playerCount: 2, worlds: ["world", "nether"], players: ["Alex", "Steve"], version: "Paper 1.21.4", uptimeSeconds: 3_600 } });
    expect(response.statusCode).toBe(200);
    const status = (await app.inject("/api/server/status")).json();
    expect(status.online).toBe(true);
    expect(status.heartbeat.players).toEqual(["Alex", "Steve"]);
    expect((await app.inject("/api/worlds")).json()).toEqual({ worlds: ["world", "nether"] });
  });

  it("stores a snapshot, supports leaderboard, and protects player API before and after linking", async () => {
    const { app, store } = setup(); apps.push(app);
    const snapshot = { minecraftUuid: uuid, name: "Alex", playtimeSeconds: 4_000, blocksBroken: 1_100, kills: 101, deaths: 2, distanceMeters: 124.5, balance: 35.25, rankName: "Member", worldName: "world" };
    const write = await app.inject({ method: "POST", url: "/api/plugin/player-snapshot", headers: pluginHeaders, payload: snapshot });
    expect(write.statusCode).toBe(200);
    expect((await app.inject("/api/leaderboard")).json().players[0]).toMatchObject({ name: "Alex", kills: 101 });
    expect((await app.inject({ method: "GET", url: "/api/me", headers: { "x-discord-user-id": "123456789012345678" } })).statusCode).toBe(404);
    const code = "ABCDEFGHIJKLMNOPQRSTUVWXYZ_123";
    await store.createLinkCode("123456789012345678", hashLinkCode(code), new Date(Date.now() + 60_000));
    const complete = await app.inject({ method: "POST", url: "/api/link-codes/complete", headers: pluginHeaders, payload: { code, minecraftUuid: uuid, javaUsername: "Alex", bedrockXuid: "bedrock-123" } });
    expect(complete.statusCode).toBe(201);
    const profile = await app.inject({ method: "GET", url: "/api/me", headers: { "x-discord-user-id": "123456789012345678" } });
    expect(profile.json().profile).toMatchObject({ javaUsername: "Alex", minecraft: { blocksBroken: 1_100 } });
    const achievements = await app.inject({ method: "GET", url: "/api/me/achievements", headers: { "x-discord-user-id": "123456789012345678" } });
    expect(achievements.json().achievements.every((achievement: { unlocked: boolean }) => achievement.unlocked)).toBe(true);
    const minecraft = await app.inject({ method: "GET", url: "/api/me/minecraft", headers: { "x-discord-user-id": "123456789012345678" } });
    expect(minecraft.json().statistics.distanceMeters).toBe(124.5);
    const reused = await app.inject({ method: "POST", url: "/api/link-codes/complete", headers: pluginHeaders, payload: { code, minecraftUuid: uuid, javaUsername: "Alex" } });
    expect(reused.statusCode).toBe(409);
  });

  it("authenticates, queues, fetches, acknowledges plugin commands, and rejects cross-state replays", async () => {
    const { app } = setup(); apps.push(app);
    const unauthorized = await app.inject({ method: "POST", url: "/api/admin/plugin-commands", payload: { serverId: "primary", commandType: "notification", payload: { player: "Alex", message: "Welcome" } } });
    expect(unauthorized.statusCode).toBe(401);
    const malformed = await app.inject({ method: "POST", url: "/api/admin/plugin-commands", headers: { authorization: `Bearer ${config.adminApiKey}` }, payload: { serverId: "primary", commandType: "notification", payload: { message: "No target" } } });
    expect(malformed.statusCode).toBe(400);
    const queued = await app.inject({ method: "POST", url: "/api/admin/plugin-commands", headers: { authorization: `Bearer ${config.adminApiKey}` }, payload: { serverId: "primary", commandType: "notification", payload: { player: "Alex", message: "Welcome" } } });
    expect(queued.statusCode).toBe(201);
    const id = queued.json().command.id as string;
    const commands = await app.inject({ method: "GET", url: "/api/plugin/commands", headers: pluginHeaders });
    expect(commands.json().commands[0]).toMatchObject({ id, status: "queued" });
    const ack = await app.inject({ method: "POST", url: `/api/plugin/commands/${id}/ack`, headers: pluginHeaders, payload: { status: "completed" } });
    expect(ack.statusCode).toBe(200);
    expect((await app.inject({ method: "GET", url: "/api/plugin/commands", headers: pluginHeaders })).json().commands).toEqual([]);
    expect((await app.inject({ method: "POST", url: `/api/plugin/commands/${id}/ack`, headers: pluginHeaders, payload: { status: "completed" } })).statusCode).toBe(404);
  });


  it("authenticates, strictly validates, and idempotently records server-isolated bridge events", async () => {
    const { app } = setup(); apps.push(app);
    const payload = {
      eventId: "event_123",
      eventType: "CHAT",
      occurredAt: "2026-09-06T20:00:00Z",
      worldName: "world",
      minecraftUuid: uuid,
      minecraftName: "Alex",
      content: "Hello Discord",
      details: { source: "minecraft" }
    };
    expect((await app.inject({ method: "POST", url: "/api/plugin/bridge-events", payload })).statusCode).toBe(401);
    const created = await app.inject({ method: "POST", url: "/api/plugin/bridge-events", headers: pluginHeaders, payload });
    expect(created.statusCode).toBe(201);
    expect(created.json()).toMatchObject({ duplicate: false, event: { serverId: "primary", eventId: "event_123", eventType: "CHAT" } });
    const duplicate = await app.inject({ method: "POST", url: "/api/plugin/bridge-events", headers: pluginHeaders, payload: { ...payload, content: "retry body is ignored" } });
    expect(duplicate.statusCode).toBe(200);
    expect(duplicate.json()).toMatchObject({ duplicate: true, event: { content: "Hello Discord" } });
    const otherServer = await app.inject({ method: "POST", url: "/api/plugin/bridge-events", headers: { ...pluginHeaders, "x-kairu-server-id": "secondary" }, payload });
    expect(otherServer.statusCode).toBe(201);
    const invalid = await app.inject({ method: "POST", url: "/api/plugin/bridge-events", headers: pluginHeaders, payload: { ...payload, serverId: "spoofed", content: "x".repeat(501) } });
    expect(invalid.statusCode).toBe(400);
  });

  it("queues bounded admin chat and enforces stable per-server poll and atomic ack isolation", async () => {
    const { app } = setup(); apps.push(app);
    const adminHeaders = { authorization: `Bearer ${config.adminApiKey}` };
    const firstPayload = { serverId: "primary", idempotencyKey: "discord_001", discordMessageId: "123456789012345678", discordAuthorId: "234567890123456789", displayName: "Ada", targetWorld: "world", content: "Hello Minecraft" };
    expect((await app.inject({ method: "POST", url: "/api/admin/chat", payload: firstPayload })).statusCode).toBe(401);
    expect((await app.inject({ method: "POST", url: "/api/admin/chat", headers: adminHeaders, payload: { serverId: "primary", content: "No durable key" } })).statusCode).toBe(400);
    const first = await app.inject({ method: "POST", url: "/api/admin/chat", headers: adminHeaders, payload: firstPayload });
    expect(first.statusCode).toBe(201);
    const firstId = first.json().message.id as string;
    const replay = await app.inject({ method: "POST", url: "/api/admin/chat", headers: adminHeaders, payload: { ...firstPayload, content: "Changed retry body" } });
    expect(replay.statusCode).toBe(200);
    expect(replay.json()).toMatchObject({ duplicate: true, message: { id: firstId, content: "Hello Minecraft" } });
    const second = await app.inject({ method: "POST", url: "/api/admin/chat", headers: adminHeaders, payload: { serverId: "primary", idempotencyKey: "discord_002", content: "Second" } });
    expect(second.statusCode).toBe(201);
    await app.inject({ method: "POST", url: "/api/admin/chat", headers: adminHeaders, payload: { serverId: "secondary", idempotencyKey: "discord_003", content: "Private other server" } });
    const limited = await app.inject({ method: "GET", url: "/api/plugin/chat/queued?limit=1", headers: pluginHeaders });
    expect(limited.statusCode).toBe(200);
    expect(limited.json().messages).toEqual([{ id: firstId, content: "Hello Minecraft", displayName: "Ada", author: { displayName: "Ada" }, targetWorld: "world" }]);
    expect((await app.inject({ method: "GET", url: "/api/plugin/chat/queued?limit=51", headers: pluginHeaders })).statusCode).toBe(400);
    expect((await app.inject({ method: "GET", url: "/api/plugin/chat/queued?limit=10", headers: { ...pluginHeaders, "x-kairu-server-id": "third" } })).json().messages).toEqual([]);
    expect((await app.inject({ method: "POST", url: `/api/plugin/chat/${firstId}/ack`, headers: { ...pluginHeaders, "x-kairu-server-id": "secondary" }, payload: { status: "delivered", detail: "spoof" } })).statusCode).toBe(404);
    const ack = await app.inject({ method: "POST", url: `/api/plugin/chat/${firstId}/ack`, headers: pluginHeaders, payload: { status: "delivered", detail: "broadcast to Minecraft" } });
    expect(ack.statusCode).toBe(200);
    expect(ack.json().message).toMatchObject({ id: firstId, status: "delivered", deliveryAttempts: 1 });
    expect((await app.inject({ method: "POST", url: `/api/plugin/chat/${firstId}/ack`, headers: pluginHeaders, payload: { status: "delivered", detail: "repeat" } })).statusCode).toBe(404);
    const secondId = second.json().message.id as string;
    const rejected = await app.inject({ method: "POST", url: `/api/plugin/chat/${secondId}/ack`, headers: pluginHeaders, payload: { status: "rejected", detail: "target world is not loaded" } });
    expect(rejected.json().message).toMatchObject({ status: "rejected", acknowledgementDetail: "target world is not loaded" });
    expect(rejected.json().message.deadLetteredAt).toBeTypeOf("string");
    expect((await app.inject({ method: "GET", url: "/api/plugin/chat/queued", headers: pluginHeaders })).json().messages).toEqual([]);
  });

  it("rejects invalid player identity header and applies CORS only to the configured origin", async () => {
    const { app } = setup(); apps.push(app);
    expect((await app.inject({ method: "GET", url: "/api/me", headers: { "x-discord-user-id": "not-a-snowflake" } })).statusCode).toBe(400);
    const allowed = await app.inject({ method: "OPTIONS", url: "/health", headers: { origin: "http://localhost:5173", "access-control-request-method": "GET" } });
    expect(allowed.headers["access-control-allow-origin"]).toBe("http://localhost:5173");
  });
});
