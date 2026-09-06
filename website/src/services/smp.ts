/** Kairu control-plane adapter with an explicit, safe development fallback. */
import * as mock from "@/data/mock";
import type {
  Achievement,
  LeaderboardEntry,
  MembershipTier,
  MinecraftStats,
  PortalUser,
  ServerStatus,
  SmpEvent,
  Stream,
  World,
} from "@/data/types";

const API_BASE_URL = (import.meta.env["VITE_SMP_API_URL"] as string | undefined)?.replace(
  /\/$/,
  "",
);
const ENABLE_MOCK_FALLBACK =
  import.meta.env.DEV && import.meta.env["VITE_ENABLE_MOCK_FALLBACK"] !== "false";
const DISCORD_USER_ID = import.meta.env["VITE_DEV_DISCORD_USER_ID"] as string | undefined;
const SERVER_POLL_INTERVAL: number | false = API_BASE_URL ? 30_000 : false;
const STREAM_POLL_INTERVAL: number | false = API_BASE_URL ? 60_000 : false;

type Heartbeat = {
  playerCount: number;
  tps: number;
  version: string;
  uptimeSeconds: number;
  receivedAt: string;
  worlds: string[];
};
type StatusEnvelope = { online: boolean; heartbeat: Heartbeat | null };
type StreamRecord = {
  id: string;
  channelName: string;
  url: string;
  platform: string;
  title: string;
  viewerCount: number;
  live: boolean;
  startedAt: string | null;
};
type EventRecord = {
  id: string;
  title: string;
  description: string;
  startsAt: string;
  endsAt: string | null;
  location: string | null;
  status: string;
};
type Snapshot = {
  minecraftUuid: string;
  name: string;
  playtimeSeconds: number;
  blocksBroken: number;
  kills: number;
  deaths: number;
  distanceMeters: number;
  balance: number;
  rankName: string;
  worldName: string | null;
  updatedAt: string;
};
type TierRecord = {
  slug: string;
  name: string;
  description: string;
  priceMonthlyCents: number;
  benefits: string[];
};
type Link = {
  discordUserId: string;
  minecraftUuid: string;
  javaUsername: string;
  bedrockXuid: string | null;
  linkedAt: string;
};
type AchievementRecord = {
  id: string;
  name: string;
  unlocked: boolean;
  progress: number;
  target: number;
  unit: string;
};

async function request<T>(path: string, fallback: T, player = false): Promise<T> {
  if (!API_BASE_URL) return fallback;
  try {
    const headers: Record<string, string> = { Accept: "application/json" };
    if (player && DISCORD_USER_ID) headers["X-Discord-User-Id"] = DISCORD_USER_ID;
    const response = await fetch(`${API_BASE_URL}${path}`, { headers });
    if (!response.ok) throw new Error(`SMP API request failed (${response.status}) for ${path}`);
    return (await response.json()) as T;
  } catch (error) {
    if (ENABLE_MOCK_FALLBACK) {
      console.warn(`Using development mock fallback for ${path}`, error);
      return fallback;
    }
    throw error;
  }
}

function mapStatus(value: StatusEnvelope): ServerStatus {
  const h = value.heartbeat;
  return {
    online: value.online,
    players: h?.playerCount ?? 0,
    maxPlayers: 400,
    tps: h?.tps ?? 0,
    uptimeDays: Math.floor((h?.uptimeSeconds ?? 0) / 86_400),
    version: h?.version ?? "1.21.4",
    region: "Kairu network",
    lastRestart: h ? new Date(h.receivedAt).toLocaleString() : "No heartbeat",
  };
}
function mapWorlds(names: string[], status: StatusEnvelope): World[] {
  return names.map((name, index) => ({
    id: name.toLowerCase().replace(/[^a-z0-9]+/g, "-") || `world-${index + 1}`,
    name,
    type: index === 0 ? "Survival" : "Resource",
    season: "Current",
    status: status.online ? "Live" : "Archived",
    blurb: "Live world reported by the Kairu Minecraft server.",
    size: "Managed",
    difficulty: "Hard",
    players: index === 0 ? (status.heartbeat?.playerCount ?? 0) : 0,
    accent: (["magenta", "violet", "electric"] as const)[index % 3]!,
  }));
}
function mapStream(s: StreamRecord): Stream {
  const platform = (
    ["TikTok", "Twitch", "YouTube"].includes(s.platform) ? s.platform : "YouTube"
  ) as Stream["platform"];
  return {
    id: s.id,
    creator: s.channelName,
    handle: s.url,
    platform,
    title: s.title,
    category: "Kairu SMP",
    viewers: s.viewerCount,
    live: s.live,
    startedAt: s.startedAt ?? "-",
  };
}
function mapEvent(e: EventRecord): SmpEvent {
  const d = new Date(e.startsAt);
  return {
    id: e.id,
    name: e.title,
    type: "Community",
    date: d.toLocaleDateString(undefined, { day: "numeric", month: "short" }),
    time: d.toLocaleTimeString(undefined, {
      hour: "2-digit",
      minute: "2-digit",
      timeZoneName: "short",
    }),
    world: e.location ?? "Kairu SMP",
    prize: e.status,
    slots: 1,
    slotsTaken: 0,
    blurb: e.description,
  };
}
function mapLeaderboard(players: Snapshot[]): LeaderboardEntry[] {
  return players.map((p, i) => ({
    rank: i + 1,
    player: p.name,
    guild: p.rankName,
    playtimeHours: Math.floor(p.playtimeSeconds / 3_600),
    blocks: p.blocksBroken,
    kills: p.kills,
    events: 0,
    points: Math.floor(p.playtimeSeconds / 3_600) + p.blocksBroken + p.kills * 10,
    trend: "flat",
  }));
}
function mapTier(t: TierRecord): MembershipTier {
  return {
    id: t.slug,
    name: t.name,
    price: t.priceMonthlyCents / 100,
    cadence: t.priceMonthlyCents ? "per month" : "free forever",
    blurb: t.description,
    perks: t.benefits,
    featured: t.slug.toLowerCase() === "ronin",
  };
}
function mapProfile(link: Link, minecraft: Snapshot | null): PortalUser {
  return {
    username: link.javaUsername,
    displayName: link.javaUsername,
    email: "Managed by Discord",
    minecraftUuid: link.minecraftUuid,
    rank: minecraft?.rankName ?? "Member",
    tier: minecraft?.rankName ?? "Member",
    joinedAt: new Date(link.linkedAt).toLocaleDateString(),
    discordTag: link.discordUserId,
    avatarSeed: link.javaUsername.slice(0, 2).toUpperCase(),
  };
}
function mapStats(s: Snapshot | null): MinecraftStats {
  return s
    ? {
        playtimeHours: Math.floor(s.playtimeSeconds / 3_600),
        blocksPlaced: 0,
        blocksMined: s.blocksBroken,
        mobKills: s.kills,
        deaths: s.deaths,
        distanceKm: Math.round(s.distanceMeters / 1_000),
        eventsWon: 0,
        weekly: [],
      }
    : mock.minecraftStats;
}
function mapAchievement(a: AchievementRecord): Achievement {
  const pct = a.target > 0 ? Math.round((a.progress / a.target) * 100) : 0;
  return {
    id: a.id,
    name: a.name,
    description: `${a.progress}/${a.target} ${a.unit}`,
    rarity: a.target >= 1_000 ? "Rare" : "Common",
    unlocked: a.unlocked,
    progress: Math.min(100, pct),
  };
}

export const smpApi = {
  getServerStatus: async (): Promise<ServerStatus> =>
    mapStatus(
      await request<StatusEnvelope>("/api/server/status", {
        online: mock.serverStatus.online,
        heartbeat: {
          playerCount: mock.serverStatus.players,
          tps: mock.serverStatus.tps,
          version: mock.serverStatus.version,
          uptimeSeconds: mock.serverStatus.uptimeDays * 86_400,
          receivedAt: new Date().toISOString(),
          worlds: mock.worlds.map((w) => w.name),
        },
      }),
    ),
  getWorlds: async (): Promise<World[]> => {
    if (!API_BASE_URL) return mock.worlds;
    const [worlds, status] = await Promise.all([
      request<{ worlds: string[] }>("/api/worlds", { worlds: [] }),
      request<StatusEnvelope>("/api/server/status", { online: false, heartbeat: null }),
    ]);
    const mapped = mapWorlds(worlds.worlds, status);
    return mapped.length ? mapped : ENABLE_MOCK_FALLBACK ? mock.worlds : [];
  },
  getStreams: async (): Promise<Stream[]> =>
    (
      await request<{ streams: StreamRecord[] }>("/api/streams", {
        streams: ENABLE_MOCK_FALLBACK
          ? mock.streams.map((s) => ({
              id: s.id,
              channelName: s.creator,
              url: s.handle,
              platform: s.platform,
              title: s.title,
              viewerCount: s.viewers,
              live: s.live,
              startedAt: null,
            }))
          : [],
      })
    ).streams.map(mapStream),
  getEvents: async (): Promise<SmpEvent[]> => {
    if (!API_BASE_URL) return mock.events;
    return (await request<{ events: EventRecord[] }>("/api/events", { events: [] })).events.map(
      mapEvent,
    );
  },
  getLeaderboard: async (): Promise<LeaderboardEntry[]> => {
    if (!API_BASE_URL) return mock.leaderboard;
    return mapLeaderboard(
      (await request<{ players: Snapshot[] }>("/api/leaderboard", { players: [] })).players,
    );
  },
  getMembershipTiers: async (): Promise<MembershipTier[]> => {
    if (!API_BASE_URL) return mock.membershipTiers;
    return (
      await request<{ tiers: TierRecord[] }>("/api/membership/tiers", { tiers: [] })
    ).tiers.map(mapTier);
  },
  getAchievements: async (): Promise<Achievement[]> => {
    if (!API_BASE_URL || !DISCORD_USER_ID) return mock.achievements;
    const body = await request<{ achievements: AchievementRecord[] }>(
      "/api/me/achievements",
      { achievements: [] },
      true,
    );
    return body.achievements.map(mapAchievement);
  },
  getPortalUser: async (): Promise<PortalUser> => {
    if (!API_BASE_URL || !DISCORD_USER_ID) return mock.portalUser;
    const body = await request<{ profile: Link & { minecraft: Snapshot | null } }>(
      "/api/me",
      {
        profile: {
          discordUserId: "",
          minecraftUuid: mock.portalUser.minecraftUuid,
          javaUsername: mock.portalUser.username,
          bedrockXuid: null,
          linkedAt: new Date().toISOString(),
          minecraft: null,
        },
      },
      true,
    );
    return mapProfile(body.profile, body.profile.minecraft);
  },
  getMinecraftStats: async (): Promise<MinecraftStats> => {
    if (!API_BASE_URL || !DISCORD_USER_ID) return mock.minecraftStats;
    const body = await request<{ link: Link; statistics: Snapshot | null }>(
      "/api/me/minecraft",
      {
        link: {
          discordUserId: "",
          minecraftUuid: "",
          javaUsername: "",
          bedrockXuid: null,
          linkedAt: new Date().toISOString(),
        },
        statistics: null,
      },
      true,
    );
    return mapStats(body.statistics);
  },
};

export const queries = {
  serverStatus: {
    queryKey: ["server-status"],
    queryFn: smpApi.getServerStatus,
    refetchInterval: SERVER_POLL_INTERVAL,
  },
  worlds: { queryKey: ["worlds"], queryFn: smpApi.getWorlds },
  streams: {
    queryKey: ["streams"],
    queryFn: smpApi.getStreams,
    refetchInterval: STREAM_POLL_INTERVAL,
  },
  events: { queryKey: ["events"], queryFn: smpApi.getEvents },
  leaderboard: { queryKey: ["leaderboard"], queryFn: smpApi.getLeaderboard },
  tiers: { queryKey: ["tiers"], queryFn: smpApi.getMembershipTiers },
  achievements: { queryKey: ["achievements"], queryFn: smpApi.getAchievements },
  portalUser: { queryKey: ["portal-user"], queryFn: smpApi.getPortalUser },
  minecraftStats: { queryKey: ["mc-stats"], queryFn: smpApi.getMinecraftStats },
};
