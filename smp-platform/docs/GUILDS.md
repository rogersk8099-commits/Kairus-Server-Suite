# Guilds

> **Status:** This is an implementation design, example configuration, and operations runbook for the intended SMPPlatform service. It does **not** claim that a JAR, database, external API, integration, backup, or deployment has been executed. The target is **Paper/Purpur 1.21.4 on Java 21**, with a **server-side-only** plugin. Java and Bedrock players use the same gameplay system; no client mod is required.

## Model and lifecycle

Guilds are a core SMPPlatform feature and a platform-level identity. A guild has a UUID, unique normalized name and tag, description, owner, creation time, members, ranks, points, level, statistics, achievements, home, world associations, Discord reference, and website visibility. The default ranks are Leader, Officer, Member, and Recruit; rank permissions are persisted for future customization.

Players use `/guild` or `/g` to create, invite, join/accept, decline, leave, inspect, list members, view top guilds, and manage eligible members. The GUI is the default discovery and management experience. Creation validates name/tag policy, performs one database transaction for guild/ranks/owner membership, and publishes an idempotent event after commit. A player has at most one active guild. Ownership transfer cannot leave a live guild without an owner. Disband requires confirmation, an actor permission, an audit record, and preserves historical membership/statistics.

## World awareness

| World | Guild policy |
|---|---|
| Ashfall | Full guild operations |
| Obsidian Gate | Configurable policy, never assumptions about Hardcore eligibility |
| The Atrium | Social/optional identity only unless configured otherwise |
| Neon Colosseum | Teams and competition integration |
| The Quarry | Inherit Ashfall guild association |
| Verdance | Read-only historical representation |

Discord integration is event publication to the Central API only. SMPPlatform does not create Discord roles or channels and never stores a Discord token. Guild mutations are unavailable in database degraded mode because membership and audit consistency would not be guaranteed.

## References

[1]: file:///home/ubuntu/upload/pasted_content_3.txt "Authoritative SMPPlatform plugin brief"
[2]: file:///home/ubuntu/neon-nexus-hub/src/data/mock.ts "Neon Nexus website product specification"
