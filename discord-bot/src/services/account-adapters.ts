import { createHash, randomBytes, randomUUID } from "node:crypto";
import type { PrismaClient } from "@prisma/client";
import type { Client, Guild, GuildMember, Role } from "discord.js";
import type { CommandServices, MinecraftLink, PlayerProfile, PublicGuildConfig } from "../account/interfaces.js";
import type { ControlPlaneApi } from "../api-client.js";
import type { AppConfig } from "../config.js";
import type { AppLogger } from "../logger.js";

function number(value: bigint): number { return Number(value > BigInt(Number.MAX_SAFE_INTEGER) ? BigInt(Number.MAX_SAFE_INTEGER) : value); }
function profile(row: Awaited<ReturnType<PrismaClient["playerSnapshot"]["findUnique"]>>): PlayerProfile | null {
  if (!row) return null;
  return { minecraftUuid: row.minecraftUuid, username: row.name, rankName: row.rankName, worldName: row.worldName, playtimeSeconds: number(row.playtimeSeconds), blocksBroken: number(row.blocksBroken), kills: number(row.kills), deaths: number(row.deaths), distanceMeters: row.distanceMeters, balance: row.balance, updatedAt: row.updatedAt.toISOString() };
}
async function guild(client: Client, guildId: string): Promise<Guild> { return client.guilds.cache.get(guildId) ?? await client.guilds.fetch(guildId); }
async function setupRoles(database: PrismaClient, guildId: string): Promise<{ member: string | null; newPlayer: string | null }> {
  const rows = await database.discordSetupResource.findMany({ where: { guildId, resourceKey: { in: ["role.member", "role.new-player"] } } });
  return { member: rows.find((row) => row.resourceKey === "role.member")?.discordId ?? null, newPlayer: rows.find((row) => row.resourceKey === "role.new-player")?.discordId ?? null };
}
async function editableRole(member: GuildMember, id: string | null): Promise<Role | null> {
  if (!id) return null;
  const role = await member.guild.roles.fetch(id).catch(() => null);
  const botTop = member.guild.members.me?.roles.highest.position ?? -1;
  return role && !role.managed && role.position < botTop ? role : null;
}
async function syncOne(database: PrismaClient, client: Client, guildId: string, userId: string, dryRun = false): Promise<{ addedRoleNames: string[]; removedRoleNames: string[]; unchangedRoleNames: string[] }> {
  const targetGuild = await guild(client, guildId);
  const member = await targetGuild.members.fetch(userId);
  const [membership, link, roles] = await Promise.all([
    database.discordMembership.findUnique({ where: { guildId_discordUserId: { guildId, discordUserId: userId } } }),
    database.playerLink.findFirst({ where: { discordUserId: userId }, orderBy: [{ isPrimary: "desc" }, { linkedAt: "asc" }] }),
    setupRoles(database, guildId)
  ]);
  const verified = link !== null && membership?.status === "ACTIVE" && (!membership.expiresAt || membership.expiresAt > new Date());
  const memberRole = await editableRole(member, roles.member);
  const newPlayerRole = await editableRole(member, roles.newPlayer);
  const addedRoleNames: string[] = [], removedRoleNames: string[] = [], unchangedRoleNames: string[] = [];
  if (memberRole) {
    if (verified && !member.roles.cache.has(memberRole.id)) { if (!dryRun) await member.roles.add(memberRole, "Kairu central membership reconciliation"); addedRoleNames.push(memberRole.name); }
    else if (!verified && member.roles.cache.has(memberRole.id)) { if (!dryRun) await member.roles.remove(memberRole, "Kairu membership no longer active"); removedRoleNames.push(memberRole.name); }
    else unchangedRoleNames.push(memberRole.name);
  }
  if (newPlayerRole) {
    if (verified && member.roles.cache.has(newPlayerRole.id)) { if (!dryRun) await member.roles.remove(newPlayerRole, "Kairu verification complete"); removedRoleNames.push(newPlayerRole.name); }
    else if (!verified && !member.roles.cache.has(newPlayerRole.id)) { if (!dryRun) await member.roles.add(newPlayerRole, "Kairu verification pending"); addedRoleNames.push(newPlayerRole.name); }
    else unchangedRoleNames.push(newPlayerRole.name);
  }
  return { addedRoleNames, removedRoleNames, unchangedRoleNames };
}

export function createAccountServices(database: PrismaClient, api: ControlPlaneApi, client: Client, config: AppConfig, logger: AppLogger): CommandServices {
  return {
    websiteBaseUrl: config.API_URL,
    logger,
    links: {
      async getLinkByDiscordUser(discordUserId): Promise<MinecraftLink | null> {
        const row = await database.playerLink.findFirst({ where: { discordUserId }, orderBy: [{ isPrimary: "desc" }, { linkedAt: "asc" }] });
        return row ? { discordUserId: row.discordUserId, minecraftUuid: row.minecraftUuid, minecraftUsername: row.javaUsername, linkedAt: row.linkedAt.toISOString(), isPrimary: row.isPrimary } : null;
      },
      async getLinksByDiscordUser(discordUserId) { const rows = await database.playerLink.findMany({ where: { discordUserId }, orderBy: [{ isPrimary: "desc" }, { linkedAt: "asc" }] }); return rows.map((row) => ({ discordUserId: row.discordUserId, minecraftUuid: row.minecraftUuid, minecraftUsername: row.javaUsername, linkedAt: row.linkedAt.toISOString(), isPrimary: row.isPrimary })); },
      async createLinkCode(discordUserId) {
        const code = randomBytes(16).toString("base64url").toUpperCase();
        const expiresAt = new Date(Date.now() + 10 * 60_000);
        const codeHash = createHash("sha256").update(code).digest("hex");
        await database.$transaction([database.linkCode.deleteMany({ where: { discordUserId, consumedAt: null } }), database.linkCode.create({ data: { codeHash, discordUserId, expiresAt } })]);
        return { code, expiresAt };
      },
      async unlinkDiscordUser(discordUserId) { return (await database.playerLink.deleteMany({ where: { discordUserId } })).count > 0; },
      async unlinkMinecraftAccount(discordUserId, minecraftUuid) { const result = await database.$transaction(async (tx) => { const row = await tx.playerLink.findFirst({ where: { discordUserId, minecraftUuid } }); if (!row) return false; await tx.playerLink.delete({ where: { discordUserId_minecraftUuid: { discordUserId, minecraftUuid } } }); if (row.isPrimary) { const replacement = await tx.playerLink.findFirst({ where: { discordUserId }, orderBy: { linkedAt: "asc" } }); if (replacement) await tx.playerLink.update({ where: { discordUserId_minecraftUuid: { discordUserId, minecraftUuid: replacement.minecraftUuid } }, data: { isPrimary: true } }); } return true; }); return result; },
      async setPrimaryMinecraftAccount(discordUserId, minecraftUuid) { return database.$transaction(async (tx) => { const selected = await tx.playerLink.findFirst({ where: { discordUserId, minecraftUuid } }); if (!selected) return false; await tx.playerLink.updateMany({ where: { discordUserId }, data: { isPrimary: false } }); await tx.playerLink.update({ where: { discordUserId_minecraftUuid: { discordUserId, minecraftUuid } }, data: { isPrimary: true } }); return true; }); }
    },
    players: {
      async getPlayerByMinecraftUuid(minecraftUuid) { return profile(await database.playerSnapshot.findUnique({ where: { minecraftUuid } })); },
      async getPlayerByUsername(username) { return profile(await database.playerSnapshot.findFirst({ where: { name: { equals: username, mode: "insensitive" } } })); }
    },
    status: {
      async getServerStatus() {
        const row = await database.serverHeartbeat.findFirst({ orderBy: { receivedAt: "desc" } });
        return row ? { online: row.online, playerCount: row.playerCount, tps: row.tps, version: row.version, uptimeSeconds: number(row.uptimeSeconds), receivedAt: row.receivedAt.toISOString() } : null;
      },
      async getOnlinePlayers() {
        const row = await database.serverHeartbeat.findFirst({ orderBy: { receivedAt: "desc" } });
        if (!row || !Array.isArray(row.players)) return [];
        return row.players.flatMap((item) => {
          if (typeof item === "string") return [{ username: item, worldName: null }];
          if (!item || typeof item !== "object" || !("username" in item) || typeof item.username !== "string") return [];
          const worldName = "worldName" in item && typeof item.worldName === "string" ? item.worldName : null;
          return [{ username: item.username, worldName }];
        });
      }
    },
    roles: { async syncRoles(input) { return syncOne(database, client, input.guildId, input.discordUserId); } },
    memberships: {
      async syncMemberships(input) {
        const rows = await database.discordMembership.findMany({ where: { guildId: input.guildId }, take: input.limit, orderBy: { updatedAt: "asc" } });
        let updated = 0, skipped = 0, failed = 0;
        for (const row of rows) {
          try { const result = await syncOne(database, client, input.guildId, row.discordUserId, input.dryRun); if (result.addedRoleNames.length + result.removedRoleNames.length > 0) updated += 1; else skipped += 1; }
          catch (error) { failed += 1; logger.warn({ err: error, guildId: input.guildId, userId: row.discordUserId }, "membership reconciliation item failed"); }
        }
        return { examined: rows.length, updated, skipped, failed };
      }
    },
    control: {
      async setMaintenance(input) {
        await database.discordGuild.upsert({ where: { guildId: input.guildId }, create: { guildId: input.guildId, maintenanceEnabled: input.enabled, maintenanceMessage: input.reason }, update: { maintenanceEnabled: input.enabled, maintenanceMessage: input.reason } });
        await api.post("/api/admin/plugin-commands", { serverId: "survival", commandType: "notification", payload: { kind: "maintenance", enabled: input.enabled, reason: input.reason, actorDiscordUserId: input.actorDiscordUserId } }, `maintenance:${input.guildId}:${input.enabled}:${randomUUID()}`);
        return { enabled: input.enabled, changedAt: new Date().toISOString() };
      },
      async announce(input) {
        await api.post("/api/admin/plugin-commands", { serverId: "survival", commandType: "notification", payload: { kind: "announcement", message: input.message, actorDiscordUserId: input.actorDiscordUserId } }, `announce:${input.guildId}:${randomUUID()}`);
        return { delivered: 1 };
      }
    },
    config: {
      async getPublicGuildConfig(guildId): Promise<PublicGuildConfig> {
        const row = await database.discordGuild.upsert({ where: { guildId }, create: { guildId }, update: {} });
        return { profileVisibility: row.profileVisibility === "public" ? "public" : "members", announcementChannelId: row.announcementChannelId, maintenanceMessage: row.maintenanceMessage };
      },
      async updatePublicGuildConfig(input) {
        const data = input.setting === "profile_visibility" ? { profileVisibility: input.value } : input.setting === "announcement_channel" ? { announcementChannelId: input.value === "clear" ? null : input.value } : { maintenanceMessage: input.value === "clear" ? null : input.value };
        const row = await database.discordGuild.upsert({ where: { guildId: input.guildId }, create: { guildId: input.guildId, ...data }, update: data });
        return { profileVisibility: row.profileVisibility === "public" ? "public" : "members", announcementChannelId: row.announcementChannelId, maintenanceMessage: row.maintenanceMessage };
      }
    }
  };
}

export { syncOne as reconcileMemberRoles };
