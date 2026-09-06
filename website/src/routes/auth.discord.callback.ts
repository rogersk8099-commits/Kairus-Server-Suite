import { createFileRoute } from "@tanstack/react-router";
import { centralAuth } from "@/server/control-plane";
import {
  clearOAuthCookie,
  oauthCookie,
  readOAuthAttempt,
  sessionCookie,
} from "@/server/auth-cookies";
import { equalState, requiredServerEnv } from "@/server/oauth";

type Redeemed = { sessionToken: string; user: unknown; expiresAt: string; csrfToken: string };
const errorLocation = (code: string) => `/login?auth_error=${encodeURIComponent(code)}`;

export const Route = createFileRoute("/auth/discord/callback")({
  server: {
    handlers: {
      GET: async ({ request }) => {
        const url = new URL(request.url);
        const providerError = url.searchParams.get("error");
        const state = url.searchParams.get("state");
        const code = url.searchParams.get("code");
        const attempt = readOAuthAttempt(request.headers.get("cookie"));
        if (providerError)
          return new Response(null, {
            status: 302,
            headers: {
              Location: errorLocation(
                providerError === "access_denied" ? "access_denied" : "provider_error",
              ),
              "Set-Cookie": clearOAuthCookie(),
              "Cache-Control": "no-store",
            },
          });
        if (!attempt || !state || !code || !equalState(state, attempt.state))
          return new Response(null, {
            status: 302,
            headers: {
              Location: errorLocation("invalid_state"),
              "Set-Cookie": clearOAuthCookie(),
              "Cache-Control": "no-store",
            },
          });
        try {
          const env = requiredServerEnv();
          const result = await centralAuth<{ ticket: string }>("/internal/auth/discord/callback", {
            code,
            state,
            codeVerifier: attempt.verifier,
            redirectUri: env.redirectUri,
          });
          const redeemed = await centralAuth<Redeemed>("/internal/auth/tickets/redeem", {
            ticket: result.ticket,
          });
          const headers = new Headers({
            Location: "/portal",
            "Cache-Control": "no-store",
            "Referrer-Policy": "no-referrer",
          });
          headers.append("Set-Cookie", clearOAuthCookie());
          headers.append("Set-Cookie", sessionCookie(redeemed.sessionToken));
          return new Response(null, { status: 302, headers });
        } catch {
          return new Response(null, {
            status: 302,
            headers: {
              Location: errorLocation("callback_failed"),
              "Set-Cookie": clearOAuthCookie(),
              "Cache-Control": "no-store",
            },
          });
        }
      },
    },
  },
});
