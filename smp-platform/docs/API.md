# Central API and event outbox

> **Status:** This is an implementation design, example configuration, and operations runbook for the intended SMPPlatform service. It does **not** claim that a JAR, database, external API, integration, backup, or deployment has been executed. The target is **Paper/Purpur 1.21.4 on Java 21**, with a **server-side-only** plugin. Java and Bedrock players use the same gameplay system; no client mod is required.

## Contract and security

The Central API is asynchronous. SMPPlatform never blocks the Paper primary thread for an HTTP request. The API base URL and bearer credential are injected by environment/secret manager; all logs redact authorization and token fields. The plugin authenticates outbound calls, sets a request/correlation ID and an idempotency key, applies a short timeout, validates responses, and treats malformed or unavailable responses as a retryable integration failure rather than a server crash.

## Synchronization

| Direction | Resource | Rule |
|---|---|---|
| API → plugin | world registry | revisioned validated snapshot; local cache remains operational offline |
| plugin → API | player presence/profile summary | asynchronous, UUID/account keyed |
| plugin → API | guilds, points, events, achievements, notifications, membership | post-commit durable outbox event |
| API ↔ plugin | account linking | hashed one-time code, expiration and audit | 
| plugin → API | server/integration health | redacted summary, no secrets |

Recommended routes are versioned, for example `GET /v1/minecraft/world-registry`, `POST /v1/minecraft/events`, `POST /v1/minecraft/account-links/consume`, and `POST /v1/minecraft/status`. Exact URLs and schemas are integration contracts to be agreed with the Central Platform; this document does not invent a production endpoint. A world registry response needs `revision`, `generatedAt`, `definitions`, and a digest/signature policy. A post-commit event contains `eventId`, `eventType`, `aggregateType`, `aggregateId`, `occurredAt`, `correlationId`, `worldId` where relevant, and a schema-versioned payload.

## Durable delivery

The transaction that changes local state inserts an `smp_event_outbox` record. A worker leases pending records, delivers with `Idempotency-Key`, marks the row delivered only after acknowledged success, and retries failure with capped exponential backoff and jitter. Lease expiry permits recovery after a process crash. Poison events reach `DEAD_LETTER` after the configured limit and require staff investigation; they are not discarded. Expected event types include `PLAYER_JOINED`, `PLAYER_LEFT`, `PLAYER_DEATH`, `HARDCORE_DEATH`, `GUILD_CREATED`, `GUILD_UPDATED`, `POINTS_CHANGED`, `ACHIEVEMENT_UNLOCKED`, `EVENT_RESULT`, `QUARRY_RESET`, and `WORLD_STATUS_CHANGED`.[1]

## References

[1]: file:///home/ubuntu/upload/pasted_content_3.txt "Authoritative SMPPlatform plugin brief"
[2]: file:///home/ubuntu/neon-nexus-hub/src/data/mock.ts "Neon Nexus website product specification"
