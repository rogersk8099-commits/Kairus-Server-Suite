import { afterEach, describe, expect, it, vi } from "vitest";
import Fastify, { type FastifyInstance } from "fastify";
import { registerAuthRoutes } from "../src/auth/routes.js";
import { MemoryAuthStore } from "../src/auth/memory-auth-store.js";
import { validateConfiguredAuthUrls } from "../src/auth/redirect.js";
import type { AppConfig } from "../src/types.js";

const config: AppConfig = {
  nodeEnv: "test",
  host: "127.0.0.1",
  port: 0,
  logLevel: "silent",
  corsOrigins: [],
  membershipRoleMap: {},
  websiteApiSecret: "a".repeat(32),
  sessionSecret: "s".repeat(32),
  websiteUrl: "https://play.example.com",
  discordOAuthClientId: "123456789012345678",
  discordOAuthClientSecret: "discord-secret-value",
  discordOAuthRedirectUri: "https://play.example.com/auth/discord/callback",
};
const headers = { authorization: `Bearer ${config.websiteApiSecret}` };
const state = "state_abcdefghijklmnopqrstuvwxyz0123456789";
const verifier = "v".repeat(64);

function discordFetch(ok = true) {
  return vi.fn<typeof fetch>(async (input, init) => {
    const url = String(input);
    if (url.endsWith("/oauth2/token")) {
      const body = init?.body as URLSearchParams;
      expect(body.get("code_verifier")).toBe(verifier);
      if (!ok) return new Response('{"error":"invalid_grant"}', { status: 400 });
      return Response.json({ access_token: "provider-token", token_type: "Bearer", expires_in: 60, refresh_token: "refresh", scope: "identify" });
    }
    if (url.endsWith("/users/@me")) {
      return Response.json({ id: "123456789012345678", username: "ember", global_name: "Ember", avatar: null });
    }
    if (url.endsWith("/oauth2/token/revoke")) return new Response(null, { status: 200 });
    throw new Error(`Unexpected fetch ${url}`);
  });
}

function setup(fetchImpl = discordFetch()) {
  const app = Fastify({ logger: false });
  registerAuthRoutes(app, config, new MemoryAuthStore(), fetchImpl);
  return { app, fetchImpl };
}

const apps: FastifyInstance[] = [];
afterEach(async () => {
  vi.restoreAllMocks();
  await Promise.all(apps.splice(0).map((app) => app.close()));
});

async function beginAndCallback(app: FastifyInstance) {
  const start = await app.inject({ method: "POST", url: "/internal/auth/oauth/states", headers, payload: { state, redirectUri: config.discordOAuthRedirectUri } });
  expect(start.statusCode).toBe(201);
  return app.inject({ method: "POST", url: "/internal/auth/discord/callback", headers, payload: { code: "discord-code", state, codeVerifier: verifier, redirectUri: config.discordOAuthRedirectUri } });
}

describe("central website authentication", () => {
  it("requires an exact HTTPS callback on the configured website origin", () => {
    expect(validateConfiguredAuthUrls(config.websiteUrl!, config.discordOAuthRedirectUri!)).toEqual({ websiteOrigin: "https://play.example.com", redirectUri: "https://play.example.com/auth/discord/callback" });
    expect(() => validateConfiguredAuthUrls(config.websiteUrl!, "https://evil.example/auth/discord/callback")).toThrow(/exactly equal/);
    expect(() => validateConfiguredAuthUrls(config.websiteUrl!, "https://play.example.com/auth/discord/callback?next=x")).toThrow();
    expect(() => validateConfiguredAuthUrls("http://play.example.com", "http://play.example.com/auth/discord/callback")).toThrow(/HTTPS/);
  });

  it("requires internal auth, consumes state once, submits PKCE, and returns only a one-time ticket", async () => {
    const { app, fetchImpl } = setup(); apps.push(app);
    expect((await app.inject({ method: "POST", url: "/internal/auth/oauth/states", payload: { state, redirectUri: config.discordOAuthRedirectUri } })).statusCode).toBe(401);
    const callback = await beginAndCallback(app);
    expect(callback.statusCode).toBe(200);
    expect(callback.json()).toEqual({ ticket: expect.any(String), expiresIn: 60 });
    expect(callback.body).not.toContain("provider-token");
    expect(callback.body).not.toContain("refresh");
    expect(callback.body).not.toContain(config.discordOAuthClientSecret!);
    const replay = await app.inject({ method: "POST", url: "/internal/auth/discord/callback", headers, payload: { code: "replay", state, codeVerifier: verifier, redirectUri: config.discordOAuthRedirectUri } });
    expect(replay.statusCode).toBe(409);
    expect(fetchImpl).toHaveBeenCalledTimes(3);
  });

  it("maps provider callback failures to a stable error without leaking Discord payloads", async () => {
    const { app } = setup(discordFetch(false)); apps.push(app);
    const callback = await beginAndCallback(app);
    expect(callback.statusCode).toBe(502);
    expect(callback.json()).toMatchObject({ code: "OAUTH_PROVIDER_ERROR", message: "Discord token exchange failed" });
    expect(callback.body).not.toContain("invalid_grant");
    expect(callback.body).not.toContain(config.discordOAuthClientSecret!);
  });

  it("redeems tickets once, validates sessions, enforces CSRF on logout, and revokes immediately", async () => {
    const { app } = setup(); apps.push(app);
    const ticket = (await beginAndCallback(app)).json().ticket as string;
    const redeemed = await app.inject({ method: "POST", url: "/internal/auth/tickets/redeem", headers, payload: { ticket } });
    expect(redeemed.statusCode).toBe(200);
    const body = redeemed.json() as { sessionToken: string; csrfToken: string; user: { displayName: string } };
    expect(body.user.displayName).toBe("Ember");
    expect((await app.inject({ method: "POST", url: "/internal/auth/tickets/redeem", headers, payload: { ticket } })).statusCode).toBe(401);
    expect((await app.inject({ method: "POST", url: "/internal/auth/sessions/validate", headers, payload: { sessionToken: body.sessionToken } })).statusCode).toBe(200);
    expect((await app.inject({ method: "POST", url: "/internal/auth/sessions/revoke", headers, payload: { sessionToken: body.sessionToken, csrfToken: "x".repeat(43) } })).statusCode).toBe(403);
    expect((await app.inject({ method: "POST", url: "/internal/auth/sessions/revoke", headers, payload: { sessionToken: body.sessionToken, csrfToken: body.csrfToken } })).statusCode).toBe(200);
    expect((await app.inject({ method: "POST", url: "/internal/auth/sessions/validate", headers, payload: { sessionToken: body.sessionToken } })).statusCode).toBe(401);
  });

  it("rejects expired tickets and sessions in the store", async () => {
    let now = new Date("2026-01-01T00:00:00.000Z");
    const store = new MemoryAuthStore(() => now);
    const user = await store.getOrCreateDemoUser("Demo");
    await store.issueLoginTicket("ticket-hash", user.id, new Date(now.getTime() + 1_000));
    now = new Date(now.getTime() + 2_000);
    expect(await store.redeemLoginTicket("ticket-hash", "session-hash", new Date(now.getTime() + 1_000))).toBeNull();
    await store.issueLoginTicket("ticket-hash-2", user.id, new Date(now.getTime() + 1_000));
    const session = await store.redeemLoginTicket("ticket-hash-2", "session-hash-2", new Date(now.getTime() + 1_000));
    expect(session).not.toBeNull();
    now = new Date(now.getTime() + 2_000);
    expect(await store.validateSession("session-hash-2")).toBeNull();
  });

  it("does not enable demo login in production", async () => {
    const app = Fastify({ logger: false }); apps.push(app);
    registerAuthRoutes(app, { ...config, nodeEnv: "production" }, new MemoryAuthStore(), discordFetch());
    const response = await app.inject({ method: "POST", url: "/internal/auth/demo", headers, payload: { displayName: "Demo" } });
    expect(response.statusCode).toBe(404);
  });
});
