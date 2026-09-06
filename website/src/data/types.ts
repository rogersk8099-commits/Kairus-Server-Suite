export type ServerStatus = {
  online: boolean;
  players: number;
  maxPlayers: number;
  tps: number;
  uptimeDays: number;
  version: string;
  region: string;
  lastRestart: string;
};

export type World = {
  id: string;
  name: string;
  type: "Survival" | "Hardcore" | "Creative" | "Events" | "Resource";
  season: string;
  status: "Live" | "Seasonal" | "Archived";
  blurb: string;
  size: string;
  difficulty: "Peaceful" | "Normal" | "Hard" | "Brutal";
  players: number;
  accent: "magenta" | "violet" | "electric";
};

export type Stream = {
  id: string;
  creator: string;
  handle: string;
  platform: "TikTok" | "Twitch" | "YouTube";
  title: string;
  category: string;
  viewers: number;
  live: boolean;
  startedAt: string;
  scheduledFor?: string;
};

export type SmpEvent = {
  id: string;
  name: string;
  type: "PvP" | "Build" | "Community" | "Seasonal";
  date: string;
  time: string;
  world: string;
  prize: string;
  slots: number;
  slotsTaken: number;
  blurb: string;
};

export type LeaderboardEntry = {
  rank: number;
  player: string;
  guild: string;
  playtimeHours: number;
  blocks: number;
  kills: number;
  events: number;
  points: number;
  trend: "up" | "down" | "flat";
};

export type MembershipTier = {
  id: string;
  name: string;
  price: number;
  cadence: string;
  blurb: string;
  perks: string[];
  featured?: boolean;
};

export type Achievement = {
  id: string;
  name: string;
  description: string;
  rarity: "Common" | "Rare" | "Epic" | "Legendary";
  unlocked: boolean;
  unlockedAt?: string;
  progress?: number;
};

export type PortalUser = {
  username: string;
  displayName: string;
  email: string;
  minecraftUuid: string;
  rank: string;
  tier: string;
  joinedAt: string;
  discordTag: string | null;
  avatarSeed: string;
};

export type MinecraftStats = {
  playtimeHours: number;
  blocksPlaced: number;
  blocksMined: number;
  mobKills: number;
  deaths: number;
  distanceKm: number;
  eventsWon: number;
  weekly: { day: string; hours: number }[];
};
