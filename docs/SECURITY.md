# Security

**Kairu SMP Complete Suite · Manus AI · 6 September 2026**

The suite uses separate public, player, plugin, administrative, and Discord trust boundaries. The release includes placeholders only. No real credential is required to build or test it.

## Credential boundaries

| Credential/data | Allowed location | Prohibited location |
| --- | --- | --- |
| `PLUGIN_API_KEY` | Railway private variable; `plugins/KairuBridge/config.yml` | Website, `VITE_*`, chat, source, release manifest, public logs |
| `ADMIN_API_KEY` | Railway and private administrative caller | Plugin, website, browser storage, source |
| `DISCORD_BOT_TOKEN` | Railway private variable | Plugin, website, source, command output |
| `DATABASE_URL` | Railway private variable | Browser, plugin, public issue reports |
| Floodgate key | Generated server/proxy plugin data with restricted access | Website, source archive, unrelated hosts |
| Link code/XUID | In transit only where required; hashed link code in database | Application logs, analytics, public support messages |

Generate independent random keys, apply least privilege, and rotate after suspected disclosure. Do not reuse the plugin and admin keys. Railway variables are the appropriate deployment boundary for control-plane secrets.[1]

## Implemented controls

The control plane applies strict Zod request validation, bearer authentication, timing-safe secret comparison, request rate limits, configured-origin Cross-Origin Resource Sharing, parameterized PostgreSQL queries, structured redacted logs, and request IDs. Link codes use 20 cryptographic random bytes, are stored as SHA-256 hashes, expire after ten minutes, and are consumed once. Database link completion is transactional.

KairuBridge validates configuration, refuses placeholder operation, keeps credentials in the authorization header and server-ID header, and performs all network calls with `HttpClient.sendAsync`. Retries are bounded and use jitter. The command consumer maps only the control plane's `whitelist` and `notification` records to the explicit `WHITELIST_ADD`, `WHITELIST_REMOVE`, and `NOTIFY` allowlist; it cannot execute arbitrary console strings.

The server-pack leaves Java `online-mode=true`, enables/enforces the whitelist, disables RCON, does not accept the Minecraft EULA, and downloads third-party software only from listed official services. Artifacts with official checksums are verified. Sources without official digests are labeled and recorded with local inventory hashes rather than falsely represented as source-verified.[2] [3]

## Production gates

> `X-Discord-User-Id` is explicitly a development bridge. A caller can assert another Discord ID. The player API must remain non-public until signed Discord OAuth sessions replace it.

Before a public player portal launch, implement OAuth state/nonce validation, secure server-side sessions, short expiry and rotation, logout/revocation, Cross-Site Request Forgery protection for state changes, authorization tests, and a privacy/retention policy. Remove `VITE_DEV_DISCORD_USER_ID` from production. Use exact HTTPS origins in `CORS_ORIGIN`; do not use `*`.

Configure trusted-proxy behavior before treating client-IP rate limits as authoritative behind a reverse proxy. Apply infrastructure rate limits, database/network access controls, encrypted backups, log access control, dependency monitoring, and platform alerting. Separate staging and production Discord applications, databases, keys, DNS, and server IDs.

## Secret-release inspection

Release validation searches text and archive names for private-key markers, known token formats, credential-bearing URLs, populated `.env` files, database URLs, and suspicious assignments. Placeholders such as `CHANGE_ME`, `REPLACE_WITH_*`, and documentation metasyntax remain intentionally. Automated scanning reduces risk but does not prove absence of every possible credential; maintain peer review and rotate any value that may have been exposed.

## References

[1]: https://docs.railway.com/guides/variables "Railway variables documentation"
[2]: https://docs.papermc.io/paper/reference/server-properties/ "Paper server properties documentation"
[3]: https://docs.papermc.io/misc/downloads-service/ "Paper Downloads Service documentation"
[4]: https://discord.com/developers/docs/topics/oauth2 "Discord OAuth2 documentation"
[5]: https://owasp.org/www-project-web-security-testing-guide/ "OWASP Web Security Testing Guide"
[6]: https://developer.mozilla.org/en-US/docs/Web/HTTP/CORS "MDN Cross-Origin Resource Sharing guide"
