# Administration GUI

> **Status:** This is an implementation design, example configuration, and operations runbook for the intended SMPPlatform service. It does **not** claim that a JAR, database, external API, integration, backup, or deployment has been executed. The target is **Paper/Purpur 1.21.4 on Java 21**, with a **server-side-only** plugin. Java and Bedrock players use the same gameplay system; no client mod is required.

## Design

`/smpadmin` and `/admin` open a Neon Nexus dark, black, purple, magenta, violet, and electric-blue inventory GUI. The experience is GUI-first, context-sensitive, keyboard-light, and inspired only by general administrative usability qualities; it does not copy source, artwork, or an exact layout from another product. Core controls use vanilla items and MiniMessage text, so no resource pack is required. CustomModelData is optional only.

The home categories are Player Administration, World Control, Guilds, Points, Hardcore, Events, Creative, Resource World, Moderation, Announcements, Server Monitor, Integrations, and Settings. All screens expose Back, Home, and Close. Destructive actions use a confirmation screen with a timeout, reason input when relevant, permission recheck at confirm time, audit logging, and a success/failure result.

## Player and world flows

The player selector has Online, Recent, Offline, Bedrock, Java, and Staff tabs plus search. A player head or compatible icon displays username, edition, current world, guild, LuckPerms rank, playtime, and status. Inspector actions include teleport/bring/send, gamemode, health/food/XP/effects, inventory and ender chest view, points, guild, world data, Hardcore status, event history, membership, account links, moderation history, and audit history. Inventory editing, bans, kicks, mutes, freezes, and status changes require the corresponding granular permission.

World Control presents exactly Ashfall, Obsidian Gate, The Atrium, Neon Colosseum, The Quarry, and Verdance in registry order. Each card shows status, type, season, players, difficulty, TPS/MSPT if measurable, border, next reset, and guild/points state. World actions include time/weather/difficulty/gamerules, cleanup, spawn, border, PvP, announcement, whitelist, and maintenance. Destructive operations never execute as an unconfirmed click.

## Bedrock compatibility

Geyser-tested inventory controls avoid Java-only click patterns: no required drag interaction, shift-click, hover-only explanation, offhand dependency, or raw text anvil dependency. The renderer abstraction can select a compatible inventory layout or optional Floodgate/Geyser form adapter. Every administrative action remains permission-checked server-side regardless of renderer.[1] [3] [4]

## References

[1]: file:///home/ubuntu/upload/pasted_content_3.txt "Authoritative SMPPlatform plugin brief"
[2]: file:///home/ubuntu/neon-nexus-hub/src/data/mock.ts "Neon Nexus website product specification"
[3]: https://geysermc.org/wiki/geyser/setup/ "Geyser setup"
[4]: https://geysermc.org/wiki/floodgate/ "Floodgate overview"
