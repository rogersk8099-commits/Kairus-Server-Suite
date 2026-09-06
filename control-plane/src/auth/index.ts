import type { AppConfig } from "../types.js";
import type { AuthStore } from "./types.js";
import { MemoryAuthStore } from "./memory-auth-store.js";
import { PostgresAuthStore } from "./postgres-auth-store.js";

export function createAuthStore(config: AppConfig): AuthStore {
  return config.databaseUrl ? new PostgresAuthStore(config.databaseUrl) : new MemoryAuthStore();
}

export { MemoryAuthStore } from "./memory-auth-store.js";
export { PostgresAuthStore } from "./postgres-auth-store.js";
