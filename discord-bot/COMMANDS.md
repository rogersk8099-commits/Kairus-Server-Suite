# Discord Command Operations

**Document status:** This document defines the intended operator command contract. It is not proof that every listed command is registered in any environment. Before activating a command, confirm its implementation, Discord registration scope, API route, authorization tests, and audit event.

## Command model

Kairu commands are Discord **application commands**. The bot must authorize both the Discord actor and the requested Kairu operation through the central API. A successful Discord permission check alone is not sufficient for a canonical Kairu change. Discord supports chat-input, user, and message application command types; Kairu’s baseline uses chat-input slash commands. [1]

| Command | Intended audience | Canonical authority | Required result |
|---|---|---|---|
| `/setup-server` | Guild administrators designated as Kairu operators. | Central API configuration endpoint. | Converges the guild to the approved configuration without duplication. |
| `/kairu-status` | Authorized operators. | API health/status endpoint. | Returns redacted dependency status and correlation ID. |
| `/link-minecraft` | Authorized guild operators. | API integration configuration endpoint. | Begins a controlled link flow; never exposes a shared secret in public chat. |
| `/unlink-minecraft` | Authorized guild operators. | API integration configuration endpoint. | Revokes/disable links safely with an audit record. |
| `/announce` | Authorized Kairu operators. | API policy and dispatch endpoint. | Posts approved content only to configured destinations. |

If the implementation registers a different command set, update this document and its authorization matrix before release. Do not use message prefixes such as `!setup` as an undocumented alternative, because doing so can create a Message Content intent requirement.

## `/setup-server` contract

`/setup-server` is the sole supported reconciliation command for initial guild configuration. It is intentionally **idempotent**: invoking it multiple times with the same desired configuration produces the same final state and does not create additional channels, roles, messages, webhooks, API configuration rows, or external subscriptions.

The command must acquire an idempotency key derived from the guild ID and desired configuration revision, and the central API must serialize or safely de-duplicate concurrent setup requests for the same guild. A Discord interaction retry, double click, worker restart, or two administrators using the command at once must not create two configurations.

| Resource type | Desired behavior on first run | Desired behavior on repeat run | Ownership guard |
|---|---|---|---|
| Guild configuration record | Create a single API record keyed by Discord guild ID. | Return existing record and current revision. | API unique constraint and transactional upsert. |
| Kairu category/channel | Create only if the approved configuration requests it. | Find by immutable Kairu metadata/record; update only managed fields. | Never adopt or rename an unmarked community channel. |
| Kairu role | Create only when role management is approved. | Resolve the recorded role ID; update only permitted properties. | Never modify roles above the bot or not marked Kairu-managed. |
| Command permissions/configuration | Apply the declared policy. | Reconcile drift only when authorized. | Log old and new values; do not broaden permissions silently. |
| External integration subscription | Create after API validation and signing configuration. | Reuse active subscription or safely replace a revoked one. | De-duplicate by provider resource and guild binding. |
| Welcome/configuration message | Post only if no current managed message exists. | Edit/reuse current message instead of posting another. | Store Discord message ID in the API record. |

### Exact operator procedure for `/setup-server`

1. Confirm the bot is installed in the intended guild with only the required permissions. If configuration does not create channels or roles, do not grant `Manage Channels` or `Manage Roles`.
2. Confirm the central API health check is successful and the guild is permitted by Kairu policy. Ensure all expected variables are present without revealing their values.
3. In the approved setup channel, invoke `/setup-server` with the documented options for that release. The command should respond ephemerally while it validates and reconciles, then provide a redacted result and correlation ID.
4. Record the correlation ID. Verify the API audit entry identifies the Discord guild, actor, configuration revision, resources created/updated, and no secret material.
5. Invoke `/setup-server` again with identical options. Verify the response identifies a no-change/reconciled state and that counts of Kairu-managed resources are unchanged.
6. If a different result occurs, stop repeated invocation. Disable the command for the guild if possible, collect the correlation IDs, and use the rollback procedure below.

## Authorization and response rules

The bot should check an allowlisted Kairu operator role or Discord permission only as a preliminary gate. The central API is the final policy decision point and should bind the Discord user ID, guild ID, command name, and requested configuration. Every mutation must carry a trace/correlation ID and idempotency key. Responses must not include tokens, database URLs, HMAC secrets, OAuth refresh tokens, raw provider errors, or internal network addresses.

| Control | Bot responsibility | API responsibility |
|---|---|---|
| Discord interaction authenticity | Verify Discord signature before processing HTTP interactions, or rely on authenticated Gateway delivery as applicable. | Accept only authenticated bot service requests. |
| Actor authorization | Read declared guild context and preliminary operator eligibility. | Enforce canonical user/guild policy and record the decision. |
| Idempotency | Send a stable idempotency key and avoid duplicate side effects. | Enforce uniqueness and return prior successful result when appropriate. |
| Audit | Emit command, actor, guild, correlation ID, outcome, and duration. | Persist immutable canonical audit event without secrets. |
| Failure | Give a concise user-safe response. | Return typed, redacted errors and rollback guidance. |

## Configuration rollback

`/setup-server` rollback must use a recorded configuration revision or a controlled disable command/API workflow, not an improvised deletion spree. A rollback should first prevent new mutations, then restore the prior Kairu configuration record, then delete only resources that the API proves were created exclusively by the failed revision and have not been adopted by users. Messages, roles, and channels not marked as Kairu-managed must remain untouched.

| Failure | Immediate action | Recovery verification |
|---|---|---|
| Duplicate resources | Disable setup execution and preserve IDs. | Deduplicate only resources with matching Kairu ownership markers. |
| Wrong permissions | Remove excess bot role permissions and channel overwrites. | Re-run least-privilege permission test; do not repeat setup until approved. |
| API mutation failed after Discord side effect | Record the affected Discord resource ID. | Compensate through the API-owned rollback workflow and audit it. |
| Uncertain ownership | Do not delete. | Escalate to guild and security owners for manual review. |

## References

[1]: https://docs.discord.com/developers/interactions/application-commands "Discord Developer Documentation: Application Commands"
[2]: https://docs.discord.com/developers/interactions/receiving-and-responding "Discord Developer Documentation: Receiving and Responding to Interactions"
[3]: https://discord.com/developers/docs/topics/permissions "Discord Developer Documentation: Permissions"
