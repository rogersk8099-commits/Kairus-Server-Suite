export interface LiveStream {
  platform: "TWITCH" | "YOUTUBE";
  streamId: string;
  channelId: string;
  channelName: string;
  title: string;
  url: string;
  startedAt: Date;
}

export interface LiveProvider { listLive(channelIds: readonly string[]): Promise<LiveStream[]>; }

interface YouTubeSearchItem {
  id: { videoId: string };
  snippet: { channelId: string; channelTitle: string; title: string; publishedAt: string };
}

interface YouTubeVideoItem {
  id: string;
  snippet: { channelId: string; channelTitle: string; title: string; publishedAt: string };
  liveStreamingDetails?: { actualStartTime?: string; actualEndTime?: string };
}

/**
 * Official YouTube Data API reconciliation. `search.list` costs 100 quota units,
 * so each bounded job checks at most `maximumChannelsPerRun` approved channels.
 * Candidate IDs are confirmed through the cheaper `videos.list` operation before
 * a Discord notification is emitted.
 */
export class YouTubeDataProvider implements LiveProvider {
  public constructor(
    private readonly apiKey: string | undefined,
    private readonly fetcher: typeof fetch = fetch,
    private readonly maximumChannelsPerRun = 5
  ) {}

  public async listLive(channelIds: readonly string[]): Promise<LiveStream[]> {
    if (!this.apiKey || channelIds.length === 0) return [];
    const candidates: YouTubeSearchItem[] = [];
    const approved = [...new Set(channelIds)].slice(0, this.maximumChannelsPerRun);
    for (const channelId of approved) {
      const query = new URLSearchParams({ part: "snippet", channelId, eventType: "live", type: "video", maxResults: "5", key: this.apiKey });
      const response = await this.fetcher(`https://www.googleapis.com/youtube/v3/search?${query.toString()}`);
      if (!response.ok) throw new Error(`YouTube search.list returned HTTP ${response.status}`);
      const payload = await response.json() as { items?: YouTubeSearchItem[] };
      candidates.push(...(payload.items ?? []));
    }
    const ids = [...new Set(candidates.map((item) => item.id.videoId))];
    if (ids.length === 0) return [];
    const query = new URLSearchParams({ part: "snippet,liveStreamingDetails", id: ids.join(","), maxResults: "50", key: this.apiKey });
    const response = await this.fetcher(`https://www.googleapis.com/youtube/v3/videos?${query.toString()}`);
    if (!response.ok) throw new Error(`YouTube videos.list returned HTTP ${response.status}`);
    const payload = await response.json() as { items?: YouTubeVideoItem[] };
    return (payload.items ?? [])
      .filter((item) => item.liveStreamingDetails?.actualStartTime && !item.liveStreamingDetails.actualEndTime)
      .map((item) => ({
        platform: "YOUTUBE",
        streamId: item.id,
        channelId: item.snippet.channelId,
        channelName: item.snippet.channelTitle,
        title: item.snippet.title,
        url: `https://www.youtube.com/watch?v=${encodeURIComponent(item.id)}`,
        startedAt: new Date(item.liveStreamingDetails!.actualStartTime!)
      }));
  }
}

/** Twitch live starts arrive through central-api-owned EventSub webhooks. */
export const TWITCH_RUNTIME = Object.freeze({
  mode: "eventsub",
  reconciliation: "helix-get-streams-on-demand",
  automatedPolling: false,
  dedupeHeader: "Twitch-Eventsub-Message-Id"
});

/** TikTok has no approved arbitrary-creator LIVE start/stop API. */
export const TIKTOK_AUTOMATED_DISCOVERY = Object.freeze({ enabled: false, reason: "Disabled until TikTok grants an approved official LIVE capability; scraping, reverse-engineered endpoints, and unofficial feeds are prohibited." });
