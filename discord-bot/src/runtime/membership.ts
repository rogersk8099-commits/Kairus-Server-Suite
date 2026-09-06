import { ChannelType, type Client, type GuildMember } from "discord.js";
import type { PrismaClient } from "@prisma/client";
import type { AppLogger } from "../logger.js";
import { reconcileMemberRoles } from "../services/account-adapters.js";

export async function welcomeMember(database: PrismaClient, member: GuildMember, logger: AppLogger): Promise<void> {
  if (member.user.bot) return;
  const resources = await database.discordSetupResource.findMany({ where: { guildId: member.guild.id, resourceKey: { in: ["role.new-player", "text-channel.welcome"] } } });
  const roleId = resources.find((row) => row.resourceKey === "role.new-player")?.discordId;
  const channelId = resources.find((row) => row.resourceKey === "text-channel.welcome")?.discordId;
  if (roleId) {
    const role = await member.guild.roles.fetch(roleId).catch(() => null);
    const highest = member.guild.members.me?.roles.highest.position ?? -1;
    if (role && !role.managed && role.position < highest) await member.roles.add(role, "Kairu welcome: verification pending");
    else logger.warn({ guildId: member.guild.id, roleId }, "New Player role missing or above bot hierarchy");
  }
  if (channelId) {
    const channel = await member.guild.channels.fetch(channelId).catch(() => null);
    if (channel?.type === ChannelType.GuildText) await channel.send({ content: `Welcome <@${member.id}>! You have **New Player** access. Use \`/link-minecraft\`, then complete the shown command in Minecraft. Kairu will replace New Player with Member after central verification.`, allowedMentions: { users: [member.id], roles: [], repliedUser: false } });
  }
}

export async function reconcileMemberships(database: PrismaClient, client: Client, guildId: string, logger: AppLogger): Promise<void> {
  const rows = await database.discordMembership.findMany({ where: { guildId }, select: { discordUserId: true } });
  for (const row of rows) {
    try { await reconcileMemberRoles(database, client, guildId, row.discordUserId); }
    catch (error) { logger.warn({ err: error, guildId, userId: row.discordUserId }, "membership reconciliation item failed"); }
  }
}
