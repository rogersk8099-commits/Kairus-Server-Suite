import { createFileRoute } from "@tanstack/react-router";
import { centralAuth } from "@/server/control-plane";
import { oauthCookie } from "@/server/auth-cookies";
import { pkceChallenge, randomBase64Url, requiredServerEnv } from "@/server/oauth";

export const Route = createFileRoute("/auth/discord")({
  server: {
    handlers: {
      GET: async () => {
        const env = requiredServerEnv();
        const state = randomBase64Url();
        const verifier = randomBase64Url(48);
        await centralAuth("/internal/auth/oauth/states", { state, redirectUri: env.redirectUri });
        const authorize = new URL("https://discord.com/oauth2/authorize");
        authorize.search = new URLSearchParams({
          response_type: "code",
          client_id: env.clientId,
          scope: "identify",
          state,
          redirect_uri: env.redirectUri,
          code_challenge: pkceChallenge(verifier),
          code_challenge_method: "S256",
        }).toString();
        return new Response(null, {
          status: 302,
          headers: {
            "Cache-Control": "no-store",
            Location: authorize.toString(),
            "Set-Cookie": oauthCookie({ state, verifier, expiresAt: Date.now() + 10 * 60 * 1_000 }),
          },
        });
      },
    },
  },
});
