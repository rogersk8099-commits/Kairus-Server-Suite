# Automated and manual integration test plan

> **Status:** This is an implementation design, example configuration, and operations runbook for the intended SMPPlatform service. It does **not** claim that a JAR, database, external API, integration, backup, or deployment has been executed. The target is **Paper/Purpur 1.21.4 on Java 21**, with a **server-side-only** plugin. Java and Bedrock players use the same gameplay system; no client mod is required.

## Test approach

Run automated unit tests for registry validation, points ledger arithmetic/idempotency, Hardcore state transitions, reset preconditions, archive event protection, API retry/backoff, and permission decisions. Run PostgreSQL integration tests against an ephemeral isolated database and Paper integration tests on a non-production server. The following manual cases are acceptance criteria, not completed test results.

| ID | Scenario | Steps | Expected result |
|---|---|---|---|
| T01 | Java join | Join as Java account; inspect profile and `/worlds` | UUID identity, edition Java, six exact worlds shown |
| T02 | Bedrock join | Join through Geyser/Floodgate; use GUI and change world | edition Bedrock/XUID captured; menu controls work; no gameplay difference |
| T03 | Multiverse mapping | Load/import all six configured folders and `/worlds teleport` | each maps only to its registry entry; bad name denied |
| T04 | Inventory groups | Place distinct test items across all groups | Ashfall/Quarry matches policy; Hardcore never shares; creative/event/archive isolated |
| T05 | LuckPerms | Test player/staff/world-context grants | least privilege; denied action remains denied on GUI confirm |
| T06 | PlotSquared | Claim/submit Atrium plot; review it | PlotSquared ownership verified; submission transitions audited |
| T07 | Skript/PAPI | Execute documented read/add effect and placeholders | one ledger row; async safety; neutral value for missing data |
| T08 | PostgreSQL outage | Disable DB during point/guild/reset mutation | mutation blocked, server remains running, staff warned, recovery reconnects |
| T09 | Central API outage | stop staging endpoint then restore | outbox retains/retries once idempotently; gameplay stays usable |
| T10 | Hardcore death | kill an eligible account in Obsidian Gate | death record, spectator, status, outbox and leaderboard update |
| T11 | Hardcore reset | open/close configured test window | eligibility/status transitions only with audit/confirmation |
| T12 | Quarry reset | exercise dry-run then forced backup failure | evacuation/backup validation; failure aborts before delete |
| T13 | Guild lifecycle | create/invite/accept/rank/transfer/disband | constraints, world policy, audit, and outbox correct |
| T14 | Points | add/remove/set/retry same idempotency key | immutable transaction, arithmetic correct, retry not duplicated |
| T15 | Event lifecycle | schedule/register/check-in/live/finish Gauntlet | max 64, result/reward idempotency, instance reset policy |
| T16 | Verdance protection | attempt break/place/container/explosion/entity modification | every modification denied; touring remains possible |
| T17 | Admin GUI | test each role and destructive confirmation | correct visibility, double permission check, audit record |

Capture server version, plugin versions, configuration revision, tester editions, timestamps, screenshots/log excerpts, and defects. Production release requires all critical cases pass in staging and an approved exception record for any skipped optional integration.[1]

## References

[1]: file:///home/ubuntu/upload/pasted_content_3.txt "Authoritative SMPPlatform plugin brief"
[2]: file:///home/ubuntu/neon-nexus-hub/src/data/mock.ts "Neon Nexus website product specification"
[3]: https://geysermc.org/wiki/geyser/setup/ "Geyser setup"
[4]: https://geysermc.org/wiki/floodgate/ "Floodgate overview"
[5]: https://mvplugins.org/ "Multiverse documentation"
[6]: https://github.com/Multiverse/Multiverse-Inventories "Multiverse-Inventories repository"
[7]: https://luckperms.net/wiki/Developer-API "LuckPerms Developer API"
[8]: https://intellectualsites.gitbook.io/plotsquared/features/commands "PlotSquared command documentation"
[9]: https://docs.skriptlang.org/docs.html "Skript documentation"
