import "@tanstack/react-start/server-only";
import { createHmac, timingSafeEqual } from "node:crypto";

export const SESSION_COOKIE = "__Host-kairu_session";
export const OAUTH_COOKIE = "__Host-kairu_oauth";
const SESSION_MAX_AGE = 12 * 60 * 60;
const OAUTH_MAX_AGE = 10 * 60;

type OAuthAttempt = { state: string; verifier: string; expiresAt: number };

export function parseCookie(header: string | null, name: string): string | null {
  if (!header) return null;
  for (const part of header.split(/;\s*/)) {
    const index = part.indexOf("=");
    if (index !== -1 && part.slice(0, index) === name) return part.slice(index + 1);
  }
  return null;
}

function cookie(name: string, value: string, maxAge: number): string {
  return `${name}=${value}; HttpOnly; Secure; SameSite=Lax; Path=/; Max-Age=${maxAge}; Priority=High`;
}

export function sessionCookie(token: string): string {
  return cookie(SESSION_COOKIE, token, SESSION_MAX_AGE);
}
export function clearSessionCookie(): string {
  return cookie(SESSION_COOKIE, "", 0);
}
export function clearOAuthCookie(): string {
  return cookie(OAUTH_COOKIE, "", 0);
}

function secret(): string {
  const value = process.env["SESSION_SECRET"];
  if (!value || value.length < 32) throw new Error("SESSION_SECRET must be at least 32 characters");
  return value;
}

function sign(payload: string): string {
  return createHmac("sha256", secret()).update(`website-oauth\0${payload}`).digest("base64url");
}

export function oauthCookie(attempt: OAuthAttempt): string {
  const payload = Buffer.from(JSON.stringify(attempt), "utf8").toString("base64url");
  return cookie(OAUTH_COOKIE, `${payload}.${sign(payload)}`, OAUTH_MAX_AGE);
}

export function readOAuthAttempt(header: string | null): OAuthAttempt | null {
  const raw = parseCookie(header, OAUTH_COOKIE);
  if (!raw) return null;
  const [payload, signature, extra] = raw.split(".");
  if (!payload || !signature || extra) return null;
  const expected = sign(payload);
  const left = Buffer.from(signature);
  const right = Buffer.from(expected);
  if (left.length !== right.length || !timingSafeEqual(left, right)) return null;
  try {
    const parsed = JSON.parse(
      Buffer.from(payload, "base64url").toString("utf8"),
    ) as Partial<OAuthAttempt>;
    if (
      typeof parsed.state !== "string" ||
      typeof parsed.verifier !== "string" ||
      typeof parsed.expiresAt !== "number" ||
      parsed.expiresAt <= Date.now()
    )
      return null;
    return { state: parsed.state, verifier: parsed.verifier, expiresAt: parsed.expiresAt };
  } catch {
    return null;
  }
}
