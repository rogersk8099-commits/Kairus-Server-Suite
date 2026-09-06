# World Registry

> **Status:** This is an implementation design, example configuration, and operations runbook for the intended SMPPlatform service. It does **not** claim that a JAR, database, external API, integration, backup, or deployment has been executed. The target is **Paper/Purpur 1.21.4 on Java 21**, with a **server-side-only** plugin. Java and Bedrock players use the same gameplay system; no client mod is required.

## Authoritative local representation

`WorldDefinition` is the sole Minecraft-side definition of a Neon Nexus world. Its fields include the fixed ID, Minecraft folder name, public display name, description, module type, season, status, difficulty, border, PvP mode, guild and points policy, currency, claims/economy flags, inventory group, reset/archive policy, visibility, access permission, spawn, maintenance flag, revision, and sync timestamp. Systems resolve world behavior through the registry; they must not embed world names in command handlers or unrelated modules.[1]

| ID | Exact display name | Module | Public model | Season | Status | Size / difficulty |
|---|---|---|---|---|---|---|
| `ashfall` | Ashfall | `SURVIVAL` | Survival | Season 7 | Live | 24k × 24k / Hard |
| `obsidian-gate` | Obsidian Gate | `HARDCORE` | Hardcore | Season 7 | Live | 8k × 8k / Brutal |
| `atrium` | The Atrium | `CREATIVE` | Creative | Permanent | Live | Plots 128 × 128 / Peaceful |
| `colosseum` | Neon Colosseum | `EVENT` | Events | Rotating | Live | Instanced / Normal |
| `quarry` | The Quarry | `RESOURCE` | Resource | Weekly reset | Seasonal | 12k × 12k / Normal |
| `verdance` | Verdance | `ARCHIVE` | Survival (archive presentation) | Season 6 | Archived | 20k × 20k / Hard |

## Source, revision, and conflicts

At startup, the registry loads the last validated local cache, then `worlds.yml`, and remains usable offline. When the Central API is configured, it fetches a signed/authorized revision asynchronously. A central revision greater than the local revision replaces non-runtime configuration in an atomic validated update and writes an audit record. Equal revisions with divergent content become `CONFLICT` health state and retain the last validated definition; an older central revision is ignored and reported. Runtime data such as player count, health, and maintenance state is not silently written back over configuration.

| Situation | Behavior |
|---|---|
| API offline | use validated local cache/configuration; queue outbound events |
| malformed remote registry | reject remote payload, retain last good revision, alert staff |
| missing required one of six worlds | fail registry validation and do not enable world-dependent mutations |
| future type from API | retain opaque type metadata if enabled; no module-specific behavior until installed |
| local/remote same revision but different digest | no automatic winner; alert and retain last good cache |

## Validation contract

The registry must assert unique IDs, unique Minecraft world names, non-empty display names, valid spawn coordinates, non-negative borders, valid inventory groups, known access policy, and exactly the six required defaults. It may accept additional future world entries but never substitutes a generic `Spawn`, `Creative`, `PVE`, `PVP`, or vanilla-hardcore default. Configuration values change behavior without Java source edits.

## References

[1]: file:///home/ubuntu/upload/pasted_content_3.txt "Authoritative SMPPlatform plugin brief"
[2]: file:///home/ubuntu/neon-nexus-hub/src/data/mock.ts "Neon Nexus website product specification"
[3]: https://mvplugins.org/ "Multiverse documentation"
