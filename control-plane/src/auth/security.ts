import { createHmac, randomBytes, timingSafeEqual } from "node:crypto";

export function createOpaqueToken(bytes = 32): string {
  return randomBytes(bytes).toString("base64url");
}

export function hashAuthValue(secret: string, purpose: string, value: string): string {
  return createHmac("sha256", secret).update(`${purpose}\0${value}`, "utf8").digest("hex");
}

export function csrfTokenForSession(secret: string, sessionToken: string): string {
  return createHmac("sha256", secret).update(`csrf\0${sessionToken}`, "utf8").digest("base64url");
}

export function constantTimeEqual(actual: string, expected: string): boolean {
  const left = Buffer.from(actual, "utf8");
  const right = Buffer.from(expected, "utf8");
  return left.length === right.length && timingSafeEqual(left, right);
}
