# Operations Runbook

**Document status:** This runbook describes intended operating procedures. It does not state that dashboards, alerts, backups, integrations, or deployments are presently configured or healthy.

## Operating posture

Operate the Kairu Discord bot as a dependent presentation service with an independent failure domain from the central API. The central API remains authoritative if the bot is unavailable. The bot must fail closed for mutating commands when API authorization, idempotency, or state confirmation is unavailable. It must not bypass the API with a direct PostgreSQL write or produce a success message for an unconfirmed canonical change.

| Service condition | User-facing behavior | Operator priority |
|---|---|---|
| Bot healthy; API healthy | Commands and notifications proceed through normal policy. | Monitor routine indicators. |
| Bot unhealthy; API healthy | Canonical API/provider processing continues; Discord delivery may be delayed or unavailable. | Restore bot; reconcile missed delivery idempotently if designed. |
| API unhealthy; bot healthy | Bot returns a concise unavailable/degraded response for commands requiring API confirmation. | Restore API; do not add direct database fallback. |
| PostgreSQL unhealthy | API rejects unsafe mutations; bot reports degraded service. | Protect data and restore database service/connection. |
| Provider webhook unavailable | Preserve provider retry/reconciliation state at API boundary. | Verify endpoint, signature, subscription, and provider status. |

## Daily and release-time checks

Perform the following checks during the documented operational window and after every deployment. Record environment, time, operator, correlation/release IDs, and outcome without storing secrets.

| Check | Method | Expected result | Escalate when |
|---|---|---|---|
| Bot process health | Railway deployment state and runtime logs. | Running, no crash/restart loop, expected release ID. | Crashed/restarting or authentication errors. |
| API health | Approved `/health` or readiness endpoint. | Dependency checks meet policy; expected API version. | Database/API/provider dependency failure. |
| Discord connectivity | Controlled staging command or production-safe status check. | Interaction acknowledged and response contains correlation ID. | Timeout, invalid signature, or authorization drift. |
| Central API authorization | Bot service authenticated call to approved health/status route. | Allowed scope only; no 401/403 increase. | New authorization failures or unexpected route access. |
| Database/migrations | `prisma migrate status` in designated release context. | Expected migration state; no drift. | Pending/unexpected migration or schema error. |
| Webhook validation | Signed provider test fixture or approved provider test. | Valid accepted once; invalid/stale/replayed rejected. | Duplicate state change or signature bypass. |
| Backup currency | Backup dashboard/record and restore evidence. | Backup meets recovery objective; last restore test is current. | Backup failure, missing encryption, or untested restore. |

## Discord rate-limit operation

Discord applies per-route and global rate limits. Rate-limit values can change, so do not hard-code them. Parse rate-limit response headers, organize queues by `X-RateLimit-Bucket` and relevant top-level resource, and on HTTP 429 honor `Retry-After` or `retry_after` before resuming. Discord documents a 50 requests/second bot global limit and an invalid-request limit that can temporarily restrict an IP; repeated 401, 403, and 429 responses require investigation, not blind retry. [1]

| Signal | Required behavior | Forbidden behavior |
|---|---|---|
| `X-RateLimit-Remaining` approaches zero | Pause/defer only that bucket until reset; coalesce updates. | Continue optimistic bursts. |
| HTTP 429 | Respect `Retry-After`; classify `global`, `user`, or `shared`; record route/bucket safely. | Retry immediately or fan out duplicate work. |
| Global limit | Stop all bot REST work for the stated interval except no work that Discord explicitly permits outside it. | Let separate workers independently keep sending. |
| Invalid-request spike | Stop problematic call path; inspect token/permissions/request payload. | Continue error loops until temporary restriction. |
| Notification burst | Queue, deduplicate, aggregate, and apply per-guild/channel limits. | Post one message per unbounded event. |

The bot should use a centralized outbound dispatcher rather than each command or webhook handler calling Discord independently. Commands that can defer their interaction response should do so within Discord’s interaction requirements, then complete API work asynchronously with a bounded timeout. Alert before customer-visible message failures become sustained.

## Provider and webhook operation

Inbound provider events are processed by the central API. An on-call operator verifies the source’s signature, freshness, event ID, and deduplication status before considering an event missing. The bot must not consume external webhooks directly as an emergency workaround.

| Symptom | Triage sequence | Resolution |
|---|---|---|
| Twitch events missing | Check EventSub subscription state, callback reachability, API logs, signature failures, and revocations. | Recreate subscription only after root cause and secret validity are confirmed. |
| YouTube quota approaching limit | Review quota dashboard, request costs, cache behavior, and polling schedule. | Throttle, use cached data, and request quota extension through official process if justified. |
| Duplicate provider notification | Locate provider event/message ID and API idempotency record. | Correct deduplication/retry behavior; do not manually repost. |
| Invalid webhook signature | Confirm raw-body preservation and active secret/key version. | Reject request; rotate secret if compromise is possible. |
| TikTok feature request | Confirm official API eligibility and written approval. | Do not scrape; route through security/legal and official developer review. |

Twitch EventSub messages may be resent and Twitch provides a unique message ID, so deduplication remains mandatory. [2] YouTube Data API calls use quota; monitor the Google Cloud quota view and avoid broad polling or search loops when budgets are constrained. [3]

## Backup, recovery, and restoration

Railway PostgreSQL is a production dependency, not a backup strategy by itself. Define recovery point objective (RPO), recovery time objective (RTO), backup cadence, retention, encryption, off-platform copy policy, access owners, and restore-test cadence in the organization’s backup system. Before destructive migrations or high-risk changes, verify a current recovery point and a named restore owner.

| Control | Minimum operational requirement | Evidence |
|---|---|---|
| Backup scope | Include canonical PostgreSQL data and required migration history. | Timestamped backup inventory and retention policy. |
| Encryption/access | Encrypt storage and restrict restoration access to designated operators. | Access review and key/role record. |
| Restore test | Restore to an isolated environment at a defined cadence. | Test date, target, duration, integrity checks, and outcome. |
| Migration gate | Verify restorable backup before destructive or irreversible migration. | Release checklist approval. |
| Reconciliation | After restore, reconcile provider/webhook events using idempotency IDs. | Reconciliation report without secrets. |

A database restoration requires deliberate coordination. First contain writers and record the intended recovery point. Restore only to an isolated target for validation unless the incident commander authorizes production restoration. Validate schema migration state, row counts or business invariants, API health, bot read behavior, and idempotency ledgers before reopening writes. If RPO means events are missing, replay only trusted provider events through the API’s normal deduplicated path.

## Incident response and escalation

Use the security incident procedure in [Security](SECURITY.md) for suspected credential exposure, forged events, or unauthorized access. For routine availability incidents, open an incident when a customer-visible command/API path exceeds the agreed error or latency objective, a critical integration is down, a backup is not recoverable, or a security alert appears. Preserve release IDs, deployment logs, correlation IDs, provider event IDs, and redacted request metadata.

1. **Declare and assign.** Assign an incident lead, technical investigator, communications owner, and scribe. State the affected environment and user impact.
2. **Stabilize.** Use feature flags, command disablement, Railway rollback, provider subscription disablement, or permission reduction to stop harm. Do not execute destructive database changes without the database owner.
3. **Diagnose.** Compare last known-good release, migration status, service logs, Discord rate limits, API authorization failures, and provider status. Change one variable at a time where possible.
4. **Recover and verify.** Restore a known-good service/configuration, then verify command, API, database, webhook, and audit behavior. Include duplicate-event and permission tests if they were relevant.
5. **Close responsibly.** Document timeline, impact, cause, remediation, missing monitoring, and follow-ups. Rotate secrets if exposure cannot be ruled out.

## References

[1]: https://docs.discord.com/developers/topics/rate-limits "Discord Developer Documentation: Rate Limits"
[2]: https://dev.twitch.tv/docs/eventsub/handling-webhook-events "Twitch Developer Documentation: Handling webhook events"
[3]: https://developers.google.com/youtube/v3/getting-started "Google Developers: YouTube Data API Overview"
[4]: https://docs.railway.com/deployments/deployment-actions "Railway Documentation: Deployment Actions"
[5]: https://www.prisma.io/docs/orm/prisma-client/deployment/deploy-database-changes-with-prisma-migrate "Prisma Documentation: Deploying database changes with Prisma Migrate"
