# Phase 1 manual integration test plan

| Scenario | Procedure | Expected result |
|---|---|---|
| Paper/Purpur bootstrap | Start Java 21 Paper or Purpur 1.21.4 with the JAR and no optional plugins. | Plugin enables; all nine YAML files exist; `/worlds` lists the six canonical worlds. |
| PostgreSQL + Flyway | Set `SMPPLATFORM_DB_PASSWORD`, provision the configured PostgreSQL database, then start. | HikariCP connects and Flyway creates `players`, `minecraft_accounts`, `worlds`, `integration_state`, `event_outbox`, and `audit_logs`. |
| Database failure | Omit the password or stop PostgreSQL while online. | Server stays online, database reports unavailable, and durable mutations are refused rather than silently written to YAML. |
| Central API outage | Start with a valid local registry cache then make the configured API unreachable. | Cached world definitions remain usable; registry enters offline mode and reports the failure. |
| Central revision conflict | Return a stale revision and then different content with the current revision. | Stale revision is rejected; same-revision content is flagged as a conflict without replacing live configuration. |
| Multiverse | Install Multiverse-Core, load each configured world, and use the adapter from a future command/module. | Adapter resolves the registry world name, delegates loading/unloading to Multiverse, and does not own raw world files. |
| Multiverse-Inventories | Install Multiverse-Inventories with defaults, then inspect desired group map. | Ashfall and Quarry map to `ASHFALL_GROUP`; Obsidian Gate maps only to `HARDCORE_GROUP`. |
| Java and Bedrock | Join once with Java and once using Geyser/Floodgate. | Java resolves to UUID/`JAVA`; Bedrock resolves to Floodgate UUID/`BEDROCK` and XUID where exposed. No username prefix is used as identity. |
| Outbox outage/recovery | Insert/enqueue an outbox event, make Central API unavailable, then restore it. | Event becomes retryable with exponential backoff, remains durable, and becomes delivered only after acknowledgement using the same idempotency key. |
| Shutdown | Trigger a controlled Paper shutdown with sync/outbox scheduled. | Schedulers and I/O pool drain or cancel within configured timeout; HikariCP closes cleanly. |
