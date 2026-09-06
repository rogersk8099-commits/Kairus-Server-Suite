# Geyser and Floodgate

> **Status:** This is an implementation design, example configuration, and operations runbook for the intended SMPPlatform service. It does **not** claim that a JAR, database, external API, integration, backup, or deployment has been executed. The target is **Paper/Purpur 1.21.4 on Java 21**, with a **server-side-only** plugin. Java and Bedrock players use the same gameplay system; no client mod is required.

## Player compatibility policy

SMPPlatform is server-side only. Java and Bedrock players receive the same gameplay rules, permissions, points, guild, Hardcore, and archive protections. No client mod, edition-specific advantage, or username-prefix identity scheme is required. Geyser translates Bedrock traffic to the Java server; Floodgate adds hybrid-server identity features and supports Paper and forks when installed.[4]

## Setup boundary

Install current compatible Geyser and Floodgate releases under the Paper/Purpur plugins directory and configure the Bedrock UDP listener outside SMPPlatform. Geyser documentation notes that the Bedrock port must permit UDP and that it cannot share a port with another UDP service. Validate external reachability using Geyser’s connection test before declaring Bedrock access ready.[3]

SMPPlatform detects Floodgate through its API as an optional soft dependency. It records edition as `JAVA` or `BEDROCK`, Minecraft UUID, and Floodgate XUID where supplied. UUID/account mapping, not displayed name, is the durable identity. If Floodgate is absent, the player is handled safely as a normal Java identity and edition-dependent UI optimizations are unavailable; plugin startup continues.

## GUI and test rules

Inventory menus must be tested through a real Bedrock client via Geyser. Controls avoid required shift-click, drag splitting, hover-only instructions, offhand interaction, or typed item-name workflows. The GUI abstraction selects compatible inventory layouts and may later select a form adapter. See the Java and Bedrock cases in [TEST_PLAN.md](TEST_PLAN.md). Floodgate linking is distinct from Neon Nexus `/link`: neither workflow requests a Minecraft password.[4]

## References

[1]: file:///home/ubuntu/upload/pasted_content_3.txt "Authoritative SMPPlatform plugin brief"
[2]: file:///home/ubuntu/neon-nexus-hub/src/data/mock.ts "Neon Nexus website product specification"
[3]: https://geysermc.org/wiki/geyser/setup/ "Geyser setup"
[4]: https://geysermc.org/wiki/floodgate/ "Floodgate overview"
