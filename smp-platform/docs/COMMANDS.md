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
| `/build` | — | `submit <title> [description]`, `featured`, `review`, `review <submission-id> <under_review\|featured\|rejected\|archived> <note>` | `featured` opens the player showcase; `review` opens the Bedrock-friendly staff queue and requires `smpplatform.admin.creative`. |

## High-risk administration

`/smpadmin hardcore revive|eliminate|status|open-reset|close-reset`, `/smpadmin quarry reset-now|regenerate|evacuate`, `/points set`, moderation actions, and guild disband operations require an explicit confirmation nonce that expires in 30 seconds. Staff commands accept a reason where the action changes another player or persistent world state. Console actors are logged as `CONSOLE`; API actors carry their authenticated actor reference and correlation ID.

## Atrium submissions

`/build submit` first checks PlotSquared on the server. The player must be standing in the configured Atrium world on a claimed plot and must be the plot owner or an added/trusted plot member. Only after that check passes does SMPPlatform create the PostgreSQL submission and audit record. The Fabric menu exposes **Plots → Submit current build for review**; it sends only the title and description, then SMPPlatform repeats the same authority check. Bedrock-friendly menu support remains a separate follow-up.

Staff may run `/build review` to open the in-game chest menu, then mark a build **under review**, **featured**, or **rejected**. The menu uses the same durable review service and audit log as the command route. Staff may also use the full command to move a pending build through `under_review`, `featured`, `rejected`, or `archived`. Final states cannot be overwritten by a later review command. A successful submit or review produces a bridge event when the Control Plane bridge is enabled.

`points.yml` now provides `points.automatic-rewards.featured-build`. It is enabled by default and awards **50 BUILD_POINTS** when a submission is first featured. The durable `submission-id` reward key prevents duplicate payouts across retries or restarts; set `enabled: false` to feature builds without rewarding points.

## References

[1]: file:///home/ubuntu/upload/pasted_content_3.txt "Authoritative SMPPlatform plugin brief"
[2]: file:///home/ubuntu/neon-nexus-hub/src/data/mock.ts "Neon Nexus website product specification"
