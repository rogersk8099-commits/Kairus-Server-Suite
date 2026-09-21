# Repository cleanup

## Keep

| Path | Why it remains |
|---|---|
| `smp-platform/` | Authoritative Minecraft gameplay and persistent-platform source. |
| `paper-plugin/` | KairuBridge transport source; deliberately separate from gameplay. |
| `control-plane/` | Railway API and PostgreSQL contract. |
| `discord-bot/` | Discord setup, channel mapping and status presentation. |
| `website/` | Player portal and website integration. |
| `server-pack/` | Host installation templates and release-artifact location. |
| `kairu-client/` | Optional Fabric UI for Java players. |

## Removed

The byte-for-byte duplicate Discord bot documents under `discord-bot/docs/` were removed. The canonical versions remain directly under `discord-bot/`.

## Do not treat as source

`server-pack/plugins/KairuBridge.jar` and `server-pack/plugins/SMPPlatform.jar` are historical release artifacts. They are useful only for a documented tested release; source modules remain authoritative.

## Client integration boundary

The retained Fabric UI speaks the `KAIRU_ADMIN_V2` protocol. SMPPlatform now provides the matching server-authoritative gateway for state, permitted world travel, basic player/world administration including cache-persisted maintenance and Hub evacuation, the caller's guild dashboard, guild invite/member/rank/leave actions, two-step ownership transfer, and a durable guild leaderboard. It also provides points balances, a player's ledger and per-currency player leaderboards, plus an opt-in, database-idempotent first-join reward source. Advanced PlotSquared edits remain a future gateway addition. Every request is still permission-checked by SMPPlatform so Java, Bedrock and command users follow the same rules.
