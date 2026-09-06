# The Atrium

> **Status:** This is an implementation design, example configuration, and operations runbook for the intended SMPPlatform service. It does **not** claim that a JAR, database, external API, integration, backup, or deployment has been executed. The target is **Paper/Purpur 1.21.4 on Java 21**, with a **server-side-only** plugin. Java and Bedrock players use the same gameplay system; no client mod is required.

## Identity and intent

The Atrium is the permanent, Peaceful creative world with 128 × 128 plots, WorldEdit, custom palettes, featured builds, competitions, and creator showcases. It uses `CREATIVE_GROUP`, `BUILD_POINTS`, `SOCIAL_OPTIONAL` guild behavior, and delegates plot mechanics to PlotSquared.

## Gameplay and integration policy

PlotSquared remains authoritative for plot ownership, membership, flags, and plot geometry. SMPPlatform reads a validated plot reference when handling a build submission; it never bypasses PlotSquared permissions. A staff-configured WorldEdit policy is granted by LuckPerms/PlotSquared context, not by a separate SMPPlatform permission clone.

## Operations

A submission records player, plot reference, title, description, website image references, time, review state, reviewer, and feature time. The workflow is `PENDING → UNDER_REVIEW → FEATURED | REJECTED`; featured records can be emitted to the Central API for website and Discord publication. A rejection or withdrawal preserves the audit history.

## Registry invariants

The registry key is `atrium` and the display name is **The Atrium**. These values are not aliases, configuration suggestions, or translation replacements. World status, access, spawn, reset/archive policy, and maintenance state resolve from `worlds.yml` and the validated World Registry.

## References

[1]: file:///home/ubuntu/upload/pasted_content_3.txt "Authoritative SMPPlatform plugin brief"
[2]: file:///home/ubuntu/neon-nexus-hub/src/data/mock.ts "Neon Nexus website product specification"
