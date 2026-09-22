import type {
  CommandInteraction,
  CommandModule,
  CommandServices,
  ComponentInteraction,
  MinecraftLink,
  OnlinePlayer,
  PlayerProfile
} from "./interfaces.js";
import { COLORS } from "./interfaces.js";
import {
  cleanPlainText,
  deferComponent,
  deferEphemeral,
  escapeDiscord,
  formatDuration,
  formatNumber,
  guildOnlyDefinition,
  isGuildInteraction,
  isMinecraftUsername,
  minecraftAvatarUrl,
  playerProfileUrl,
  relativeTime,
  respond,
  respondServiceFailure,
  stableList,
  truncateEmbed,
  updateComponent
} from "./utils.js";

const LINK_CODE_TTL_MINUTES = 10;
const UNLINK_COMPONENT_PREFIX = "kairu:unlink";

function expiryTime(expiresAt: Date | string): string {
  const milliseconds = new Date(expiresAt).getTime();
  return Number.isFinite(milliseconds)
    ? `<t:${Math.floor(milliseconds / 1_000)}:R>`
    : `within ${LINK_CODE_TTL_MINUTES} minutes`;
}

function publicGuildCommand(interaction: CommandInteraction): boolean {
  return isGuildInteraction(interaction);
}

function playerEmbed(profile: PlayerProfile, websiteBaseUrl: string) {
  const fields = [
    { name: "Rank", value: escapeDiscord(profile.rankName ?? "Member", 100), inline: true },
    { name: "World", value: escapeDiscord(profile.worldName ?? "Offline", 100), inline: true },
    { name: "Playtime", value: formatDuration(profile.playtimeSeconds), inline: true },
    { name: "Kills", value: formatNumber(profile.kills), inline: true },
    { name: "Deaths", value: formatNumber(profile.deaths), inline: true },
    { name: "Blocks broken", value: formatNumber(profile.blocksBroken), inline: true }
  ];
  const url = playerProfileUrl(websiteBaseUrl, profile.minecraftUuid);
  return {
    color: COLORS.BRAND,
    title: `${escapeDiscord(profile.username, 64)}'s profile`,
    ...(url ? { url } : {}),
    thumbnail: { url: minecraftAvatarUrl(profile.username) },
    fields,
    footer: { text: profile.updatedAt ? `Updated ${relativeTime(profile.updatedAt)}` : "Minecraft profile" }
  };
}

export const linkMinecraftCommand: CommandModule = {
  definition: guildOnlyDefinition({
    name: "link-minecraft",
    description: "Create a one-time code to link your Minecraft account"
  }),
  async execute(interaction, services) {
    await deferEphemeral(interaction);
    if (!publicGuildCommand(interaction)) {
      await respond(interaction, { ephemeral: true, content: "This command is only available in a server." });
      return;
    }
    try {
      const code = await services.links.createLinkCode(interaction.user.id);
      const safeCode = cleanPlainText(code.code, 128);
      if (!safeCode || !/^[A-Z0-9_-]{8,128}$/.test(safeCode)) {
        services.logger?.error("Link service returned an invalid pairing code");
        await respond(interaction, { ephemeral: true, content: "I could not create a secure linking code. Please try again later." });
        return;
      }
      await respond(interaction, {
        ephemeral: true,
        embeds: [{
          color: COLORS.SUCCESS,
          title: "Link a Minecraft account",
          description: `In Minecraft, run:\n\n\`/kairu link ${safeCode}\`\n\nThis adds this Minecraft account to your Discord identity. You can link Java, Bedrock, and alt accounts. This one-time code expires ${expiryTime(code.expiresAt)}. Do not share it.`,
          footer: { text: "Kairu never asks for or stores Minecraft passwords." }
        }]
      });
    } catch (error) {
      await respondServiceFailure(interaction, services.logger, "link-minecraft", error);
    }
  }
};

export const unlinkMinecraftCommand: CommandModule = {
  definition: guildOnlyDefinition({
    name: "unlink-minecraft",
    description: "Remove one linked Minecraft account after confirming",
    options: [{ type: 3, name: "minecraft_uuid", description: "Account UUID; defaults to primary", required: false, min_length: 36, max_length: 36 }]
  }),
  async execute(interaction, services) {
    await deferEphemeral(interaction);
    if (!publicGuildCommand(interaction)) {
      await respond(interaction, { ephemeral: true, content: "This command is only available in a server." });
      return;
    }
    try {
      const requestedUuid = interaction.options.getString("minecraft_uuid", false);
      const links = await services.links.getLinksByDiscordUser(interaction.user.id);
      const link = requestedUuid ? links.find((item) => item.minecraftUuid === requestedUuid) ?? null : links.find((item) => item.isPrimary) ?? links[0] ?? null;
      if (!link) {
        await respond(interaction, { ephemeral: true, content: "You do not have a linked Minecraft account." });
        return;
      }
      await respond(interaction, {
        ephemeral: true,
        content: `Remove the link to **${escapeDiscord(link.minecraftUsername, 16)}**? This cannot be undone. Your other linked accounts remain intact.`,
        components: [{
          type: 1,
          components: [
            { type: 2, style: 4, label: "Confirm unlink", custom_id: `${UNLINK_COMPONENT_PREFIX}:confirm:${interaction.user.id}:${link.minecraftUuid}` },
            { type: 2, style: 2, label: "Cancel", custom_id: `${UNLINK_COMPONENT_PREFIX}:cancel:${interaction.user.id}:${link.minecraftUuid}` }
          ]
        }]
      });
    } catch (error) {
      await respondServiceFailure(interaction, services.logger, "unlink-minecraft", error);
    }
  },
  async handleComponent(interaction: ComponentInteraction, services: CommandServices): Promise<boolean> {
    const match = new RegExp(`^${UNLINK_COMPONENT_PREFIX.replace(/[:]/g, "\\:")}:(confirm|cancel):(\\d{5,25}):([0-9a-f-]{36})$`, "i").exec(interaction.customId);
    if (!match) return false;
    const [, action, ownerId, minecraftUuid] = match;
    if (interaction.user.id !== ownerId) {
      await interaction.reply({ ephemeral: true, content: "Only the member who requested this confirmation can use it.", allowedMentions: { parse: [] } });
      return true;
    }
    await deferComponent(interaction);
    if (action === "cancel") {
      await updateComponent(interaction, { content: "Unlink cancelled.", components: [] });
      return true;
    }
    try {
      const removed = await services.links.unlinkMinecraftAccount(ownerId, minecraftUuid);
      await updateComponent(interaction, {
        content: removed ? "Your Minecraft account link has been removed." : "Your Minecraft account link was already removed.",
        components: []
      });
    } catch (error) {
      services.logger?.error("Discord command unlink-minecraft component failed", error instanceof Error ? error.name : "unknown error");
      await updateComponent(interaction, { content: "I could not remove that link right now. Please try again later.", components: [] });
    }
    return true;
  }
};

async function getRequestedLink(interaction: CommandInteraction, services: CommandServices): Promise<MinecraftLink | null> {
  const requestedUser = interaction.options.getUser("member", false);
  return services.links.getLinkByDiscordUser((requestedUser ?? interaction.user).id);
}

export const profileCommand: CommandModule = {
  definition: guildOnlyDefinition({
    name: "profile",
    description: "Show a linked Minecraft profile",
    options: [{ type: 6, name: "member", description: "Discord member (defaults to you)", required: false }]
  }),
  async execute(interaction, services) {
    await deferEphemeral(interaction, false);
    if (!publicGuildCommand(interaction)) {
      await respond(interaction, { ephemeral: true, content: "This command is only available in a server." });
      return;
    }
    try {
      const link = await getRequestedLink(interaction, services);
      if (!link) {
        await respond(interaction, { ephemeral: true, content: "That member does not have a linked Minecraft account." });
        return;
      }
      const profile = await services.players.getPlayerByMinecraftUuid(link.minecraftUuid);
      if (!profile) {
        await respond(interaction, {
          ephemeral: true,
          content: `**${escapeDiscord(link.minecraftUsername, 16)}** is linked, but no player profile is available yet.`
        });
        return;
      }
      await respond(interaction, { embeds: [playerEmbed(profile, services.websiteBaseUrl)] });
    } catch (error) {
      await respondServiceFailure(interaction, services.logger, "profile", error);
    }
  }
};

export const serverStatusCommand: CommandModule = {
  definition: guildOnlyDefinition({
    name: "server-status",
    description: "Show the current Minecraft server status"
  }),
  async execute(interaction, services) {
    await deferEphemeral(interaction, false);
    if (!publicGuildCommand(interaction) || !interaction.guildId) {
      await respond(interaction, { ephemeral: true, content: "This command is only available in a server." });
      return;
    }
    try {
      const status = await services.status.getServerStatus(interaction.guildId);
      if (!status) {
        await respond(interaction, { ephemeral: true, content: "Live server status is not available yet." });
        return;
      }
      await respond(interaction, {
        embeds: [{
          color: status.online ? COLORS.SUCCESS : COLORS.DANGER,
          title: status.online ? "Minecraft server is online" : "Minecraft server is offline",
          fields: [
            { name: "Players", value: formatNumber(status.playerCount), inline: true },
            { name: "TPS", value: status.online ? formatNumber(status.tps, 2) : "—", inline: true },
            { name: "Version", value: escapeDiscord(status.version ?? "Unknown", 100), inline: true },
            { name: "Uptime", value: formatDuration(status.uptimeSeconds), inline: true },
            { name: "Last heartbeat", value: relativeTime(status.receivedAt), inline: true }
          ]
        }]
      });
    } catch (error) {
      await respondServiceFailure(interaction, services.logger, "server-status", error);
    }
  }
};

function groupedOnlinePlayers(players: OnlinePlayer[]): string {
  const groups = new Map<string, string[]>();
  for (const player of players) {
    if (!isMinecraftUsername(player.username)) continue;
    const world = cleanPlainText(player.worldName ?? "Unknown world", 128) ?? "Unknown world";
    const list = groups.get(world) ?? [];
    list.push(player.username);
    groups.set(world, list);
  }
  return [...groups.entries()]
    .sort(([left], [right]) => left.localeCompare(right))
    .map(([world, names]) => `**${escapeDiscord(world, 128)}** (${names.length})\n${stableList(names.sort((a, b) => a.localeCompare(b)))}`)
    .join("\n\n");
}

export const onlineCommand: CommandModule = {
  definition: guildOnlyDefinition({
    name: "online",
    description: "List online players grouped by world"
  }),
  async execute(interaction, services) {
    await deferEphemeral(interaction, false);
    if (!publicGuildCommand(interaction) || !interaction.guildId) {
      await respond(interaction, { ephemeral: true, content: "This command is only available in a server." });
      return;
    }
    try {
      const players = await services.status.getOnlinePlayers(interaction.guildId);
      if (!players.length) {
        await respond(interaction, { embeds: [{ color: COLORS.MUTED, title: "Online players", description: "No players are online right now." }] });
        return;
      }
      await respond(interaction, {
        embeds: [{
          color: COLORS.BRAND,
          title: `Online players (${players.length})`,
          description: truncateEmbed(groupedOnlinePlayers(players))
        }]
      });
    } catch (error) {
      await respondServiceFailure(interaction, services.logger, "online", error);
    }
  }
};

export const playerCommand: CommandModule = {
  definition: guildOnlyDefinition({
    name: "player",
    description: "Look up a Minecraft player by username",
    options: [{ type: 3, name: "username", description: "Minecraft Java username", required: true, min_length: 3, max_length: 16 }]
  }),
  async execute(interaction, services) {
    await deferEphemeral(interaction, false);
    if (!publicGuildCommand(interaction)) {
      await respond(interaction, { ephemeral: true, content: "This command is only available in a server." });
      return;
    }
    const requested = interaction.options.getString("username", true) ?? "";
    if (!isMinecraftUsername(requested)) {
      await respond(interaction, { ephemeral: true, content: "Enter a valid Minecraft Java username (3–16 letters, numbers, or underscores)." });
      return;
    }
    try {
      const profile = await services.players.getPlayerByUsername(requested);
      if (!profile) {
        await respond(interaction, { ephemeral: true, content: `No profile was found for **${escapeDiscord(requested, 16)}**.` });
        return;
      }
      const url = playerProfileUrl(services.websiteBaseUrl, profile.minecraftUuid);
      await respond(interaction, {
        embeds: [playerEmbed(profile, services.websiteBaseUrl)],
        ...(url ? { components: [{ type: 1, components: [{ type: 2, style: 5, label: "View website profile", url }] }] } : {})
      });
    } catch (error) {
      await respondServiceFailure(interaction, services.logger, "player", error);
    }
  }
};

export const minecraftAccountsCommand: CommandModule = {
  definition: guildOnlyDefinition({ name: "minecraft-accounts", description: "List your linked Minecraft accounts" }),
  async execute(interaction, services) {
    await deferEphemeral(interaction);
    try {
      const links = await services.links.getLinksByDiscordUser(interaction.user.id);
      if (!links.length) return respond(interaction, { ephemeral: true, content: "You have no linked Minecraft accounts. Use /link-minecraft to add one." });
      return respond(interaction, { ephemeral: true, content: `${links.map((link) => `${link.isPrimary ? "⭐ **Primary**" : "•"} **${escapeDiscord(link.minecraftUsername, 16)}**\\n\\`${link.minecraftUuid}\\``).join("\\n")}\\n\\nUse /primary-minecraft with an account UUID to change your primary account.` });
    } catch (error) { await respondServiceFailure(interaction, services.logger, "minecraft-accounts", error); }
  }
};

export const primaryMinecraftCommand: CommandModule = {
  definition: guildOnlyDefinition({ name: "primary-minecraft", description: "Set your primary Minecraft account", options: [{ type: 3, name: "minecraft_uuid", description: "UUID shown by /minecraft-accounts", required: true, min_length: 36, max_length: 36 }] }),
  async execute(interaction, services) {
    await deferEphemeral(interaction);
    const minecraftUuid = interaction.options.getString("minecraft_uuid", true);
    if (!/^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i.test(minecraftUuid)) return respond(interaction, { ephemeral: true, content: "Use the UUID shown by /minecraft-accounts." });
    try { const changed = await services.links.setPrimaryMinecraftAccount(interaction.user.id, minecraftUuid); return respond(interaction, { ephemeral: true, content: changed ? "Your primary Minecraft account has been updated." : "That Minecraft account is not linked to your Discord account." }); }
    catch (error) { await respondServiceFailure(interaction, services.logger, "primary-minecraft", error); }
  }
};

export const playerCommands: readonly CommandModule[] = [
  linkMinecraftCommand,
  unlinkMinecraftCommand,
  minecraftAccountsCommand,
  primaryMinecraftCommand,
  profileCommand,
  serverStatusCommand,
  onlineCommand,
  playerCommand
];
