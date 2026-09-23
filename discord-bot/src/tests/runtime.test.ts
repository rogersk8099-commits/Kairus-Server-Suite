import { readFile } from "node:fs/promises";
import { describe, expect, it, vi } from "vitest";
import { loadConfig } from "../config.js";
import { buildHttpServer } from "../http/server.js";
import {
  TIKTOK_AUTOMATED_DISCOVERY,
  TWITCH_RUNTIME,
  YouTubeDataProvider,
} from "../providers.js";
import { SERVER_BLUEPRINT } from "../setup/config/server-blueprint.js";

const baseEnvironment = {
  DISCORD_CLIENT_ID: "123456789012345678",
  DISCORD_GUILD_ID: "223456789012345678",
  DATABASE_URL: "postgresql://dummy:dummy@127.0.0.1:5432/dummy?schema=public",
  API_URL: "https://api.example.test",
  API_SECRET: "test-api-secret-123456789",
  NODE_ENV: "test",
};

describe("consolidated runtime invariants", () => {
  it("locks the exact requested setup counts and preserves every listed channel name", () => {
    expect(SERVER_BLUEPRINT.categories).toHaveLength(15);
    expect(SERVER_BLUEPRINT.roles).toHaveLength(15);
    expect(SERVER_BLUEPRINT.channels).toHaveLength(69);
    expect(
      SERVER_BLUEPRINT.channels.filter(
        (channel) => channel.type === "text-channel",
      ),
    ).toHaveLength(63);
    expect(
      SERVER_BLUEPRINT.channels.filter(
        (channel) => channel.type === "voice-channel",
      ),
    ).toHaveLength(6);
    expect(SERVER_BLUEPRINT.channels.map((channel) => channel.name)).toEqual([
      "welcome",
      "rules",
      "how-to-join",
      "announcements",
      "server-info",
      "faq",
      "general",
      "introductions",
      "screenshots-and-clips",
      "suggestions",
      "server-status",
      "game-chat",
      "marketplace",
      "events",
      "event-chat",
      "stream-announcements",
      "creator-chat",
      "premium-lounge",
      "premium-news",
      "open-a-ticket",
      "support",
      "staff-chat",
      "mod-log",
      "reports",
      "ticket-log",
      "event-stage",
      "creator-lounge",
      "afk",
      "Lounge",
      "Gaming",
      "Survival",
      "Building",
      "Premium Lounge",
      "Staff Meeting",
      "global-chat", "account-links", "achievements", "deaths", "events-global", "server-news",
      "ashfall-chat", "ashfall-news", "ashfall-guilds", "ashfall-deaths", "ashfall-leaderboard", "ashfall-showcase",
      "obsidian-chat", "obsidian-deaths", "obsidian-leaderboard", "obsidian-news", "reset-window",
      "atrium-chat", "build-showcase", "featured-builds", "build-competitions", "atrium-news",
      "colosseum-chat", "event-registration", "match-results", "tournaments", "colosseum-leaderboard", "colosseum-news",
      "quarry-chat", "quarry-reset", "quarry-news",
      "verdance-discussion", "season-history", "verdance-memories", "archived-leaderboard",
    ]);
  });

  it("keeps the HTTP service ready with gateway dormant when DISCORD_TOKEN is absent", async () => {
    const config = loadConfig(baseEnvironment);
    const database = {
      $queryRaw: vi.fn().mockResolvedValue([{ "?column?": 1 }]),
    };
    const state = { gateway: "dormant" as const, shuttingDown: false };
    const server = buildHttpServer({
      config,
      database: database as never,
      idempotency: { claim: vi.fn(), release: vi.fn() },
      logger: false as never,
      state,
    });
    const response = await server.inject({ method: "GET", url: "/health" });
    expect(response.statusCode).toBe(200);
    expect(response.json()).toMatchObject({
      status: "ok",
      database: "ready",
      gateway: "dormant",
    });
    await server.close();
  });

  it("uses actual Prisma/API services in production startup and never wires a noop service", async () => {
    const source = await readFile(
      new URL("../index.ts", import.meta.url),
      "utf8",
    );
    expect(source).toContain(
      "createAccountServices(prisma, api, client, config, logger)",
    );
    expect(source).toContain("new SetupResourceStore(prisma)");
    expect(source).not.toMatch(/Noop|noopServices|createNoop/i);
  });
});

describe("streaming provider policy", () => {
  it("uses EventSub for Twitch and permanently disables automated TikTok discovery", () => {
    expect(TWITCH_RUNTIME).toMatchObject({
      mode: "eventsub",
      automatedPolling: false,
      reconciliation: "helix-get-streams-on-demand",
    });
    expect(TIKTOK_AUTOMATED_DISCOVERY.enabled).toBe(false);
  });

  it("bounds YouTube search.list calls and confirms candidates through videos.list", async () => {
    const fetcher = vi
      .fn<typeof fetch>()
      .mockResolvedValueOnce(
        new Response(
          JSON.stringify({
            items: [
              {
                id: { videoId: "video-1" },
                snippet: {
                  channelId: "channel-1",
                  channelTitle: "Kairu",
                  title: "Live",
                  publishedAt: "2030-01-01T00:00:00Z",
                },
              },
            ],
          }),
          { status: 200 },
        ),
      )
      .mockResolvedValueOnce(
        new Response(
          JSON.stringify({
            items: [
              {
                id: "video-1",
                snippet: {
                  channelId: "channel-1",
                  channelTitle: "Kairu",
                  title: "Live",
                  publishedAt: "2030-01-01T00:00:00Z",
                },
                liveStreamingDetails: {
                  actualStartTime: "2030-01-01T00:00:00Z",
                },
              },
            ],
          }),
          { status: 200 },
        ),
      );
    const provider = new YouTubeDataProvider(
      "youtube-api-key-123456",
      fetcher,
      1,
    );
    const live = await provider.listLive(["channel-1", "channel-2"]);
    expect(fetcher).toHaveBeenCalledTimes(2);
    expect(fetcher.mock.calls[0]?.[0]).toContain("/youtube/v3/search?");
    expect(fetcher.mock.calls[1]?.[0]).toContain("/youtube/v3/videos?");
    expect(live).toHaveLength(1);
    expect(live[0]).toMatchObject({
      platform: "YOUTUBE",
      streamId: "video-1",
      channelId: "channel-1",
    });
  });
});
