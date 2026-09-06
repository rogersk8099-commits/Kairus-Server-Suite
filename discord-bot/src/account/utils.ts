import type {
  CommandInteraction,
  ComponentInteraction,
  DiscordPermission,
  ReplyPayload,
  SlashCommandDefinition
} from "./interfaces.js";
import { DISCORD_SNOWFLAKE_PATTERN, MINECRAFT_USERNAME_PATTERN } from "./interfaces.js";

const DISCORD_MAX_CONTENT = 2_000;
const DISCORD_MAX_EMBED_DESCRIPTION = 4_096;
const CONTROL_CHARACTER_CODES = new Set([...Array.from({ length: 9 }, (_, code) => code), 11, 12, ...Array.from({ length: 18 }, (_, index) => index + 14), 127]);
function stripControlCharacters(value: string): string { return [...value].filter((character) => !CONTROL_CHARACTER_CODES.has(character.charCodeAt(0))).join(""); }

/**
 * Make arbitrary API/user-provided strings inert in Discord Markdown. This is
 * also applied to all service data before it is interpolated into responses.
 */
export function escapeDiscord(value: unknown, maxLength = 1_500): string {
  const scalar = typeof value === "string" || typeof value === "number" || typeof value === "bigint" || typeof value === "boolean" ? String(value) : "";
  const text = stripControlCharacters(scalar)
    .replace(/@/g, "@\u200b")
    .replace(/([\\`*_{}[\]<>()#+\-.!|~])/g, "\\$1")
    .trim();
  if (!text) return "—";
  return text.length <= maxLength ? text : `${text.slice(0, Math.max(1, maxLength - 1))}…`;
}

/** Normalizes user-controlled plain text prior to passing it to injected services. */
export function cleanPlainText(value: string, maxLength: number): string | null {
  const cleaned = stripControlCharacters(value).trim();
  if (!cleaned || cleaned.length > maxLength) return null;
  return cleaned;
}

export function isMinecraftUsername(value: string): boolean {
  return MINECRAFT_USERNAME_PATTERN.test(value);
}

export function isSnowflake(value: string): boolean {
  return DISCORD_SNOWFLAKE_PATTERN.test(value);
}

export function isGuildInteraction(interaction: Pick<CommandInteraction, "guildId" | "inGuild">): boolean {
  return interaction.inGuild() && Boolean(interaction.guildId);
}

export function hasPermission(interaction: Pick<CommandInteraction, "memberPermissions">, permission: DiscordPermission): boolean {
  return Boolean(interaction.memberPermissions?.has("Administrator") || interaction.memberPermissions?.has(permission));
}

export function guildOnlyDefinition(definition: Omit<SlashCommandDefinition, "type" | "dm_permission">): SlashCommandDefinition {
  return { type: 1, dm_permission: false, ...definition };
}

export function noMentions(payload: ReplyPayload): ReplyPayload {
  return { ...payload, allowedMentions: { parse: [] } };
}

/**
 * Defer every command response before network/service work. A failed defer is
 * tolerated so the handler can still issue a direct reply when Discord allows.
 */
export async function deferEphemeral(interaction: CommandInteraction, ephemeral = true): Promise<void> {
  if (interaction.deferred || interaction.replied) return;
  try {
    await interaction.deferReply({ ephemeral });
  } catch {
    // An interaction may already have been acknowledged by middleware.
  }
}

/** Send a response regardless of whether middleware has already acknowledged it. */
export async function respond(interaction: CommandInteraction, payload: ReplyPayload): Promise<void> {
  const safePayload = noMentions({ ...payload, content: payload.content ? payload.content.slice(0, DISCORD_MAX_CONTENT) : undefined });
  if (interaction.deferred) {
    await interaction.editReply(safePayload);
    return;
  }
  if (!interaction.replied) {
    await interaction.reply(safePayload);
    return;
  }
  if (interaction.followUp) {
    await interaction.followUp(safePayload);
    return;
  }
  // There is no safe write method remaining; intentionally do not throw/retry.
}

export async function respondServiceFailure(
  interaction: CommandInteraction,
  logger: Pick<Console, "error"> | undefined,
  commandName: string,
  error: unknown
): Promise<void> {
  logger?.error(`Discord command ${commandName} failed`, error instanceof Error ? error.name : "unknown error");
  await respond(interaction, {
    ephemeral: true,
    content: "I could not complete that request right now. Please try again later."
  });
}

/** Component acknowledgement used before an unlink/delete database call. */
export async function deferComponent(interaction: ComponentInteraction): Promise<void> {
  if (interaction.deferred || interaction.replied) return;
  try {
    await interaction.deferUpdate();
  } catch {
    // Fall through: `update` can still be used if Discord did not accept defer.
  }
}

export async function updateComponent(interaction: ComponentInteraction, payload: Omit<ReplyPayload, "ephemeral">): Promise<void> {
  const safePayload = noMentions({ ...payload, content: payload.content ? payload.content.slice(0, DISCORD_MAX_CONTENT) : undefined });
  if (interaction.deferred) {
    await interaction.editReply(safePayload);
  } else if (!interaction.replied) {
    await interaction.update(safePayload);
  }
}

export function truncateEmbed(value: string): string {
  return value.length <= DISCORD_MAX_EMBED_DESCRIPTION ? value : `${value.slice(0, DISCORD_MAX_EMBED_DESCRIPTION - 1)}…`;
}

export function relativeTime(value: string | Date | null | undefined): string {
  if (!value) return "Unknown";
  const milliseconds = new Date(value).getTime();
  if (!Number.isFinite(milliseconds)) return "Unknown";
  return `<t:${Math.floor(milliseconds / 1_000)}:R>`;
}

export function formatDuration(seconds: number | null | undefined): string {
  if (!Number.isFinite(seconds) || !seconds || seconds < 0) return "Unknown";
  const total = Math.floor(seconds);
  const days = Math.floor(total / 86_400);
  const hours = Math.floor((total % 86_400) / 3_600);
  const minutes = Math.floor((total % 3_600) / 60);
  return [days ? `${days}d` : "", hours ? `${hours}h` : "", `${minutes}m`].filter(Boolean).join(" ");
}

export function safeWebsiteBaseUrl(value: string): URL | null {
  try {
    const url = new URL(value);
    if (url.protocol !== "https:" || url.username || url.password || url.search || url.hash) return null;
    return url;
  } catch {
    return null;
  }
}

export function playerProfileUrl(websiteBaseUrl: string, minecraftUuid: string): string | null {
  const base = safeWebsiteBaseUrl(websiteBaseUrl);
  if (!base || !/^[a-f0-9-]{32,36}$/i.test(minecraftUuid)) return null;
  const path = base.pathname.endsWith("/") ? base.pathname : `${base.pathname}/`;
  base.pathname = `${path}players/${encodeURIComponent(minecraftUuid)}`;
  return base.toString();
}

export function minecraftAvatarUrl(username: string): string {
  // Username is validated before this is called; encoding remains a defense in depth.
  return `https://mc-heads.net/avatar/${encodeURIComponent(username)}/128`;
}

export function formatNumber(value: number | null | undefined, maximumFractionDigits = 0): string {
  if (!Number.isFinite(value)) return "Unknown";
  return new Intl.NumberFormat("en-US", { maximumFractionDigits }).format(value as number);
}

export function stableList(values: string[], empty = "None"): string {
  return values.length ? values.map((value) => escapeDiscord(value, 100)).join(", ") : empty;
}
