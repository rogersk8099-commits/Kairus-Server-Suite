# Kairu Discord Bot Operator Guide

**Document status:** Source consolidation, verification, and production activation are complete. The shared control-plane migrations and bot-owned Prisma migration are deployed. Railway `Kairu-Discord-Bot` runs from `rogersk8099-commits/Kairus-Server-Suite` at `/discord-bot`; the gateway is ready on Kairu SMP, and 46 guild commands are registered.

The production guild is **Kairu SMP** (`1546281211876479050`), the application ID is `1546281826341879878`, and the official invite is <https://discord.gg/cbBj6EvcV4>. The final idempotent setup pass reported **Created 0, Reused 54, Updated 4, Failed 0**. Independent verification matched 15 roles, 9 categories, and 34 channels.

## Purpose and operating model

The Kairu Discord bot is a **dedicated Discord service**. It receives Discord application interactions, presents Kairu workflows in Discord, and delegates canonical business decisions and durable domain changes to the **central Kairu API**. The bot is not a second Kairu backend and must not become an alternate source of truth.

The supported production topology has distinct Railway services for the central API and the bot. They may use the same Railway PostgreSQL service only under the ownership and migration rules in [API Integration](API_INTEGRATION.md). External event producers, including a Minecraft plugin and Twitch EventSub, send signed requests to the central API; the central API validates, deduplicates, persists, and then exposes the resulting state or dispatches an authenticated bot notification.

| Component | Primary responsibility | Must not do |
|---|---|---|
| **Discord bot** | Verify and handle Discord interactions; render Discord responses; call the central API with a bot service identity. | Make canonical domain decisions, silently change schema, or store Discord secrets in Discord content. |
| **Central Kairu API** | Own canonical data, authorization, validation, audit records, integration processing, and Prisma migration ownership. | Depend on the bot being available to accept authoritative external events. |
| **PostgreSQL** | Persist canonical Kairu data and migration history. | Be edited manually during routine operation or used as an integration message queue without ownership controls. |
| **Minecraft plugin** | Emit game events and consume only approved game actions. | Hold a Discord bot token or write directly to PostgreSQL. |
| **Railway** | Run independent API and bot services and provide their configured environment variables. | Be treated as a substitute for backups, secret governance, or release approval. |

> **Dormant recovery mode:** Production is active. Removing `DISCORD_TOKEN` intentionally leaves only the HTTP health/webhook service and reports `gateway=dormant`; it does not log in to Discord or start gateway jobs. Use this fail-closed mode during credential rotation or incident containment.

## Documentation map

| Document | Use it when | Outcome |
|---|---|---|
| [Setup](SETUP.md) | Preparing a local or staged release | A repeatable preflight, migration, and activation sequence. |
| [Discord setup](DISCORD_SETUP.md) | Creating the Discord application and installing the bot | Least-privilege portal configuration and an invite procedure. |
| [Commands](COMMANDS.md) | Operating or validating slash commands | Defined `/setup-server` semantics and command authorization boundaries. |
| [Minecraft integration](MINECRAFT_INTEGRATION.md) | Connecting a game server | Signed, idempotent, API-mediated event flow. |
| [API integration](API_INTEGRATION.md) | Connecting the bot to Kairu and external providers | Source-of-truth, authentication, webhook, and provider boundaries. |
| [Streaming providers](docs/STREAMING_PROVIDERS.md) | Activating creator notifications | Verified Twitch EventSub/Helix, YouTube quota, and disabled TikTok behavior. |
| [Environment variables](ENVIRONMENT_VARIABLES.md) | Supplying configuration | Placeholder-only variable inventory and secret handling rules. |
| [Railway deployment](RAILWAY_DEPLOYMENT.md) | Deploying or reverting a service | Separate-service configuration, migration, health, and rollback runbook. |
| [Security](SECURITY.md) | Reviewing or responding to risk | Least privilege, signing, token rotation, and incident response. |
| [Operations](OPERATIONS.md) | Routine support or incident management | Monitoring, rate-limit, backup, restoration, and escalation procedures. |

## Verified implementation status

The production entry point constructs real `PrismaClient`, `ControlPlaneApi`, account adapters, setup repository, idempotency store, HTTP handlers, and runtime jobs. No noop command service is wired into production. The declarative `/setup-server` blueprint is guarded and tested at exactly **9 categories, 34 channels (28 text and 6 voice), and 15 roles**. The 34 display names present in the consolidated blueprint are preserved exactly.

The Prisma schema maps to reviewed Discord tables. A versioned, idempotent bot migration is retained under `prisma/migrations/`, and Railway runs `npm run db:migrate` before application start. The control plane remains the canonical owner of shared domain data. Twitch is EventSub-led through the control plane, YouTube uses bounded `search.list` plus batched `videos.list` confirmation, and TikTok automated discovery remains disabled.

## Required activation order

An operator should use the following sequence without skipping the gates. Each step has a verification criterion; failure means stop and remediate rather than proceeding.

1. **Establish ownership.** Assign a release owner, Discord application owner, Railway project administrator, PostgreSQL backup owner, and incident contact. Record their on-call route outside of the repository.
2. **Create or select isolated non-production resources.** Create a Discord test guild, staging Railway environment, and staging PostgreSQL database. Do not test using a live community guild. Discord recommends a server not actively used by others for development. [1]
3. **Configure the central API first.** Set its production-ready base URL, service authentication, PostgreSQL connection, migration job, health endpoint, backups, and audit logging. The bot must point only to this API endpoint.
4. **Create the Discord application and bot identity.** Follow [Discord setup](DISCORD_SETUP.md); preserve the new bot token in an approved secret manager and never commit it. Discord treats that token as highly sensitive. [1]
5. **Populate runtime variables.** Add only the variables listed in [Environment variables](ENVIRONMENT_VARIABLES.md), using actual values in the platform secret store and placeholders only in documentation or examples.
6. **Apply reviewed migrations.** Deploy ordered control-plane SQL first, then allow the bot's idempotent `prisma migrate deploy` pre-deploy command to reconcile its Discord persistence tables. Never run `prisma db push` in production.
7. **Deploy API and bot as separate services.** Release the API, verify its health, then release the bot and verify its Discord readiness and API connectivity. A successful build is not an activation.
8. **Install the bot in the test guild.** Use the generated least-privilege guild installation link with the `bot` and `applications.commands` scopes. Validate the actual permissions displayed in Discord before authorization. [1]
9. **Run `/setup-server` once, then again.** Confirm the second run reports the same configuration and makes no duplicate channels, roles, webhooks, or records. See [Commands](COMMANDS.md).
10. **Perform a controlled end-to-end test.** Send a harmless Discord interaction, a signed test webhook, and—if enabled—a Minecraft test event. Verify one audit record, one canonical API result, and one expected Discord response.
11. **Approve production activation.** Review monitoring, backup restore evidence, rate-limit metrics, token-rotation record, and rollback target. Then repeat steps 4–10 in production with a maintenance window and named approver.

## Non-negotiable operational boundaries

The bot uses **native slash commands and interaction payloads** rather than reading ordinary message text. Production enables only **Server Members Intent** because membership and role synchronization consume guild-member events. Message Content and Presence remain disabled. Discord requires privileged intents to be enabled in the Developer Portal and, for qualifying verified apps, approved. [3]

Use official provider interfaces only. Twitch notifications use EventSub with HMAC verification. YouTube data uses YouTube Data API v3 with an API key for permitted public read calls or OAuth 2.0 for user-authorized operations. Do **not** implement TikTok scraping, undocumented endpoints, robots, or browser automation. TikTok permits automated collection only as described in its developer documentation and prohibits unauthorized collection and certain automated retrieval uses. [4] [5] [6]

## References

[1]: https://docs.discord.com/developers/quick-start/getting-started "Discord Developer Documentation: Getting Started"
[2]: https://www.prisma.io/docs/orm/prisma-client/deployment/deploy-database-changes-with-prisma-migrate "Prisma Documentation: Deploying database changes with Prisma Migrate"
[3]: https://docs.discord.com/developers/events/gateway "Discord Developer Documentation: Gateway and privileged intents"
[4]: https://dev.twitch.tv/docs/eventsub/handling-webhook-events "Twitch Developer Documentation: Handling webhook events"
[5]: https://developers.google.com/youtube/v3/getting-started "Google Developers: YouTube Data API Overview"
[6]: https://www.tiktok.com/legal/page/global/tik-tok-developer-terms-of-service/en "TikTok Developer Terms of Service"
