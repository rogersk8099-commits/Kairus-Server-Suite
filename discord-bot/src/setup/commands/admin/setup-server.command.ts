import {
  PermissionFlagsBits,
  SlashCommandBuilder,
  type ChatInputCommandInteraction,
  type Client
} from "discord.js";
import { SERVER_BLUEPRINT, expectedResourceCount } from "../../config/server-blueprint.js";
import { SETUP_COMMAND_DEFAULT_MEMBER_PERMISSIONS } from "../../permissions/policy.js";
import { DiscordJsGuildSetupAdapter, type ServerSetupResult, type ServerSetupService } from "../../services/server-setup.service.js";

export const SETUP_SERVER_COMMAND = new SlashCommandBuilder()
  .setName("setup-server")
  .setDescription("Preview or apply the idempotent Kairu server layout.")
  .setDefaultMemberPermissions(SETUP_COMMAND_DEFAULT_MEMBER_PERMISSIONS)
  .setDMPermission(false)
  .addBooleanOption((option) => option
    .setName("confirm")
    .setDescription("Apply changes. Leave false to receive a safe preview.")
    .setRequired(false));

export interface SetupServerCommandDependencies {
  readonly setupService: ServerSetupService;
  readonly client?: Client;
}

export async function handleSetupServerCommand(
  interaction: ChatInputCommandInteraction,
  dependencies: SetupServerCommandDependencies
): Promise<void> {
  if (!interaction.inGuild() || !interaction.guild) {
    await interaction.reply({ ephemeral: true, content: "This command can only be used inside a server." });
    return;
  }
  if (!interaction.memberPermissions?.has(PermissionFlagsBits.ManageGuild)) {
    await interaction.reply({ ephemeral: true, content: "Only staff with the **Manage Server** permission can run this command." });
    return;
  }

  const confirmed = interaction.options.getBoolean("confirm") === true;
  if (!confirmed) {
    await interaction.reply({ ephemeral: true, content: buildSetupConfirmationSummary() });
    return;
  }

  await interaction.deferReply({ ephemeral: true });
  try {
    const result = await dependencies.setupService.setup(new DiscordJsGuildSetupAdapter(interaction.guild));
    await interaction.editReply(formatSetupResult(result));
  } catch (error) {
    const message = error instanceof Error ? error.message : "Unknown setup failure.";
    await interaction.editReply(`**Kairu setup did not start.** ${message}`);
  }
}

/** The preview is intentionally a confirmation checkpoint; no Discord mutation occurs until confirm:true. */
export function buildSetupConfirmationSummary(): string {
  const textChannels = SERVER_BLUEPRINT.channels.filter((channel) => channel.type === "text-channel").length;
  const voiceChannels = SERVER_BLUEPRINT.channels.filter((channel) => channel.type === "voice-channel").length;
  return [
    "**Kairu server setup preview**",
    `This idempotent operation will reconcile **${SERVER_BLUEPRINT.roles.length} roles**, **${SERVER_BLUEPRINT.categories.length} categories**, **${textChannels} text channels**, and **${voiceChannels} voice channels** (${expectedResourceCount()} resources).`,
    "Existing resources are reused by stable database key and, if that key is stale, a unique exact name match. It will not create duplicates.",
    "Only Kairu-managed staff/premium permission overwrites are changed; all other channel overwrites are retained.",
    "Run `/setup-server confirm:true` to apply it. The bot must have Manage Roles and Manage Channels, and its highest role must sit above every existing Kairu role."
  ].join("\n");
}

export function formatSetupResult(result: ServerSetupResult): string {
  const failures = result.items.filter((item) => item.action === "failed");
  const lines = [
    "**Kairu server setup complete**",
    `Created: **${result.counts.created}** | Reused: **${result.counts.reused}** | Updated: **${result.counts.updated}** | Failed: **${result.counts.failed}**.`
  ];
  if (failures.length > 0) {
    lines.push("**Failures:**");
    lines.push(...failures.slice(0, 10).map((item) => `• ${item.key}: ${item.detail ?? "unknown failure"}`));
    if (failures.length > 10) lines.push(`• …and ${failures.length - 10} more.`);
  }
  return lines.join("\n");
}
