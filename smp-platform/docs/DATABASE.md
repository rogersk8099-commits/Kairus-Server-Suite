# Database, ownership, migrations, and backup

> **Status:** This is an implementation design, example configuration, and operations runbook for the intended SMPPlatform service. It does **not** claim that a JAR, database, external API, integration, backup, or deployment has been executed. The target is **Paper/Purpur 1.21.4 on Java 21**, with a **server-side-only** plugin. Java and Bedrock players use the same gameplay system; no client mod is required.

## PostgreSQL model

PostgreSQL is the authoritative durable store for Minecraft gameplay data. The initial baseline is `migrations/V001__initial_smp_platform.sql`. It creates only `smp_`-prefixed tables and indexes; it does not create, alter, or foreign-key to existing Kairu shared tables. `platform_user_id` is an external reference with no assumed Kairu table name. Constraints enforce valid states, uniqueness, account ownership, idempotency, and ledger arithmetic; indexes serve expected account, world, time, and dispatch paths. PostgreSQL requires indexes on referencing columns when query patterns need them, because foreign keys do not create those indexes automatically.[3]

## Source of truth

| Domain | Authoritative system | SMPPlatform role |
|---|---|---|
| Minecraft gameplay, guilds, points, Hardcore, events, audit/outbox | SMPPlatform PostgreSQL | writer and reader |
| Website accounts and platform user profiles | Central Platform | external reference/cache only |
| Discord IDs, channel routing, bot configuration | Central Platform / Discord bot | publish events only |
| permissions and rank calculations | LuckPerms | query/enforce only |
| runtime worlds | Multiverse + World Registry | coordinate/mapping layer |
| plots | PlotSquared | read/submit integration only |

## Migration discipline

Migrations are immutable, ordered `VNNN__description.sql` files. Apply them with a dedicated migration role in a maintenance window or a deployment stage that provides transactional DDL. Record version, checksum, actor, environment, and outcome in the migration tool’s metadata, not by editing a previous migration. A failed migration must abort the transaction; inspect the database, preserve logs, restore from a verified pre-change backup if necessary, and remediate with a later migration rather than modifying V001.

## Backup and recovery

Back up the database consistently before every migration, seasonal archive, or destructive operation. Test restoration into a non-production target and validate row counts, constraints, a selected account ledger, and outbox state. Back up worlds separately; backup metadata stores an opaque storage reference and checksum, never a credential. The Quarry reset requires a `VERIFIED` backup record before unload. Database unavailability causes a safe degraded mode that blocks persistent and destructive mutations, retries connections with backoff, alerts staff, and leaves unrelated Paper gameplay available where possible.[1]

## References

[1]: file:///home/ubuntu/upload/pasted_content_3.txt "Authoritative SMPPlatform plugin brief"
[2]: file:///home/ubuntu/neon-nexus-hub/src/data/mock.ts "Neon Nexus website product specification"
[3]: https://www.postgresql.org/docs/current/ddl-constraints.html "PostgreSQL constraints documentation"
[4]: https://luckperms.net/wiki/Developer-API "LuckPerms Developer API"
[5]: https://mvplugins.org/ "Multiverse documentation"
[6]: https://intellectualsites.gitbook.io/plotsquared/features/commands "PlotSquared command documentation"
