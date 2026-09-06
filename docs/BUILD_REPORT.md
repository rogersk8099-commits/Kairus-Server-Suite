# Build Report

**Kairu SMP Complete Suite · 6 September 2026**

## Result

The suite now contains the live website and control plane, server-side Discord OAuth, a dedicated modular Discord bot, KairuBridge, a unified SMPPlatform shaded JAR, and the Paper/Purpur crossplay server pack. External credentials and destructive game-server workflows remain gated rather than being simulated.

## Integrated components

| Component | Result | Primary artifact |
| --- | --- | --- |
| Railway control plane | Pass | `control-plane/` |
| Dedicated Discord bot | Pass, dormant until credentials | `discord-bot/` |
| Website and Discord OAuth | Pass, live in fail-closed activation state | `website/` |
| Branded favicon set | Pass, live | `website/public/favicon.ico` and PNG/touch icons |
| KairuBridge | Pass | `release/KairuBridge.jar` |
| SMPPlatform | Pass with documented feature activation boundary | `release/SMPPlatform.jar` |
| Paper/Purpur server pack | Pass | `release/Kairu-Server-Pack.zip` |

## Verification evidence

| Check | Method | Result |
| --- | --- | --- |
| Control-plane OAuth/bridge tests | `npm test` | **Pass:** 13 tests, 0 failures. |
| Control-plane types/build | `npm run typecheck && npm run build` | **Pass.** |
| Control-plane dependencies | `npm install` | **Pass:** 0 vulnerabilities reported. |
| OAuth SQL replay | All four migrations applied twice to disposable PostgreSQL | **Pass:** platform user, identity, state, ticket, and session tables/indexes present. |
| Railway control plane | New deployment plus route probes | **Pass:** health and five public routes returned 200; OAuth route returned fail-closed 503 without credentials; bridge route returned 401 without plugin credentials. |
| Website OAuth tests | `pnpm typecheck && pnpm lint && pnpm test && pnpm build` | **Pass:** 5 OAuth tests; 0 lint errors, 7 pre-existing Fast Refresh warnings; production build emitted. |
| Railway package-manager parity | `pnpm@9.15.9 install --frozen-lockfile` | **Pass** after adding the explicit workspace package root. |
| Railway website | Deployment and HTTP probes | **Pass:** `/`, `/login`, `/favicon.ico`, and `/favicon-32x32.png` returned 200. |
| Website visual review | Authenticated browser inspection | **Pass:** branded Discord login panel renders, and the credential-pending action is visibly disabled. |
| Dedicated bot Prisma | Format, validate, generate | **Pass** with Prisma/client 6.12.0. |
| Dedicated bot quality | Typecheck, ESLint, build | **Pass.** |
| Dedicated bot tests | Vitest | **Pass:** 22 tests, 0 failures. |
| Dedicated bot dependencies | `npm audit --omit=dev` and full `npm audit` | **Pass:** 0 vulnerabilities. |
| Discord setup blueprint | Static invariant tests | **Pass:** 9 categories, 28 text channels, 6 voice channels, 34 total channels, and 15 roles. |
| SMPPlatform clean build | `./gradlew clean test shadowJar` | **Pass:** shaded JAR generated. |
| SMPPlatform tests | JUnit XML aggregation | **Pass:** 46 tests, 0 failures. |
| SMPPlatform database | Both canonical migrations applied twice to PostgreSQL 16.15 | **Pass:** 27 distinct `smp_` tables and required guild/points/lifecycle/event/creative/audit/outbox tables. |
| SMPPlatform JAR inspection | `jar tf` and `javap` | **Pass:** one JavaPlugin entry, one plugin descriptor, ten YAML files, two migrations, and classes from every requested module. |
| SMPPlatform source scan | Private-key/JDBC credential, duplicate descriptor, blocking-call scan | **Pass:** no credential material, duplicate `plugin.yml`, `Thread.sleep`, synchronous `HttpClient.send`, or production `Future.join()`. |
| Secret boundary | Source/archive review | **Pass:** no real Discord, database, Railway, plugin, website API, or session credential is committed. |

## Runtime boundaries

SMPPlatform's single entry point composes configuration, PostgreSQL/Hikari/Flyway, async executors, cached remote world registry, Multiverse inventory validation, Floodgate identity, durable outbox, and PostgreSQL-backed guild/points commands. The JAR contains lifecycle, event/creative, admin, integration, PlaceholderAPI, and Skript modules, but their destructive or optional-plugin-dependent production adapters intentionally remain fail closed until staged against the actual Minecraft host.

The dedicated Discord bot starts its HTTP health server before attempting gateway login. Without `DISCORD_TOKEN` its gateway state is `dormant` and no Discord jobs run. Streaming follows official provider limits: Twitch EventSub delivery, quota-bounded YouTube `search.list` plus `videos.list` confirmation, and no automated TikTok detection without approved official LIVE capability.

## Remaining external activation

The Discord Developer Portal session was not authenticated. A user-authorized application, exact OAuth redirect URI, client secret, guild ID, bot token, privileged intent selection, and least-privilege invite are still required. After provisioning, configure the documented Railway variables, run command registration, test OAuth end to end, run `/setup-server` twice, and verify membership, account linking, event, support, suggestion, reminder, and chat-relay paths in staging.

The Minecraft server itself was not started and the EULA was not accepted. Install on the intended game host, configure PostgreSQL and environment-only credentials, stage-test Java/Bedrock joins and optional plugins, and prove backup restoration before enabling lifecycle resets or administrative destructive actions.

## Release artifacts

| Artifact | Purpose |
| --- | --- |
| `release/Kairu-SMP-Complete-Suite.zip` | Complete source, documentation, and server-pack distribution without dependency caches or generated build trees. |
| `release/KairuBridge.jar` | Telemetry, linking, queue, and chat bridge plugin. |
| `release/SMPPlatform.jar` | Unified world/guild/points/platform plugin. |
| `release/Kairu-Server-Pack.zip` | Crossplay server pack containing both compiled Kairu plugins. |
| `release/Kairu-Control-Plane-Source.zip` | Control-plane source and lockfile. |
| `release/Kairu-Discord-Bot-Source.zip` | Dedicated bot source, Prisma schema, tests, and docs. |
| `release/SMPPlatform-Source.zip` | Unified Java plugin source, configs, migrations, tests, and docs. |
| `release/SHA256SUMS.txt` | SHA-256 integrity manifest. |
