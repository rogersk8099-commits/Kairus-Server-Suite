import "@tanstack/react-start/server-only";
import { createHash, randomBytes, timingSafeEqual } from "node:crypto";

export function randomBase64Url(bytes = 32): string {
  return randomBytes(bytes).toString("base64url");
}
export function pkceChallenge(verifier: string): string {
  return createHash("sha256").update(verifier, "ascii").digest("base64url");
}

export function equalState(actual: string, expected: string): boolean {
  const left = Buffer.from(actual);
  const right = Buffer.from(expected);
  return left.length === right.length && timingSafeEqual(left, right);
}

export function requiredServerEnv() {
  const values = {
    clientId: process.env["DISCORD_OAUTH_CLIENT_ID"],
    redirectUri: process.env["DISCORD_OAUTH_REDIRECT_URI"],
    apiUrl: process.env["API_URL"],
    websiteApiSecret: process.env["WEBSITE_API_SECRET"],
    websiteUrl: process.env["WEBSITE_URL"],
  };
  if (
    !values.clientId ||
    !values.redirectUri ||
    !values.apiUrl ||
    !values.websiteApiSecret ||
    !values.websiteUrl
  )
    throw new Error("Website authentication environment is incomplete");
  const origin = new URL(values.websiteUrl).origin;
  if (values.websiteUrl !== origin || values.redirectUri !== `${origin}/auth/discord/callback`)
    throw new Error("OAuth callback is not the exact WEBSITE_URL callback");
  if (new URL(values.apiUrl).origin !== values.apiUrl.replace(/\/$/, ""))
    throw new Error("API_URL must be an origin without a path");
  return {
    clientId: values.clientId,
    redirectUri: values.redirectUri,
    apiUrl: values.apiUrl.replace(/\/$/, ""),
    websiteApiSecret: values.websiteApiSecret,
    websiteUrl: origin,
  };
}

export function safePortalReturn(value: string | null): string {
  if (!value || !value.startsWith("/portal") || value.startsWith("//") || value.includes("\\"))
    return "/portal";
  try {
    const url = new URL(value, "https://local.invalid");
    return url.origin === "https://local.invalid" && url.pathname.startsWith("/portal")
      ? `${url.pathname}${url.search}${url.hash}`
      : "/portal";
  } catch {
    return "/portal";
  }
}
