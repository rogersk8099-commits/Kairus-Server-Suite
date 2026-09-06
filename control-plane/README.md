# Kairu Control Plane

**Kairu Control Plane** is the Railway-hosted Node.js 22 service for the Kairu SMP website API, Paper/Purpur telemetry integration, Discord identity linking, queued server commands, and Discord slash commands. It implements the API and bot behavior in the repository integration contract. The HTTP API always starts. The Discord gateway client is deliberately optional and starts only when `DISCORD_BOT_TOKEN` is configured.

> The initial player portal uses `X-Discord-User-Id` as a **development bridge**. It is not browser authentication. Replace it with signed Discord OAuth sessions before opening player portal endpoints publicly.

## Architecture

The service uses **Fastify**, **Zod**, **PostgreSQL via `pg`**, and **discord.js**. It writes state to PostgreSQL whenever `DATABASE_URL` is configured and otherwise selects the deterministic in-memory store. This makes local API tests independent of infrastructure while Railway runs retain durable state. The database implementation uses parameterized queries and completes link-code consumption plus identity binding in a database transaction.

| Concern | Implementation |
| --- | --- |
| HTTP platform | Fastify with structured Pino JSON logs, request IDs, redaction, CORS, and rate limits |
| Validation | Strict Zod schemas for telemetry, snapshots, link completion, acknowledgements, and administrative command queuing |
| Authentication | Constant-time comparison of bearer tokens for plugin and administrative endpoints |
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

### Player portal API

The temporary bridge requires an `X-Discord-User-Id` header containing a Discord snowflake. A request without a completed link returns `404 NOT_LINKED`.

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

A heartbeat example, with placeholder values only:

```bash
curl -X POST "$CONTROL_PLANE_URL/api/plugin/heartbeat" \
  -H "Authorization: Bearer $PLUGIN_API_KEY" \
  -H "X-Kairu-Server-Id: primary" \
  -H "Content-Type: application/json" \
  --data '{"online":true,"tps":20,"playerCount":0,"worlds":["world"],"players":[],"version":"Paper 1.21.4","uptimeSeconds":42}'
```

### Administrative queue API

`POST /api/admin/plugin-commands` requires `Authorization: Bearer <ADMIN_API_KEY>`. It queues a strictly validated `whitelist` (`action` and `player`) or `notification` (`player` and `message`) command for one server. This endpoint is intentionally server-side only; no browser should possess `ADMIN_API_KEY`.

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

The schema is in [`migrations/001_initial.sql`](migrations/001_initial.sql). It includes heartbeats, player snapshots, hashed link codes, links, queued plugin commands, events, streams, tiers, and a migration ledger. Apply migrations against a configured PostgreSQL URL with:

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

**Before public portal launch, replace the temporary Discord-ID header bridge with signed OAuth sessions, add CSRF protections appropriate to the session design, and perform a threat-model review.**

## References

[1]: ../CONTRACT.md "Kairu SMP Integration Contract"
[2]: https://fastify.dev/docs/latest/ "Fastify documentation"
[3]: https://discord.js.org/ "discord.js documentation"
[4]: https://docs.railway.com/ "Railway documentation"
