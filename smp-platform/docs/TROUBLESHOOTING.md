# Troubleshooting and failure behavior

> **Status:** This is an implementation design, example configuration, and operations runbook for the intended SMPPlatform service. It does **not** claim that a JAR, database, external API, integration, backup, or deployment has been executed. The target is **Paper/Purpur 1.21.4 on Java 21**, with a **server-side-only** plugin. Java and Bedrock players use the same gameplay system; no client mod is required.

## First response

Do not restart repeatedly or delete data to make a warning disappear. Preserve the server log, configuration revision, correlation ID, recent audit/outbox records, and precise time. Check `smp_integration_state` and the Admin GUI Server Monitor for database, Central API, Multiverse, Multiverse-Inventories, LuckPerms, Geyser, Floodgate, PlotSquared, PlaceholderAPI, and Skript health. Redact credentials before sharing logs.

| Symptom | Safe response | Do not do |
|---|---|---|
| PostgreSQL unavailable | enter degraded mode; block durable/destructive mutations; reconnect with backoff; alert staff | write “temporary” YAML player data or silently change balances |
| Central API/Discord unavailable | retain local operation; queue outbox; retry idempotently | block logins or embed a Discord token |
| Multiverse unavailable | block lifecycle/reset actions; do not unload worlds | substitute direct folder deletion |
| Quarry backup failed | mark reset aborted; retain Quarry; investigate storage | regenerate/delete Quarry |
| Hardcore status mismatch | freeze affected staff action, inspect death/audit/ledger records | toggle gamemode manually without audit/state repair |
| Bedrock menu failure | use compatible renderer; reproduce with Geyser test account | identify users by prefixes or grant edition perks |
| Registry digest conflict | retain last valid registry; alert platform owner | choose a config source silently |
| Points mismatch | stop further mutation for the account; reconcile ledger and account transactionally | edit balance without a ledger transaction |

## Incident recovery

For a reset interruption, leave the world closed, preserve `smp_world_resets` and `smp_world_backups` records, determine the last safe state, and restore only from a verified backup. For outbox poison messages, inspect redacted error and payload schema, fix the receiving contract or payload handling, then explicitly replay with the original idempotency key. For account-link problems, revoke the code, issue a new hashed code after rate limit, and audit the action. Every repair has a reason, actor, before/after record, and correlation ID.

## References

[1]: file:///home/ubuntu/upload/pasted_content_3.txt "Authoritative SMPPlatform plugin brief"
[2]: file:///home/ubuntu/neon-nexus-hub/src/data/mock.ts "Neon Nexus website product specification"
