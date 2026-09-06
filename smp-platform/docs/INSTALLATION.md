# Installation and upgrade runbook

> **Status:** This is an implementation design, example configuration, and operations runbook for the intended SMPPlatform service. It does **not** claim that a JAR, database, external API, integration, backup, or deployment has been executed. The target is **Paper/Purpur 1.21.4 on Java 21**, with a **server-side-only** plugin. Java and Bedrock players use the same gameplay system; no client mod is required.

## Preconditions

Provision a **Paper or Purpur 1.21.4** server running **Java 21**. Create a least-privilege PostgreSQL role and empty database or schema dedicated to SMPPlatform. Install Geyser and Floodgate only if Bedrock access is required. Install Multiverse-Core for managed worlds; Multiverse-Inventories, LuckPerms, PlotSquared, PlaceholderAPI, and Skript are optional integrations. Verify compatibility and releases in a non-production environment before a production change.[3] [4]

## Procedure

1. Stop the server cleanly and take a filesystem backup plus a PostgreSQL logical backup. Do not upgrade during a Quarry reset or a live event.
2. Place the future `SMPPlatform.jar` in `plugins/`. This documentation package intentionally does not provide or claim to provide that JAR.
3. Copy the nine root YAML files to `plugins/SMPPlatform/`. Preserve environment-variable references; supply them through the service manager, container runtime, or secret manager rather than writing credentials to YAML.
4. Review `worlds.yml`. The values `ashfall/Ashfall`, `obsidian-gate/Obsidian Gate`, `atrium/The Atrium`, `colosseum/Neon Colosseum`, `quarry/The Quarry`, and `verdance/Verdance` must remain exact.
5. Apply `migrations/V001__initial_smp_platform.sql` using the deployment migration role. Record the migration checksum and result. Do not run it against a shared Kairu schema without reviewing the `smp_` namespace policy.
6. Configure Multiverse world imports or creation for the six `minecraft-world-name` values. Configure Multiverse-Inventories before permitting player travel; verify that `HARDCORE_GROUP` is isolated and that Ashfall/Quarry sharing is intentional.
7. If central synchronization is enabled, inject the API base URL and token at runtime. Start with outbound delivery disabled or a staging endpoint; inspect the outbox and audit records first.
8. Start the server, review migration and integration health messages, then execute the smoke tests in [TEST_PLAN.md](TEST_PLAN.md). Only then open player access.

## Upgrade and rollback

A migration is append-only and versioned. Back up PostgreSQL and affected world directories before applying a newer migration. Rollback is operationally a restore to a tested database backup plus matching plugin/configuration version; never improvise `DROP TABLE` in production. Configuration changes use review, syntax validation, a staging restart, and an audit record. For a failed startup, retain logs, restore the last known-good JAR/configuration, and follow [TROUBLESHOOTING.md](TROUBLESHOOTING.md).

## Backup minimum

| Asset | Minimum method | Verify |
|---|---|---|
| PostgreSQL | consistent logical or physical backup | restore rehearsal and checksum |
| World directories | snapshot/archive while world is stable | manifest and checksum |
| Plugin configuration | version-controlled encrypted backup where needed | review exact six-world registry |
| Quarry pre-reset image | completed backup before unload | `VERIFIED` `smp_world_backups` record |

A failed Quarry backup aborts the reset. A backup command must run off the main thread and must not delete a source world until verification succeeds. [1]

## References

[1]: file:///home/ubuntu/upload/pasted_content_3.txt "Authoritative SMPPlatform plugin brief"
[2]: file:///home/ubuntu/neon-nexus-hub/src/data/mock.ts "Neon Nexus website product specification"
[3]: https://docs.papermc.io/paper/ "Paper documentation"
[4]: https://mvplugins.org/ "Multiverse documentation"
[5]: https://www.postgresql.org/docs/current/ddl-constraints.html "PostgreSQL constraints documentation"
