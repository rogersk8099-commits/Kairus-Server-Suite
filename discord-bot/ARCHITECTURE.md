# Kairu Discord Integration Architecture

## Decision

The Discord integration runs as a **dedicated Railway service**. It shares the Kairu PostgreSQL database with the central control plane and calls the control plane through authenticated HTTPS for Minecraft and website operations. Discord is a communication surface, not a source of truth.

| Concern | System of record | Discord bot responsibility |
| --- | --- | --- |
| Discord resource IDs and guild policy | PostgreSQL | Reconcile categories, channels, roles, and permission overwrites idempotently. |
| Platform identities | Central PostgreSQL tables | Create secure link requests, display profiles, and reconcile verified roles. |
| Minecraft telemetry and commands | Control-plane API and KairuBridge | Display status and enqueue bounded server actions. |
| Membership status | Central PostgreSQL membership records | Add or remove configured Discord roles from current membership state. |
| Events, registrations, polls, and votes | PostgreSQL | Expose commands and components while enforcing central uniqueness constraints. |
| Streams and live state | Approved provider APIs and PostgreSQL | Publish deduplicated notifications and temporary live roles. |
| Moderation, reports, tickets, and audit records | PostgreSQL | Enforce Discord permissions and create durable audit records. |

## Runtime

The service uses Node.js 22, TypeScript, Discord.js, Prisma, PostgreSQL, Fastify, Zod, and Pino. One long-running Discord gateway process also exposes an HTTP health endpoint and authenticated webhook receivers. Background reconciliation jobs use database-backed deduplication and bounded polling. The process handles `SIGTERM` for Railway deployments and closes Discord, HTTP, timers, and Prisma cleanly.

## Trust boundaries

The bot receives `DISCORD_TOKEN`, `DATABASE_URL`, `API_URL`, and `API_SECRET` only as Railway service variables. It never sends these values to Discord messages, the website, Minecraft players, logs, or repository files. `DISCORD_CLIENT_ID` and `DISCORD_GUILD_ID` are identifiers rather than credentials, but they are still configurable.

Minecraft servers authenticate to the existing control plane with the separate plugin key. Webhooks into the bot use an HMAC SHA-256 signature, a timestamp window, replay protection, request-body limits, schema validation, and a durable idempotency key. Discord role and channel identifiers are discovered or created at runtime and stored in PostgreSQL.

## Availability and failure isolation

A failed provider request, missing channel, deleted role, or rejected Discord action is isolated to one operation. Transient HTTP calls retry with bounded exponential backoff. Reconciliation jobs continue after individual failures and produce structured logs. Setup and sync operations return a resource-by-resource summary rather than failing without context.

## Activation gate

The service can build, migrate, and pass HTTP health checks without a Discord token. Gateway login and command registration require a real Discord application token and client ID. Server creation and permission changes require an authorised administrator to run `/setup-server` after the bot has been invited with the documented permissions.
