import { randomUUID } from "node:crypto";
import type { AppConfig } from "./config.js";
import type { AppLogger } from "./logger.js";

const transientStatuses = new Set([408, 425, 429, 500, 502, 503, 504]);

export class ApiError extends Error {
  public constructor(message: string, public readonly status: number | undefined, public readonly retryable: boolean) {
    super(message);
    this.name = "ApiError";
  }
}

export class ControlPlaneApi {
  public constructor(private readonly config: AppConfig, private readonly logger: AppLogger, private readonly fetcher: typeof fetch = fetch) {}

  public get<T>(path: string, headers?: Record<string, string>): Promise<T> { return this.request<T>(path, { method: "GET", headers }); }
  public post<T>(path: string, body: unknown, idempotencyKey = randomUUID()): Promise<T> {
    return this.request<T>(path, { method: "POST", body: JSON.stringify(body), headers: { "content-type": "application/json", "idempotency-key": idempotencyKey } });
  }

  public async request<T>(path: string, init: RequestInit): Promise<T> {
    if (!path.startsWith("/")) throw new TypeError("Control-plane API path must begin with '/'");
    const method = init.method ?? "GET";
    for (let attempt = 0; ; attempt += 1) {
      try {
        const headers = new Headers(init.headers);
        headers.set("accept", "application/json");
        headers.set("authorization", `Bearer ${this.config.API_SECRET}`);
        headers.set("x-admin-api-key", this.config.API_SECRET);
        const response = await this.fetcher(new URL(path, `${this.config.API_URL}/`), {
          ...init,
          headers,
          signal: AbortSignal.timeout(this.config.API_TIMEOUT_MS)
        });
        if (!response.ok) {
          const retryable = transientStatuses.has(response.status);
          throw new ApiError(`Control plane returned HTTP ${response.status}`, response.status, retryable);
        }
        if (response.status === 204) return undefined as T;
        return await response.json() as T;
      } catch (error) {
        const typed = error instanceof ApiError ? error : new ApiError("Control plane request failed", undefined, true);
        if (!typed.retryable || attempt >= this.config.API_MAX_RETRIES) throw typed;
        const delay = Math.min(250 * (2 ** attempt) + Math.floor(Math.random() * 100), 5_000);
        this.logger.warn({ path, method, attempt: attempt + 1, delay, status: typed.status }, "retrying control-plane request");
        await new Promise((resolve) => setTimeout(resolve, delay));
      }
    }
  }
}
