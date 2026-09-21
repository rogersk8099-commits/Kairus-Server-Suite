import { Client, Events, GatewayIntentBits } from "discord.js";
import { ControlPlaneApi } from "./api-client.js";
import { createAccountServices } from "./services/account-adapters.js";
import { createCommandRegistry, dispatchCommand, dispatchComponent } from "./commands.js";
import { loadConfig } from "./config.js";
import { prisma } from "./database.js";
import { buildHttpServer, type HealthState } from "./http/server.js";
import { PrismaIdempotencyStore } from "./idempotency.js";
import { createLogger } from "./logger.js";
import { YouTubeDataProvider } from "./providers.js";
import { RuntimeJobs } from "./runtime/jobs.js";
import { welcomeMember } from "./runtime/membership.js";
import { SetupResourceStore } from "./setup/prisma-setup.js";
import { ServerSetupService } from "./setup/services/server-setup.service.js";

const config = loadConfig();
const logger = createLogger(config);
const state: HealthState = { gateway: config.DISCORD_TOKEN ? "connecting" : "dormant", shuttingDown: false };
const client = new Client({ intents: [GatewayIntentBits.Guilds, GatewayIntentBits.GuildMembers] });
const idempotency = new PrismaIdempotencyStore(prisma);
const api = new ControlPlaneApi(config, logger.child({ component: "control-plane" }));
const account = createAccountServices(prisma, api, client, config, logger);
const setup = new ServerSetupService(new SetupResourceStore(prisma), undefined, { warn: (message, metadata) => logger.warn(metadata, message) });
const dependencies = { database: prisma, client, logger, account, setup };
const commands = createCommandRegistry(dependencies);
const jobs = new RuntimeJobs(prisma, client, config, api, { youtube: new YouTubeDataProvider(config.YOUTUBE_API_KEY) }, idempotency, logger.child({ component: "jobs" }));
const http = buildHttpServer({ config, database: prisma, idempotency, logger: logger.child({ component: "http" }), state, onMembership: async (userId) => { const member = await client.guilds.fetch(config.DISCORD_GUILD_ID).then((guild) => guild.members.fetch(userId)); await welcomeMember(prisma, member, logger); } });

client.once(Events.ClientReady, (ready) => { state.gateway = "ready"; logger.info({ userId: ready.user.id, guilds: ready.guilds.cache.size }, "Discord gateway ready"); jobs.start(); });
client.on(Events.ShardDisconnect, () => { state.gateway = "disconnected"; });
client.on(Events.ShardResume, () => { state.gateway = "ready"; });
client.on(Events.InteractionCreate, (interaction) => { if (interaction.isChatInputCommand()) void dispatchCommand(interaction, commands, logger); else if (interaction.isButton() || interaction.isAnySelectMenu() || interaction.isModalSubmit()) void dispatchComponent(interaction, dependencies); });
client.on(Events.GuildMemberAdd, (member) => { void welcomeMember(prisma, member, logger).catch((error) => logger.error({ err: error, guildId: member.guild.id, userId: member.id }, "welcome flow failed")); });
client.on(Events.Error, (error) => logger.error({ err: error }, "Discord client error"));

let shutdownPromise: Promise<void> | undefined;
async function shutdown(signal: string): Promise<void> {
  if (shutdownPromise) return shutdownPromise;
  state.shuttingDown = true;
  shutdownPromise = (async () => { logger.info({ signal }, "graceful shutdown started"); jobs.stop(); await client.destroy(); await http.close().catch((error: unknown) => logger.warn({ err: error }, "HTTP close failed")); await prisma.$disconnect(); logger.info({ signal }, "graceful shutdown complete"); })();
  return shutdownPromise;
}
process.once("SIGTERM", () => { void shutdown("SIGTERM").finally(() => process.exit(0)); });
process.once("SIGINT", () => { void shutdown("SIGINT").finally(() => process.exit(0)); });
process.on("unhandledRejection", (error) => logger.error({ err: error }, "unhandled rejection"));
process.on("uncaughtException", (error) => { logger.fatal({ err: error }, "uncaught exception"); void shutdown("uncaughtException").finally(() => process.exit(1)); });

async function main(): Promise<void> {
  await http.listen({ host: config.HOST, port: config.PORT });
  logger.info({ host: config.HOST, port: config.PORT, gateway: state.gateway }, "HTTP health/webhook server listening");
  if (!config.DISCORD_TOKEN) { logger.warn("DISCORD_TOKEN is absent; gateway is dormant while HTTP health remains active"); return; }
  await client.login(config.DISCORD_TOKEN);
}
void main().catch((error) => { logger.fatal({ err: error }, "startup failed"); void shutdown("startup_failure").finally(() => process.exit(1)); });
