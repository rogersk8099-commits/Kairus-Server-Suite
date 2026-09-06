import { createServerFn } from "@tanstack/react-start";
import { getRequestHeader, setResponseHeader } from "@tanstack/react-start/server";
import { z } from "zod";
import { centralAuth, CentralAuthError } from "@/server/control-plane";
import {
  clearSessionCookie,
  parseCookie,
  SESSION_COOKIE,
  sessionCookie,
} from "@/server/auth-cookies";

export type AuthUser = { id: string; displayName: string; avatarUrl: string | null };
export type PublicSession = {
  user: AuthUser;
  username: string;
  expiresAt: string;
  csrfToken: string;
} | null;
type CentralSession = { user: AuthUser; expiresAt: string; csrfToken: string };
function publicSession(value: CentralSession): NonNullable<PublicSession> {
  return { ...value, username: value.user.displayName };
}
type Redeemed = CentralSession & { sessionToken: string };

function noStore() {
  setResponseHeader("Cache-Control", "private, no-store");
  setResponseHeader("Vary", "Cookie");
}
function token(): string | null {
  return parseCookie(getRequestHeader("cookie") ?? null, SESSION_COOKIE);
}

export const getSession = createServerFn({ method: "GET" }).handler(
  async (): Promise<PublicSession> => {
    noStore();
    const sessionToken = token();
    if (!sessionToken) return null;
    try {
      return publicSession(
        await centralAuth<CentralSession>("/internal/auth/sessions/validate", { sessionToken }),
      );
    } catch (error) {
      if (error instanceof CentralAuthError && error.status === 401) {
        setResponseHeader("Set-Cookie", clearSessionCookie());
        return null;
      }
      throw error;
    }
  },
);

export const logout = createServerFn({ method: "POST" })
  .validator(z.object({ csrfToken: z.string().min(32).max(256) }))
  .handler(async ({ data }) => {
    noStore();
    const sessionToken = token();
    if (sessionToken)
      await centralAuth("/internal/auth/sessions/revoke", {
        sessionToken,
        csrfToken: data.csrfToken,
      }).catch(() => undefined);
    setResponseHeader("Set-Cookie", clearSessionCookie());
    return { ok: true };
  });

export const demoLogin = createServerFn({ method: "POST" })
  .validator(z.object({ displayName: z.string().trim().min(1).max(32) }))
  .handler(async ({ data }): Promise<PublicSession> => {
    noStore();
    if (process.env["NODE_ENV"] === "production") throw new Error("Not found");
    const issued = await centralAuth<{ ticket: string }>("/internal/auth/demo", data);
    const redeemed = await centralAuth<Redeemed>("/internal/auth/tickets/redeem", {
      ticket: issued.ticket,
    });
    setResponseHeader("Set-Cookie", sessionCookie(redeemed.sessionToken));
    return publicSession(redeemed);
  });
