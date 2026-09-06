# Kairu SMP Integration Contract

## Scope

The suite targets **Paper or Purpur 1.21.4** with **Geyser and Floodgate** for Bedrock crossplay. It contains one Railway-hosted Node.js control plane that exposes the website API and runs the Discord bot, plus one installable Paper plugin that sends telemetry and completes Discord account links.

## Identity and Authentication

The Paper plugin authenticates outbound requests with `Authorization: Bearer <PLUGIN_API_KEY>` and includes `X-Kairu-Server-Id`. Discord interactions use Discord's gateway through `discord.js`. Website requests use public read-only endpoints. Administrative API requests use `Authorization: Bearer <ADMIN_API_KEY>`.

The Discord `/link` command creates a cryptographically random, single-use, ten-minute code. The player completes linking in Minecraft with `/kairu link <code>`. The plugin submits the code, Minecraft UUID, Java username, and optional Floodgate Bedrock XUID. The control plane binds that identity to the Discord user that created the code.

## Public API

| Method | Path | Purpose |
| --- | --- | --- |
| GET | `/health` | Railway health check |
| GET | `/api/server/status` | Website server telemetry |
| GET | `/api/worlds` | World directory |
| GET | `/api/streams` | Live stream directory |
| GET | `/api/events` | Event calendar |
| GET | `/api/leaderboard` | Ranked player statistics |
| GET | `/api/membership/tiers` | Membership tier configuration |

## Player API

For the initial release, player endpoints accept a Discord user ID through `X-Discord-User-Id`. This header is a development bridge and must be replaced by signed Discord OAuth sessions before production portal accounts are opened publicly.

| Method | Path | Purpose |
| --- | --- | --- |
| GET | `/api/me` | Linked portal profile |
| GET | `/api/me/achievements` | Player achievements |
| GET | `/api/me/minecraft` | Minecraft statistics |

## Plugin API

| Method | Path | Purpose |
| --- | --- | --- |
| POST | `/api/plugin/heartbeat` | Server online state, TPS, player count, world list, version, uptime |
| POST | `/api/plugin/player-snapshot` | UUID, name, playtime, blocks, kills, deaths, distance, balance, rank, world |
| POST | `/api/link-codes/complete` | Complete Discord-to-Minecraft identity link |
| GET | `/api/plugin/commands` | Fetch queued whitelist or notification commands |
| POST | `/api/plugin/commands/:id/ack` | Mark a queued command completed or failed |

## Discord Commands

| Command | Behaviour |
| --- | --- |
| `/link` | Creates a private one-time linking code and in-game instructions |
| `/status` | Shows live server health and player count |
| `/players` | Lists online players from the latest heartbeat |
| `/events` | Lists the next scheduled events |
| `/sync` | Administrator-only role synchronization for a linked member |
| `/unlink` | Removes the caller's Minecraft identity link after confirmation |

## Plugin Commands and Permissions

| Command | Permission |
| --- | --- |
| `/kairu link <code>` | `kairu.link` (default true) |
| `/kairu status` | `kairu.status` (default true) |
| `/kairu reload` | `kairu.admin` (operator) |
| `/kairu sync <player>` | `kairu.admin` (operator) |

The plugin must integrate softly with Vault, LuckPerms, PlaceholderAPI, Geyser, and Floodgate. It must start without any optional plugin present. Network calls must be asynchronous and must never block the Minecraft server thread.

## Data and Security

The control plane uses PostgreSQL when `DATABASE_URL` is set and an in-memory store for local tests. It rate-limits link-code endpoints, validates all request bodies, redacts secrets from logs, and never exposes bot or plugin keys to the browser. The plugin stores secrets only in its server-side `config.yml`. Examples must use placeholders, never real credentials.
