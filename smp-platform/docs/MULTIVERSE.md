# Multiverse and inventory groups

> **Status:** This is an implementation design, example configuration, and operations runbook for the intended SMPPlatform service. It does **not** claim that a JAR, database, external API, integration, backup, or deployment has been executed. The target is **Paper/Purpur 1.21.4 on Java 21**, with a **server-side-only** plugin. Java and Bedrock players use the same gameplay system; no client mod is required.

## Responsibility split

Multiverse-Core is the underlying world manager for import, create, load, unload, teleport, spawn, gamemode, difficulty, environment, PvP, and world properties. SMPPlatform maps registry definitions to Multiverse worlds, applies higher-level entry/status/reset policy, and never reimplements a parallel world manager. Multiverse exposes configurable world properties and an API suitable for a multiworld layer.[3]

## Inventory isolation

Multiverse-Inventories is optional but recommended. Its purpose is per-world/group inventory separation, so its configuration must match the World Registry before players cross worlds.[4]

| Group | Members | Required verification |
|---|---|---|
| `ASHFALL_GROUP` | `nx_ashfall`, `nx_quarry` | intended sharing is enabled and documented |
| `HARDCORE_GROUP` | `nx_obsidian_gate` | no Ashfall sharing under any configuration |
| `CREATIVE_GROUP` | `nx_atrium` | creative inventory does not bleed into survival |
| `EVENT_GROUP` | `nx_colosseum` | event loadout is restored/reset per policy |
| `ARCHIVE_GROUP` | `nx_verdance` | tour inventory carries no modification privilege |

At startup, validate every configured Minecraft world folder resolves to one registry ID and group. Do not permit an unvalidated Multiverse world name as a `/worlds teleport` target. If Multiverse is unavailable, show degraded world-management health and block destructive lifecycle actions. If Multiverse-Inventories is absent, block or explicitly warn for world transitions whose isolation guarantee cannot be met; **never** silently allow Obsidian Gate to share survival inventory.

## References

[1]: file:///home/ubuntu/upload/pasted_content_3.txt "Authoritative SMPPlatform plugin brief"
[2]: file:///home/ubuntu/neon-nexus-hub/src/data/mock.ts "Neon Nexus website product specification"
[3]: https://mvplugins.org/ "Multiverse documentation"
[4]: https://github.com/Multiverse/Multiverse-Inventories "Multiverse-Inventories repository"
