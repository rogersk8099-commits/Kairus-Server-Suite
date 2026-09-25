import type { FastifyInstance, FastifyRequest } from "fastify";
import { z } from "zod";
import { AppError } from "../errors.js";
import { getBearerToken, safeSecretEquals } from "../security.js";
import type { AppConfig } from "../types.js";
import { authenticateDiscordCode } from "./discord-client.js";
import { assertExactRedirectUri } from "./redirect.js";
import { constantTimeEqual, createOpaqueToken, csrfTokenForSession, hashAuthValue } from "./security.js";
import type { AuthStore } from "./types.js";

const STATE_TTL_MS = 10 * 60 * 1_000;
const TICKET_TTL_MS = 60 * 1_000;
const SESSION_TTL_MS = 12 * 60 * 60 * 1_000;
const opaque = z.string().min(32).max(256).regex(/^[A-Za-z0-9_-]+$/);
const redirectUri = z.string().url().max(2_048);

const startSchema = z.object({ state: opaque, redirectUri }).strict();
const callbackSchema = z.object({
  code: z.string().min(1).max(2_048).regex(/^[^\u0000-\u001F\u007F]+$/),
  state: opaque,
  codeVerifier: z.string().min(43).max(128).regex(/^[A-Za-z0-9._~-]+$/),
  redirectUri,
}).strict();
const tokenSchema = z.object({ sessionToken: opaque }).strict();
const revokeSchema = z.object({ sessionToken: opaque, csrfToken: opaque }).strict();
const ticketSchema = z.object({ ticket: opaque }).strict();
const demoSchema = z.object({ displayName: z.string().trim().min(1).max(32).regex(/^[A-Za-z0-9_ -]+$/) }).strict();

type Fetch = typeof fetch;

/** A website owner is either the actual Discord guild owner or holds the configured Kairu Owner role. */
async function isDiscordOwner(config: AppConfig, discordUserId: string): Promise<boolean> {
  if (!config.discordBotToken || !config.discordGuildId) return false;
  const headers = { authorization: `Bot ${config.discordBotToken}`, accept: "application/json" };
  try {
    const [guildResponse, memberResponse] = await Promise.all([
      fetch(`https://discord.com/api/v10/guilds/${config.discordGuildId}`, { headers, signal: AbortSignal.timeout(5_000) }),
      fetch(`https://discord.com/api/v10/guilds/${config.discordGuildId}/members/${discordUserId}`, { headers, signal: AbortSignal.timeout(5_000) }),
    ]);
    const guild = guildResponse.ok ? await guildResponse.json() as { owner_id?: string } : null;
    if (guild?.owner_id === discordUserId) return true;
    const member = memberResponse.ok ? await memberResponse.json() as { roles?: string[] } : null;
    return !!config.discordOwnerRoleId && Array.isArray(member?.roles) && member.roles.includes(config.discordOwnerRoleId);
  } catch { return false; }
}

function requireAuthConfig(config: AppConfig) {
  if (!config.websiteApiSecret || !config.sessionSecret || !config.websiteUrl || !config.discordOAuthClientId || !config.discordOAuthClientSecret || !config.discordOAuthRedirectUri) {
    throw new AppError(503, "AUTH_NOT_CONFIGURED", "Website authentication is not configured");
  }
  return {
    websiteApiSecret: config.websiteApiSecret,
    sessionSecret: config.sessionSecret,
    websiteUrl: config.websiteUrl,
    discordOAuthClientId: config.discordOAuthClientId,
    discordOAuthClientSecret: config.discordOAuthClientSecret,
    discordOAuthRedirectUri: config.discordOAuthRedirectUri,
  };
}

function requireInternalApi(request: FastifyRequest, apiSecret: string): void {
  if (!safeSecretEquals(getBearerToken(request.headers.authorization), apiSecret)) {
    throw new AppError(401, "UNAUTHORIZED", "Invalid internal API credentials");
  }
}

export function registerAuthRoutes(
  app: FastifyInstance,
  config: AppConfig,
  store: AuthStore,
  fetchImpl: Fetch = fetch,
): void {
  app.post("/internal/auth/oauth/states", { config: { rateLimit: { max: 30, timeWindow: "1 minute" } } }, async (request, reply) => {
    const auth = requireAuthConfig(config);
    requireInternalApi(request, auth.websiteApiSecret);
    const body = startSchema.parse(request.body);
    try { assertExactRedirectUri(body.redirectUri, auth.websiteUrl, auth.discordOAuthRedirectUri); }
    catch { throw new AppError(400, "REDIRECT_URI_NOT_ALLOWED", "OAuth redirect URI is not allowlisted"); }
    await store.cleanupAuthArtifacts();
    await store.createOAuthState(hashAuthValue(auth.sessionSecret, "oauth-state", body.state), body.redirectUri, new Date(Date.now() + STATE_TTL_MS));
    return reply.code(201).send({ ok: true, expiresIn: STATE_TTL_MS / 1_000 });
  });

  app.post("/internal/auth/discord/callback", { config: { rateLimit: { max: 30, timeWindow: "1 minute" } } }, async (request, reply) => {
    const auth = requireAuthConfig(config);
    requireInternalApi(request, auth.websiteApiSecret);
    const body = callbackSchema.parse(request.body);
    try { assertExactRedirectUri(body.redirectUri, auth.websiteUrl, auth.discordOAuthRedirectUri); }
    catch { throw new AppError(400, "REDIRECT_URI_NOT_ALLOWED", "OAuth redirect URI is not allowlisted"); }
    const consumed = await store.consumeOAuthState(hashAuthValue(auth.sessionSecret, "oauth-state", body.state), body.redirectUri);
    if (!consumed) throw new AppError(409, "OAUTH_STATE_INVALID", "OAuth state is invalid, expired, or already used");
    const profile = await authenticateDiscordCode({
      clientId: auth.discordOAuthClientId,
      clientSecret: auth.discordOAuthClientSecret,
      redirectUri: auth.discordOAuthRedirectUri,
    }, body.code, body.codeVerifier, fetchImpl);
    const user = await store.upsertDiscordIdentity(profile);
    const ticket = createOpaqueToken();
    await store.issueLoginTicket(hashAuthValue(auth.sessionSecret, "login-ticket", ticket), user.id, new Date(Date.now() + TICKET_TTL_MS));
    return reply.send({ ticket, expiresIn: TICKET_TTL_MS / 1_000 });
  });

  app.post("/internal/auth/tickets/redeem", { config: { rateLimit: { max: 60, timeWindow: "1 minute" } } }, async (request) => {
    const auth = requireAuthConfig(config);
    requireInternalApi(request, auth.websiteApiSecret);
    const { ticket } = ticketSchema.parse(request.body);
    const sessionToken = createOpaqueToken();
    const session = await store.redeemLoginTicket(
      hashAuthValue(auth.sessionSecret, "login-ticket", ticket),
      hashAuthValue(auth.sessionSecret, "session", sessionToken),
      new Date(Date.now() + SESSION_TTL_MS),
    );
    if (!session) throw new AppError(401, "LOGIN_TICKET_INVALID", "Login ticket is invalid, expired, or already used");
    const discordUserId = await store.getDiscordUserId(session.user.id);
    return { sessionToken, csrfToken: csrfTokenForSession(auth.sessionSecret, sessionToken), user: session.user, expiresAt: session.expiresAt, isOwner: discordUserId ? await isDiscordOwner(config, discordUserId) : false };
  });

  app.post("/internal/auth/sessions/validate", async (request) => {
    const auth = requireAuthConfig(config);
    requireInternalApi(request, auth.websiteApiSecret);
    const { sessionToken } = tokenSchema.parse(request.body);
    const session = await store.validateSession(hashAuthValue(auth.sessionSecret, "session", sessionToken));
    if (!session) throw new AppError(401, "SESSION_INVALID", "Session is invalid, expired, or revoked");
    const discordUserId = await store.getDiscordUserId(session.user.id);
    return { user: session.user, expiresAt: session.expiresAt, csrfToken: csrfTokenForSession(auth.sessionSecret, sessionToken), isOwner: discordUserId ? await isDiscordOwner(config, discordUserId) : false };
  });

  app.post("/internal/auth/sessions/revoke", async (request) => {
    const auth = requireAuthConfig(config);
    requireInternalApi(request, auth.websiteApiSecret);
    const body = revokeSchema.parse(request.body);
    const expectedCsrf = csrfTokenForSession(auth.sessionSecret, body.sessionToken);
    if (!constantTimeEqual(body.csrfToken, expectedCsrf)) throw new AppError(403, "CSRF_INVALID", "CSRF validation failed");
    await store.revokeSession(hashAuthValue(auth.sessionSecret, "session", body.sessionToken));
    return { ok: true };
  });

  app.post("/internal/auth/demo", { config: { rateLimit: { max: 10, timeWindow: "1 minute" } } }, async (request) => {
    const auth = requireAuthConfig(config);
    requireInternalApi(request, auth.websiteApiSecret);
    if (config.nodeEnv === "production") throw new AppError(404, "NOT_FOUND", "Route not found");
    const { displayName } = demoSchema.parse(request.body);
    const user = await store.getOrCreateDemoUser(displayName);
    const ticket = createOpaqueToken();
    await store.issueLoginTicket(hashAuthValue(auth.sessionSecret, "login-ticket", ticket), user.id, new Date(Date.now() + TICKET_TTL_MS));
    return { ticket };
  });
}
