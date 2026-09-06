import { PermissionFlagsBits } from "discord.js";
import {
  PREMIUM_ROLE_KEYS,
  STAFF_ROLE_KEYS,
  channelRequiredRoleKeys,
  type ChannelAudience,
  type ChannelBlueprint,
  type RoleKey,
} from "../config/server-blueprint.js";

export const SETUP_COMMAND_DEFAULT_MEMBER_PERMISSIONS =
  PermissionFlagsBits.ManageGuild;
export {
  PREMIUM_ROLE_KEYS,
  STAFF_ROLE_KEYS,
} from "../config/server-blueprint.js";

export type PermissionOverwrite = Readonly<{
  id: string;
  type: "role";
  allow: bigint;
  deny: bigint;
}>;

export type ResolvedRoleIds = Readonly<Partial<Record<RoleKey, string>>>;

const VIEW_HISTORY =
  PermissionFlagsBits.ViewChannel | PermissionFlagsBits.ReadMessageHistory;
const TEXT_INTERACTION =
  PermissionFlagsBits.SendMessages | PermissionFlagsBits.AddReactions;
const VOICE_INTERACTION =
  PermissionFlagsBits.Connect | PermissionFlagsBits.Speak;

export function isStaffMember(
  memberRoleIds: Iterable<string>,
  roleIds: ResolvedRoleIds,
): boolean {
  const memberRoles = new Set(memberRoleIds);
  return STAFF_ROLE_KEYS.some((key) => {
    const roleId = roleIds[key];
    return roleId !== undefined && memberRoles.has(roleId);
  });
}

/**
 * The only role set authorized for an audience. Staff are intentionally not
 * added to premium channels, and premium subscribers are not added to staff
 * channels. This prevents privilege leakage through a broad shared overwrite.
 */
export function audienceRoleKeys(
  audience: ChannelAudience,
): readonly RoleKey[] {
  return channelRequiredRoleKeys(audience);
}

export function assertAudienceRoleIds(
  audience: ChannelAudience,
  roleIds: ResolvedRoleIds,
): void {
  const missing = audienceRoleKeys(audience).filter((key) => !roleIds[key]);
  if (missing.length > 0) {
    throw new Error(
      `Cannot apply ${audience} permissions; required role IDs are unavailable: ${missing.join(", ")}`,
    );
  }
}

export function createChannelPermissionPlan(
  guildId: string,
  channel: ChannelBlueprint,
  roleIds: ResolvedRoleIds,
  botRoleId: string,
): readonly PermissionOverwrite[] {
  assertAudienceRoleIds(channel.audience, roleIds);

  const interaction =
    channel.type === "voice-channel" ? VOICE_INTERACTION : TEXT_INTERACTION;
  const everyone: PermissionOverwrite = {
    id: guildId,
    type: "role",
    allow:
      channel.audience === "public" ? VIEW_HISTORY | interaction : VIEW_HISTORY,
    deny:
      channel.audience === "public" || channel.audience === "read-only"
        ? 0n
        : PermissionFlagsBits.ViewChannel,
  };
  const bot: PermissionOverwrite = {
    id: botRoleId,
    type: "role",
    allow: VIEW_HISTORY | interaction,
    deny: 0n,
  };

  if (channel.audience === "public") return [everyone];

  if (channel.audience === "read-only") {
    return [
      { ...everyone, deny: interaction },
      bot,
      ...STAFF_ROLE_KEYS.map((key) => ({
        id: roleIds[key]!,
        type: "role" as const,
        allow: VIEW_HISTORY | interaction,
        deny: 0n,
      })),
    ];
  }

  const permittedRoleKeys =
    channel.audience === "staff" ? STAFF_ROLE_KEYS : PREMIUM_ROLE_KEYS;
  return [
    { ...everyone, allow: 0n, deny: PermissionFlagsBits.ViewChannel },
    bot,
    ...permittedRoleKeys.map((key) => ({
      id: roleIds[key]!,
      type: "role" as const,
      allow: VIEW_HISTORY | interaction,
      deny: 0n,
    })),
  ];
}

/** Managed IDs are the sole entries the setup process is permitted to replace. */
export function managedOverwriteIds(
  channel: ChannelBlueprint,
  guildId: string,
  roleIds: ResolvedRoleIds,
): ReadonlySet<string> {
  return new Set([
    guildId,
    ...audienceRoleKeys(channel.audience)
      .map((key) => roleIds[key]!)
      .filter(Boolean),
  ]);
}

export function formatPermissionBits(value: bigint): string {
  return value.toString();
}
