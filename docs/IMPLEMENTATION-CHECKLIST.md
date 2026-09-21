# Kairu SMP implementation checklist

Status is deliberately based on source wired into `SMPPlatform`, not on unregistered scaffolding
elsewhere in the repository. A checked item still requires Java 25 compilation and Paper staging
validation before deployment.

## Server and client foundation

| Area | Status | Current implementation |
|---|---:|---|
| Seven-world registry and Spawn Hub | Done | Hub is a permanent `HUB` world; it is the safe evacuation target and has no dedicated Discord category. |
| Fabric Kairu client | Done | Custom purple/neon UI uses the permission-checked `KAIRU_ADMIN_V2` SMPPlatform gateway. |
| Bedrock-friendly server path | Partial | Server commands and inventory guide remain usable without Fabric; full Bedrock forms are not yet wired. |
| Compilation/staging validation | Not done | Requires Java 25, Gradle dependencies, and a Paper 26.2 staging server. |

## Guilds and points

| Area | Status | Current implementation |
|---|---:|---|
| Guild dashboard, members and ranks | Done | Invite, kick, promote, demote and leave run through the transactional GuildService. |
| Ownership transfer | Done | Client uses a server-held 30-second confirmation; command requires explicit `confirm`. |
| Guild leaderboard/statistics | Done | Top 20 ranks by durable guild points with member counts. |
| Configurable custom ranks/permissions | Not done | Current fixed ranks are Leader, Officer, Member and Recruit. |
| Point balances/history/player leaderboard | Done | PostgreSQL ledger views in the client. |
| Automatic rewards | Partial | First-join reward is configurable and disabled by default; database reservation prevents duplicate payout. |
| Other automatic sources/guild-point rules | Not done | Gameplay, event, creative and guild reward policies still need individual designs. |

## World administration

| Area | Status | Current implementation |
|---|---:|---|
| Player travel access filtering | Done | Menu lists only registered, available worlds the player may access. |
| Time/weather/PvP/difficulty/gamerules | Done | Permission-checked controls for registered loaded worlds. |
| Maintenance and evacuation | Done | Persisted local registry override; maintenance blocks travel and evacuates to Spawn Hub. |
| Multiverse load/unload | Done | Load plus confirmation-backed unload; Hub cannot be unloaded. |
| Multiverse create/import | Not done | Needs explicit validated world name, environment, seed and import-path workflow. |
| Inventory groups, Nether/End setup | Partial | Policy/configuration foundation exists; operational setup controls are not exposed. |

## Gameplay systems

| Area | Status | Current implementation |
|---|---:|---|
| Atrium readable plot guide | Done | `/plotflags` explains player-facing settings. |
| Actual PlotSquared flag editor | Not done | PlotSquared is not yet a compile-time adapter; no fake editor is exposed. |
| Hardcore death/reset/leaderboard/admin GUI | Scaffold only | Lifecycle domain exists but only has an in-memory repository; do not enable for production. |
| Quarry lock/evacuation/reset/backup workflow | Scaffold only | Lifecycle domain exists but is not wired to durable production storage. |
| Atrium review/showcase/competitions/build points | Scaffold only | Domain code exists but production adapters/UI remain unwired. |
| Colosseum event flow | Scaffold only | Domain/configuration exists; production event runner/UI remains unwired. |
| Verdance archive/snapshot GUI | Scaffold only | Archive policy exists; durable snapshot workflow remains unwired. |

## Integrations

| Area | Status | Current implementation |
|---|---:|---|
| Control Plane outbox foundation | Partial | PostgreSQL outbox is enabled when database and API configuration are healthy. |
| Discord channel mapping/status embed/chat bridge | Not done in SMPPlatform | Requires completing the separate Discord bot and Control Plane integration. |
| PlaceholderAPI/Skript hooks | Partial | Existing adapters/scaffolding require final bootstrap and test coverage. |
| Command documentation and integration tests | Partial | Source documentation exists; full command reference and live integration tests remain. |

## Next safe implementation order

1. Compile with Java 25 and run Paper staging smoke tests for the completed gateway work.
2. Add durable JDBC repositories, migrations and startup wiring for Hardcore and Quarry.
3. Add a version-pinned PlotSquared adapter, then expose real readable flag values and permitted edits.
4. Implement Multiverse create/import with validated inputs and backup-aware confirmations.
5. Complete Discord bot/Control Plane channel mapping, status embed and chat bridge as one end-to-end feature.
