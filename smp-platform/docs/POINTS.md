# Points engine

> **Status:** This is an implementation design, example configuration, and operations runbook for the intended SMPPlatform service. It does **not** claim that a JAR, database, external API, integration, backup, or deployment has been executed. The target is **Paper/Purpur 1.21.4 on Java 21**, with a **server-side-only** plugin. Java and Bedrock players use the same gameplay system; no client mod is required.

## Ledger-first design

Points are configurable currencies, not a single mutable integer. The initial currencies are `NEXUS_POINTS`, `GUILD_POINTS`, `HARDCORE_POINTS`, `BUILD_POINTS`, `EVENT_POINTS`, and `SEASON_POINTS`; new currencies require configuration/data additions, not a schema redesign. Each balance is one `smp_point_accounts` row and every change appends one immutable `smp_point_transactions` row containing ID, account, currency, amount, before/after balance, source, reason, metadata, world, actor, timestamp, idempotency key, and correlation ID.[1]

A mutation locks or versions the account, validates currency/world/permission policy, calculates the balance, inserts the transaction, updates the account, creates audit/outbox records, and commits atomically. A retry with the same idempotency key returns the existing result rather than double-awarding. There are no silent balance edits. The allowed sources are `GAMEPLAY`, `ADMIN`, `ACHIEVEMENT`, `EVENT`, `GUILD`, `QUEST`, `SKRIPT`, `API`, `SYSTEM`, `REFUND`, and `ADJUSTMENT`.

## User and staff surface

| Command / GUI | Audience | Behavior |
|---|---|---|
| `/points`, `balance`, `history`, `top` | player | read own permitted currency, ledger history, leaderboard |
| `/points add/remove/set` | staff | validated ledger change with reason; `set` confirmed |
| `/points inspect` | staff | inspect account and transaction trail |

Point operations fail closed while PostgreSQL is degraded. A GUI must display the final reason and transaction reference only after commit. PlaceholderAPI exposes safe read-only values such as `%nexus_points%`; it must not offer mutation through text placeholders.

## References

[1]: file:///home/ubuntu/upload/pasted_content_3.txt "Authoritative SMPPlatform plugin brief"
[2]: file:///home/ubuntu/neon-nexus-hub/src/data/mock.ts "Neon Nexus website product specification"
[3]: https://www.postgresql.org/docs/current/ddl-constraints.html "PostgreSQL constraints documentation"
[4]: https://wiki.placeholderapi.com/developers/ "PlaceholderAPI developer guides"
