# Central API and External Provider Integration

**Document status:** This document defines integration boundaries and implementation requirements. It does not certify that an endpoint, account, OAuth consent screen, subscription, webhook, or credential is active.

## Central API is the source of truth

The central Kairu API owns canonical authorization, guild configuration, provider bindings, durable state transitions, audit records, idempotency records, and Prisma schema/migration ownership. The Discord bot is a client of that API. It may cache non-authoritative presentation data briefly, but it must not independently decide a Kairu state transition or write canonical domain data outside the API contract.

| Concern | Central API owns | Discord bot may do | Discord bot must not do |
|---|---|---|---|
| Guild setup | Desired state, revision, ownership markers, authorization, audit record. | Invoke reconciliation and present status. | Create untracked canonical configuration. |
| Provider events | Signature verification, replay checks, deduplication, validation, persistence. | Render an API-approved notification. | Accept external webhooks directly without API policy. |
| Database schema | Prisma schema, reviewed migration history, release migration job. | Use API contract; optionally use a separately owned operational schema only by explicit design. | Run competing automatic migrations at service startup. |
| Secrets | Provider credential references, rotation state, access policy. | Receive only its own runtime secrets from platform store. | Return secrets in command output, logs, or user content. |
| Authorization | Kairu policy across actor, guild, integration, and action. | Perform preliminary Discord-context checks. | Treat a Discord role check as final authority. |

The bot-to-API interface should use HTTPS, a scoped service credential such as `KAIRU_BOT_API_TOKEN`, strict request/response schemas, timeouts, retries only for idempotent requests, correlation IDs, and explicit idempotency keys for mutations. Restrict the bot credential to the routes and guilds it requires. Prefer private Railway networking where available, but do not treat network location as the only security control.

## HMAC webhook signing requirements

All external webhook endpoints terminate at the central API. Validate the signature against the **raw request body** before parsing JSON. Include a timestamp and provider event ID in the signed material or use the provider’s specified message construction. Compare MACs using a constant-time comparison. Reject invalid, stale, replayed, or oversized input before generating a side effect. Preserve only redacted diagnostic context.

| Control | Required implementation | Verification |
|---|---|---|
| Secret | Cryptographically random secret, unique per provider subscription or integration as feasible. | Secret manager record has owner, purpose, creation, and rotation date. |
| Raw body | Capture exact request bytes before framework body parsing alters them. | Signature test fails when body whitespace/order changes. |
| Timestamp | Enforce provider-specific freshness window. | Stale signed fixture is rejected. |
| Replay | Store provider event/message IDs with a uniqueness constraint and bounded retention. | Same valid payload twice produces one canonical action. |
| MAC comparison | Use timing-safe equality after validating comparable lengths. | Invalid signature returns a generic 4xx with no processing. |
| Response | Acknowledge only after minimum validation; queue slow work where necessary. | Provider retry does not create duplicates. |

Provider implementation details and the verified source references are consolidated in [Streaming Providers](docs/STREAMING_PROVIDERS.md).

### Twitch EventSub

Use the official Twitch EventSub API for Twitch events. Twitch signs `message-id + timestamp + raw request body` with HMAC-SHA256 using the subscription secret and sends the result in `Twitch-Eventsub-Message-Signature`; Twitch instructs implementers to use a time-safe comparison. Twitch can retry notifications and includes a unique message ID, so deduplication is required. Verification challenges must be signature-checked, then answered exactly as required by Twitch. [1]

| Twitch requirement | Kairu implementation rule |
|---|---|
| Subscription secret | Generate an ASCII secret meeting Twitch requirements; store it as a provider secret, never in source. |
| Callback verification | Verify signature and return the supplied challenge as plain text only after valid verification. |
| Notifications | Verify first; deduplicate on `Twitch-Eventsub-Message-Id`; persist before requesting Discord delivery. |
| Revocation | Record revocation reason, disable dependent automation, alert the integration owner, and recreate only after remediation. |
| Delivery response | Return the correct status promptly; queue slow Discord/API actions after validation. |

### YouTube Data API v3

Use only the official YouTube Data API v3. Create a Google Cloud project, enable the API, and use an API key only for permitted public data requests. Use OAuth 2.0 and least-privilege scopes for user-authorized write or private-data operations. The API has quota; invalid requests also consume quota. Monitor usage, cache stable resources with ETags where appropriate, request only needed `part` and `fields`, and apply a provider-aware budget. [2]

| YouTube operation | Credential pattern | Operations control |
|---|---|---|
| Public channel/video metadata | Restricted API key where provider policy permits. | Cache, use ETags, enforce daily budget. |
| User/private data or writes | OAuth 2.0 access and refresh tokens. | Explicit user consent, encrypted secret storage, minimal scopes, revocation handling. |
| Quota exhaustion | No new broad polling or search loops. | Return a degraded status, preserve cached data, and alert owner. |

### TikTok boundary

Kairu does **not** use TikTok scraping, undocumented endpoints, browser automation, credential sharing, robots, or attempts to bypass access controls. If a future approved feature requires TikTok, it must use an official TikTok Developer Service/API, comply with current documentation and terms, obtain all required approval, document authorized data use, and pass legal/security review. TikTok’s Developer Terms allow automated collection only as described in the Developer Documentation and restrict unauthorized collection, excessive use, and use not expressly authorized. [3]

## Error and retry contract

Use provider-specific retry behavior. Never retry a signature failure, authorization denial, schema validation error, revoked credential, or duplicate event as if it were a transient network problem. For transient upstream failures, use bounded exponential backoff with jitter, an idempotency/event key, timeout, circuit breaker, and a dead-letter/reconciliation path. The central API, not a Discord channel, owns the retry state.

## References

[1]: https://dev.twitch.tv/docs/eventsub/handling-webhook-events "Twitch Developer Documentation: Handling webhook events"
[2]: https://developers.google.com/youtube/v3/getting-started "Google Developers: YouTube Data API Overview"
[3]: https://www.tiktok.com/legal/page/global/tik-tok-developer-terms-of-service/en "TikTok Developer Terms of Service"
[4]: https://cheatsheetseries.owasp.org/cheatsheets/Webhook_Security_Guidelines_Cheat_Sheet.html "OWASP Cheat Sheet Series: Webhook Security Guidelines"
[5]: https://www.prisma.io/docs/orm/prisma-client/deployment/deploy-database-changes-with-prisma-migrate "Prisma Documentation: Deploying database changes with Prisma Migrate"
