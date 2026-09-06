# KairuBridge

**KairuBridge** is a production-oriented **Paper/Purpur 1.21.4** plugin that securely connects a Minecraft server to the Kairu control plane. It completes Discord account links, publishes server and player telemetry, and consumes a tightly scoped remote command queue. It requires **Java 21** and compiles against **Paper API 1.21.4**.

## Features

| Capability | Implementation |
| --- | --- |
| Secure service identity | Every control-plane request carries `Authorization: Bearer <api-key>` and `X-Kairu-Server-Id`; secrets are never written to logs. |
| Discord linking | `/kairu link <code>` submits the one-time link code, Java UUID, Java username, and Floodgate XUID when available to `POST /api/link-codes/complete`. |
| Telemetry | Periodic `POST /api/plugin/heartbeat` includes online state, TPS, player count and names, worlds, a version descriptor, and uptime using the exact control-plane schema. Periodic player snapshots include contract-named Bukkit statistics, balance, rank, and world with tick/centimeter values converted to seconds/meters. |
| Safe networking | Java `HttpClient.sendAsync` is used for every request. No request joins, waits, or otherwise blocks the server thread. Retryable transport errors and HTTP `408`, `429`, and `5xx` responses receive bounded exponential backoff with jitter. |
| Remote queue | Periodic `GET /api/plugin/commands` polls in an asynchronous Bukkit task. Only `WHITELIST_ADD`, `WHITELIST_REMOVE`, and online-player `NOTIFY` are accepted, then acknowledged through `POST /api/plugin/commands/:id/ack`. Arbitrary console-command execution is deliberately unsupported. |
| Optional plugins | Vault, LuckPerms, PlaceholderAPI, Geyser, and Floodgate are detected softly at runtime. KairuBridge starts without any of them. |
| Lifecycle | Config reload validates before replacing live services; disable cancels Bukkit tasks and promptly stops the retry scheduler. |

## Installation

1. Run a **Paper or Purpur 1.21.4** server on **Java 21**.
2. Copy `build/libs/KairuBridge-1.0.0.jar` to the server's `plugins/` directory.
3. Start the server once, stop it, and edit `plugins/KairuBridge/config.yml`.
4. Set a stable `server-id`, HTTPS `api-base-url`, and a provisioned `api-key`. Do not use placeholders in production.
5. Start the server and run `/kairu status` as an operator. The status must report `Configuration: ready`.

> Treat `api-key` as a server secret. Keep `config.yml` out of public repositories, backups, web roots, and client downloads. KairuBridge never accepts an API key from a command or player message.

## Configuration

```yaml
server-id: "survival-eu-01"
api-base-url: "https://control.kairu.example"
api-key: "replace-with-a-server-side-plugin-key"

http:
  connect-timeout-seconds: 10
  request-timeout-seconds: 15
  max-retries: 3
  retry-base-delay-millis: 500

heartbeat:
  enabled: true
  interval-seconds: 30
snapshots:
  enabled: true
  interval-seconds: 300
commands:
  enabled: true
  poll-interval-seconds: 15
link:
  snapshot-after-link: true
```

Intervals are clamped to at least 5 seconds. HTTP retry count is capped at 5, retry base delay is capped at 10 seconds, and an individual retry delay is capped at 30 seconds. The control-plane URL must be an absolute `https://` or `http://` URL without embedded credentials. Use HTTPS outside local development.

## Commands and permissions

| Command | Permission | Default | Description |
| --- | --- | --- | --- |
| `/kairu link <code>` | `kairu.link` | true | Completes a Discord-generated, single-use link code for the executing player. |
| `/kairu status` | `kairu.status` | true | Shows configuration, last heartbeat/poll result, and optional integration status. |
| `/kairu reload` | `kairu.admin` | op | Reloads and validates `config.yml`. An invalid config leaves the active bridge running. |
| `/kairu sync <player>` | `kairu.admin` | op | Sends one snapshot for an online player. |

## Control-plane contract

The plugin uses the Kairu contract paths below. It does not expose a listener or an HTTP endpoint of its own.

| Method | Path | Data |
| --- | --- | --- |
| `POST` | `/api/plugin/heartbeat` | Online state, TPS, population/names, worlds, version descriptor, uptime; server identity is in `X-Kairu-Server-Id` |
| `POST` | `/api/plugin/player-snapshot` | Minecraft UUID, name, playtime seconds, blocks broken, kills, deaths, distance meters, balance, rank name, and optional world |
| `POST` | `/api/link-codes/complete` | Link code, Minecraft UUID, Java username, optional Floodgate XUID |
| `GET` | `/api/plugin/commands` | Retrieves bounded queued commands |
| `POST` | `/api/plugin/commands/:id/ack` | Acknowledges command success or failure |

## Optional integration behavior

| Plugin | Enhancement when installed | Behavior when absent |
| --- | --- | --- |
| Vault | Reads the player's economy balance for snapshots. | Contract-safe `balance: 0` is sent. |
| LuckPerms | Reads the player's primary group as `rankName`. | Contract-safe `rankName: Member` is sent. |
| PlaceholderAPI | Expands placeholders in a remote `NOTIFY` message. | Sends literal notification text. |
| Geyser | Presence is reported in `/kairu status` diagnostics. | No impact. |
| Floodgate | Adds the Bedrock player's XUID to link-completion payloads. | No Bedrock XUID is sent. |

## Build from source

```bash
./gradlew test
./gradlew build
```

The deployable shaded jar is `build/libs/KairuBridge-1.0.0.jar`; a `-sources.jar` is also generated. The build requires Java 21 (with `--release 21` enforced), JUnit 5, and the Gradle Shadow plugin. The runtime currently has no external bundled libraries, but Shadow is configured so the distribution remains a single production jar as integrations evolve.

## Operational notes

- **Never run blocking I/O in Bukkit event handlers.** KairuBridge gathers Bukkit state on the primary thread, serializes it, and hands the JSON to `HttpClient.sendAsync`. Completion callbacks schedule player-facing messages back onto the primary thread.
- API errors do not disconnect players or stop the server. They are logged without API keys or response bodies.
- Link failures with `400`, `404`, `409`, or `410` are shown to the player as an expired/invalid/already-used code. Other failures are treated as temporarily unavailable.
- The remote queue is intentionally restrictive. New remote actions should be explicitly implemented, validated, and documented rather than executing payload-supplied console commands.

See [TROUBLESHOOTING.md](TROUBLESHOOTING.md) for diagnosis and recovery steps.
