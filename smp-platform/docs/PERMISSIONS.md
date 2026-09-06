# Permissions

> **Status:** This is an implementation design, example configuration, and operations runbook for the intended SMPPlatform service. It does **not** claim that a JAR, database, external API, integration, backup, or deployment has been executed. The target is **Paper/Purpur 1.21.4 on Java 21**, with a **server-side-only** plugin. Java and Bedrock players use the same gameplay system; no client mod is required.

## Permission authority

LuckPerms is the permissions and rank authority. SMPPlatform checks Bukkit permissions and uses the LuckPerms API/context to read groups, prefixes, membership-related metadata, and world contexts; it does not create a competing role engine. Grant the smallest node set necessary and prefer context-specific grants for creative, staff, or per-world access.[3]

| Node | Default | Grants |
|---|---:|---|
| `smpplatform.admin` | op only | Administration GUI home and base staff access |
| `smpplatform.admin.players` | false | player inspector and non-destructive player tools |
| `smpplatform.admin.worlds` | false | world controls and maintenance |
| `smpplatform.admin.guilds` | false | guild staff management |
| `smpplatform.admin.points` | false | points inspection/add/remove/set |
| `smpplatform.admin.hardcore` | false | Hardcore administration |
| `smpplatform.admin.events` | false | event/arena administration |
| `smpplatform.admin.quarry` | false | Quarry schedule/reset controls |
| `smpplatform.admin.moderation` | false | moderation actions |
| `smpplatform.guild.create` | true by policy | create a guild |
| `smpplatform.guild.manage` | false | staff guild override; members use rank policy |
| `smpplatform.points.view` | true by policy | own points balance/history |
| `smpplatform.world.ashfall` | policy | entry to Ashfall |
| `smpplatform.world.obsidian-gate` | policy | entry to Obsidian Gate |
| `smpplatform.world.atrium` | policy | entry to The Atrium |
| `smpplatform.world.colosseum` | policy | entry to Neon Colosseum |
| `smpplatform.world.quarry` | policy | entry to The Quarry |
| `smpplatform.world.verdance` | policy | entry to Verdance |

Additional implementation nodes should use the same prefix, such as `smpplatform.admin.inventory.edit`, `smpplatform.admin.points.set`, `smpplatform.admin.hardcore.revive`, and `smpplatform.admin.quarry.reset`. Parent grants must never accidentally grant a bypass for Verdance protection or the Hardcore inventory boundary.

## References

[1]: file:///home/ubuntu/upload/pasted_content_3.txt "Authoritative SMPPlatform plugin brief"
[2]: file:///home/ubuntu/neon-nexus-hub/src/data/mock.ts "Neon Nexus website product specification"
[3]: https://luckperms.net/wiki/Developer-API "LuckPerms Developer API"
