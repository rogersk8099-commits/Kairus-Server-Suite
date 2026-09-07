# SMPPlatform 1.0

SMPPlatform is the unified **Paper/Purpur 1.21.4, Java 21** server plugin for the Neon Nexus world network. It is a server-side JAR; players do not install a client mod. Java and Bedrock players join the same server through Geyser and Floodgate.

## Release artifact

Build from this directory with:

```bash
./gradlew clean test shadowJar
```

The deployable output is `build/libs/SMPPlatform-1.0.0.jar`. It is a single shaded plugin with exactly one `JavaPlugin` entry point and one `plugin.yml`.

## Canonical worlds

The registry contains exactly six world products:

| ID | Display name | Role |
| --- | --- | --- |
| `ashfall` | Ashfall | Seasonal survival and canonical guild membership. |
| `obsidian-gate` | Obsidian Gate | Isolated hardcore group. |
| `atrium` | The Atrium | Creative plots and build submissions. |
| `colosseum` | Neon Colosseum | Scheduled events and competition. |
| `quarry` | The Quarry | Regenerating resource world sharing Ashfall inventory. |
| `verdance` | Verdance | Read-only archived season world. |

Multiverse-Core remains the world lifecycle owner. SMPPlatform validates and orchestrates configured worlds instead of reimplementing Multiverse. Multiverse-Inventories remains the inventory authority; the defaults share Quarry with Ashfall and isolate Obsidian Gate.

## Runtime-composed features

The production lifecycle currently composes the typed configuration loader, HikariCP/PostgreSQL service, Flyway migrations, asynchronous IO/scheduler ownership, cached remote World Registry, Multiverse and inventory adapters, Floodgate identity listener, durable event outbox, and PostgreSQL-backed guild and points services. `/worlds`, `/guild`, `/g`, and `/points` are registered through the single plugin entry point. Every guild/points persistence operation runs on the platform IO executor and returns immutable messages to the Paper thread.

Guild and points mutations use the canonical `smp_` tables, transaction boundaries, optimistic locking, mandatory audit writes, and durable idempotent outbox events. When PostgreSQL is unavailable, these commands fail closed before mutation.

The JAR also contains the reviewed domain modules, configuration, tests, and integration adapters for hardcore lives, Quarry reset/backup, Verdance protection, Colosseum events, Atrium submissions, administration, PlaceholderAPI, Skript, Vault, LuckPerms, PlotSquared, Geyser/Floodgate, chat relay, and account linking. Their command names are reserved in `plugin.yml`; modules whose production repository or optional-server adapter is unavailable return an explicit unavailable response rather than performing an in-memory or partial mutation.

> **Activation boundary:** Do not claim the packaged lifecycle, events/creative, or administration modules as active until they are composed against the real server's backup manager, Multiverse/PlotSquared APIs, staff permission policy, and PostgreSQL repositories in staging. The code and tests are present; the unified 1.0 entry point intentionally fails closed for those adapters rather than pretending a destructive workflow is safe.

## Configuration

The JAR includes `config.yml`, `worlds.yml`, `guilds.yml`, `points.yml`, `hardcore.yml`, `events.yml`, `events-creative.yml`, `integrations.yml`, `gui.yml`, and `messages.yml`. Review them before first production start.

The database password is resolved from `SMPPLATFORM_DB_PASSWORD` first. On managed Minecraft hosts that do not export arbitrary environment variables to Paper, place only the password in `plugins/SMPPlatform/database-password.txt`. The fallback path is confined to the plugin data directory and is never logged.

```text
SMPPLATFORM_DB_PASSWORD=<PostgreSQL password>
SMPPLATFORM_CENTRAL_API_TOKEN=<optional central API bearer token>
```

The JDBC URL, username, pool limits, timeouts, registry cache, world names, feature flags, and token environment-variable names are non-secret YAML configuration. Never place a real password or bearer token in YAML or source control. On MineKeep, configure the JDBC URL and username in `plugins/SMPPlatform/config.yml`, upload the one-line password file to `plugins/SMPPlatform/database-password.txt`, then restart the server.

## Optional integrations

`Multiverse-Core`, `Multiverse-Inventories`, `LuckPerms`, `PlaceholderAPI`, `Vault`, `PlotSquared`, `Geyser-Spigot`, `floodgate`, and `Skript` are soft dependencies. Their absence must not prevent core bootstrap. Features that require a missing plugin report an unavailable health state and refuse unsafe work.

## Validation

The unified project passes **46 JUnit tests with zero failures** and produces a 5.4 MB shaded JAR. Both canonical SQL migrations replayed twice without error on PostgreSQL 16.15 and created 27 `smp_` tables. JAR inspection confirmed the sole entry point, all ten YAML files, both Flyway migrations, and classes from the world, guild, points, lifecycle, events/creative, admin, and integration packages. A source scan found no private key, credential-bearing JDBC URL, duplicate `plugin.yml`, `Thread.sleep`, synchronous `HttpClient.send`, or blocking `Future.join()` call in production code.

## Installation

1. Stop the Paper/Purpur server and take a tested backup.
2. Copy `SMPPlatform-1.0.0.jar` to `plugins/SMPPlatform.jar`.
3. Configure PostgreSQL and either the preferred environment password or the protected MineKeep-compatible password file.
4. Install only the reviewed optional plugins needed by the enabled modules.
5. Start in staging, inspect Flyway and integration health logs, and run `/worlds`, `/guild`, and `/points` smoke tests.
6. Validate Java and Bedrock identity behavior, inventory groups, permission precedence, economy separation, outbox delivery, and backup restoration before production.
7. Keep destructive lifecycle and administration features disabled until their real backup/world adapters have passed the staging gate above.
