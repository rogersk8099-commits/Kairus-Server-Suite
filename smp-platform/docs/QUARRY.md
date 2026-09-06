# The Quarry

> **Status:** This is an implementation design, example configuration, and operations runbook for the intended SMPPlatform service. It does **not** claim that a JAR, database, external API, integration, backup, or deployment has been executed. The target is **Paper/Purpur 1.21.4 on Java 21**, with a **server-side-only** plugin. Java and Bedrock players use the same gameplay system; no client mod is required.

## Identity and intent

The Quarry is the 12k × 12k Normal resource world. It is Seasonal, resets every Monday at 04:00 UTC, uses `ASHFALL_GROUP` by default, and inherits Ashfall guild identity. Its purpose is to supply bulk resources without damaging Ashfall’s protected biomes.

## Gameplay and integration policy

The scheduled sequence is `SCHEDULED → 24h → 1h → 15m → 5m → 1m warnings → LOCK_ENTRY → EVACUATE → SAVE → BACKUP → UNLOAD → REGENERATE → LOAD → VALIDATE → OPEN → COMPLETE`. The reset verifies an operational destination spawn, saves player data, teleports every player out, verifies the world is empty, creates and verifies a backup, and only then unloads or changes world data.

## Operations

A backup failure is a hard abort by default. The system records `ABORTED`, retains the existing Quarry, unlocks only after staff assessment, alerts staff, and never blindly deletes the world. Reset Now, regeneration, schedule changes, closing/opening, and evacuation are permission-protected confirmed actions. A manual emergency reset follows the same precondition and backup checks.

## Registry invariants

The registry key is `quarry` and the display name is **The Quarry**. These values are not aliases, configuration suggestions, or translation replacements. World status, access, spawn, reset/archive policy, and maintenance state resolve from `worlds.yml` and the validated World Registry.

## References

[1]: file:///home/ubuntu/upload/pasted_content_3.txt "Authoritative SMPPlatform plugin brief"
[2]: file:///home/ubuntu/neon-nexus-hub/src/data/mock.ts "Neon Nexus website product specification"
