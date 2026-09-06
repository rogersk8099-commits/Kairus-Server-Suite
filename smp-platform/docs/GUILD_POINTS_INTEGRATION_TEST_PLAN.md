# Phase 3 Manual Integration Test Plan

Run these checks on a non-production Paper/Purpur **1.21.4** server with Java 21. Use separate Java and Bedrock test identities; do not use a username prefix as the identity assertion.

| Area | Procedure | Expected result |
|---|---|---|
| Bootstrap | Apply `V003__guilds_and_points.sql` through the parent Flyway runner, configure a Hikari pool, bind the repositories/services, and enable the command adapters. | Plugin starts with no SQL, main-thread, or missing optional-integration errors. No database or API secret appears in source or logs. |
| Java guild lifecycle | Create a guild in Ashfall; invite, accept, decline, promote, demote, kick, edit, transfer ownership, leave, and disband with distinct accounts. | Commands and GUI actions enforce ranks. The leader cannot leave; transfer leaves exactly one leader. Audit events appear for privileged actions. |
| Bedrock/Geyser | Join as a Floodgate/Geyser Bedrock identity; open Guild and Points screens; perform invite acceptance, history, and leaderboard navigation. | Screens remain usable as standard inventory views (or renderer-equivalent form); stable UUID maps to the same account across reconnects; no edition-only gameplay distinction. |
| Obsidian Gate | With `obsidian-gate-enabled: false`, try guild creation/invites in Obsidian Gate. Enable it, then repeat. Award `HARDCORE_POINTS` in Gate and Ashfall. | Guild mutation is rejected while disabled and permitted only when enabled. Hardcore points work only in Obsidian Gate. |
| Ashfall and Quarry | Create guild in Ashfall, then enter Quarry and check guild display/action context. | Quarry inherits the Ashfall guild identity; no duplicate Quarry membership exists. |
| Verdance | Attempt guild mutation and every points mutation in Verdance. | Both fail with a policy result; no account balance or ledger entry changes. |
| Ledger atomicity | In PostgreSQL, induce an insert failure after an account update attempt (for example, temporarily deny `INSERT` on `point_transactions`) then call admin add. Restore permissions. | The service reports storage failure and the account balance remains unchanged. No transaction row is written without an account mutation. |
| Concurrency | From two async command invocations, spend the final available balance simultaneously. | At most one mutation succeeds. `FOR UPDATE`, optimistic account versioning, and rollback prevent an overdraft when negative balances are disabled. |
| History and metadata | Award points with metadata containing quotes, backslashes, and newline characters; inspect `/points history`. | The immutable ledger returns the original flat metadata values, source, actor, world, before/after values, and correlation ID. |
| Outage behavior | Stop PostgreSQL temporarily and invoke a guild edit and points add. | The plugin reports a safe storage failure; it does not silently mutate in-memory/account state or crash the server. |
| Events | Register listeners for `NexusGuildCreateEvent`, `NexusGuildJoinEvent`, `NexusGuildLeaveEvent`, and `NexusPointsChangeEvent`. | Events fire after the database operation succeeds and are delivered on Paper's primary thread. |

## Production acceptance checklist

Confirm that PostgreSQL application-role grants do not include `UPDATE`, `DELETE`, or `TRUNCATE` on `point_transactions`; that every service dispatch is made from the parent asynchronous database executor; and that Adventure/MiniMessage rendering happens only in the Paper command/GUI edge adapter. Bind permissions to LuckPerms: `smpplatform.guild.create`, `smpplatform.guild.manage`, `smpplatform.admin.guilds`, `smpplatform.points.view`, `smpplatform.admin.points`, and `smpplatform.admin`.
