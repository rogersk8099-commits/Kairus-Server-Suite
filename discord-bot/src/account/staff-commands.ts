import type { CommandInteraction, CommandModule, ConfigSetting, PublicGuildConfig } from "./interfaces.js";
import { COLORS, DISCORD_PERMISSIONS } from "./interfaces.js";
import {
  cleanPlainText,
  deferEphemeral,
  escapeDiscord,
  formatNumber,
  guildOnlyDefinition,
  hasPermission,
  isGuildInteraction,
  isSnowflake,
  respond,
  respondServiceFailure,
  stableList
} from "./utils.js";

function guildAndPermission(interaction: CommandInteraction, permission: "ManageGuild" | "ManageRoles" | "Administrator"): string | null {
  if (!isGuildInteraction(interaction) || !interaction.guildId) return "This command is only available in a server.";
  if (!hasPermission(interaction, permission)) return `You need the **${permission === "ManageGuild" ? "Manage Server" : permission === "ManageRoles" ? "Manage Roles" : "Administrator"}** permission to use this command.`;
  return null;
}

function configEmbed(config: PublicGuildConfig) {
  return {
    color: COLORS.BRAND,
    title: "Kairu server configuration",
    description: "Only safe, user-facing settings are shown. Credentials, tokens, and other secrets are never exposed here.",
    fields: [
      { name: "Profile visibility", value: escapeDiscord(config.profileVisibility, 32), inline: true },
      { name: "Announcement channel", value: config.announcementChannelId ? `<#${config.announcementChannelId}>` : "Not configured", inline: true },
      { name: "Maintenance message", value: escapeDiscord(config.maintenanceMessage ?? "Not configured", 512), inline: false }
    ]
  };
}

export const syncRolesCommand: CommandModule = {
  definition: guildOnlyDefinition({
    name: "sync-roles",
    description: "Synchronize mapped Discord roles for a linked member",
    default_member_permissions: DISCORD_PERMISSIONS.MANAGE_ROLES,
    options: [{ type: 6, name: "member", description: "Linked member to synchronize", required: true }]
  }),
  async execute(interaction, services) {
    await deferEphemeral(interaction);
    const denial = guildAndPermission(interaction, "ManageRoles");
    if (denial) {
      await respond(interaction, { ephemeral: true, content: denial });
      return;
    }
    const member = interaction.options.getUser("member", true);
    if (!member || member.bot || !isSnowflake(member.id)) {
      await respond(interaction, { ephemeral: true, content: "Choose a valid non-bot server member." });
      return;
    }
    try {
      const link = await services.links.getLinkByDiscordUser(member.id);
      if (!link) {
        await respond(interaction, { ephemeral: true, content: `**${escapeDiscord(member.username, 80)}** does not have a linked Minecraft account.` });
        return;
      }
      const result = await services.roles.syncRoles({ guildId: interaction.guildId!, discordUserId: member.id, actorDiscordUserId: interaction.user.id });
      const changes = [
        result.addedRoleNames.length ? `Added: ${stableList(result.addedRoleNames)}` : "",
        result.removedRoleNames.length ? `Removed: ${stableList(result.removedRoleNames)}` : "",
        result.unchangedRoleNames?.length ? `Already correct: ${stableList(result.unchangedRoleNames)}` : ""
      ].filter(Boolean);
      await respond(interaction, {
        ephemeral: true,
        embeds: [{
          color: COLORS.SUCCESS,
          title: "Roles synchronized",
          description: `Updated **${escapeDiscord(member.username, 80)}** (${escapeDiscord(link.minecraftUsername, 16)}).`,
          fields: [{ name: "Result", value: changes.join("\n") || "No role changes were needed." }]
        }]
      });
    } catch (error) {
      await respondServiceFailure(interaction, services.logger, "sync-roles", error);
    }
  }
};

export const syncMembershipsCommand: CommandModule = {
  definition: guildOnlyDefinition({
    name: "sync-memberships",
    description: "Bulk synchronize linked-member membership roles",
    default_member_permissions: DISCORD_PERMISSIONS.MANAGE_ROLES,
    options: [
      { type: 5, name: "dry-run", description: "Preview without applying changes", required: false },
      { type: 4, name: "limit", description: "Maximum members to process (1–1000)", required: false, min_value: 1, max_value: 1_000 }
    ]
  }),
  async execute(interaction, services) {
    await deferEphemeral(interaction);
    const denial = guildAndPermission(interaction, "ManageRoles");
    if (denial) {
      await respond(interaction, { ephemeral: true, content: denial });
      return;
    }
    const dryRun = interaction.options.getBoolean("dry-run", false) ?? false;
    const limit = interaction.options.getInteger("limit", false) ?? 100;
    if (!Number.isInteger(limit) || limit < 1 || limit > 1_000) {
      await respond(interaction, { ephemeral: true, content: "Limit must be a whole number from 1 to 1,000." });
      return;
    }
    try {
      const result = await services.memberships.syncMemberships({ guildId: interaction.guildId!, actorDiscordUserId: interaction.user.id, dryRun, limit });
      await respond(interaction, {
        ephemeral: true,
        embeds: [{
          color: result.failed ? COLORS.WARNING : COLORS.SUCCESS,
          title: dryRun ? "Membership sync preview" : "Membership sync complete",
          fields: [
            { name: "Examined", value: formatNumber(result.examined), inline: true },
            { name: dryRun ? "Would update" : "Updated", value: formatNumber(result.updated), inline: true },
            { name: "Skipped", value: formatNumber(result.skipped), inline: true },
            { name: "Failed", value: formatNumber(result.failed), inline: true }
          ]
        }]
      });
    } catch (error) {
      await respondServiceFailure(interaction, services.logger, "sync-memberships", error);
    }
  }
};

export const maintenanceCommand: CommandModule = {
  definition: guildOnlyDefinition({
    name: "maintenance",
    description: "Enable or disable server maintenance mode",
    default_member_permissions: DISCORD_PERMISSIONS.ADMINISTRATOR,
    options: [
      { type: 5, name: "enabled", description: "Whether maintenance mode is enabled", required: true },
      { type: 3, name: "reason", description: "Optional player-facing maintenance reason", required: false, max_length: 256 }
    ]
  }),
  async execute(interaction, services) {
    await deferEphemeral(interaction);
    const denial = guildAndPermission(interaction, "Administrator");
    if (denial) {
      await respond(interaction, { ephemeral: true, content: denial });
      return;
    }
    const enabled = interaction.options.getBoolean("enabled", true);
    if (enabled === null) {
      await respond(interaction, { ephemeral: true, content: "Choose whether maintenance mode should be enabled." });
      return;
    }
    const rawReason = interaction.options.getString("reason", false);
    const reason = rawReason === null ? null : cleanPlainText(rawReason, 256);
    if (rawReason !== null && !reason) {
      await respond(interaction, { ephemeral: true, content: "Reason must contain 1–256 non-control characters." });
      return;
    }
    if (!enabled && reason) {
      await respond(interaction, { ephemeral: true, content: "A maintenance reason can only be set when enabling maintenance mode." });
      return;
    }
    try {
      const result = await services.control.setMaintenance({ guildId: interaction.guildId!, enabled, reason, actorDiscordUserId: interaction.user.id });
      await respond(interaction, {
        ephemeral: true,
        embeds: [{
          color: result.enabled ? COLORS.WARNING : COLORS.SUCCESS,
          title: result.enabled ? "Maintenance mode enabled" : "Maintenance mode disabled",
          description: result.enabled && reason ? `Reason: ${escapeDiscord(reason, 256)}` : "The server maintenance setting has been updated.",
          footer: { text: `Changed ${new Date(result.changedAt).toLocaleString("en-US", { timeZone: "UTC", timeZoneName: "short" })}` }
        }]
      });
    } catch (error) {
      await respondServiceFailure(interaction, services.logger, "maintenance", error);
    }
  }
};

export const announceCommand: CommandModule = {
  definition: guildOnlyDefinition({
    name: "announce",
    description: "Send a safe in-game announcement",
    default_member_permissions: DISCORD_PERMISSIONS.MANAGE_GUILD,
    options: [{ type: 3, name: "message", description: "Announcement text (up to 512 characters)", required: true, min_length: 1, max_length: 512 }]
  }),
  async execute(interaction, services) {
    await deferEphemeral(interaction);
    const denial = guildAndPermission(interaction, "ManageGuild");
    if (denial) {
      await respond(interaction, { ephemeral: true, content: denial });
      return;
    }
    const rawMessage = interaction.options.getString("message", true) ?? "";
    const message = cleanPlainText(rawMessage, 512);
    if (!message) {
      await respond(interaction, { ephemeral: true, content: "Announcement text must contain 1–512 non-control characters." });
      return;
    }
    try {
      const result = await services.control.announce({ guildId: interaction.guildId!, message, actorDiscordUserId: interaction.user.id });
      await respond(interaction, {
        ephemeral: true,
        embeds: [{
          color: COLORS.SUCCESS,
          title: "Announcement queued",
          description: "The announcement was handed to the server safely.",
          fields: [{ name: "Delivered", value: formatNumber(result.delivered), inline: true }]
        }]
      });
    } catch (error) {
      await respondServiceFailure(interaction, services.logger, "announce", error);
    }
  }
};

function parseConfigUpdate(setting: string, rawValue: string): { setting: ConfigSetting; value: string } | null {
  switch (setting) {
    case "profile_visibility": {
      const value = rawValue.trim().toLowerCase();
      return value === "public" || value === "members" ? { setting, value } : null;
    }
    case "announcement_channel": {
      const value = rawValue.trim();
      return value === "clear" || isSnowflake(value) ? { setting, value } : null;
    }
    case "maintenance_message": {
      const value = rawValue.trim().toLowerCase() === "clear" ? "clear" : cleanPlainText(rawValue, 512);
      return value ? { setting, value } : null;
    }
    default:
      return null;
  }
}

export const configCommand: CommandModule = {
  definition: guildOnlyDefinition({
    name: "config",
    description: "View or change safe Kairu server settings",
    default_member_permissions: DISCORD_PERMISSIONS.MANAGE_GUILD,
    options: [
      {
        type: 3,
        name: "setting",
        description: "Setting to update; omit to view settings",
        required: false,
        choices: [
          { name: "Profile visibility", value: "profile_visibility" },
          { name: "Announcement channel", value: "announcement_channel" },
          { name: "Maintenance message", value: "maintenance_message" }
        ]
      },
      { type: 3, name: "value", description: "New value; use clear where supported", required: false, max_length: 512 }
    ]
  }),
  async execute(interaction, services) {
    await deferEphemeral(interaction);
    const denial = guildAndPermission(interaction, "ManageGuild");
    if (denial) {
      await respond(interaction, { ephemeral: true, content: denial });
      return;
    }
    const setting = interaction.options.getString("setting", false);
    const rawValue = interaction.options.getString("value", false);
    if (!setting && rawValue !== null) {
      await respond(interaction, { ephemeral: true, content: "Choose a setting before providing a value." });
      return;
    }
    if (setting && rawValue === null) {
      await respond(interaction, { ephemeral: true, content: "Provide a value for the selected setting." });
      return;
    }
    try {
      if (!setting) {
        await respond(interaction, { ephemeral: true, embeds: [configEmbed(await services.config.getPublicGuildConfig(interaction.guildId!))] });
        return;
      }
      const update = parseConfigUpdate(setting, rawValue!);
      if (!update) {
        await respond(interaction, {
          ephemeral: true,
          content: "Invalid setting value. Profile visibility must be `public` or `members`; channel must be a Discord ID or `clear`; maintenance message must be 1–512 characters or `clear`."
        });
        return;
      }
      const config = await services.config.updatePublicGuildConfig({ guildId: interaction.guildId!, actorDiscordUserId: interaction.user.id, ...update });
      await respond(interaction, { ephemeral: true, embeds: [configEmbed(config)] });
    } catch (error) {
      await respondServiceFailure(interaction, services.logger, "config", error);
    }
  }
};

export const staffCommands: readonly CommandModule[] = [
  syncRolesCommand,
  syncMembershipsCommand,
  maintenanceCommand,
  announceCommand,
  configCommand
];
