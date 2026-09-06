import { z } from "zod";
import { AppError } from "../errors.js";
import type { DiscordIdentityProfile } from "./types.js";

const tokenResponseSchema = z.object({
  access_token: z.string().min(1).max(4_096),
  token_type: z.string().min(1).max(32),
  expires_in: z.number().int().positive(),
  refresh_token: z.string().min(1).max(4_096).optional(),
  scope: z.string(),
});

const discordUserSchema = z.object({
  id: z.string().regex(/^\d{5,25}$/),
  username: z.string().min(1).max(80),
  global_name: z.string().max(80).nullable().optional(),
  avatar: z.string().max(256).nullable().optional(),
}).passthrough();

export type DiscordOAuthClientConfig = {
  clientId: string;
  clientSecret: string;
  redirectUri: string;
};

type Fetch = typeof fetch;

function basicCredentials(clientId: string, clientSecret: string): string {
  return Buffer.from(`${clientId}:${clientSecret}`, "utf8").toString("base64");
}

async function providerError(response: Response, operation: string): Promise<never> {
  await response.body?.cancel().catch(() => undefined);
  throw new AppError(502, "OAUTH_PROVIDER_ERROR", `Discord ${operation} failed`);
}

export async function authenticateDiscordCode(
  config: DiscordOAuthClientConfig,
  code: string,
  codeVerifier: string,
  fetchImpl: Fetch = fetch,
): Promise<DiscordIdentityProfile> {
  const credentials = basicCredentials(config.clientId, config.clientSecret);
  const tokenResponse = await fetchImpl("https://discord.com/api/oauth2/token", {
    method: "POST",
    headers: {
      accept: "application/json",
      authorization: `Basic ${credentials}`,
      "content-type": "application/x-www-form-urlencoded",
    },
    body: new URLSearchParams({
      grant_type: "authorization_code",
      code,
      redirect_uri: config.redirectUri,
      code_verifier: codeVerifier,
    }),
    signal: AbortSignal.timeout(10_000),
  });
  if (!tokenResponse.ok) return providerError(tokenResponse, "token exchange");

  let accessToken: string | undefined;
  try {
    const token = tokenResponseSchema.parse(await tokenResponse.json());
    accessToken = token.access_token;
    if (token.token_type.toLowerCase() !== "bearer" || !token.scope.split(/\s+/).includes("identify")) {
      throw new AppError(502, "OAUTH_PROVIDER_ERROR", "Discord returned an invalid OAuth grant");
    }
    const profileResponse = await fetchImpl("https://discord.com/api/v10/users/@me", {
      headers: { accept: "application/json", authorization: `Bearer ${accessToken}` },
      signal: AbortSignal.timeout(10_000),
    });
    if (!profileResponse.ok) return providerError(profileResponse, "profile request");
    const raw = discordUserSchema.parse(await profileResponse.json());
    const avatarHash = raw.avatar ?? null;
    const avatarUrl = avatarHash
      ? `https://cdn.discordapp.com/avatars/${raw.id}/${avatarHash}.${avatarHash.startsWith("a_") ? "gif" : "png"}?size=128`
      : null;
    return {
      id: raw.id,
      username: raw.username,
      globalName: raw.global_name ?? null,
      avatarHash,
      avatarUrl,
      rawProfile: raw,
    };
  } catch (error) {
    if (error instanceof AppError) throw error;
    throw new AppError(502, "OAUTH_PROVIDER_ERROR", "Discord returned an invalid response");
  } finally {
    if (accessToken) {
      await fetchImpl("https://discord.com/api/oauth2/token/revoke", {
        method: "POST",
        headers: {
          authorization: `Basic ${credentials}`,
          "content-type": "application/x-www-form-urlencoded",
        },
        body: new URLSearchParams({ token: accessToken, token_type_hint: "access_token" }),
        signal: AbortSignal.timeout(5_000),
      }).then((response) => response.body?.cancel()).catch(() => undefined);
    }
  }
}
