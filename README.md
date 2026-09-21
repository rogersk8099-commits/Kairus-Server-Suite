# Kairu SMP Complete Suite

**Repository structure and integration guide**

Kairu SMP Complete Suite is an integrated distribution for Java and Bedrock Minecraft. It combines a Node.js 22 control plane, dedicated Discord bot, KairuBridge transport plugin, SMPPlatform gameplay plugin, optional Fabric client UI, crossplay-ready server pack, and React/TanStack community website with Discord OAuth. The wire contract is defined in [`CONTRACT.md`](CONTRACT.md).

> **Lifecycle warning.** Paper 1.21.4 remains downloadable but is officially unsupported. Use this target only when 1.21.4 is a hard requirement, pin and stage-test the entire stack, and maintain an upgrade plan. [1] [2]

## Suite contents

| Path | Component | Release role |
| --- | --- | --- |
| `control-plane/` | Fastify API and shared PostgreSQL contract | Public website endpoints, Discord OAuth sessions, plugin telemetry/linking/queue/chat endpoints, migrations, and Railway deployment. |
| `discord-bot/` | Dedicated Discord ecosystem bot | Idempotent `/setup-server`, account/linking commands, membership sync, events, suggestions, support, reminders, stream notifications, webhooks, and dormant health server. |
| `paper-plugin/` | KairuBridge source | Transport-only Paper plugin for telemetry, linking, bounded queue execution, chat relay and soft integrations. |
| `smp-platform/` | Unified SMPPlatform source | Authoritative Paper plugin for worlds, guilds, currencies, points, lifecycle, events, creative and administration. |
| `kairu-client/` | Optional Fabric client UI | OneConfig-inspired Java-player interface. It sends only permission-checked requests to SMPPlatform; Bedrock players use server-side interfaces. |
| `server-pack/` | Paper/Purpur installation pack | Official-source installers, secure templates, Java launch scripts, `KairuBridge.jar`, and `SMPPlatform.jar`. |
| `website/` | Community website | TanStack Start/React site with Discord OAuth, secure host-only sessions, branded favicon assets, real control-plane responses, and development-only mock fallback. |
| `docs/` | Operator documentation | Installation, operations, security, and reproducible build evidence. |

## Source-of-truth rules

- **SMPPlatform** owns Minecraft gameplay state, permissions, guilds, points and world policy.
- **KairuBridge** only transports approved data between Minecraft and the Control Plane. It never owns Discord credentials.
- **Control Plane** owns API contracts, PostgreSQL persistence, queued chat/commands and identity linking.
- **Discord bot** owns Discord categories, channel mappings and persistent status embeds.
- **Kairu Client** is optional and never replaces the server plugin.
- `server-pack/plugins/*.jar` are release artifacts only. Build from source before replacing them.

The repository intentionally keeps both Paper plugins: they have different responsibilities and must not be merged into a second competing gameplay plugin.

## Live deployment

The community website is live at <https://kairu-smp-website-production.up.railway.app>. The PostgreSQL-backed control-plane API is live at <https://kairu-control-plane-production.up.railway.app>. Discord sign-up/sign-in is active and end-to-end verified. The official Kairu SMP Discord invite is <https://discord.gg/cbBj6EvcV4>. Railway `Kairu-Discord-Bot` is gateway-ready, has 46 registered guild commands, and manages a verified 15-role, 9-category, 34-channel blueprint without Administrator permission. See [`docs/DEPLOYMENT_STATUS.md`](docs/DEPLOYMENT_STATUS.md) and [`docs/DISCORD_LIVE_HANDOFF.md`](docs/DISCORD_LIVE_HANDOFF.md).

## Trust boundaries

The public website receives only public data and a server-validated user principal. It must never receive `PLUGIN_API_KEY`, `ADMIN_API_KEY`, `WEBSITE_API_SECRET`, `SESSION_SECRET`, Discord tokens/secrets, database credentials, or Floodgate keys. OAuth uses authorization code plus PKCE, HMAC-hashed one-use states and login tickets, a revocable server-side session, exact redirect/origin checks, CSRF-protected logout, and a `Secure`/`HttpOnly`/host-only cookie. KairuBridge uses its own plugin bearer key; administration, website auth, and the dedicated bot use separate service credentials.

## Installation path

1. Verify the release hashes in `release/SHA256SUMS.txt`.
2. Read [`docs/INSTALLATION.md`](docs/INSTALLATION.md) and prepare Java 21, Node.js 22, PostgreSQL, a Railway project, a Discord application, DNS, and firewall rules.
3. Deploy `control-plane/`, apply ordered SQL migrations, and confirm `/health`.
4. Register the exact Discord OAuth callback, provision the documented server-only variables, and activate the website flag only after an end-to-end staging sign-in.
5. Deploy `discord-bot/`, register commands, and run the two-pass `/setup-server` idempotency check.
6. Install the server from `server-pack/`; review and accept the Minecraft EULA yourself.
7. Configure `KairuBridge` and `SMPPlatform` with distinct credentials and reviewed feature flags. SMPPlatform prefers its password environment variable; MineKeep and similar hosts may use the protected plugin-data password file documented below.
8. Execute the staging checks in [`docs/OPERATIONS.md`](docs/OPERATIONS.md).

Windows administrators can run `Install-Kairu-Suite.ps1`; Command Prompt users can launch `install-kairu-suite.cmd`. These copy source into the clearly named `Kairu-Website`, `Kairu-Server-Pack`, and `Kairu-Control-Plane` folders. They do not create `.env`, populate keys, start services, download third-party plugins, or accept the EULA.

## Build and verification

The reproducible commands and observed results are in [`docs/BUILD_REPORT.md`](docs/BUILD_REPORT.md). Security controls and remaining production gates are in [`docs/SECURITY.md`](docs/SECURITY.md). Component-specific details remain in each component README.

## References

[1]: https://fill-ui.papermc.io/projects/paper/version/1.21.4 "Paper 1.21.4 official version listing"
[2]: https://docs.papermc.io/paper/getting-started/ "Paper getting started and Java requirements"
[3]: https://discord.com/developers/docs/intro "Discord developer documentation"
[4]: https://docs.railway.com/ "Railway documentation"
[5]: https://geysermc.org/wiki/geyser/setup/ "Geyser setup documentation"
[6]: https://geysermc.org/wiki/floodgate/setup/ "Floodgate setup documentation"
