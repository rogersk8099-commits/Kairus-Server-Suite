# KairuBridge troubleshooting

## Configuration remains "needs setup"

This is expected when `server-id`, `api-base-url`, or `api-key` is still a placeholder. Stop the server or use `/kairu reload` after editing `plugins/KairuBridge/config.yml`. A valid URL is an absolute `https://` or `http://` URL without a username or password. Production URLs should use HTTPS.

KairuBridge rejects malformed configuration rather than making a potentially unsafe request. Read the server console message after `/kairu reload`; the previously active configuration is retained if a reload is rejected.

## Heartbeat or command polling fails

Run `/kairu status` as an operator to see the last heartbeat and poll outcome. Check that the host can resolve and reach the configured control-plane URL, that a reverse proxy permits the documented paths, and that the control plane accepts both required headers:

```text
Authorization: Bearer <PLUGIN_API_KEY>
X-Kairu-Server-Id: <server-id>
```

Verify that the API key is a **plugin key**, not an administrative or Discord token. Do not paste the key into support tickets or console output. Network failures and retryable `408`, `429`, and `5xx` responses retry with capped backoff, so repeated warning messages can indicate a sustained control-plane or DNS/TLS issue rather than plugin thread blocking.

## A player cannot link

The player must run `/kairu link <code>` from the Minecraft account being linked, with a code issued by Discord `/link` that is still within its ten-minute single-use window. Confirm that the player has `kairu.link`. The plugin sends the UUID and Java username automatically, and sends a Floodgate XUID only for a detected Floodgate Bedrock player.

If the player receives the expired/invalid/already-used message, create a new Discord link code. For a generic service-unavailable message, check `/kairu status` and control-plane logs. Never ask a player to reveal an API key.

## Snapshot fields are null or incomplete

KairuBridge starts without optional plugins. Install and enable Vault to populate `balance`; install and enable LuckPerms to populate the primary-group `rank`. Install Floodgate to include Bedrock XUIDs, and PlaceholderAPI only if placeholder expansion is desired in remote notifications. `/kairu status` lists detected integration state.

Bukkit statistic values are cumulative values supplied by Paper/Purpur. A newly joined player may naturally have zeros. Use `/kairu sync <player>` as an operator to send a fresh snapshot for an online player.

## Remote commands are not taking effect

Set `commands.enabled: true`, then check `/kairu status`. The command endpoint response must contain either a JSON array or an object with a `commands` array. Command IDs must be safe identifier strings, and only these exact action types execute:

| Type | Required data | Effect |
| --- | --- | --- |
| `WHITELIST_ADD` | `player` (3–16 alphanumeric/underscore characters) | Adds the offline player to the server whitelist. |
| `WHITELIST_REMOVE` | `player` (3–16 alphanumeric/underscore characters) | Removes the offline player from the server whitelist. |
| `NOTIFY` | online `player`, optional `message` | Sends that player a chat message, expanding PlaceholderAPI if present. |

Unsupported, malformed, or failed commands are acknowledged as failures. KairuBridge will not run arbitrary server-console commands delivered by the network.

## Plugin fails to load or command is absent

Use Paper or Purpur 1.21.4 and Java 21. Confirm that the deployed artifact is `KairuBridge-1.0.0.jar`, not the sources jar, and that its `plugin.yml` remains at the root of the jar. Check for duplicate older KairuBridge jars in `plugins/`; remove duplicates with the server stopped.

## Clean shutdown and upgrades

Stop the server normally. On disable, KairuBridge cancels Bukkit schedules and stops its retry scheduler. Do not hot-replace jars while the server runs. Back up the configuration file, replace the jar while stopped, then start the server and run `/kairu status`.
