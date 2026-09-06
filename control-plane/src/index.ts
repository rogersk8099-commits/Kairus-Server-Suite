import type { Client } from "discord.js";
import { buildApp } from "./app.js";
import { createAuthStore } from "./auth/index.js";
import { startDiscordBot } from "./bot/discord-bot.js";
import { loadConfig } from "./config.js";
import { createStore } from "./db/index.js";

async function main() {
  const config = loadConfig();
  const store = createStore(config);
  const authStore = createAuthStore(config);
  const app = buildApp(config, store, authStore);
  let bot: Client | undefined;
  let shuttingDown = false;

  const shutdown = async (signal: string) => {
    if (shuttingDown) return;
    shuttingDown = true;
    app.log.info({ signal }, "graceful shutdown started");
    const forceTimer = setTimeout(() => process.exit(1), 10_000);
    forceTimer.unref();
    try {
      bot?.destroy();
      await app.close();
      await Promise.all([store.close(), authStore.close()]);
      app.log.info("graceful shutdown complete");
      process.exitCode = 0;
    } catch (error) {
      app.log.error({ err: error }, "graceful shutdown failed");
      process.exitCode = 1;
    }
  };
  process.once("SIGINT", () => void shutdown("SIGINT"));
  process.once("SIGTERM", () => void shutdown("SIGTERM"));

  await app.listen({ host: config.host, port: config.port });
  app.log.info({ storage: store.kind, authStorage: authStore.kind, port: config.port }, "Kairu control-plane API listening");
  if (config.discordBotToken) {
    bot = await startDiscordBot(config, store);
    app.log.info("Discord bot startup requested");
  } else {
    app.log.info("DISCORD_BOT_TOKEN is absent; API is running without Discord bot");
  }
}

main().catch((error: unknown) => {
  console.error("Control-plane startup failed", error);
  process.exitCode = 1;
});
