# Commands

> **Status:** This is an implementation design, example configuration, and operations runbook for the intended SMPPlatform service. It does **not** claim that a JAR, database, external API, integration, backup, or deployment has been executed. The target is **Paper/Purpur 1.21.4 on Java 21**, with a **server-side-only** plugin. Java and Bedrock players use the same gameplay system; no client mod is required.

## Command contract

Commands are Paper command registrations backed by the same services as the GUI. Syntax below documents intended behavior, not a claim that registrations are currently deployed. All mutating subcommands check permissions, database health, input policy, and confirmations. `<player>` resolves UUID-backed accounts; usernames are only lookup conveniences.

| Root | Aliases | Main subcommands | Notes |
|---|---|---|---|
| `/smpadmin` | `/admin` | `players`, `worlds`, `guilds`, `points`, `hardcore`, `events`, `quarry`, `monitor`, `integrations` | opens or routes to staff GUI |
| `/guild` | `/g` | `create`, `invite`, `join`, `decline`, `leave`, `kick`, `promote`, `demote`, `transfer`, `edit`, `disband`, `info`, `members`, `top` | membership and world policy aware |
| `/guildchat` | `/gc` | `toggle`, `send` | platform guild chat only where enabled |
| `/points` | — | `balance`, `history`, `top`, `add`, `remove`, `set`, `inspect` | staff changes append immutable ledger rows |
| `/profile` | — | `view [player]` | player-facing profile; staff inspection is permission-gated |
| `/link` | — | `create`, `revoke`, `status` | single-use short-lived code, no passwords |
| `/worlds` | — | `list`, `teleport <world-id>`, `info <world-id>` | exact registry IDs |
| `/events` | — | `list`, `info`, `register`, `withdraw`, `checkin` | Colosseum lifecycle controls |

## High-risk administration

`/smpadmin hardcore revive|eliminate|status|open-reset|close-reset`, `/smpadmin quarry reset-now|regenerate|evacuate`, `/points set`, moderation actions, and guild disband operations require an explicit confirmation nonce that expires in 30 seconds. Staff commands accept a reason where the action changes another player or persistent world state. Console actors are logged as `CONSOLE`; API actors carry their authenticated actor reference and correlation ID.

## References

[1]: file:///home/ubuntu/upload/pasted_content_3.txt "Authoritative SMPPlatform plugin brief"
[2]: file:///home/ubuntu/neon-nexus-hub/src/data/mock.ts "Neon Nexus website product specification"
