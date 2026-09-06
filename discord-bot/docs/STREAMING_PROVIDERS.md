# Streaming Provider Behavior

**Verified status:** The dedicated bot uses only official provider interfaces. Twitch delivery is event-driven through the control plane, YouTube is reconciled with bounded official Data API calls, and automated TikTok LIVE detection is disabled.

## Runtime behavior

| Provider | Production behavior | Credential gate | Safety and quota boundary |
|---|---|---|---|
| **Twitch** | The control plane owns official EventSub `stream.online` subscriptions, HMAC-SHA-256 verification, persistence, and durable deduplication by `Twitch-Eventsub-Message-Id`. The bot consumes the authenticated resulting notification. Helix `Get Streams` is reserved for on-demand metadata reconciliation, including viewer count when required; it is not the bot's periodic discovery loop. | Control-plane Twitch application credentials and EventSub subscription secret. The bot does not need Twitch credentials for its normal EventSub notification path. | EventSub is at-least-once, so every message ID must be idempotent. `stream.online` does not include viewer count; query Helix only when that current field is actually needed. |
| **YouTube** | The bot's bounded reconciliation job calls official `search.list` with `eventType=live`, `type=video`, and each approved channel ID. Candidate video IDs are confirmed with official `videos.list` requesting `snippet,liveStreamingDetails`; only records with `actualStartTime` and no `actualEndTime` are announced. | Restricted `YOUTUBE_API_KEY`. Without it, reconciliation safely returns no results. | `search.list` is expensive. A run checks at most five unique approved channels, requests at most five candidates per channel, deduplicates candidate IDs, and performs one batched `videos.list` confirmation. Keep `STREAM_INTERVAL_MS` at a quota-reviewed cadence. |
| **TikTok** | Automated creator LIVE start/stop detection is disabled. A creator-initiated Discord or platform announcement is the compliant fallback. | None. No TikTok LIVE-discovery environment variable exists. | TikTok's documented webhooks and Display API do not expose arbitrary creator LIVE start/stop events. Scraping, browser automation, reverse-engineered endpoints, and unofficial feeds are prohibited. Enable only after TikTok grants an approved official LIVE capability and the integration passes compliance review. |

## Twitch EventSub contract

The preferred deployed model is official Twitch EventSub webhook delivery. The control plane must validate the signature over Twitch's specified message construction, enforce timestamp freshness, process verification challenges, and deduplicate using `Twitch-Eventsub-Message-Id` before creating a downstream notification. Twitch may redeliver the same event. The bot must never infer exactly-once delivery from the webhook transport.

A `stream.online` notification identifies the broadcaster and stream but does not include current viewer count. If a feature needs that count, use official Helix `Get Streams` after the event is accepted and persisted. Do not replace EventSub with an unbounded Helix polling loop.

## YouTube reconciliation contract

YouTube does not provide a dedicated general-purpose live-start webhook. An official channel feed may trigger reconciliation, but it does not replace Data API confirmation. The implemented provider therefore uses `search.list` only for approved channel IDs and then confirms current live state with `videos.list` and `liveStreamingDetails`.

Operators must monitor Google Cloud quota and choose `STREAM_INTERVAL_MS` using the approved-channel count and daily quota budget. When quota is constrained, increase the interval or reduce the approved set rather than broadening or parallelizing searches. Invalid requests also consume quota; credential and channel-ID validation belongs in the approval workflow.

## References

[1]: https://dev.twitch.tv/docs/eventsub/ "Twitch EventSub documentation"
[2]: https://dev.twitch.tv/docs/eventsub/handling-webhook-events/ "Twitch EventSub webhook handling"
[3]: https://dev.twitch.tv/docs/api/reference/#get-streams "Twitch Helix Get Streams"
[4]: https://developers.google.com/youtube/v3/guides/push_notifications "YouTube push notifications"
[5]: https://developers.google.com/youtube/v3/docs/search/list "YouTube search.list"
[6]: https://developers.google.com/youtube/v3/docs/videos "YouTube videos resource"
[7]: https://developers.tiktok.com/doc/webhooks-events?enter_method=left_navigation "TikTok webhook events"
[8]: https://developers.tiktok.com/doc/display-api-overview?enter_method=left_navigation "TikTok Display API"
[9]: https://www.tiktok.com/legal/page/us/terms-of-service/en "TikTok terms of service"
