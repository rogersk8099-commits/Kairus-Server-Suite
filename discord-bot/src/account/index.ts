import type {
  CommandInteraction,
  CommandModule,
  CommandRegistry,
  CommandServices,
  ComponentInteraction,
  SlashCommandDefinition
} from "./interfaces.js";
import { playerCommands } from "./player-commands.js";
import { staffCommands } from "./staff-commands.js";
import { respondServiceFailure } from "./utils.js";

/** All modules are standalone exports as well as members of this convenience registry. */
export { linkMinecraftCommand, unlinkMinecraftCommand, profileCommand, serverStatusCommand, onlineCommand, playerCommand, playerCommands } from "./player-commands.js";
export { syncRolesCommand, syncMembershipsCommand, maintenanceCommand, announceCommand, configCommand, staffCommands } from "./staff-commands.js";
export * from "./interfaces.js";

export const commandModules: readonly CommandModule[] = [...playerCommands, ...staffCommands];

function assertUniqueCommandNames(modules: readonly CommandModule[]): void {
  const names = new Set<string>();
  for (const module of modules) {
    if (names.has(module.definition.name)) throw new Error(`Duplicate Discord command definition: ${module.definition.name}`);
    names.add(module.definition.name);
  }
}
assertUniqueCommandNames(commandModules);

/** Plain JSON definitions suitable for REST registration without discord.js coupling. */
export const slashCommandDefinitions: readonly SlashCommandDefinition[] = commandModules.map((module) => module.definition);

export function createCommandRegistry(modules: readonly CommandModule[] = commandModules): CommandRegistry {
  assertUniqueCommandNames(modules);
  return new Map(modules.map((module) => [module.definition.name, module]));
}

/**
 * Calls exactly one modular handler. The caller can use the boolean result to
 * decide whether to report an unknown interaction.
 */
export async function dispatchCommand(
  interaction: CommandInteraction,
  services: CommandServices,
  registry: CommandRegistry = createCommandRegistry()
): Promise<boolean> {
  const command = registry.get(interaction.commandName);
  if (!command) return false;
  try {
    await command.execute(interaction, services);
  } catch (error) {
    // A module should normally catch its own service failures. This boundary
    // prevents unhandled rejections if an adapter or unexpected input throws.
    await respondServiceFailure(interaction, services.logger, interaction.commandName, error);
  }
  return true;
}

/** Route button interactions such as /unlink-minecraft confirmation safely. */
export async function dispatchComponent(
  interaction: ComponentInteraction,
  services: CommandServices,
  modules: readonly CommandModule[] = commandModules
): Promise<boolean> {
  for (const module of modules) {
    if (!module.handleComponent) continue;
    try {
      if (await module.handleComponent(interaction, services)) return true;
    } catch (error) {
      services.logger?.error("Discord component dispatch failed", error instanceof Error ? error.name : "unknown error");
      if (!interaction.deferred && !interaction.replied) {
        await interaction.reply({ ephemeral: true, content: "I could not complete that request right now. Please try again later.", allowedMentions: { parse: [] } });
      }
      return true;
    }
  }
  return false;
}

/**
 * Adapter example:
 *
 * client.on(Events.InteractionCreate, async (raw) => {
 *   if (raw.isChatInputCommand()) await dispatchCommand(raw as unknown as CommandInteraction, services);
 *   if (raw.isButton()) await dispatchComponent(raw as unknown as ComponentInteraction, services);
 * });
 *
 * The command package has no Discord token, password, or secret configuration.
 */
