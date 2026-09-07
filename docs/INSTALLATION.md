# Installation

**Kairu SMP Complete Suite · Manus AI · 7 September 2026**

This procedure installs the website, control plane, dedicated Discord bot, KairuBridge, SMPPlatform, and crossplay server pack without embedding credentials in source or release archives. Paper 1.21.4 requires Java 21. Current Geyser requires Java 21 and directs older backends such as 1.21.4 to use ViaVersion.[1] [2]

## Prerequisites

| Requirement | Minimum or decision | Purpose |
| --- | --- | --- |
| Java | JDK/JRE 21 | Paper/Purpur and KairuBridge runtime; plugin build toolchain. |
| Node.js | 22 | Control-plane and website build/runtime. |
| PostgreSQL | Managed service for production | Durable links, telemetry, directory data, and queue records. |
| Network | TCP 25565, UDP 19132 inbound; HTTPS outbound | Java, Bedrock/Geyser, and control-plane traffic. |
| Discord application | OAuth client/secret, bot token, application ID, and target guild ID | Website sign-up/sign-in, slash commands, setup, and account linking. |
| Public HTTPS | Railway control-plane URL and website URL | Plugin/API transport and browser access. |

## 1. Unpack and verify

Extract `Kairu-SMP-Complete-Suite.zip` into an administrator-owned directory. From the release directory, verify all artifacts before use:

```bash
sha256sum -c SHA256SUMS.txt
```

PowerShell provides equivalent verification with `Get-FileHash -Algorithm SHA256` for each manifest entry. Do not continue if any digest differs.

## 2. Deploy the control plane

Create a Railway project from `control-plane/`, attach PostgreSQL, and set private variables using `.env.example` as a name reference. Do not upload a populated `.env`. Generate independent high-entropy values for `PLUGIN_API_KEY` and `ADMIN_API_KEY`. Set `CORS_ORIGIN` to the exact website HTTPS origin.[3]

```bash
cd control-plane
npm ci
npm test
npm run typecheck
npm run build
```

The included Dockerfile runs the idempotent migration command before starting the API, and `railway.toml` checks `/health`. Configure `DATABASE_URL`, both API keys, and optionally the Discord variables in Railway's secret store. Register commands after deployment:

```bash
npm run register-commands
```

If `DISCORD_GUILD_ID` is set, registration targets that development guild. Without it, commands are global and may take longer to appear. The HTTP API starts when Discord credentials are absent.

## 3. Install Paper/Purpur and crossplay

The server-pack installer obtains reviewed dependencies from their documented official sources. It refuses to overwrite an existing 1.21.4 server JAR and never accepts the Minecraft EULA.

```bash
cd server-pack
chmod 750 install-linux.sh templates/start.sh
export PAPER_USER_AGENT_CONTACT='mailto:mc-admin@example.net'
./install-linux.sh paper
```

On Windows PowerShell:

```powershell
$env:PAPER_USER_AGENT_CONTACT = 'mailto:mc-admin@example.net'
.\install-windows.ps1 -ServerFlavor Paper
```

The suite-built `plugins/KairuBridge.jar` and `plugins/SMPPlatform.jar` are already present. The installer adds ViaVersion, Geyser, Floodgate, LuckPerms, and EssentialsX. Read `server-pack/README.md` for optional plugins, official-hash behavior, configuration merge guidance, and the Purpur alternative. Paper plugins belong directly in `plugins/`; the Paper server JAR belongs in the server root.[4]

Run once to generate `eula.txt`. An authorized administrator must read the Minecraft EULA and deliberately set `eula=true` only if the organization accepts it. Start again, stop cleanly, and merge the Geyser/Floodgate templates into the version-generated files. Preserve Java `online-mode=true`. Open TCP 25565 and UDP 19132; TCP reachability does not prove UDP reachability.[2] [5]

## 4. Configure KairuBridge

After the server generates `plugins/KairuBridge/config.yml`, set the exact keys below. The same plugin key value must be stored privately in the control plane and plugin, never in the website.

```yaml
server-id: "production-01"
api-base-url: "https://your-control-plane.up.railway.app"
api-key: "GENERATE_AND_STORE_PRIVATELY"
```

Restrict config permissions, restart the server, and run `/kairu status`. Confirm a heartbeat appears at `/api/server/status`. Test `/link` in Discord, then `/kairu link <code>` in Minecraft. Codes are random, single-use, and valid for ten minutes.

## 5. Configure SMPPlatform

SMPPlatform prefers database and central-API secrets from environment variables, never YAML. Review `smp-platform/README.md` and every supplied YAML file before starting production. On a host that exports custom variables to Paper, set:

```text
SMPPLATFORM_DB_PASSWORD=<private database password>
SMPPLATFORM_CENTRAL_API_TOKEN=<private token when central sync is enabled>
```

MineKeep does not export arbitrary `.paper.env` keys to the Paper JVM. On MineKeep, configure `storage.jdbc-url` and `storage.username` in `plugins/SMPPlatform/config.yml`, then place only the PostgreSQL password plus a final newline in `plugins/SMPPlatform/database-password.txt`. The plugin confines the fallback path to its own data directory, logs only which source was used, and continues to prefer `SMPPLATFORM_DB_PASSWORD` whenever the environment variable exists. Keep the password file out of backups shared with untrusted parties and out of source control.

Start in staging. Confirm the exact six worlds, Flyway success, inventory grouping, `/worlds`, `/guild`, and `/points`. Keep lifecycle resets, event/creative automation, and destructive admin actions disabled until their actual backup, Multiverse, PlotSquared, and staff-policy adapters pass the documented staging gate.

## 6. Deploy the dedicated Discord bot

Deploy `discord-bot/` as a service separate from the control plane. Apply ordered control-plane SQL first; the bot's Railway pre-deploy step then runs the reviewed, idempotent `prisma migrate deploy` mapping for Discord-owned persistence tables. Configure only the variables in `discord-bot/ENVIRONMENT_VARIABLES.md`; never use `prisma db push` in production. Without `DISCORD_TOKEN`, the service reports a dormant gateway and starts no Discord jobs.

Production uses application `1546281826341879878` and guild `1546281211876479050`. After supplying a reviewed token, API service credential, and Server Members privileged intent, run `npm run register:commands`. Install with least privilege, run `/setup-server` until reconciliation reports no failures, then verify all managed roles, categories, channels, and private-channel bot overwrites independently. The completed Kairu deployment registered 46 commands and verified 58 managed resources.

## 7. Build and deploy the website

Use `website/docs/DISCORD_OAUTH.md` as the server-side environment contract. `VITE_*` values are visible to browsers; the only OAuth `VITE_` value is the non-secret activation boolean.

```bash
cd website
pnpm install --frozen-lockfile
pnpm build
```

Deploy `.output/` using a Node-compatible host and run `node .output/server/index.mjs`. The service adapter consumes the exact control-plane envelopes. Mock data is automatic when no API URL exists and may be retained for development failures, but production should set `VITE_ENABLE_MOCK_FALLBACK=false`.

Register the exact Discord callback `https://<website>/auth/discord/callback`. Configure `WEBSITE_API_SECRET`, `SESSION_SECRET`, Discord client identifiers, the control-plane `DISCORD_OAUTH_CLIENT_SECRET`, and exact website/API origins only in the server secret stores. Deploy the control plane first. After an end-to-end staging sign-in and logout pass, set `VITE_DISCORD_AUTH_ENABLED=true` and redeploy the website. The production callback is `https://kairu-smp-website-production.up.railway.app/auth/discord/callback`, and the official server invite is <https://discord.gg/cbBj6EvcV4>.

## 8. Windows source installation helper

From the suite root:

```powershell
.\Install-Kairu-Suite.ps1 -DestinationRoot 'C:\KairuSMP'
```

This creates `C:\KairuSMP\Kairu-Website`, `Kairu-Server-Pack`, and `Kairu-Control-Plane`. Add `-InstallDependencies` only if Node.js 22 and package managers are already trusted on that host. The helper excludes caches, output, VCS metadata, `.env*`, logs, and generated server data. It never provisions credentials.

## References

[1]: https://docs.papermc.io/paper/getting-started/ "Paper getting started and Java requirements"
[2]: https://geysermc.org/wiki/geyser/setup/ "Geyser setup documentation"
[3]: https://docs.railway.com/guides/variables "Railway variables documentation"
[4]: https://docs.papermc.io/paper/adding-plugins/ "Paper plugin installation documentation"
[5]: https://geysermc.org/wiki/floodgate/setup/ "Floodgate setup documentation"
[6]: https://discord.com/developers/docs/interactions/application-commands "Discord application commands documentation"
