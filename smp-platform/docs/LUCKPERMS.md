# LuckPerms integration

> **Status:** This is an implementation design, example configuration, and operations runbook for the intended SMPPlatform service. It does **not** claim that a JAR, database, external API, integration, backup, or deployment has been executed. The target is **Paper/Purpur 1.21.4 on Java 21**, with a **server-side-only** plugin. Java and Bedrock players use the same gameplay system; no client mod is required.

## Authority and access

LuckPerms owns membership, staff, creator, premium, and world permission calculation. SMPPlatform uses its API as a provided/soft dependency and uses Bukkit’s service lookup where available; it must not store a second authority copy of ranks or groups. The API provides asynchronous facilities and documents that Bukkit operations must be rescheduled to the server thread when invoked from an asynchronous callback.[3]

## Usage pattern

At player entry, evaluate `smpplatform.world.<id>` and any configured LuckPerms context. The administration GUI evaluates the granular nodes in [PERMISSIONS.md](PERMISSIONS.md) on screen open and action confirmation. Cache short-lived resolved authorization only if invalidated by the relevant LuckPerms event; do not turn a stale cache into an elevated grant. Use LuckPerms meta/context for display rank and membership presentation where defined by central policy.

A missing LuckPerms installation does not crash SMPPlatform because it is a soft dependency, but production policy must decide whether to fail closed for staff and restricted-world actions. The recommended safe default is to allow only Paper operator emergency access, log an integration warning, and deny membership/context-sensitive actions until LuckPerms returns. Guild ranks are gameplay ranks and do not create global LuckPerms groups without an explicit adapter policy.

## References

[1]: file:///home/ubuntu/upload/pasted_content_3.txt "Authoritative SMPPlatform plugin brief"
[2]: file:///home/ubuntu/neon-nexus-hub/src/data/mock.ts "Neon Nexus website product specification"
[3]: https://luckperms.net/wiki/Developer-API "LuckPerms Developer API"
