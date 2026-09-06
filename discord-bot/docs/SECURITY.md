# Security Standard and Incident Response

**Document status:** This standard prescribes controls. It does not attest that controls, credentials, monitoring, backups, or incident procedures are currently implemented.

## Security objectives

Kairu protects Discord bot credentials, API service credentials, provider credentials, webhook secrets, database access, guild configuration, and player/provider data. The highest-risk paths are bot-token exposure, a forged webhook, an over-privileged Discord invite, direct database bypass of the central API, unreviewed migration, and uncontained provider or plugin compromise.

| Asset | Threat | Minimum control | Owner |
|---|---|---|---|
| Discord bot token | Account takeover and bot impersonation. | Secret manager, no source control, least-privilege invite, rotation playbook. | Discord application owner. |
| Discord public key | Forged HTTP interactions if verification is omitted. | Verify Ed25519 interaction signature and timestamp before processing. | Bot service owner. |
| API service token | Unauthorized canonical API calls. | Route-scoped credential, TLS, short rotation cadence, audit. | API owner. |
| Webhook HMAC secret | Forged/replayed provider events. | Raw-body HMAC, freshness, replay ledger, constant-time comparison. | Integration owner. |
| PostgreSQL credentials | Data loss/exfiltration or unauthorized schema changes. | Isolated credentials, encrypted transport, least privilege, backups, migration owner. | Database owner. |
| Discord permissions | Unwanted channel/role or moderation actions. | No `Administrator`; minimum bot permissions and role hierarchy tests. | Guild administrator. |

## Mandatory technical controls

The Discord bot token is an authentication credential, not an application identifier. Discord warns that it authorizes API requests and should never be shared or checked into version control. Store it only in the approved secret system. [1] Require a human approval record before granting privileged intents, `Manage Roles`, `Manage Channels`, or a new provider scope.

For HTTP interactions, verify Discord’s request signature using the application public key before parsing or acting on the payload. For external webhooks, calculate HMAC from the provider-prescribed message using the raw request body, use constant-time equality, validate a narrow timestamp window, and de-duplicate message IDs. The Twitch EventSub contract, for example, requires HMAC-SHA256 over message ID, timestamp, and raw body and specifies that duplicates may occur. [2]

| Area | Must do | Must not do |
|---|---|---|
| Logging | Log event type, correlation ID, outcome, latency, IDs minimized to need, and redacted error code. | Log tokens, Authorization headers, database URLs, HMAC values, raw private payloads, or OAuth refresh tokens. |
| Secrets | Use unique environment/provider/server secrets and documented rotation. | Reuse production secrets in staging or send secrets through Discord. |
| Discord | Use slash commands, minimal scopes/permissions, and no privileged intents by default. | Grant `Administrator` or enable Message Content for a command-only bot. |
| Database | Run reviewed `migrate deploy` once from designated release path. | Auto-run competing migrations from API and bot startup or use `db push` in production. |
| Provider data | Use official APIs and enforce provider quotas and terms. | Scrape TikTok or bypass provider limits/access controls. |
| Input | Apply size limits, schemas, rate limits, and output encoding. | Trust Minecraft/plugin/provider payloads because they arrived over TLS. |

## Token and secret rotation

Rotate credentials on a defined schedule and immediately when compromise is suspected. Rotation must have an owner, start/end time, consumer inventory, validation result, and revocation confirmation. Where a provider permits overlap, deploy the new value, verify it, and then revoke the old value. Discord bot tokens are reset in the Developer Portal; reset invalidates the old token, so coordinate deployment to minimize downtime. [1]

| Credential | Routine cadence | Immediate rotation trigger | Rotation procedure |
|---|---|---|---|
| Discord bot token | Organization-defined, risk-based cadence. | Suspected disclosure, unexpected authentication, owner change. | Reset token, store new value, deploy bot, validate, verify old token no longer works. |
| Bot-to-API token | Organization-defined cadence. | Bot/API compromise or authorization anomaly. | Mint scoped replacement, deploy consumer, revoke prior token, review API audit. |
| Twitch/EventSub HMAC secret | On subscription replacement and risk-based cadence. | Invalid signature anomaly or secret exposure. | Create/recreate subscription with new secret, verify challenge, retire old subscription. |
| Minecraft per-server secret | Per server, risk-based cadence. | Server host/plugin compromise or wrong binding. | Disable binding, issue new key ID/secret, reconfigure plugin, validate signed test. |
| Database password | Organization-defined cadence. | Credential exposure, access anomaly, departing administrator. | Create/change credential, update authorized services, validate, revoke old access. |

## Incident response

Treat a suspected token or signing-secret exposure as an incident, not a configuration inconvenience. The incident lead owns containment; preserve enough evidence to understand use but do not copy secrets into the incident record.

1. **Identify and triage.** Capture time, source, affected environment, credential type, indicator, and correlation IDs. Classify whether active misuse is plausible.
2. **Contain.** Disable the affected integration, revoke/reset exposed tokens, restrict bot permissions, pause automation, or isolate the service as appropriate. For bot token exposure, reset the token in Discord and stop old token-bearing instances.
3. **Eradicate.** Remove the leak from source, configuration, log pipeline, or access path. Rotate all credentials that could have been reachable through the same compromise.
4. **Recover.** Deploy clean configuration; test authentication, webhook verification, API policy, and expected commands in staging or a controlled guild before reopening broad access.
5. **Notify and document.** Follow the organization’s legal, privacy, and customer notification policy. Record timeline, scope, containment, data assessment, credential IDs/versions—not values—and corrective actions.
6. **Learn.** Add detection, tests, access restrictions, or architectural changes before closure. Revisit whether a privileged intent, direct DB credential, or provider permission was necessary.

| Alert | Severity guide | First action |
|---|---|---|
| Bot token in repository/log/chat | Critical. | Reset token, remove artifact, search downstream copies, deploy replacement. |
| Signature verification failures spike | High. | Rate limit/disable source; distinguish configuration change from forgery. |
| Unexpected `Manage Roles`/channel action | High. | Remove excess permissions and preserve Discord/API audit records. |
| Repeated 401/403/429 responses | Medium to high. | Stop blind retries; inspect credential/permission/rate-limit behavior. |
| Backup restore failure | High. | Freeze destructive releases and remediate restore capability. |

## References

[1]: https://docs.discord.com/developers/quick-start/getting-started "Discord Developer Documentation: Getting Started"
[2]: https://dev.twitch.tv/docs/eventsub/handling-webhook-events "Twitch Developer Documentation: Handling webhook events"
[3]: https://cheatsheetseries.owasp.org/cheatsheets/Secrets_Management_Cheat_Sheet.html "OWASP Cheat Sheet Series: Secrets Management"
