# Worlds and cross-world policy

> **Status:** This is an implementation design, example configuration, and operations runbook for the intended SMPPlatform service. It does **not** claim that a JAR, database, external API, integration, backup, or deployment has been executed. The target is **Paper/Purpur 1.21.4 on Java 21**, with a **server-side-only** plugin. Java and Bedrock players use the same gameplay system; no client mod is required.

## Network model

The website’s six maps have their own rules, economies, reset cadence, and presentation. SMPPlatform maintains a platform-level account, guild identity, audit trail, and configurable inventory relationship; it does **not** assume every inventory or economy travels everywhere. The website phrase “Your inventory and progress travel with you” is implemented through explicit group policy, not accidental global sharing.[2]

| ID | Exact display name | Module | Public model | Season | Status | Size / difficulty |
|---|---|---|---|---|---|---|
| `ashfall` | Ashfall | `SURVIVAL` | Survival | Season 7 | Live | 24k × 24k / Hard |
| `obsidian-gate` | Obsidian Gate | `HARDCORE` | Hardcore | Season 7 | Live | 8k × 8k / Brutal |
| `atrium` | The Atrium | `CREATIVE` | Creative | Permanent | Live | Plots 128 × 128 / Peaceful |
| `colosseum` | Neon Colosseum | `EVENT` | Events | Rotating | Live | Instanced / Normal |
| `quarry` | The Quarry | `RESOURCE` | Resource | Weekly reset | Seasonal | 12k × 12k / Normal |
| `verdance` | Verdance | `ARCHIVE` | Survival (archive presentation) | Season 6 | Archived | 20k × 20k / Hard |

## Default inventory and access policy

| Inventory group | Worlds | Default | Non-negotiable control |
|---|---|---|---|
| `ASHFALL_GROUP` | Ashfall, The Quarry | shared | Quarry sharing is configurable but explicit |
| `HARDCORE_GROUP` | Obsidian Gate | isolated | never share with Ashfall |
| `CREATIVE_GROUP` | The Atrium | isolated creative | no survival item movement |
| `EVENT_GROUP` | Neon Colosseum | temporary loadout | reset at event boundary |
| `ARCHIVE_GROUP` | Verdance | isolated tour | no transfer of modified content |

World entry evaluates the registry access permission, maintenance status, module state, and any applicable membership/whitelist rule. A denied entry explains the reason without exposing hidden policy. An administrator action that changes PvP, border, gamemode, reset state, or maintenance is audit logged with before/after state and correlation ID.

## Common lifecycle rules

Ashfall is seasonal survival. Obsidian Gate uses custom status transitions, not vanilla Hardcore alone. The Atrium defers plot enforcement to PlotSquared. Neon Colosseum treats events as registered lifecycles and arenas, not a generic PvP world. The Quarry evacuates, backs up, validates, and only then regenerates. Verdance denies modifications through server event protection in addition to Adventure mode. Detailed runbooks are linked from this document.

## References

[1]: file:///home/ubuntu/upload/pasted_content_3.txt "Authoritative SMPPlatform plugin brief"
[2]: file:///home/ubuntu/neon-nexus-hub/src/data/mock.ts "Neon Nexus website product specification"
[3]: https://github.com/Multiverse/Multiverse-Inventories "Multiverse-Inventories repository"
[4]: https://mvplugins.org/ "Multiverse documentation"
