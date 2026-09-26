# Kairu Control Plane

**Kairu Control Plane** is the Railway-hosted Node.js 22 service for the Kairu SMP website API, Discord OAuth identities and sessions, Paper/Purpur telemetry, queued platform chat, and server commands. The HTTP API always starts. The legacy embedded gateway remains optional, while the full Discord ecosystem bot is deployed as the dedicated `discord-bot` service.

## Architecture

The service uses **Fastify**, **Zod**, **PostgreSQL via `pg`**, and **discord.js**. It writes state to PostgreSQL whenever `DATABASE_URL` is configured and otherwise selects the deterministic in-memory store. This makes local API tests independent of infrastructure while Railway runs retain durable state. The database implementation uses parameterized queries and completes link-code consumption plus identity binding in a database transaction.

| Concern | Implementation |
| --- | --- |
| HTTP platform | Fastify with structured Pino JSON logs, request IDs, redaction, CORS, and rate limits |
| Validation | Strict Zod schemas for telemetry, snapshots, link completion, acknowledgements, and administrative command queuing |
| Authentication | Discord OAuth authorization code with PKCE and one-use states/tickets; hashed revocable website sessions; constant-time bearer authentication for service endpoints |
| Storage | PostgreSQL schema migrations; in-memory store when `DATABASE_URL` is absent |
| Identity linking | Cryptographically random 20-byte code, SHA-256 storage hash, ten-minute expiry, one-time transactional consumption |
| Discord | discord.js gateway bot, deployable slash-command registration script, ephemeral link/unlink interaction responses |
| Operations | `/health`, Dockerfile, Railway configuration, migration command, SIGINT/SIGTERM graceful shutdown |

## Quick start

Install Node.js **22**. Copy the example environment file, set development-only secrets, then install and run the API. With no `DATABASE_URL`, the server reports `"storage":"memory"` and is appropriate for development and tests only.

```bash
cd /home/ubuntu/kairu-suite/control-plane
cp .env.example .env
# Edit .env. Do not commit it.
npm install
npm run dev
```

Verify the process from another terminal:

```bash
curl http://localhost:3000/health
```

The API is served on `0.0.0.0:3000` by default. Override `HOST` and `PORT` as needed. `npm run start` starts compiled production output after `npm run build`.

## Configuration

No secret is included in this repository. Replace every placeholder with a generated value in your deployment provider’s private environment-variable settings.

| Variable | Required | Purpose |
| --- | --- | --- |
| `NODE_ENV` | No | `development`, `test`, or `production`; defaults to `development`. |
| `HOST`, `PORT` | No | Bind address and port. Railway supplies `PORT`; the application honors it. |
| `LOG_LEVEL` | No | Pino threshold; default is `info`. |
| `CORS_ORIGIN` | Yes for browser use | Comma-separated allowed website origins. Do not use `*` with a public player portal. |
| `DATABASE_URL` | Yes in Railway | PostgreSQL connection URL. Omit only for non-persistent local development/tests. |
| `PLUGIN_API_KEY` | Yes for plugin API | Long random bearer secret used by the Paper plugin. |
| `ADMIN_API_KEY` | Yes for administrative queue API | Separate long random bearer secret. Never expose it to clients. |
| `WEBSITE_API_SECRET` | Yes for website auth | Dedicated website-to-control-plane bearer secret; must differ from plugin and administrative credentials. |
| `SESSION_SECRET` | Yes for OAuth | HMAC key for OAuth states, login tickets, and sessions; use the matching value on the website service. |
| `WEBSITE_URL` | Yes for OAuth | Exact public HTTPS website origin without a trailing slash or path. |
| `DISCORD_OAUTH_CLIENT_ID` | Yes for OAuth | Discord application snowflake used by the website sign-in flow. |
| `DISCORD_OAUTH_CLIENT_SECRET` | Yes for OAuth | Discord OAuth client secret; control-plane secret store only. |
| `DISCORD_OAUTH_REDIRECT_URI` | Yes for OAuth | Exact callback URI, normally `https://<website>/auth/discord/callback`. |
| `DISCORD_BOT_TOKEN` | No | Discord bot token. Its presence starts the bot after the API begins listening. |
| `DISCORD_APPLICATION_ID` | With bot token | Discord application snowflake; required for command registration and validated at startup. |
| `DISCORD_GUILD_ID` | No | Development guild target for immediate command updates. Omit for global commands. |
| `MEMBERSHIP_ROLE_MAP` | No | JSON object mapping Minecraft rank names to Discord role snowflakes for `/sync`. |

Generate values locally with a password manager or `openssl rand -base64 48`; do not paste generated secrets into source, README examples, Discord messages, browser bundles, or logs.

## API reference

All responses are JSON. Validation, authorization, and internal failures return an envelope such as `{"error":{"code":"VALIDATION_ERROR","message":"..."},"requestId":"..."}`. The request ID can be supplied with `X-Request-Id`, or taken from server logs for troubleshooting.

### Public website API

| Method | Endpoint | Result |
| --- | --- | --- |
| `GET` | `/health` | Process liveness, selected storage driver, and timestamp. Railway health check target. |
| `GET` | `/api/server/status` | Latest heartbeat and an `online` projection; safely false without telemetry. |
| `GET` | `/api/worlds` | Worlds from the latest heartbeat. |
| `GET` | `/api/streams` | Active stream directory. |
| `GET` | `/api/events` | Upcoming scheduled events. |
| `GET` | `/api/leaderboard` | Up to 100 players ordered by recorded playtime. |
| `GET` | `/api/membership/tiers` | Configured membership tiers. |

### Website authentication API

The website calls the internal OAuth endpoints with `WEBSITE_API_SECRET`. The browser never calls these endpoints directly and never receives a provider token, API secret, raw session token, or login ticket.

| Method | Endpoint | Result |
| --- | --- | --- |
| `POST` | `/internal/auth/oauth/states` | Creates a short-lived, HMAC-hashed OAuth state and PKCE challenge. |
| `POST` | `/internal/auth/discord/callback` | Atomically consumes state, exchanges the Discord code, upserts the platform user, and returns a one-use ticket. |
| `POST` | `/internal/auth/tickets/redeem` | Consumes the ticket and creates a hashed, revocable session. |
| `POST` | `/internal/auth/sessions/validate` | Validates a server-held session and returns the current platform principal. |
| `POST` | `/internal/auth/sessions/revoke` | Revokes the current session during logout. |
| `POST` | `/internal/auth/demo` | Non-production development helper only. Disabled in production. |

### Player portal API

Private portal calls use the validated platform session principal. A request without a current linked Minecraft identity returns `404 NOT_LINKED` where link data is required.

| Method | Endpoint | Result |
| --- | --- | --- |
| `GET` | `/api/me` | Link identity with the most recent Minecraft snapshot, if any. |
| `GET` | `/api/me/achievements` | Derived milestone progress for playtime, blocks broken, and kills. |
| `GET` | `/api/me/minecraft` | Link identity and full latest Minecraft statistics. |

### Paper/Purpur plugin API

Every plugin request must carry both `Authorization: Bearer <PLUGIN_API_KEY>` and `X-Kairu-Server-Id: <server-id>`. Use a distinct server identifier such as `primary` or `season-2`. Requests have endpoint-specific rate limiting and strict payload validation.

| Method | Endpoint | Required payload/result |
| --- | --- | --- |
| `POST` | `/api/plugin/heartbeat` | `online`, `tps`, `playerCount`, `worlds`, optional `players`, `version`, and `uptimeSeconds`. Saves current server status. |
| `POST` | `/api/plugin/player-snapshot` | UUID, Java name, playtime, blocks, kills, deaths, distance, balance, rank, and optional world. Upserts player statistics. |
| `POST` | `/api/link-codes/complete` | One-time Discord code, Minecraft UUID/name, optional Floodgate XUID. Creates identity link once. |
| `GET` | `/api/plugin/commands` | Retrieves only queued commands for the requesting server. |
| `POST` | `/api/plugin/commands/:id/ack` | Marks a command `completed` or `failed`; failed acknowledgements require `errorMessage`. |
| `POST` | `/api/plugin/bridge-events` | Stores a bounded, idempotent Minecraft chat or lifecycle event for central routing. |
| `GET` | `/api/plugin/chat/queued` | Returns only queued Discord-to-Minecraft messages for the authenticated server ID. |
| `POST` | `/api/plugin/chat/:id/ack` | Atomically marks one server-scoped chat item delivered or rejected. |

A heartbeat example, with placeholder values only:

```bash
curl -X POST "$CONTROL_PLANE_URL/api/plugin/heartbeat" \
  -H "Authorization: Bearer $PLUGIN_API_KEY" \
  -H "X-Kairu-Server-Id: primary" \
  -H "Content-Type: application/json" \
  --data '{"online":true,"tps":20,"playerCount":0,"worlds":["world"],"players":[],"version":"Paper 1.21.4","uptimeSeconds":42}'
```

### Administrative queue API

`POST /api/admin/plugin-commands` and `POST /api/admin/chat` require `Authorization: Bearer <ADMIN_API_KEY>`. They queue strictly validated, server-scoped operations. These endpoints are server-side only; no browser should possess `ADMIN_API_KEY`.

```json
{
  "serverId": "primary",
  "commandType": "notification",
  "payload": { "player": "Steve", "message": "Restart in five minutes" }
}
```

## Discord bot

Create a Discord application and bot in the Discord Developer Portal. Enable the `applications.commands` and `bot` OAuth2 scopes when inviting it. Grant **Manage Roles** only if `/sync` will be used, and place the bot’s highest role above every mapped membership role. Set `DISCORD_BOT_TOKEN` and `DISCORD_APPLICATION_ID`; then register commands.

```bash
# Uses DISCORD_GUILD_ID for near-immediate development registration when set.
npm run register-commands
```

| Command | Behavior |
| --- | --- |
| `/link` | Creates an ephemeral, single-use code valid for ten minutes and shows `/kairu link <code>` instructions. The code is never logged. |
| `/status` | Displays online state, player count, TPS, version, and heartbeat time. |
| `/players` | Lists names included in the latest online heartbeat. |
| `/events` | Shows up to ten next scheduled events. |
| `/sync member:@user` | Requires the invoking member to have **Manage Roles**. It maps the linked player’s rank using `MEMBERSHIP_ROLE_MAP` and ensures that role is assigned. |
| `/unlink` | Shows a private confirmation button and removes the caller’s Minecraft identity link only after confirmation. |

Global Discord command updates can take up to an hour to propagate. Use `DISCORD_GUILD_ID` while developing to avoid that delay. The bot is not started at all when `DISCORD_BOT_TOKEN` is absent, which leaves the API usable for website and plugin-only deployments.

## Database and migrations

Ordered SQL migrations under `migrations/` define telemetry, public content, Discord platform resources, chat/event relay state, and website OAuth identities/sessions. `004_website_auth.sql` adds platform users, one-use OAuth states and login tickets, hashed sessions, and cleanup indexes/functions. Apply migrations against a configured PostgreSQL URL with:

```bash
DATABASE_URL='postgresql://USER:PASSWORD@HOST:5432/DBNAME' npm run migrate:dev
```

The compiled `npm run migrate` command is used by Railway’s pre-deploy phase. The migration runner records each successful SQL filename in `schema_migrations`, making re-runs idempotent. Add immutable, ordered SQL files such as `002_seed_tiers.sql` for subsequent changes; never edit a migration that has reached production.

## Railway deployment

Create a Railway project from this directory, attach a Railway PostgreSQL service, and provide the environment variables listed above. `railway.toml` builds with the supplied Dockerfile, runs `node dist/scripts/migrate.js` before deployment, starts `node dist/src/index.js`, and checks `/health` for up to 30 seconds. Set `CORS_ORIGIN` to the exact deployed website origin or origins.

The Docker build uses Node.js 22, compiles TypeScript in a build stage, and installs production dependencies only in the runtime stage. Do not deploy without `DATABASE_URL`, `PLUGIN_API_KEY`, and `ADMIN_API_KEY`: missing plugin/admin secrets intentionally make their protected endpoints return `503` instead of silently accepting unauthenticated traffic.

## Security and operational safeguards

- API logs are structured JSON and redact bearer credentials, the temporary Discord header, link codes, Bedrock XUIDs, and configured secrets.
- Secrets are compared in constant time after bearer parsing. PostgreSQL queries use parameters rather than string interpolation.
- Link codes use 160 bits of cryptographic randomness, are SHA-256 hashed before storage, expire in ten minutes, and cannot be used twice.
- Public browser responses never contain bot tokens, plugin keys, or administrative keys. The Paper plugin must keep `PLUGIN_API_KEY` only in server-side `config.yml`.
- The default global limit is 300 requests per minute per client IP. High-risk link completion is additionally limited to 12 per minute. Tune limits based on trusted proxy configuration and actual plugin heartbeat volume.
- Graceful shutdown stops Discord, stops accepting Fastify traffic, closes PostgreSQL, and has a ten-second hard-stop guard for platform termination.

> If Railway is behind a proxy, configure the platform’s forwarded client IP behavior before relying on IP-based rate limiting for abuse enforcement. The implementation’s limits still protect direct deployments and provide a baseline control.

## Verification and development commands

| Command | Purpose |
| --- | --- |
| `npm run dev` | Watch-run TypeScript locally. |
| `npm run build` | Emit production JavaScript under `dist/`. |
| `npm run start` | Run the compiled control plane. |
| `npm run typecheck` | Strict TypeScript validation without emitting files. |
| `npm test` | Fastify `inject()` test suite; no port or database required. |
| `npm run migrate:dev` | Run migrations from TypeScript source. |
| `npm run migrate` | Run compiled migration runner. |
| `npm run register-commands` | Register guild or global Discord slash commands. |

Run the release checks before deployment:

```bash
npm install
npm test
npm run typecheck
npm run build
```

The test suite verifies health, all public directory endpoints, plugin authorization and validation, heartbeat visibility, snapshots and leaderboard behavior, link completion/replay rejection, player portal behavior, administrative command queueing, acknowledgements, player-header validation, and configured-origin CORS using Fastify’s in-process `inject()` API.

## Contract notes and scope boundary

This repository is the control plane only. The Paper/Purpur plugin remains responsible for asynchronous network delivery, its `/kairu` commands, optional Vault/LuckPerms/PlaceholderAPI/Geyser/Floodgate soft integrations, and never blocking Minecraft’s main thread. The control plane accepts optional Floodgate XUID values, but does not require optional Minecraft plugins.

**Before public portal launch, register the exact Discord callback URI, provision independent secrets, replay-test migrations against staging, and complete the OAuth threat-model and log-redaction review.**

## References

[1]: ../CONTRACT.md "Kairu SMP Integration Contract"
[2]: https://fastify.dev/docs/latest/ "Fastify documentation"
[3]: https://discord.js.org/ "discord.js documentation"
[4]: https://docs.railway.com/ "Railway documentation"


## Auction ownership

Auction persistence is owned by the Control Plane. Minecraft does **not** need a direct
PostgreSQL connection for auction listings, bids, or deliveries.

Production flow:

`Kairu Client -> SMPPlatform -> Control Plane /api/plugin/auction -> PostgreSQL`

Required Railway variables remain `DATABASE_URL` and `PLUGIN_API_KEY`. SMPPlatform must use
the same `PLUGIN_API_KEY` as `SMPPLATFORM_API_TOKEN` and its central API base URL must point
at this Control Plane service. Run `npm run migrate` after deployment so
`008_auction_platform.sql` creates the auction tables.

The plugin endpoint requires both `Authorization: Bearer <PLUGIN_API_KEY>` and
`X-Kairu-Server-Id`.
