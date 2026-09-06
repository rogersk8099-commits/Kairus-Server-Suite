# Skript and PlaceholderAPI integration

> **Status:** This is an implementation design, example configuration, and operations runbook for the intended SMPPlatform service. It does **not** claim that a JAR, database, external API, integration, backup, or deployment has been executed. The target is **Paper/Purpur 1.21.4 on Java 21**, with a **server-side-only** plugin. Java and Bedrock players use the same gameplay system; no client mod is required.

## Integration design

Skript is an optional soft dependency. The maintainable implementation is a small SMPPlatform Skript addon that registers typed expressions/effects/conditions against the public Java service API and fails registration cleanly when Skript is absent or incompatible. It must run database/API work asynchronously and return player-affecting Bukkit changes to the server thread. A documented console-command fallback is retained for emergency administration but is not a substitute for typed effects.[3]

| Facility | Intended syntax | Safety |
|---|---|---|
| World type | `nexus world type of player` | read-only registry lookup |
| Guild | `nexus guild of player` | read-only platform identity |
| Points | `nexus balance of player in "NEXUS_POINTS"` | read-only cache/service |
| Add/remove points | `add 50 nexus points to player because "event reward"` | permission/context, idempotency, ledger |
| Hardcore | `nexus hardcore status of player` | read-only status |
| Notification/event | `send nexus notification ...` / `trigger nexus event ...` | schema validation and outbox |

```skript
# Example only; requires the intended SMPPlatform Skript addon.
on death of player:
    if nexus world type of player is "HARDCORE":
        # SMPPlatform itself owns the state transition. This only grants a named reward.
        add 10 nexus points to player because "hardcore participation"

command /myguild:
    trigger:
        send "Guild: %nexus guild of player%"
        send "Nexus Points: %nexus balance of player in "NEXUS_POINTS"%"
```

## Bukkit events and placeholders

Public events are `NexusPlayerLinkedEvent`, `NexusPointsChangeEvent`, `NexusGuildCreateEvent`, `NexusGuildJoinEvent`, `NexusGuildLeaveEvent`, `NexusHardcoreDeathEvent`, `NexusHardcoreResetEvent`, `NexusEventStartEvent`, `NexusEventEndEvent`, `NexusWorldResetEvent`, and `NexusMembershipChangeEvent`. Events document cancellation, async status, and mutable fields explicitly before release.

When PlaceholderAPI is installed, the `nexus` expansion provides `%nexus_world%`, `%nexus_world_name%`, `%nexus_guild%`, `%nexus_guild_tag%`, `%nexus_points%`, `%nexus_hardcore_status%`, `%nexus_membership%`, `%nexus_platform%`, `%nexus_season%`, and `%nexus_event_points%`. Missing data returns a neutral configured value (for example `—`), never triggers a blocking query, and never mutates state.[4]

## References

[1]: file:///home/ubuntu/upload/pasted_content_3.txt "Authoritative SMPPlatform plugin brief"
[2]: file:///home/ubuntu/neon-nexus-hub/src/data/mock.ts "Neon Nexus website product specification"
[3]: https://docs.skriptlang.org/docs.html "Skript documentation"
[4]: https://wiki.placeholderapi.com/developers/ "PlaceholderAPI developer guides"
