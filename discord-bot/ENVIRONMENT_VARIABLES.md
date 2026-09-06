# Environment Variables and Secret Handling

**Document status:** These are the exact names accepted by `src/config.ts`. Unknown names are rejected. Values shown in `.env.example` are placeholders, not credentials or deployment evidence.

## Required process configuration

| Variable | Required to start HTTP service | Required for Discord activation | Purpose |
|---|---:|---:|---|
| `DISCORD_CLIENT_ID` | Yes | Yes | Discord application/client ID used for command registration. |
| `DISCORD_GUILD_ID` | Yes | Yes | Dedicated guild ID used by setup, jobs, and guild-scoped command registration. |
| `DATABASE_URL` | Yes | Yes | PostgreSQL connection used by the generated Prisma client. The control plane, not this bot, owns all DDL migrations. |
| `API_URL` | Yes | Yes | Base URL of the central Kairu control-plane API. |
| `API_SECRET` | Yes | Yes | Scoped bot-to-control-plane service secret; minimum 16 characters. |
| `DISCORD_TOKEN` | No | Yes | Discord bot token. When absent/blank, HTTP starts and reports `gateway=dormant`; no gateway login or jobs start. |

The implemented names are intentionally **not** `DISCORD_APPLICATION_ID`, `DISCORD_BOT_TOKEN`, `KAIRU_API_BASE_URL`, or `KAIRU_BOT_API_TOKEN`. Do not configure those aliases unless the code is deliberately changed and revalidated.

## Optional runtime settings

| Variable | Default | Purpose |
|---|---:|---|
| `NODE_ENV` | `development` | One of `development`, `test`, or `production`. |
| `LOG_LEVEL` | `info` | One of `fatal`, `error`, `warn`, `info`, `debug`, `trace`, or `silent`. |
| `HOST` | `0.0.0.0` | HTTP bind host. |
| `PORT` | `8080` | HTTP bind port. |
| `API_TIMEOUT_MS` | `10000` | Control-plane request timeout. |
| `API_MAX_RETRIES` | `3` | Bounded retries for transient control-plane failures. |
| `WEBHOOK_SECRET` | unset | Enables Kairu-signed webhook routes; minimum 16 characters when set. |
| `WEBHOOK_MAX_AGE_SECONDS` | `300` | Webhook timestamp freshness window. |
| `WEBHOOK_RATE_LIMIT_PER_MINUTE` | `60` | Per-source/IP in-process webhook limit. |
| `MEMBERSHIP_INTERVAL_MS` | `900000` | Membership reconciliation interval. |
| `STREAM_INTERVAL_MS` | `300000` | YouTube reconciliation interval; size using the quota budget. |
| `REMINDER_INTERVAL_MS` | `60000` | Event reminder delivery interval. |
| `STATUS_INTERVAL_MS` | `60000` | Minecraft status publication interval. |
| `TWITCH_CLIENT_ID` | unset | Reserved for approved on-demand Helix reconciliation. Twitch discovery itself is EventSub-led through the control plane. |
| `TWITCH_CLIENT_SECRET` | unset | Reserved for approved on-demand Helix reconciliation; minimum 16 characters when set. |
| `YOUTUBE_API_KEY` | unset | Restricted official YouTube Data API key; minimum 16 characters when set. |

There is no TikTok discovery variable. Automated TikTok LIVE detection is disabled until TikTok grants an approved official capability. Provider and EventSub secrets owned by the control plane do not belong in this bot's environment.

## Secret handling and migration boundary

Store production values only in the platform secret store. Never commit `.env`, log values, or paste credentials into Discord. The bot must not run `prisma migrate`, `prisma db push`, or any DDL command. The control-plane SQL release process owns schema changes; this bot only formats/validates its mapping and generates the client.
