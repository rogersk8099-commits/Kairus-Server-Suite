# PlotSquared integration

> **Status:** This is an implementation design, example configuration, and operations runbook for the intended SMPPlatform service. It does **not** claim that a JAR, database, external API, integration, backup, or deployment has been executed. The target is **Paper/Purpur 1.21.4 on Java 21**, with a **server-side-only** plugin. Java and Bedrock players use the same gameplay system; no client mod is required.

## The Atrium boundary

PlotSquared is the authoritative plot system in The Atrium. SMPPlatform supplies the platform layer: build-submission workflow, featured-build review, build points, announcements, and website/Discord event publication. It does not claim plots, alter ownership, or decide PlotSquared membership independently. Configure the Atrium plot size as 128 × 128 in the PlotSquared area and mirror that fact in the registry.[1]

## Permissions and workflow

Players obtain plot mechanics from PlotSquared and permissions from LuckPerms. PlotSquared documents `/plot claim`, `/plot info`, and management commands/permission nodes such as `plots.claim`, `plots.info`, and administrative setup/area controls.[3] SMPPlatform checks the referenced plot through PlotSquared before accepting a build submission. A submission is pending review, may become featured or rejected, and then emits a platform event after database commit. A staff feature action requires an audit record.

When PlotSquared is unavailable, The Atrium’s world can remain loaded but submission/review controls must be disabled with a visible health warning; SMPPlatform must never infer plot ownership from location alone. Creative inventory isolation and WorldEdit permissions remain separately configured through Multiverse-Inventories and LuckPerms.

## References

[1]: file:///home/ubuntu/upload/pasted_content_3.txt "Authoritative SMPPlatform plugin brief"
[2]: file:///home/ubuntu/neon-nexus-hub/src/data/mock.ts "Neon Nexus website product specification"
[3]: https://intellectualsites.gitbook.io/plotsquared/features/commands "PlotSquared command documentation"
[4]: https://luckperms.net/wiki/Developer-API "LuckPerms Developer API"
[5]: https://github.com/Multiverse/Multiverse-Inventories "Multiverse-Inventories repository"
