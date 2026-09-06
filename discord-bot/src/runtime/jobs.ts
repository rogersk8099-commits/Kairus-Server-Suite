import { ChannelType, type Client, type TextChannel } from "discord.js";
import type { PrismaClient } from "@prisma/client";
import type { AppConfig } from "../config.js";
import type { IdempotencyStore } from "../idempotency.js";
import type { AppLogger } from "../logger.js";
import type { LiveProvider, LiveStream } from "../providers.js";
import { reconcileMemberships } from "./membership.js";

interface Job { name: string; interval: number; work(): Promise<void>; running: boolean; timer?: NodeJS.Timeout; }

export class RuntimeJobs {
  private readonly jobs: Job[];

  public constructor(
    private readonly database: PrismaClient,
    private readonly client: Client,
    config: AppConfig,
    private readonly providers: { youtube: LiveProvider },
    private readonly dedupe: IdempotencyStore,
    private readonly logger: AppLogger
  ) {
    this.jobs = [
      { name: "memberships", interval: config.MEMBERSHIP_INTERVAL_MS, work: () => reconcileMemberships(database, client, config.DISCORD_GUILD_ID, logger), running: false },
      { name: "youtube-reconciliation", interval: config.STREAM_INTERVAL_MS, work: () => this.youtubeStreams(config.DISCORD_GUILD_ID), running: false },
      { name: "reminders", interval: config.REMINDER_INTERVAL_MS, work: () => this.reminders(), running: false },
      { name: "status", interval: config.STATUS_INTERVAL_MS, work: () => this.status(config.DISCORD_GUILD_ID), running: false }
    ];
  }

  public start(): void {
    for (const job of this.jobs) {
      const run = async () => {
        if (job.running) return;
        job.running = true;
        try { await job.work(); }
        catch (error) { this.logger.error({ err: error, job: job.name }, "scheduled job failed"); }
        finally { job.running = false; }
      };
      job.timer = setInterval(() => { void run(); }, job.interval);
      job.timer.unref();
      void run();
    }
  }

  public stop(): void { for (const job of this.jobs) if (job.timer) clearInterval(job.timer); }

  private async textResource(guildId: string, key: string): Promise<TextChannel | null> {
    const row = await this.database.discordSetupResource.findUnique({ where: { guildId_resourceKey: { guildId, resourceKey: key } } });
    if (!row) return null;
    const channel = await this.client.channels.fetch(row.discordId).catch(() => null);
    return channel?.type === ChannelType.GuildText ? channel : null;
  }

  private async youtubeStreams(guildId: string): Promise<void> {
    const rows = await this.database.discordStreamer.findMany({
      where: { guildId, status: "APPROVED", platform: "YOUTUBE", channelExternalId: { not: null } },
      orderBy: { updatedAt: "asc" }
    });
    const youtubeIds = rows.map((row) => row.channelExternalId).filter((id): id is string => id !== null);
    const live = await this.providers.youtube.listLive(youtubeIds);
    await this.publishStreams(guildId, live);
  }

  private async publishStreams(guildId: string, live: readonly LiveStream[]): Promise<void> {
    const channel = await this.textResource(guildId, "text-channel.stream-announcements");
    if (!channel) return;
    for (const stream of live) {
      const key = `live:${guildId}:${stream.platform}:${stream.streamId}`;
      if (!await this.dedupe.claim(key, 7 * 24 * 60 * 60_000, { streamId: stream.streamId })) continue;
      await channel.send({ content: `**${stream.channelName} is live**\n${stream.title}\n${stream.url}`, allowedMentions: { parse: [] } });
      await this.database.discordNotification.create({ data: { guildId, target: "DISCORD", subject: `${stream.channelName} is live`, body: `${stream.title}\n${stream.url}`, dedupeKey: key, status: "SENT", sentAt: new Date(), metadata: { provider: stream.platform, streamId: stream.streamId } } });
    }
  }

  private async reminders(): Promise<void> {
    const rows = await this.database.discordEventReminder.findMany({ where: { dueAt: { lte: new Date() }, deliveredAt: null }, include: { event: true }, take: 100, orderBy: { dueAt: "asc" } });
    for (const row of rows) {
      if (!await this.dedupe.claim(`deliver:${row.dedupeKey}`, 30 * 24 * 60 * 60_000)) continue;
      const channelId = row.event.announcementChannelId;
      const channel = channelId ? await this.client.channels.fetch(channelId).catch(() => null) : null;
      if (channel?.type === ChannelType.GuildText && row.event.status === "SCHEDULED") {
        await channel.send({ content: `**${row.kind === "NOW" ? "Starting now" : `Event reminder (${row.kind})`}: ${row.event.title}**\n${row.event.description}`, allowedMentions: { parse: [] } });
      }
      await this.database.discordEventReminder.update({ where: { id: row.id }, data: { deliveredAt: new Date() } });
    }
  }

  private async status(guildId: string): Promise<void> {
    const [heartbeat, channel] = await Promise.all([this.database.serverHeartbeat.findFirst({ orderBy: { receivedAt: "desc" } }), this.textResource(guildId, "text-channel.server-status")]);
    if (!heartbeat || !channel) return;
    const key = `status:${guildId}:${heartbeat.receivedAt.toISOString()}:${heartbeat.online}:${heartbeat.playerCount}`;
    if (!await this.dedupe.claim(key, 2 * 60 * 60_000)) return;
    await channel.send({ content: `Minecraft is **${heartbeat.online ? "online" : "offline"}** · ${heartbeat.playerCount} player(s) · TPS ${heartbeat.tps.toFixed(2)} · updated <t:${Math.floor(heartbeat.receivedAt.getTime() / 1_000)}:R>`, allowedMentions: { parse: [] } });
  }
}
