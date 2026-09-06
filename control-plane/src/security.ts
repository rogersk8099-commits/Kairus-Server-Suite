import { createHash, randomBytes, timingSafeEqual } from "node:crypto";

export function generateLinkCode(): string {
  // 20 random bytes encode to 27 URL-safe characters: high entropy and easy to paste in chat.
  return randomBytes(20).toString("base64url").toUpperCase();
}

export function hashLinkCode(code: string): string {
  return createHash("sha256").update(code).digest("hex");
}

export function safeSecretEquals(actual: string | undefined, expected: string | undefined): boolean {
  if (!actual || !expected) return false;
  const actualBuffer = Buffer.from(actual);
  const expectedBuffer = Buffer.from(expected);
  if (actualBuffer.length !== expectedBuffer.length) return false;
  return timingSafeEqual(actualBuffer, expectedBuffer);
}

export function getBearerToken(header: string | undefined): string | undefined {
  if (!header) return undefined;
  const match = /^Bearer\s+(.+)$/i.exec(header);
  return match?.[1];
}
