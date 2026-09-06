# Verdance

> **Status:** This is an implementation design, example configuration, and operations runbook for the intended SMPPlatform service. It does **not** claim that a JAR, database, external API, integration, backup, or deployment has been executed. The target is **Paper/Purpur 1.21.4 on Java 21**, with a **server-side-only** plugin. Java and Bedrock players use the same gameplay system; no client mod is required.

## Identity and intent

Verdance is the frozen 20k × 20k Hard Season 6 map. It is presented by the website as an archived survival map open for tours, with member-portal world-file downloads. In Minecraft it is an `ARCHIVE` module with `ARCHIVE_GROUP`, points disabled, historical guild information read-only, and Tour Mode by default.

## Gameplay and integration policy

Tour Mode allows exploration, teleporting to approved locations, viewing old builds, and screenshots. It denies block break/place, fluid and terrain changes, container modification, destructive entity interaction, explosions, fire spread where relevant, and other world-changing interactions. Event listeners provide protection in addition to Adventure mode, so client or gamemode edge cases cannot modify the archive.

## Operations

The generic archive workflow is backup, statistics snapshot, leaderboard snapshot, freeze, archive entry, and tour-mode publication. A later seasonal archive is a new registry entry and archive record; Verdance is an implementation example rather than a hardcoded exception.

## Registry invariants

The registry key is `verdance` and the display name is **Verdance**. These values are not aliases, configuration suggestions, or translation replacements. World status, access, spawn, reset/archive policy, and maintenance state resolve from `worlds.yml` and the validated World Registry.

## References

[1]: file:///home/ubuntu/upload/pasted_content_3.txt "Authoritative SMPPlatform plugin brief"
[2]: file:///home/ubuntu/neon-nexus-hub/src/data/mock.ts "Neon Nexus website product specification"
