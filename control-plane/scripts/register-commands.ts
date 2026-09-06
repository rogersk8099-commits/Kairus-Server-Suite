import { REST, Routes } from "discord.js";
import { loadConfig } from "../src/config.js";
import { slashCommands } from "../src/bot/discord-bot.js";

async function main() {
  const config = loadConfig();
  if (!config.discordBotToken || !config.discordApplicationId) throw new Error("DISCORD_BOT_TOKEN and DISCORD_APPLICATION_ID are required to register slash commands");
  const rest = new REST({ version: "10" }).setToken(config.discordBotToken);
  if (config.discordGuildId) {
    await rest.put(Routes.applicationGuildCommands(config.discordApplicationId, config.discordGuildId), { body: slashCommands });
    console.log(`Registered ${slashCommands.length} slash commands in guild ${config.discordGuildId}.`);
  } else {
    await rest.put(Routes.applicationCommands(config.discordApplicationId), { body: slashCommands });
    console.log(`Registered ${slashCommands.length} global slash commands. Propagation may take up to one hour.`);
  }
}

main().catch((error: unknown) => { console.error(error); process.exitCode = 1; });
