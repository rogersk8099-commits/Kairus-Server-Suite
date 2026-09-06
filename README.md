# Kairu SMP Complete Suite

**Release integration guide · Manus AI · 6 September 2026**

Kairu SMP Complete Suite is an integrated distribution for a **Paper/Purpur 1.21.4** Minecraft community with Java and Bedrock access. It combines a Node.js 22 control plane and Discord bot, the Java 21 KairuBridge Paper plugin, a crossplay-ready server installation pack, and a React/TanStack community website. The wire contract is defined in [`CONTRACT.md`](CONTRACT.md).

> **Lifecycle warning.** Paper 1.21.4 remains downloadable but is officially unsupported. Use this target only when 1.21.4 is a hard requirement, pin and stage-test the entire stack, and maintain an upgrade plan. [1] [2]

## Suite contents

| Path | Component | Release role |
| --- | --- | --- |
| `control-plane/` | Fastify API and Discord bot | Public website endpoints, player bridge, plugin telemetry/linking/queue endpoints, PostgreSQL persistence, Discord commands, and Railway deployment. |
| `paper-plugin/` | KairuBridge source | Java 21 Gradle project for telemetry, linking, bounded queue execution, and soft integrations. |
| `server-pack/` | Paper/Purpur installation pack | Official-source installers, secure templates, Java launch scripts, and the compiled `plugins/KairuBridge.jar`. |
| `website/` | Community website | TanStack Start/React site with an adapter for the exact control-plane response envelopes and development-only mock fallback. |
| `docs/` | Operator documentation | Installation, operations, security, and reproducible build evidence. |
| `release/` | Distribution artifacts | Complete suite ZIP, component archives, plugin JAR, and SHA-256 manifest. |

## Live deployment

The community website is live at `https://kairu-smp-website-production.up.railway.app`. The PostgreSQL-backed control-plane API is live at `https://kairu-control-plane-production.up.railway.app`. Both services are deployed in Railway production. The Discord bot code is included in and deployed with the control plane, but it remains intentionally dormant until `DISCORD_BOT_TOKEN` and `DISCORD_APPLICATION_ID` are added. See [`docs/DEPLOYMENT_STATUS.md`](docs/DEPLOYMENT_STATUS.md) for activation and Minecraft connection steps.

## Trust boundaries

The public website may call only unauthenticated read endpoints and the temporary player bridge. It must never receive `PLUGIN_API_KEY`, `ADMIN_API_KEY`, `DISCORD_BOT_TOKEN`, database credentials, or Floodgate keys. KairuBridge sends `Authorization: Bearer <PLUGIN_API_KEY>` and `X-Kairu-Server-Id` from its server-side configuration. Administrative queue calls use a separate bearer key. Discord uses the gateway through `discord.js`.[3]

The initial portal's `X-Discord-User-Id` header is a development bridge, not authentication. Do not open player portal endpoints publicly until this header is replaced with signed Discord OAuth sessions and suitable Cross-Site Request Forgery controls.

## Installation path

1. Verify the release hashes in `release/SHA256SUMS.txt`.
2. Read [`docs/INSTALLATION.md`](docs/INSTALLATION.md) and prepare Java 21, Node.js 22, PostgreSQL, a Railway project, a Discord application, DNS, and firewall rules.
3. Deploy `control-plane/`, run its migration, register Discord commands, and confirm `/health`.
4. Install the server from `server-pack/`; review and accept the Minecraft EULA yourself.
5. Configure `plugins/KairuBridge/config.yml` with the Railway HTTPS URL, stable server ID, and a generated plugin key.
6. Build/deploy `website/` with `VITE_SMP_API_URL` and an exact matching control-plane `CORS_ORIGIN`.
7. Execute the staging checks in [`docs/OPERATIONS.md`](docs/OPERATIONS.md).

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
