# Operations

**Kairu SMP Complete Suite · Manus AI · 6 September 2026**

Operate Kairu as four distinct trust and failure domains: Minecraft, KairuBridge, the control plane/Discord bot, and the public website. A website failure must not stop the game server. A missing Discord token must not stop the API. A control-plane outage must not block the Minecraft primary thread.

## Service matrix

| Service | Start/health signal | Normal stop | Durable data |
| --- | --- | --- | --- |
| Control plane | Railway start; `GET /health` returns `status: ok` and `storage: postgres` | `SIGTERM`; Fastify, Discord, and database close gracefully | PostgreSQL plus Railway environment variables |
| Paper/Purpur | `start.sh` or `start.ps1`; console reaches Done | Server console `stop` | Worlds, configs, plugin data, allowlist, operator files |
| KairuBridge | `/kairu status`; recent successful heartbeat/poll | Disabled as part of normal server stop | `plugins/KairuBridge/config.yml` only |
| Website | Node host start; public route responds | Host-specific graceful process stop | Source/build only; no authoritative player data |

## Routine checks

Run the following after each deploy and daily through ordinary platform monitoring. The control-plane health response must say `storage: postgres` in production; `memory` means state will be lost on restart.

```bash
curl --fail --show-error https://CONTROL-PLANE/health
curl --fail --show-error https://CONTROL-PLANE/api/server/status
curl --fail --show-error https://WEBSITE/
```

On Minecraft, confirm `/kairu status`, `/plugins`, Java login, Bedrock login, a test heartbeat, and an administrator-approved link flow. Use Geyser's `geyser connectiontest <host> 19132` from outside the host network. Use the bundled Paper spark profiler for bounded performance captures; Paper 1.21+ includes spark.[1] [2]

## Logs and alerting

The control plane writes structured Pino logs with request IDs and redacts bearer authorization, temporary Discord IDs, link codes, XUIDs, and configured secrets. Alert on repeated `5xx`, process restart loops, PostgreSQL errors, failed migrations, Discord login failures, and an old `receivedAt` heartbeat. KairuBridge logs HTTP status classes but not response bodies or API keys. Treat unexplained authentication failures as possible secret drift or attempted abuse.

Do not enable debug output in production without reviewing its content and retention. Restrict log access to operators, define retention, and avoid exporting logs to public issue trackers.

## Backup and restore

Use provider-native encrypted PostgreSQL backups plus periodic logical dumps. Stop Minecraft cleanly before filesystem backups to guarantee world consistency, or use a tested server-aware snapshot process. Back up server root files, world directories, plugin data, allowlist/operator files, and a version/hash inventory. Protect Floodgate key material and KairuBridge configuration as secrets.

Restore into an isolated environment first. Restore PostgreSQL, server files, and private configuration separately. Start the control plane, apply migrations, start Minecraft, then validate Java/Bedrock identity behavior. Do not point a restore test at production Discord or public DNS unless deliberately isolated.

## Change management

Paper 1.21.4 is unsupported, while Geyser and protocol compatibility evolve. Never blindly update one crossplay component. Review release notes, back up, stage the complete Paper/Purpur, ViaVersion, Geyser, Floodgate, permissions, EssentialsX, and KairuBridge set, then promote during a maintenance window.[3] [4]

For source changes, rerun every command in `BUILD_REPORT.md`. Regenerate all release archives and `SHA256SUMS.txt`; never edit files inside a published ZIP without issuing a new manifest and versioned release.

## Incident response

| Incident | Immediate containment | Recovery |
| --- | --- | --- |
| Plugin key disclosed | Rotate `PLUGIN_API_KEY` in Railway and KairuBridge; stop the bridge if abuse continues | Restart/reload, test heartbeat/linking, review logs and queued commands |
| Admin key disclosed | Rotate `ADMIN_API_KEY`; disable administrative clients | Review queue history and acknowledgements before re-enabling |
| Discord token disclosed | Reset token in Developer Portal and Railway | Restart bot, verify slash commands and permissions |
| Floodgate key disclosed | Restrict server access and rotate per current Floodgate procedure | Validate Bedrock identities and proxy/backend key placement |
| Database compromise | Isolate service and revoke database credentials | Restore clean backup, rotate all related secrets, assess link/identity exposure |
| Malicious queued command | Stop command polling or control plane | Inspect database; only allowlisted whitelist/notification actions can execute |

## References

[1]: https://docs.papermc.io/paper/profiling/ "Paper profiling with spark"
[2]: https://geysermc.org/wiki/geyser/setup/ "Geyser connection testing and setup"
[3]: https://fill-ui.papermc.io/projects/paper/version/1.21.4 "Paper 1.21.4 official version listing"
[4]: https://geysermc.org/wiki/geyser/supported-versions/ "Geyser supported versions"
[5]: https://docs.railway.com/guides/healthchecks "Railway health checks"
