# Neon Colosseum

> **Status:** This is an implementation design, example configuration, and operations runbook for the intended SMPPlatform service. It does **not** claim that a JAR, database, external API, integration, backup, or deployment has been executed. The target is **Paper/Purpur 1.21.4 on Java 21**, with a **server-side-only** plugin. Java and Bedrock players use the same gameplay system; no client mod is required.

## Identity and intent

Neon Colosseum is the rotating, Normal event and arena network. It supports Sky Duels, Block Rush, the Friday-night 64-player Gauntlet, and future custom event definitions. It uses `EVENT_GROUP`, `EVENT_POINTS`, event-controlled PvP, and registered arena definitions.

## Gameplay and integration policy

An event progresses `DRAFT → SCHEDULED → REGISTRATION_OPEN → CHECK_IN → STARTING → LIVE → FINISHED → ARCHIVED`. Registration limits, waiting lists, check-in, teams, brackets, rounds, scores, results, rewards, and central synchronization are persisted. The Gauntlet maximum is 64 and the start must fail safely if minimum participation or arena validation fails.

## Operations

An arena has an ID, name, registry world, game type, min/max participants, spawn points, spectator spawn, bounds, and reset policy. Event instances are template-restored off-thread where technically possible; player teleports and Bukkit state changes return to the server thread. Results and point awards are idempotent and auditable.

## Registry invariants

The registry key is `colosseum` and the display name is **Neon Colosseum**. These values are not aliases, configuration suggestions, or translation replacements. World status, access, spawn, reset/archive policy, and maintenance state resolve from `worlds.yml` and the validated World Registry.

## References

[1]: file:///home/ubuntu/upload/pasted_content_3.txt "Authoritative SMPPlatform plugin brief"
[2]: file:///home/ubuntu/neon-nexus-hub/src/data/mock.ts "Neon Nexus website product specification"
