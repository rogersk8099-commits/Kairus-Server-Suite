# Installation

**Kairu SMP Complete Suite · Manus AI · 6 September 2026**

This procedure installs the four suite components without embedding credentials in source or release archives. Paper 1.21.4 requires Java 21. Current Geyser requires Java 21 and directs older backends such as 1.21.4 to use ViaVersion.[1] [2]

## Prerequisites

| Requirement | Minimum or decision | Purpose |
| --- | --- | --- |
| Java | JDK/JRE 21 | Paper/Purpur and KairuBridge runtime; plugin build toolchain. |
| Node.js | 22 | Control-plane and website build/runtime. |
| PostgreSQL | Managed service for production | Durable links, telemetry, directory data, and queue records. |
| Network | TCP 25565, UDP 19132 inbound; HTTPS outbound | Java, Bedrock/Geyser, and control-plane traffic. |
| Discord application | Bot token and application ID | Slash commands and account-link code creation. |
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

The suite-built `plugins/KairuBridge.jar` is already present. The installer adds ViaVersion, Geyser, Floodgate, LuckPerms, and EssentialsX. Read `server-pack/README.md` for optional plugins, official-hash behavior, configuration merge guidance, and the Purpur alternative. Paper plugins belong directly in `plugins/`; the Paper server JAR belongs in the server root.[4]

Run once to generate `eula.txt`. An authorized administrator must read the Minecraft EULA and deliberately set `eula=true` only if the organization accepts it. Start again, stop cleanly, and merge the Geyser/Floodgate templates into the version-generated files. Preserve Java `online-mode=true`. Open TCP 25565 and UDP 19132; TCP reachability does not prove UDP reachability.[2] [5]

## 4. Configure KairuBridge

After the server generates `plugins/KairuBridge/config.yml`, set the exact keys below. The same plugin key value must be stored privately in the control plane and plugin, never in the website.

```yaml
server-id: "production-01"
api-base-url: "https://your-control-plane.up.railway.app"
api-key: "GENERATE_AND_STORE_PRIVATELY"
```

Restrict config permissions, restart the server, and run `/kairu status`. Confirm a heartbeat appears at `/api/server/status`. Test `/link` in Discord, then `/kairu link <code>` in Minecraft. Codes are random, single-use, and valid for ten minutes.

## 5. Build and deploy the website

Use `website/.env.example` as a reference. `VITE_*` values are visible to browsers; never place secrets in them.

```bash
cd website
printf 'VITE_SMP_API_URL=https://your-control-plane.up.railway.app
VITE_ENABLE_MOCK_FALLBACK=false
' > .env.production.local
pnpm install --frozen-lockfile
pnpm build
```

Deploy `.output/` using a Node-compatible host and run `node .output/server/index.mjs`. The service adapter consumes the exact control-plane envelopes. Mock data is automatic when no API URL exists and may be retained for development failures, but production should set `VITE_ENABLE_MOCK_FALLBACK=false`.

Do not configure `VITE_DEV_DISCORD_USER_ID` in a public production build. Portal authentication remains gated until signed Discord OAuth sessions replace the temporary header bridge.

## 6. Windows source installation helper

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
