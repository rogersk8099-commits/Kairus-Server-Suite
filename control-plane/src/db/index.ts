import type { AppConfig, ControlPlaneStore } from "../types.js";
import { MemoryStore } from "./memory-store.js";
import { PostgresStore } from "./postgres-store.js";

export function createStore(config: AppConfig): ControlPlaneStore {
  return config.databaseUrl ? new PostgresStore(config.databaseUrl) : new MemoryStore();
}

export { MemoryStore } from "./memory-store.js";
export { PostgresStore } from "./postgres-store.js";
