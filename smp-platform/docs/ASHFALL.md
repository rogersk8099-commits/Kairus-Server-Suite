# Ashfall

> **Status:** This is an implementation design, example configuration, and operations runbook for the intended SMPPlatform service. It does **not** claim that a JAR, database, external API, integration, backup, or deployment has been executed. The target is **Paper/Purpur 1.21.4 on Java 21**, with a **server-side-only** plugin. Java and Bedrock players use the same gameplay system; no client mod is required.

## Identity and intent

Ashfall is the Season 7 flagship survival map: nation borders, claim wars, player-run economy, redstone freight lines, community survival, guilds, progression, and leaderboards. It is a 24k × 24k Hard world with `SURVIVAL`, `ASHFALL_GROUP`, `NEXUS_POINTS`, claims and economy adapters enabled.

## Gameplay and integration policy

Guild mode is `FULL`. The plugin owns guild identity and emits integration events; a claims adapter and economy adapter remain external-system adapters rather than a second claims/economy implementation. Any claim/economy data exposed to the platform is read or synchronized through its adapter. The owner must explicitly select compatible claims and economy plugins before enabling their adapters.

## Operations

At a season end: close mutations according to the season plan, make a verified world backup, snapshot player/world statistics and leaderboards, create the archive record, freeze the source map, and transition to tour-only archive policy. This generic workflow can later create another archive without hardcoding Verdance.

## Registry invariants

The registry key is `ashfall` and the display name is **Ashfall**. These values are not aliases, configuration suggestions, or translation replacements. World status, access, spawn, reset/archive policy, and maintenance state resolve from `worlds.yml` and the validated World Registry.

## References

[1]: file:///home/ubuntu/upload/pasted_content_3.txt "Authoritative SMPPlatform plugin brief"
[2]: file:///home/ubuntu/neon-nexus-hub/src/data/mock.ts "Neon Nexus website product specification"
