# SMPPlatform changelog

## Build compatibility

- Updates the SMPPlatform Gradle wrapper to 9.5.1 so Temurin Java 25.0.4.1 is accepted as the
  runtime JDK. The optional Fabric client already used this wrapper version.
- Replaces the removed Paper 26.2 `Material.CHAIN` GUI icon with `Material.NAME_TAG`.
- Adds the Spawn Hub case to the older server-admin world GUI switch expression.
- Disables Shadow bytecode relocation for Java 25 compatibility while continuing to bundle runtime
  database dependencies in the plugin JAR.

## Unreleased

- Adds server-authoritative Fabric client actions for guild invites, removal, promotion,
  demotion and leaving, with a refreshed guild summary returned after each completed action.
- Adds a server-held, 30-second two-step confirmation before a client ownership transfer can
  execute; the normal leader/admin validation still runs at confirmation time.
- Adds permission-safe client views for a player's point ledger and durable per-currency
  player leaderboards.
- Adds a disabled-by-default, configurable first-join automatic reward source. Its database
  reservation prevents duplicate payouts across reconnects and restarts.
- Adds a durable top-20 guild leaderboard with guild points and member-count statistics to the
  Fabric client.
- Expands the permission-checked World admin client controls to include thunder, all standard
  difficulties, and selected safety/maintenance gamerules.
- Adds cache-persisted per-world maintenance mode. Enabling it evacuates players to Spawn Hub,
  blocks menu travel, and cannot lock the Hub itself.
- Adds Multiverse load and confirmation-backed unload controls; unload evacuates players to the
  configured Spawn Hub and never permits unloading the Hub.

## 0.5.0-kairu-foundation

- Adds the permanent `spawn-hub` as the seventh Kairu world and adds the extensible `HUB` world type.
- Aligns the build target with Paper 26.2 and Java 25.
- Applies Hub policy: no guild mutations, no point earning, separate hub inventory, Adventure mode, and no dedicated Discord category.
- Adds `/plotflags` (alias `/plothelp`) in The Atrium: a player-readable guide for building, containers, PvP, explosions, mobs, fire spread, and redstone settings.
- Adds explicit world/global/guild chat configuration including a Minecraft-only Spawn Hub world chat and no Spawn Hub Discord category.

## Verification still required

This source change must be compiled with Java 25 and tested on a staging Paper 26.2 server. Guild and points commands require the configured PostgreSQL service to be healthy before they become available; they intentionally fail closed if it is not.
