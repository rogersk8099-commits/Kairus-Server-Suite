import pino, { type Logger } from "pino";
import type { AppConfig } from "./config.js";

export function createLogger(config: Pick<AppConfig, "LOG_LEVEL">): Logger {
  return pino({
    level: config.LOG_LEVEL,
    base: { service: "kairu-discord-bot" },
    redact: {
      paths: [
        "*.token", "*.secret", "*.password", "*.authorization", "*.cookie", "*.signature",
        "token", "secret", "password", "authorization", "cookie", "signature",
        "req.headers.authorization", "req.headers.x-kairu-signature"
      ],
      censor: "[REDACTED]"
    },
    serializers: {
      err(error: Error) { return { name: error.name, message: error.message, stack: error.stack }; }
    }
  });
}

export type AppLogger = Logger;
