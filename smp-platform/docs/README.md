# SMPPlatform documentation

> **Status:** This is an implementation design, example configuration, and operations runbook for the intended SMPPlatform service. It does **not** claim that a JAR, database, external API, integration, backup, or deployment has been executed. The target is **Paper/Purpur 1.21.4 on Java 21**, with a **server-side-only** plugin. Java and Bedrock players use the same gameplay system; no client mod is required.

## Purpose

SMPPlatform is the Minecraft-side platform layer for **Neon Nexus**. It unifies the exact six website worlds behind one extensible World Registry rather than creating six disconnected plugins. It is designed for Paper/Purpur 1.21.4, Java 21, PostgreSQL, Adventure/MiniMessage, and asynchronous I/O. Geyser and Floodgate make the same server accessible to Bedrock players without a client mod. The website specification is the product authority for player-facing terminology and world identity.[1] [2]

## Contents

The root contains safe example `*.yml` files; `migrations/V001__initial_smp_platform.sql` is the initial PostgreSQL baseline. Read [INSTALLATION.md](INSTALLATION.md) before changing any configuration. [ARCHITECTURE.md](ARCHITECTURE.md), [WORLD_REGISTRY.md](WORLD_REGISTRY.md), and [DATABASE.md](DATABASE.md) establish the system boundaries. Each world has a dedicated operational document.

## Fixed public world contract

| ID | Exact display name | Module | Public model | Season | Status | Size / difficulty |
|---|---|---|---|---|---|---|
| `ashfall` | Ashfall | `SURVIVAL` | Survival | Season 7 | Live | 24k × 24k / Hard |
| `obsidian-gate` | Obsidian Gate | `HARDCORE` | Hardcore | Season 7 | Live | 8k × 8k / Brutal |
| `atrium` | The Atrium | `CREATIVE` | Creative | Permanent | Live | Plots 128 × 128 / Peaceful |
| `colosseum` | Neon Colosseum | `EVENT` | Events | Rotating | Live | Instanced / Normal |
| `quarry` | The Quarry | `RESOURCE` | Resource | Weekly reset | Seasonal | 12k × 12k / Normal |
| `verdance` | Verdance | `ARCHIVE` | Survival (archive presentation) | Season 6 | Archived | 20k × 20k / Hard |

The IDs and display names above are contractual. A future seventh world is added as a new WorldDefinition and configuration entry; it must not reuse or rename these six IDs. `worlds.yml` is the local bootstrap/cache configuration and validates the exact six-world set at startup.

## Safety and scope

Gameplay mutations require PostgreSQL health. The plugin must keep the server playable while the Central API, Discord, or an optional integration is unavailable; it does so through a durable outbox and local cache. It must not perform HTTP, SQL, large scans, or backups on the primary server thread. It must never contain a Discord bot token, database password, or API token. Placeholder values in example configuration are environment-variable references, not secrets.

## Reading order

| Task | Read first |
|---|---|
| First installation | [INSTALLATION.md](INSTALLATION.md), [DATABASE.md](DATABASE.md), [GEYSER_FLOODGATE.md](GEYSER_FLOODGATE.md) |
| World configuration | [WORLD_REGISTRY.md](WORLD_REGISTRY.md), [WORLDS.md](WORLDS.md), the relevant world file |
| Staff workflow | [ADMIN_GUI.md](ADMIN_GUI.md), [COMMANDS.md](COMMANDS.md), [PERMISSIONS.md](PERMISSIONS.md) |
| External systems | [API.md](API.md), [MULTIVERSE.md](MULTIVERSE.md), [LUCKPERMS.md](LUCKPERMS.md) |
| Verification or incident response | [TEST_PLAN.md](TEST_PLAN.md), [TROUBLESHOOTING.md](TROUBLESHOOTING.md) |

## References

[1]: file:///home/ubuntu/upload/pasted_content_3.txt "Authoritative SMPPlatform plugin brief"
[2]: file:///home/ubuntu/neon-nexus-hub/src/data/mock.ts "Neon Nexus website product specification"
[3]: https://docs.papermc.io/paper/ "Paper documentation"
[4]: https://geysermc.org/wiki/geyser/setup/ "Geyser setup"
[5]: https://geysermc.org/wiki/floodgate/ "Floodgate overview"
