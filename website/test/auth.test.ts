import { readFile } from "node:fs/promises";
import { resolve } from "node:path";
import { beforeEach, describe, expect, it } from "vitest";
import {
  clearSessionCookie,
  oauthCookie,
  readOAuthAttempt,
  sessionCookie,
} from "../src/server/auth-cookies";
import {
  equalState,
  pkceChallenge,
  requiredServerEnv,
  safePortalReturn,
} from "../src/server/oauth";

beforeEach(() => {
  process.env.SESSION_SECRET = "s".repeat(32);
  process.env.WEBSITE_URL = "https://play.example.com";
  process.env.API_URL = "https://api.example.com";
  process.env.WEBSITE_API_SECRET = "a".repeat(32);
  process.env.DISCORD_OAUTH_CLIENT_ID = "123456789012345678";
  process.env.DISCORD_OAUTH_REDIRECT_URI = "https://play.example.com/auth/discord/callback";
});

describe("website auth boundary", () => {
  it("creates an RFC 7636 S256 challenge and uses constant-time state comparison", () => {
    expect(pkceChallenge("dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk")).toBe(
      "E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM",
    );
    expect(equalState("same-state", "same-state")).toBe(true);
    expect(equalState("one", "two")).toBe(false);
  });

  it("signs, verifies, and expires the HttpOnly OAuth attempt cookie", () => {
    const now = Date.now();
    const value = oauthCookie({ state: "state", verifier: "verifier", expiresAt: now + 60_000 });
    expect(value).toContain("HttpOnly");
    expect(value).toContain("Secure");
    expect(value).toContain("SameSite=Lax");
    expect(readOAuthAttempt(value)).toMatchObject({ state: "state", verifier: "verifier" });
    const prefix = "__Host-kairu_oauth=";
    const first = value.charAt(prefix.length);
    const tampered = `${prefix}${first === "A" ? "B" : "A"}${value.slice(prefix.length + 1)}`;
    expect(readOAuthAttempt(tampered)).toBeNull();
    const expired = oauthCookie({ state: "state", verifier: "verifier", expiresAt: now - 1 });
    expect(readOAuthAttempt(expired)).toBeNull();
  });

  it("uses hardened host-only session cookies and clears with identical attributes", () => {
    expect(sessionCookie("opaque")).toBe(
      "__Host-kairu_session=opaque; HttpOnly; Secure; SameSite=Lax; Path=/; Max-Age=43200; Priority=High",
    );
    expect(clearSessionCookie()).toContain("Max-Age=0");
    expect(sessionCookie("opaque")).not.toContain("Domain=");
  });

  it("rejects callback and API URL configuration drift and open redirects", () => {
    expect(requiredServerEnv().redirectUri).toBe("https://play.example.com/auth/discord/callback");
    process.env.DISCORD_OAUTH_REDIRECT_URI = "https://evil.example/callback";
    expect(() => requiredServerEnv()).toThrow(/exact WEBSITE_URL/);
    expect(safePortalReturn("https://evil.example")).toBe("/portal");
    expect(safePortalReturn("//evil.example/portal")).toBe("/portal");
    expect(safePortalReturn("/portal/settings?tab=security")).toBe("/portal/settings?tab=security");
  });

  it("contains no localStorage auth or browser-side OAuth/client secrets and protects portal before rendering", async () => {
    const root = resolve(import.meta.dirname, "..");
    const auth = await readFile(resolve(root, "src/lib/auth.tsx"), "utf8");
    const login = await readFile(resolve(root, "src/routes/login.tsx"), "utf8");
    const portal = await readFile(resolve(root, "src/routes/portal.tsx"), "utf8");
    const callback = await readFile(resolve(root, "src/routes/auth.discord.callback.ts"), "utf8");
    expect(`${auth}${login}${portal}`).not.toMatch(/localStorage|sessionStorage/);
    expect(`${auth}${login}`).not.toMatch(/DISCORD_OAUTH_CLIENT_SECRET|WEBSITE_API_SECRET/);
    expect(callback).not.toMatch(/access_token|refresh_token/);
    expect(portal).toMatch(/beforeLoad:[\s\S]*getSession\(\)[\s\S]*redirect/);
  });
});
