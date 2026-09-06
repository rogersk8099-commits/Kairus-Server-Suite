# Minecraft Integration

**Document status:** This is a target operating design. It does not state that a Minecraft plugin, server endpoint, shared secret, or production link exists.

## Supported integration boundary

A Minecraft server integration communicates with the **central Kairu API**, not directly with Discord, the Discord bot process, or PostgreSQL. The plugin emits approved game events to an HTTPS API endpoint. The API authenticates the source, verifies freshness and signature, applies canonical authorization and deduplication, persists the result, and optionally asks the bot to post a Discord notification. This prevents a game-server compromise from becoming a Discord bot-token or database compromise.

| Direction | Sender | Receiver | Authentication and integrity | Canonical result |
|---|---|---|---|---|
| Game event | Minecraft plugin | Central API | HTTPS plus per-server HMAC signature and timestamp; mTLS may be added where managed. | API validates, deduplicates, audits, and records the event. |
| Operator link request | Discord bot | Central API | Bot service credential; Discord user/guild context. | API creates a pending, expiring link challenge. |
| Link confirmation | Minecraft plugin | Central API | Plugin HMAC plus one-time challenge. | API binds the approved server identity to the guild. |
| Notification | Central API | Discord bot | Private service authentication or signed internal webhook. | Bot renders an allowed Discord message. |
| Game command | Central API | Minecraft plugin | Authenticated outbound channel; explicit allowlist. | Plugin acknowledges an idempotent command execution. |

## Event envelope and signing

The API should reject unsigned, stale, duplicated, malformed, or unauthorized game events before any state change. The HMAC is computed over a **versioned canonical string** containing at least the request method, path, timestamp, nonce/event ID, and raw body bytes. The exact serialization must be documented and tested identically by plugin and API. Use a cryptographically generated secret unique to each Minecraft server, never a guild-wide static secret reused across servers.

| Field | Example placeholder | Requirement |
|---|---|---|
| `event_id` | `<UUID_OR_PROVIDER_EVENT_ID>` | Globally unique for the source; retained for deduplication. |
| `occurred_at` | `<RFC3339_UTC_TIMESTAMP>` | Must pass a narrow clock-skew window; reject stale events. |
| `server_id` | `<Kairu_Server_UUID>` | Bound to exactly one active integration record. |
| `event_type` | `player.joined` | Must be on an API allowlist with a versioned payload schema. |
| `payload` | `<JSON_EVENT_PAYLOAD>` | Minimize personal data and size; validate schema. |
| `X-Kairu-Key-Id` | `<KEY_VERSION>` | Identifies current or briefly overlapping rotated secret. |
| `X-Kairu-Signature` | `sha256=<HMAC_HEX>` | Calculated from the raw bytes; compare in constant time. |

The API keeps an event-id ledger with a unique constraint for an agreed retention period. On duplicate delivery, it returns an idempotent accepted/previous-result response but does not repost or replay side effects. The plugin retries only transient outcomes with bounded exponential backoff and jitter. It must not retry a definitive authentication, authorization, or schema error.

## Exact link and activation sequence

1. **Create the integration record in the central API.** An authorized operator initiates `/link-minecraft` or the equivalent API workflow. The API creates a pending record associated with the Discord guild, an expiration, a one-time challenge, and a server-specific secret reference. It must not reveal the secret in a public Discord response.
2. **Place plugin configuration securely.** On the Minecraft host, configure the HTTPS API base URL, server identity, active secret/key ID, request timeout, and trust settings using host-level protected configuration. Restrict filesystem access to the game service account. Do not place the Discord bot token, Discord application secret, or PostgreSQL URL in plugin configuration.
3. **Send a signed challenge confirmation.** The plugin sends the one-time challenge to the API using the signed envelope. The API verifies signature, timestamp, unique event ID, pending challenge, and operator approval before binding.
4. **Verify the link.** The API returns a redacted server identity and link status. The bot may show the guild and server display name, but never secret material or private host addresses unless policy permits it.
5. **Run a harmless test event.** Send one allowlisted test event. Verify one API audit event, one deduplication record, and at most one configured Discord notification.
6. **Retry the same event ID.** Verify no second canonical mutation or Discord notification occurs.
7. **Activate event types incrementally.** Enable only reviewed event types. Each new type requires a schema, authorization policy, data classification, Discord rendering policy, rate budget, and rollback plan.

## Failure, unlink, and rollback

If the plugin server is compromised or a signature is suspected to be exposed, disable the integration at the API first, reject new events, rotate that server’s secret, and invalidate any outbound command channel. Preserve event/audit evidence. Do not simply change the plugin URL while leaving the old secret valid.

| Condition | Immediate containment | Recovery |
|---|---|---|
| Repeated invalid signatures | Rate limit source and disable the server integration after threshold. | Investigate configuration mismatch or compromise; rotate per-server secret. |
| Clock skew | Reject with non-sensitive error and record measured skew. | Correct host time using approved time synchronization; do not widen acceptance indefinitely. |
| Duplicate event storm | Preserve deduplication ledger and apply source rate limit. | Fix plugin retry logic; replay only through an approved tool. |
| Wrong guild link | Disable the binding before notifications. | Require a new operator-approved one-time link; audit the correction. |
| API unavailable | Plugin queues bounded non-sensitive events if designed to do so. | Resume with idempotent event IDs; expire/drop safely after configured retention. |

## Privacy and operational controls

Minimize player data sent to the API and Discord. Use stable internal identifiers where a display name is unnecessary. Define retention, access control, and deletion behavior before enabling player-facing events. Ensure Minecraft events cannot cause arbitrary command execution, arbitrary mentions, mass notifications, role changes, or Discord markdown injection. Apply API-side allowlists and escaping even if the plugin claims to validate input.

## References

[1]: https://www.rfc-editor.org/rfc/rfc2104 "RFC 2104: HMAC: Keyed-Hashing for Message Authentication"
[2]: https://cheatsheetseries.owasp.org/cheatsheets/Webhook_Security_Guidelines_Cheat_Sheet.html "OWASP Cheat Sheet Series: Webhook Security Guidelines"
[3]: https://discord.com/developers/docs/topics/permissions "Discord Developer Documentation: Permissions"
