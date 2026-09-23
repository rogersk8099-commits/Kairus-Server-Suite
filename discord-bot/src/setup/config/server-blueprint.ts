/** Stable keys are persisted; human names may be reconciled safely. */
export const ROLE_KEYS = [
  "owner",
  "administrator",
  "developer",
  "moderator",
  "helper",
  "builder",
  "event-team",
  "content-creator",
  "premium",
  "vip",
  "vip-plus",
  "booster",
  "member",
  "new-player",
  "bots",
] as const;
export type RoleKey = (typeof ROLE_KEYS)[number];
export type SetupResourceKind =
  "role" | "category" | "text-channel" | "voice-channel";
export type ChannelAudience = "public" | "read-only" | "staff" | "premium";
export interface RoleBlueprint {
  readonly key: RoleKey;
  readonly resourceKey: `role.${RoleKey}`;
  readonly name: string;
  readonly color: `#${string}`;
  readonly hoist: boolean;
  readonly mentionable: boolean;
}
export interface CategoryBlueprint {
  readonly key: string;
  readonly resourceKey: `category.${string}`;
  readonly name: string;
}
interface BaseChannelBlueprint {
  readonly key: string;
  readonly resourceKey: `${"text-channel" | "voice-channel"}.${string}`;
  readonly name: string;
  readonly parentKey: string;
  readonly audience: ChannelAudience;
}
export interface TextChannelBlueprint extends BaseChannelBlueprint {
  readonly type: "text-channel";
  readonly topic: string;
  readonly nsfw: boolean;
}
export interface VoiceChannelBlueprint extends BaseChannelBlueprint {
  readonly type: "voice-channel";
  readonly bitrate: number;
  readonly userLimit: number;
}
export type ChannelBlueprint = TextChannelBlueprint | VoiceChannelBlueprint;
export interface ServerBlueprint {
  readonly version: 1;
  readonly roles: readonly RoleBlueprint[];
  readonly categories: readonly CategoryBlueprint[];
  readonly channels: readonly ChannelBlueprint[];
}

const text = (
  key: string,
  parentKey: string,
  audience: ChannelAudience,
  topic: string,
  name = key,
): TextChannelBlueprint => ({
  key,
  resourceKey: `text-channel.${key}`,
  type: "text-channel",
  name,
  parentKey,
  audience,
  topic,
  nsfw: false,
});
const voice = (
  key: string,
  name: string,
  parentKey: string,
  audience: ChannelAudience,
): VoiceChannelBlueprint => ({
  key,
  resourceKey: `voice-channel.${key}`,
  type: "voice-channel",
  name,
  parentKey,
  audience,
  bitrate: 64_000,
  userLimit: 0,
});

export const SERVER_BLUEPRINT: ServerBlueprint = {
  version: 1,
  roles: [
    {
      key: "owner",
      resourceKey: "role.owner",
      name: "Owner",
      color: "#F1C40F",
      hoist: true,
      mentionable: false,
    },
    {
      key: "administrator",
      resourceKey: "role.administrator",
      name: "Administrator",
      color: "#E74C3C",
      hoist: true,
      mentionable: false,
    },
    {
      key: "developer",
      resourceKey: "role.developer",
      name: "Developer",
      color: "#9B59B6",
      hoist: true,
      mentionable: false,
    },
    {
      key: "moderator",
      resourceKey: "role.moderator",
      name: "Moderator",
      color: "#3498DB",
      hoist: true,
      mentionable: false,
    },
    {
      key: "helper",
      resourceKey: "role.helper",
      name: "Helper",
      color: "#2ECC71",
      hoist: true,
      mentionable: false,
    },
    {
      key: "builder",
      resourceKey: "role.builder",
      name: "Builder",
      color: "#E67E22",
      hoist: true,
      mentionable: false,
    },
    {
      key: "event-team",
      resourceKey: "role.event-team",
      name: "Event Team",
      color: "#1ABC9C",
      hoist: true,
      mentionable: false,
    },
    {
      key: "content-creator",
      resourceKey: "role.content-creator",
      name: "Content Creator",
      color: "#E91E63",
      hoist: true,
      mentionable: false,
    },
    {
      key: "premium",
      resourceKey: "role.premium",
      name: "Premium",
      color: "#F39C12",
      hoist: true,
      mentionable: false,
    },
    {
      key: "vip",
      resourceKey: "role.vip",
      name: "VIP",
      color: "#00BCD4",
      hoist: true,
      mentionable: false,
    },
    {
      key: "vip-plus",
      resourceKey: "role.vip-plus",
      name: "VIP+",
      color: "#03A9F4",
      hoist: true,
      mentionable: false,
    },
    {
      key: "booster",
      resourceKey: "role.booster",
      name: "Booster",
      color: "#FF73FA",
      hoist: true,
      mentionable: false,
    },
    {
      key: "member",
      resourceKey: "role.member",
      name: "Member",
      color: "#95A5A6",
      hoist: false,
      mentionable: false,
    },
    {
      key: "new-player",
      resourceKey: "role.new-player",
      name: "New Player",
      color: "#7F8C8D",
      hoist: false,
      mentionable: false,
    },
    {
      key: "bots",
      resourceKey: "role.bots",
      name: "Bots",
      color: "#5865F2",
      hoist: false,
      mentionable: false,
    },
  ],
  categories: [
    {
      key: "start-here",
      resourceKey: "category.start-here",
      name: "👋 START HERE",
    },
    {
      key: "information",
      resourceKey: "category.information",
      name: "📌 INFORMATION",
    },
    {
      key: "community",
      resourceKey: "category.community",
      name: "💬 COMMUNITY",
    },
    {
      key: "minecraft",
      resourceKey: "category.minecraft",
      name: "⛏️ MINECRAFT",
    },
    { key: "events", resourceKey: "category.events", name: "📅 EVENTS" },
    { key: "creators", resourceKey: "category.creators", name: "🎥 CREATORS" },
    { key: "premium", resourceKey: "category.premium", name: "💎 PREMIUM" },
    { key: "support", resourceKey: "category.support", name: "🎫 SUPPORT" },
    { key: "staff", resourceKey: "category.staff", name: "🛡️ STAFF" },
    { key: "ashfall", resourceKey: "category.ashfall", name: "🔥 ASHFALL" },
    { key: "obsidian-gate", resourceKey: "category.obsidian-gate", name: "💀 OBSIDIAN GATE" },
    { key: "atrium", resourceKey: "category.atrium", name: "🏛️ THE ATRIUM" },
    { key: "colosseum", resourceKey: "category.colosseum", name: "⚔️ NEON COLOSSEUM" },
    { key: "quarry", resourceKey: "category.quarry", name: "⛏️ THE QUARRY" },
    { key: "verdance", resourceKey: "category.verdance", name: "🌿 VERDANCE" },
  ],
  channels: [
    text(
      "welcome",
      "start-here",
      "public",
      "Welcome, verification, and onboarding.",
    ),
    text(
      "rules",
      "start-here",
      "read-only",
      "Kairu community and server rules.",
    ),
    text(
      "how-to-join",
      "start-here",
      "read-only",
      "Minecraft connection and account verification instructions.",
    ),
    text(
      "announcements",
      "information",
      "read-only",
      "Official Kairu announcements.",
    ),
    text(
      "server-info",
      "information",
      "read-only",
      "Server addresses and Kairu information.",
    ),
    text("faq", "information", "read-only", "Frequently asked questions."),
    text("general", "community", "public", "General community discussion."),
    text("introductions", "community", "public", "Introduce yourself."),
    text(
      "screenshots-and-clips",
      "community",
      "public",
      "Share Kairu screenshots and clips.",
    ),
    text(
      "suggestions",
      "community",
      "public",
      "Constructive suggestions for Kairu.",
    ),
    text(
      "server-status",
      "minecraft",
      "read-only",
      "Minecraft status maintained by Kairu.",
    ),
    text(
      "game-chat",
      "minecraft",
      "public",
      "Minecraft-focused community conversation.",
    ),
    text(
      "marketplace",
      "minecraft",
      "public",
      "Player trading and marketplace discussion.",
    ),
    text(
      "events",
      "events",
      "read-only",
      "Scheduled events and RSVP controls.",
    ),
    text("event-chat", "events", "public", "Community event discussion."),
    text(
      "stream-announcements",
      "creators",
      "read-only",
      "Approved creator live notifications.",
    ),
    text(
      "creator-chat",
      "creators",
      "public",
      "Creator discussion and collaboration.",
    ),
    text("premium-lounge", "premium", "premium", "Premium member lounge."),
    text("premium-news", "premium", "premium", "Premium member updates."),
    text(
      "open-a-ticket",
      "support",
      "read-only",
      "Use /ticket-menu or /ticket-open for private support.",
    ),
    text("support", "support", "public", "Non-sensitive support questions."),
    text("staff-chat", "staff", "staff", "Private staff coordination."),
    text("mod-log", "staff", "staff", "Moderation and audit log."),
    text("reports", "staff", "staff", "Private player reports."),
    text("ticket-log", "staff", "staff", "Private support-ticket audit log."),
    text("event-stage", "events", "public", "Event staging and coordination."),
    text(
      "creator-lounge",
      "creators",
      "public",
      "Creator collaboration lounge.",
    ),
    text("afk", "community", "public", "Away-from-keyboard notices."),
    // Spawn Hub intentionally has no Discord category. Network-wide discussion
    // stays in these global Minecraft channels instead.
    text("global-chat", "minecraft", "public", "Network-wide Minecraft and Discord chat."),
    text("account-links", "minecraft", "read-only", "Link one or more Minecraft accounts to Discord."),
    text("achievements", "minecraft", "read-only", "Kairu SMP achievements."),
    text("deaths", "minecraft", "read-only", "Optional global Minecraft death feed."),
    text("events-global", "minecraft", "read-only", "Network-wide Minecraft events."),
    text("server-news", "minecraft", "read-only", "Kairu SMP server news."),
    // Registry-driven Discord categories for the six non-Hub worlds.
    text("ashfall-chat", "ashfall", "public", "Ashfall world chat."),
    text("ashfall-news", "ashfall", "read-only", "Ashfall announcements."),
    text("ashfall-guilds", "ashfall", "public", "Ashfall guild discussion."),
    text("ashfall-deaths", "ashfall", "read-only", "Ashfall deaths."),
    text("ashfall-leaderboard", "ashfall", "read-only", "Ashfall leaderboard."),
    text("ashfall-showcase", "ashfall", "public", "Ashfall player showcase."),
    text("obsidian-chat", "obsidian-gate", "public", "Obsidian Gate world chat."),
    text("obsidian-deaths", "obsidian-gate", "read-only", "Hardcore death notices."),
    text("obsidian-leaderboard", "obsidian-gate", "read-only", "Obsidian Gate leaderboard."),
    text("obsidian-news", "obsidian-gate", "read-only", "Obsidian Gate announcements."),
    text("reset-window", "obsidian-gate", "read-only", "Hardcore reset schedule and eligibility."),
    text("atrium-chat", "atrium", "public", "The Atrium world chat."),
    text("build-showcase", "atrium", "public", "Completed Atrium builds."),
    text("featured-builds", "atrium", "read-only", "Featured Atrium builds."),
    text("build-competitions", "atrium", "public", "Atrium build competitions."),
    text("atrium-news", "atrium", "read-only", "Atrium announcements."),
    text("colosseum-chat", "colosseum", "public", "Neon Colosseum world chat."),
    text("event-registration", "colosseum", "public", "Colosseum event registration."),
    text("match-results", "colosseum", "read-only", "Colosseum match results."),
    text("tournaments", "colosseum", "public", "Tournament discussion."),
    text("colosseum-leaderboard", "colosseum", "read-only", "Colosseum leaderboard."),
    text("colosseum-news", "colosseum", "read-only", "Colosseum announcements."),
    text("quarry-chat", "quarry", "public", "The Quarry world chat."),
    text("quarry-reset", "quarry", "read-only", "Quarry reset timing and notices."),
    text("quarry-news", "quarry", "read-only", "Quarry announcements."),
    text("verdance-discussion", "verdance", "public", "Verdance archive discussion; Minecraft bridge is off by default."),
    text("season-history", "verdance", "read-only", "Verdance season history."),
    text("verdance-memories", "verdance", "public", "Verdance memories and screenshots."),
    text("archived-leaderboard", "verdance", "read-only", "Archived Verdance leaderboard."),
    voice("lounge", "Lounge", "community", "public"),
    voice("gaming", "Gaming", "community", "public"),
    voice("survival", "Survival", "minecraft", "public"),
    voice("building", "Building", "minecraft", "public"),
    voice("premium-lounge", "Premium Lounge", "premium", "premium"),
    voice("staff-meeting", "Staff Meeting", "staff", "staff"),
  ],
};
export const STAFF_ROLE_KEYS = [
  "owner",
  "administrator",
  "developer",
  "moderator",
  "helper",
] as const satisfies readonly RoleKey[];
export const PREMIUM_ROLE_KEYS = [
  "premium",
  "vip",
  "vip-plus",
  "booster",
] as const satisfies readonly RoleKey[];
export function channelRequiredRoleKeys(
  audience: ChannelAudience,
): readonly RoleKey[] {
  if (audience === "staff" || audience === "read-only") return STAFF_ROLE_KEYS;
  if (audience === "premium") return PREMIUM_ROLE_KEYS;
  return [];
}
export function expectedResourceCount(
  blueprint: ServerBlueprint = SERVER_BLUEPRINT,
): number {
  return (
    blueprint.roles.length +
    blueprint.categories.length +
    blueprint.channels.length
  );
}
export function assertBlueprintIntegrity(
  blueprint: ServerBlueprint = SERVER_BLUEPRINT,
): void {
  const resources = [
    ...blueprint.roles.map((item) => item.resourceKey),
    ...blueprint.categories.map((item) => item.resourceKey),
    ...blueprint.channels.map((item) => item.resourceKey),
  ];
  if (new Set(resources).size !== resources.length)
    throw new Error("SERVER_BLUEPRINT contains duplicate stable resource keys");
  if (blueprint.roles.length !== 15)
    throw new Error(
      `SERVER_BLUEPRINT must define exactly 15 roles; found ${blueprint.roles.length}`,
    );
  if (blueprint.categories.length !== 15)
    throw new Error(
      `SERVER_BLUEPRINT must define exactly 15 categories; found ${blueprint.categories.length}`,
    );
  if (blueprint.channels.length !== 69)
    throw new Error(
      `SERVER_BLUEPRINT must define exactly 69 channels; found ${blueprint.channels.length}`,
    );
  const textChannels = blueprint.channels.filter(
    (channel) => channel.type === "text-channel",
  ).length;
  const voiceChannels = blueprint.channels.filter(
    (channel) => channel.type === "voice-channel",
  ).length;
  if (textChannels !== 63 || voiceChannels !== 6)
    throw new Error(
      `SERVER_BLUEPRINT must define 63 text and 6 voice channels; found ${textChannels} text and ${voiceChannels} voice`,
    );
  const categoryKeys = new Set(
    blueprint.categories.map((category) => category.key),
  );
  const missingParents = blueprint.channels.filter(
    (channel) => !categoryKeys.has(channel.parentKey),
  );
  if (missingParents.length > 0)
    throw new Error(
      `SERVER_BLUEPRINT contains channels with missing parent categories: ${missingParents.map((channel) => channel.resourceKey).join(", ")}`,
    );
}
assertBlueprintIntegrity();
