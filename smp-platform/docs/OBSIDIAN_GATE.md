# Obsidian Gate

> **Status:** This is an implementation design, example configuration, and operations runbook for the intended SMPPlatform service. It does **not** claim that a JAR, database, external API, integration, backup, or deployment has been executed. The target is **Paper/Purpur 1.21.4 on Java 21**, with a **server-side-only** plugin. Java and Bedrock players use the same gameplay system; no client mod is required.

## Identity and intent

Obsidian Gate is the Season 7, 8k × 8k Brutal Hardcore world. Its public contract is: “One life. Shared world. Death moves you to spectator until the next reset window opens on Sunday night.” The module uses `HARDCORE_GROUP`, `HARDCORE_POINTS`, and a configurable guild policy.

## Gameplay and integration policy

The death listener captures cause, killer where present, location, survival time, and statistics in one persistence workflow. It creates a death record, transitions `ALIVE → DEAD → SPECTATING`, applies spectator on the Paper thread, writes a durable `HARDCORE_DEATH` outbox event, and updates leaderboard cache asynchronously. Vanilla Hardcore alone is insufficient because it cannot express the platform reset lifecycle.

## Operations

The default reset window is Sunday 22:00 in `Europe/London`, for 120 minutes, but it is configuration and GUI controlled. Opening makes eligible users `RESET_ELIGIBLE`; a verified reset/revival returns them to `ALIVE`; closing locks unclaimed eligibility as `LOCKED`. Revive, eliminate, state change, and window controls require permission, confirmation, reason where applicable, and audit logging.

## Registry invariants

The registry key is `obsidian-gate` and the display name is **Obsidian Gate**. These values are not aliases, configuration suggestions, or translation replacements. World status, access, spawn, reset/archive policy, and maintenance state resolve from `worlds.yml` and the validated World Registry.

## References

[1]: file:///home/ubuntu/upload/pasted_content_3.txt "Authoritative SMPPlatform plugin brief"
[2]: file:///home/ubuntu/neon-nexus-hub/src/data/mock.ts "Neon Nexus website product specification"
