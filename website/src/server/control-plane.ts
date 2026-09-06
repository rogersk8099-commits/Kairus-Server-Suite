import "@tanstack/react-start/server-only";
import { requiredServerEnv } from "./oauth";

export class CentralAuthError extends Error {
  constructor(
    public readonly status: number,
    public readonly code: string,
    message: string,
  ) {
    super(message);
  }
}

export async function centralAuth<T>(path: string, body: Record<string, unknown>): Promise<T> {
  const env = requiredServerEnv();
  let response: Response;
  try {
    response = await fetch(`${env.apiUrl}${path}`, {
      method: "POST",
      headers: {
        accept: "application/json",
        authorization: `Bearer ${env.websiteApiSecret}`,
        "content-type": "application/json",
      },
      body: JSON.stringify(body),
      signal: AbortSignal.timeout(10_000),
    });
  } catch {
    throw new CentralAuthError(
      503,
      "AUTH_UNAVAILABLE",
      "Authentication service is temporarily unavailable",
    );
  }
  let payload: unknown;
  try {
    payload = await response.json();
  } catch {
    payload = null;
  }
  if (!response.ok) {
    const error =
      payload && typeof payload === "object" && "error" in payload
        ? (payload as { error?: { code?: string } }).error
        : undefined;
    throw new CentralAuthError(
      response.status,
      error?.code ?? "AUTH_FAILED",
      "Authentication request failed",
    );
  }
  return payload as T;
}
