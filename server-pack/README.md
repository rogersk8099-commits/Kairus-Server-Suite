# Kairu Paper/Purpur 1.21.4 Server Installation Pack

**Administrator guide · Manus AI · 6 September 2026**

This pack installs a **Paper or Purpur 1.21.4** Java server with a production-oriented crossplay baseline. It provides official-source download scripts, Java 21 start scripts, security-conscious configuration templates, and the suite-built `KairuBridge.jar` with a secure configuration template. It intentionally contains **no third-party binaries** and **does not accept the Minecraft End User License Agreement (EULA)** on the administrator’s behalf.

> **Support lifecycle warning.** Paper 1.21.4 build 232 is a stable published build, but Paper marks the 1.21.4 line unsupported as of 17 July 2025. Use this pack only where 1.21.4 is a hard requirement, pin and stage-test the complete plugin stack, and maintain an upgrade plan. [1] [2]

## 1. What this pack creates

Run an installer from this directory. It places the selected server JAR in the **server root**, plugin JARs directly in `plugins/`, generated/download records in `downloads/`, and copies templates only when the destination does not already exist. It will **not overwrite an existing server JAR or existing configuration**.

| Path | Purpose | Included initially | Notes |
| --- | --- | --- | --- |
| `install-linux.sh` | Linux installer | Yes | Downloads only after Java 21 check; run with Bash. |
| `install-windows.ps1` | Windows PowerShell installer | Yes | Downloads only after Java 21 check. |
| `templates/start.sh` | Linux launch template | Yes | Copies to `start.sh` on first installation. |
| `templates/start.ps1` | Windows launch template | Yes | Copies to `start.ps1` on first installation. |
| `templates/server.properties` | Java-server baseline | Yes | Enables Java authentication and a whitelist. |
| `templates/Geyser-config.yml` | Geyser crossplay baseline | Yes | Uses UDP 19132 and Floodgate authentication. |
| `templates/floodgate-config.yml` | Floodgate baseline | Yes | Retains the collision-safe `.` username prefix. |
| `templates/KairuBridge-config.yml.example` | Kairu control-plane placeholder | Yes | Contains placeholders only; it is not a working credential file. |
| `plugins/` | Direct plugin JAR location | `KairuBridge.jar` | The installer adds reviewed third-party plugins from official endpoints. |
| `downloads/` | Local checksum audit files | Empty initially | Contains verified hashes and local inventory hashes after installation. |

This complete-suite release ships the validated private plugin at `plugins/KairuBridge.jar`. Keep it server-side and replace it only through the controlled release process. Do not place server or Bukkit/Paper plugin JARs in `mods/`, nested plugin folders, or `plugins/` subdirectories.

## 2. Preconditions and design choices

A Java 21 or newer runtime is mandatory. Paper’s version-specific requirement table assigns Java 21 to the 1.20–1.21.11 family, which includes 1.21.4. Geyser-Spigot likewise requires Java 21 or later. [3] [4]

Paper is the reference target. Purpur is a Paper fork and follows the same root-JAR and `plugins/` layout, but individual upstream projects sometimes name Paper/Spigot rather than Purpur in their support claims. Validate the actual Purpur build, worlds, and every plugin in staging before operating it as production. Geyser officially covers Spigot/Paper 1.20.5 and newer, so its Paper path includes 1.21.4; the same explicit wording should not be read as a separate Purpur guarantee. [4]

The scripts use HTTPS, fail when downloads or API metadata are malformed, retain downloaded files only after verification, and make no attempt to start Minecraft. Paper, Geyser, Floodgate, PlaceholderAPI, and ViaVersion expose SHA-256 through their official services and are verified before installation. Purpur’s current legacy API exposes an official MD5 rather than SHA-256 for this line; the installer verifies that MD5 and records a local SHA-256 inventory value but clearly does not represent it as upstream source verification. The official EssentialsX, LuckPerms, and Vault endpoints cited here do not publish a corresponding checksum, so the installer verifies a non-empty JAR/ZIP header and records a **local** SHA-256 for audit only. Treat that as weaker assurance and independently approve those pinned releases before production deployment.

### 2.1 Required administrator inputs

| Input | Why it is needed | Example / decision |
| --- | --- | --- |
| Java 21+ | Required runtime for Paper 1.21.4 and the Geyser stack. | Temurin/OpenJDK 21. |
| Server choice | Determines the server artifact installed. | `paper` is the reference choice; `purpur` is a tested-fork choice. |
| RAM limits | Prevents Java from exhausting the host. | Start at `Xms=2G`, `Xmx=4G`; change after profiling. |
| Public IP/DNS | Players need a routable Java and Bedrock address. | `play.example.net`. |
| Firewall/security-group access | Java and Bedrock use different protocols/ports. | TCP `25565`, UDP `19132`. |
| Kairu control-plane values | Required only after KairuBridge is supplied. | Railway HTTPS URL, server ID, plugin API key. |

## 3. Installation

Choose **one** server flavor. Do not install Paper and Purpur side by side in the same instance root. If an installer reports a pre-existing server JAR, stop and use the controlled update procedure in [Section 10](#10-controlled-update-procedure).

### 3.1 Linux shell

From the server root, mark the script executable and run it. Paper’s Downloads Service requires a descriptive User-Agent containing a contact URL or email, so set a genuine organization contact before requesting Paper. [2]

```bash
cd /path/to/server-pack
chmod 750 install-linux.sh templates/start.sh
export PAPER_USER_AGENT_CONTACT='mailto:mc-admin@example.net'
./install-linux.sh paper
```

For Purpur, use the same directory and command with `purpur`:

```bash
./install-linux.sh purpur
```

Floodgate is included by default because the Kairu crossplay configuration uses Floodgate authentication. To operate Geyser without Floodgate, set the installation switch and later change Geyser `auth-type` to the Java-account method required by the installed Geyser version. This is a distinct authentication policy, not merely a plugin removal.

```bash
INSTALL_FLOODGATE=false ./install-linux.sh paper
```

The optional plugins are intentionally opt-in:

```bash
INSTALL_PLACEHOLDERAPI=true INSTALL_VAULT=true ./install-linux.sh paper
```

### 3.2 Windows PowerShell

Open **PowerShell** as the account that will own the server files. Windows execution policy may block scripts downloaded from another location; set a per-process policy only when your local policy permits it.

```powershell
cd C:\path\to\server-pack
Set-ExecutionPolicy -Scope Process -ExecutionPolicy Bypass
$env:PAPER_USER_AGENT_CONTACT = 'mailto:mc-admin@example.net'
.\install-windows.ps1 -ServerFlavor Paper
```

For Purpur:

```powershell
.\install-windows.ps1 -ServerFlavor Purpur
```

To omit Floodgate, or to include optional PlaceholderAPI and Vault:

```powershell
.\install-windows.ps1 -ServerFlavor Paper -NoFloodgate -InstallPlaceholderAPI -InstallVault
```

### 3.3 First launch and deliberate EULA acceptance

Before first launch, review `server.properties`. The installers intentionally do **not** pre-seed version-sensitive Geyser or Floodgate configuration files. Start the installed plugins after deliberate EULA acceptance so each produces its version-matched configuration, stop it cleanly with `stop`, then compare and merge the documented values from `templates/Geyser-config.yml` and `templates/floodgate-config.yml`. Do not blindly replace a newly generated file.

Start once. This creates `eula.txt` and exits or waits according to server behavior; it does **not** make the legal decision for you.

```bash
# Linux
./start.sh

# Windows PowerShell
.\start.ps1
```

Read the EULA. Only if the organization agrees, manually edit the generated `eula.txt` from `eula=false` to `eula=true`. Then start again. The installers and templates never alter that setting.

After the second launch, use `stop` at the server console for a graceful shutdown. Never terminate a live Java process merely to apply configuration or plugin changes.

## 4. Plugin classification and load sequence

The following classification is specific to the stated Kairu deployment: Paper/Purpur 1.21.4 with Java and Bedrock players, Geyser/Floodgate, managed permissions, and KairuBridge telemetry/linking. “Required” does not mean universally required for Minecraft; it means required for this declared operating model.

| Component | Classification | Pin or selection | Why it is present | Installation / compatibility boundary |
| --- | --- | --- | --- | --- |
| Paper **or** Purpur server JAR | **Required** | Paper latest stable returned by official API; build 232 is the verified 1.21.4 reference. Purpur latest 1.21.4 API build. | The server runtime. | In the instance root, never `plugins/`. Paper 1.21.4 is unsupported; Purpur is a test-first fork path. [1] [2] |
| Geyser-Spigot | **Required** | Latest official Geyser API artifact, verified SHA-256. | Bedrock-to-Java protocol bridge. | `plugins/Geyser-Spigot.jar`; its default Bedrock listener is UDP 19132. [4] |
| Floodgate-Spigot | **Required** for this template; **optional** if Bedrock users will authenticate as Java owners | Latest official Floodgate API artifact, verified SHA-256. | Lets Microsoft-authenticated Bedrock players join an online-mode Java server without owning/authenticating a Java Edition account. | `plugins/floodgate-spigot.jar`; set Geyser `auth-type: floodgate`. Keep the default non-alphanumeric prefix. [5] |
| ViaVersion | **Required** with current Geyser on a 1.21.4 backend | Pinned stable 5.11.0, official Hangar SHA-256 verified. | Current Geyser emulates a newer Java client; its guidance requires ViaVersion when the backend is older than 26.2. | `plugins/ViaVersion-5.11.0.jar`; do not rely on `/reload`. [4] [6] |
| LuckPerms Bukkit | **Recommended** | Pinned 5.5.81 official Bukkit JAR. | The supported permission/group authority for staff and player permissions. KairuBridge can soft-integrate with it. | Install as the sole permissions manager except during migration. Use Java 21 host runtime. [7] |
| EssentialsX core | **Recommended** | **Pinned 2.21.0**, not “latest.” | Common operational commands, homes, kits, warps, and basic economy hooks. | 2.21.0 explicitly supports 1.21.4. Newer stated matrices no longer list it; test exact configuration with Bedrock identities. [8] |
| KairuBridge | **Required** for Kairu suite functions | Privately supplied, tested Kairu build. | Implements Kairu telemetry and the Discord-to-Minecraft linking flow described in the integration contract. | Copy `KairuBridge.jar` to `plugins/`; use server-side placeholders only. It must soft-integrate and make asynchronous network calls. |
| PlaceholderAPI | **Optional** | Pinned 2.12.3, official GitHub SHA-256 verified. | Needed only when another installed plugin/configuration consumes placeholders. | Not a Geyser/Floodgate dependency. Install required expansions separately and minimally. [9] |
| Vault | **Optional**, test-gated | Pinned 1.7.3 official release asset. | API bridge for compatible economy/chat/permission providers. EssentialsX group prefixes/suffixes need Vault. | Upstream provides no explicit 1.21.4 claim; test provider, prefixes, Floodgate identities, chat, and economy together. [10] |
| spark profiler | **Optional as a separate JAR** | **No download** by default. | Paper 1.21.4 bundles spark. | Use `/spark`; do not install an override unless a tested reason exists. [11] |

### 4.1 Load/dependency order

Paper/Purpur resolves Bukkit/Paper plugin dependencies from each JAR’s descriptor; filename order is **not** an ordering mechanism. The install order below is the troubleshooting and staging sequence, not an instruction to rename JARs. Fully stop and start after every change; do not use `/reload`.

| Stage | Components | Dependency reason | Verification after restart |
| --- | --- | --- | --- |
| 1 | Paper/Purpur, bundled spark | Establish the Java 21 server baseline. | Console identifies the expected 1.21.4 build; `/spark` responds. |
| 2 | ViaVersion | Must be available for a current Geyser client on the older 1.21.4 protocol. | Console shows ViaVersion enabled; Java 1.21.4 test client can join. |
| 3 | Geyser-Spigot, Floodgate-Spigot | Floodgate is the Geyser auth provider for the crossplay template. | Console shows both enabled; Geyser config points to Floodgate; a Bedrock test account joins. |
| 4 | LuckPerms, EssentialsX, optional Vault | Establish permission policy and operational commands before applications consume them. | `/lp` works; staff/player groups have least privilege; EssentialsX basic commands work for Java and Bedrock users. |
| 5 | Optional PlaceholderAPI and only needed expansions | Adds placeholder services only where a consumer needs them. | Consumer renders expected output; no unused expansions are installed. |
| 6 | KairuBridge | Consumes optional integrations softly and reports to the control plane. | `/kairu status`; heartbeat arrives; `/kairu link` handles test codes; server thread remains responsive. |

## 5. Crossplay configuration: Geyser + Floodgate

The baseline preserves a secure Java authentication model. Leave `online-mode=true`; Floodgate is designed to admit Bedrock users through Geyser while Java players continue to be authenticated against the Java account service. Do **not** set offline mode just to support Bedrock.

The installer adds a same-host design to `plugins/Geyser-Spigot/config.yml`:

```yaml
bedrock:
  address: 0.0.0.0
  port: 19132
remote:
  address: 127.0.0.1
  port: 25565
  auth-type: floodgate
```

This means Java players connect to `play.example.net:25565` over **TCP**, while Bedrock players connect to the same hostname or public IP at **UDP 19132**. They cannot share the same UDP listener, and a host firewall or cloud security group must explicitly allow both services. Geyser’s official setup places the plugin configuration under `plugins/Geyser-Spigot/config.yml` and recommends testing connectivity with `geyser connectiontest <ip> <port>`. [4]

Floodgate generates `plugins/floodgate/key.pem`. Geyser must use the corresponding public key as represented by the **version-generated** Geyser configuration. The supplied template uses the usual `floodgate-key-file: key.pem` baseline, but server operators must retain all keys written by their installed Geyser release and compare any template merge against its generated comments. Do not copy private keys between unrelated environments, do not commit them, and do not expose them through a web server.

Keep Floodgate’s `username-prefix: "."` and `replace-spaces: true`. The prefix prevents Java/Bedrock name collisions; removing it can create confusing command, teleport, and identity behavior. Bedrock and Java identities use different UUID/account semantics, so homes, balances, kits, permissions, and data do not automatically merge across identities. Test staff tools with one Java account and one Bedrock account before player launch. [5] [8]

If Floodgate is deliberately omitted, change the Geyser authentication setting through the config generated by the installed Geyser release, restart, and test that policy. Do not leave `auth-type: floodgate` with no Floodgate plugin installed.

## 6. Java server configuration and firewall

The supplied `server.properties` is intentionally conservative: Java `online-mode=true`, modern secure profiles, a whitelist enabled/enforced, RCON disabled, a blank `server-ip` binding all available interfaces, and no query listener. Paper documents that `server-port` is the Java listener, `server-ip` should remain blank to bind all interfaces, `online-mode=true` verifies Java accounts, and `enforce-whitelist=true` removes users not on the whitelist. [12]

> Do not set `server-ip=127.0.0.1` for this standalone public instance. That would prevent public Java clients from reaching TCP 25565. The **Geyser remote backend** is what should use `127.0.0.1` when Geyser and Paper/Purpur run on the same host.

### 6.1 Required network rules

| Service | Transport | Port | Direction | Exposure | Rationale |
| --- | --- | ---: | --- | --- | --- |
| Java Edition / Paper/Purpur | TCP | 25565 | Inbound | Public player networks, or a trusted proxy only | Minecraft Java listener. |
| Bedrock / Geyser | UDP | 19132 | Inbound | Public player networks | Geyser Bedrock listener. |
| Server updates / KairuBridge telemetry | TCP | 443 | Outbound | Official download services and Kairu Railway HTTPS endpoint | Official APIs and control-plane communication. |
| RCON | TCP | 25575 | Inbound | **None** by default | RCON is disabled; do not open it casually. |
| SSH / Windows management | TCP | Host-specific | Inbound | Administrator IP ranges only | Use key-based/managed access, not public-anywhere administration. |

Linux examples for a host using **UFW** are below. Apply equivalent rules in the cloud provider firewall/security group as well; host firewalls cannot override a provider-level block. Limit SSH to known administrator addresses rather than copying a broad rule.

```bash
sudo ufw allow 25565/tcp comment 'Minecraft Java'
sudo ufw allow 19132/udp comment 'Minecraft Bedrock via Geyser'
sudo ufw status numbered
```

Windows Defender Firewall examples, run from elevated PowerShell:

```powershell
New-NetFirewallRule -DisplayName 'Kairu Minecraft Java TCP 25565' -Direction Inbound -Action Allow -Protocol TCP -LocalPort 25565
New-NetFirewallRule -DisplayName 'Kairu Geyser Bedrock UDP 19132' -Direction Inbound -Action Allow -Protocol UDP -LocalPort 19132
```

After opening the ports, test from an external network—not only the LAN. Use Geyser’s `geyser connectiontest <public-ip-or-hostname> 19132`, then test a real Bedrock client. Confirm UDP is permitted by the hosting provider; TCP success does not prove UDP reachability.

## 7. KairuBridge placement and security

Verify the bundled plugin at `plugins/KairuBridge.jar`, then copy the example to the location the release documents. The provided example calls out the values expected by the integration contract.

```bash
# Linux example—use the release's documented directory/name if it differs.
mkdir -p plugins/KairuBridge
cp templates/KairuBridge-config.yml.example plugins/KairuBridge/config.yml
chmod 640 plugins/KairuBridge/config.yml
```

The contract requires KairuBridge to send `Authorization: Bearer <PLUGIN_API_KEY>` and `X-Kairu-Server-Id` on its outbound control-plane calls. It records heartbeat/player data and completes link codes; it must not expose those server secrets to clients or browser code. Use a unique, randomly generated `PLUGIN_API_KEY` for each server; set the Railway endpoint to HTTPS; restrict the plugin config’s filesystem permissions; redact link codes, XUIDs, and secrets in logs; and rotate the key after suspected disclosure. Network calls must be asynchronous and must never block the Minecraft main thread.

KairuBridge must continue to start if Vault, LuckPerms, PlaceholderAPI, Geyser, or Floodgate is absent. In the declared crossplay setup, all are expected except optional PlaceholderAPI/Vault, but the plugin’s soft-dependency design remains a stability requirement from the contract.

## 8. First-production checklist

Complete this checklist in staging before advertising the server.

| Check | Expected evidence |
| --- | --- |
| Runtime | `java -version` reports major 21 or later; `start.sh`/`start.ps1` selects exactly one server JAR. |
| Legal acceptance | An authorized administrator has manually reviewed and accepted `eula.txt`. |
| Server security | `online-mode=true`; RCON disabled; whitelist and enforced whitelist match the launch policy; no secrets in repository or backup export. |
| Geyser/Floodgate | Geyser connects to `127.0.0.1:25565`; `auth-type: floodgate`; Floodgate key and prefix remain present; external UDP 19132 test succeeds. |
| ViaVersion | Current Geyser connection succeeds through the 1.21.4 backend; no unreviewed snapshot plugin is present. |
| Identity | One Java and one Bedrock test user can join, receive correct groups, use intended commands, and cannot obtain staff privileges. |
| Operations | Backups restore to an isolated test directory; `stop` completes cleanly; restart logs contain no unresolved plugin errors. |
| Kairu | KairuBridge heartbeat reaches the expected Railway server ID; `/kairu link <code>` completes a test one-time code; logs redact credentials. |
| Performance | `/spark` is accessible to authorized operators; baseline profiling is saved before public launch. |

## 9. Start, stop, memory, and profiling

The start scripts use Java 21 and G1 garbage collection with `-Xms2G -Xmx4G` by default. These are **examples**, not a capacity plan. Do not allocate all physical RAM to Java; preserve memory for the operating system, filesystem cache, backups, and monitoring. Measure under representative player load with Paper’s bundled spark, then change values deliberately.

```bash
# Linux—4 GB initial / 6 GB maximum example
XMS=4G XMX=6G ./start.sh
```

```powershell
# Windows PowerShell—4 GB initial / 6 GB maximum example
.\start.ps1 -Xms 4G -Xmx 6G
```

Use the console `stop` command for every normal shutdown. Paper 1.21+ includes spark, so no plugin JAR is needed for routine profiling. Start a bounded capture with `/spark profiler start --timeout 600`, reproduce the issue, then stop/open the report according to the returned command. Restrict spark permissions because profiling output can disclose server behavior. [11]

## 10. Controlled update procedure

There is no safe universal “auto-update” for a server with an unsupported Minecraft line and a cross-version protocol stack. Use a maintenance window, pin reviewable releases, preserve rollback artifacts, and update **one compatible set at a time**.

1. Announce maintenance and stop the server cleanly with `stop`. Confirm the Java process has exited.
2. Make the offline backup described in [Section 11](#11-backup-and-restore-procedure), including the root files, worlds, plugin data, and a record of JAR hashes/versions.
3. Clone the instance into staging. Update and test the intended server/plugin combination there first. For Paper, query the official Downloads Service, select the stable 1.21.4 build, and SHA-256 verify it. For Purpur, verify the official API’s available MD5 and record a local SHA-256. [2]
4. Download plugins only from their official pages/APIs. Select a reviewed Geyser/Floodgate pair, the pinned ViaVersion or a tested stable successor, and tested KairuBridge build. Do not assume “latest” remains compatible with 1.21.4.
5. Compare newly generated configuration schemas with your version-controlled redacted configuration. Preserve secrets and world-specific settings; do not blindly overwrite configs.
6. Start staging. Check logs, `/version`, `/plugins`, `/spark`, Java login, Bedrock login, groups, Economy/Vault behavior when enabled, Kairu heartbeat, and linking.
7. Promote the tested JAR set in a short production window. Retain the previous JARs outside `plugins/` and the verified backup for rollback.
8. If errors occur, stop cleanly, restore the entire matched server/plugin/config set and world backup, then investigate in staging. Do not mix new plugin data with old JARs without a documented rollback plan.

When a plugin source does not publish a checksum, record its local SHA-256 at approval time in a controlled change record and compare it before deployment. This does not replace upstream signing/hashing, but it detects unexpected local substitution after approval.

## 11. Backup and restore procedure

A usable backup must be consistent, encrypted/protected as appropriate, retained off-host, and periodically restored into an isolated test instance. Do not back up a live world by copying arbitrary files while it changes. For the simple manual procedure below, stop the server first.

### 11.1 Offline Linux backup

```bash
# At the server console first: stop
cd /path/to/server-pack
stamp=$(date -u +%Y%m%dT%H%M%SZ)
mkdir -p /srv/kairu-backups
# Excludes volatile logs/cache and excludes no configuration by default.
tar --xattrs --acls -czf "/srv/kairu-backups/kairu-1.21.4-${stamp}.tar.gz" \
  --exclude='./logs' --exclude='./cache' --exclude='./.installer-tmp' \
  .
sha256sum "/srv/kairu-backups/kairu-1.21.4-${stamp}.tar.gz" \
  > "/srv/kairu-backups/kairu-1.21.4-${stamp}.tar.gz.sha256"
```

Copy the archive and checksum to protected off-host storage with encryption and an access policy separate from the game host. If the backup system includes plugin keys or KairuBridge credentials, treat it as secret material. For large production worlds, use a backup solution designed for coordinated save/flush or filesystem snapshots and test its consistency; the manual stop-and-archive procedure remains the clear recovery baseline.

### 11.2 Windows backup

Stop the server first, then archive the full instance with a timestamp. Include the root JAR, `world*`, `plugins/`, configs, whitelist/ops files, and `eula.txt`; exclude logs only if policies do not require them. Use a Windows backup system or protected archive destination that supports off-host retention and encryption. Generate and retain a SHA-256 for every archive:

```powershell
$stamp = Get-Date -Format 'yyyyMMddTHHmmssZ'
$backup = "D:\KairuBackups\kairu-1.21.4-$stamp.zip"
Compress-Archive -Path 'C:\path\to\server-pack\*' -DestinationPath $backup -CompressionLevel Optimal
Get-FileHash -Algorithm SHA256 $backup | Format-List | Out-File "$backup.sha256.txt"
```

### 11.3 Restore test

1. Provision an isolated server directory and keep it unreachable from public players.
2. Verify the archive SHA-256 before extraction. Extract all root files, worlds, `plugins/`, and configuration as one matched set.
3. Use the same Java major version and start script. Do not point the test at production databases, control-plane secrets, public DNS, or shared plugin storage.
4. Confirm the worlds load, a test account can join, expected homes/permissions exist, and console logs are clean. Then stop the test server.
5. Document the recovery point objective, recovery duration, archive location, result, and any gap. A backup not restored successfully is not a verified backup.

## 12. Troubleshooting boundaries

| Symptom | Likely cause | Safe first action |
| --- | --- | --- |
| Startup says Java is unsupported | Host service invokes Java 17 or older. | Run `java -version` under the same service account; set the Java 21 path explicitly and restart. |
| Bedrock cannot connect but Java can | UDP 19132 blocked, wrong Geyser listener, or Geyser unavailable. | Check Geyser startup logs, host/provider UDP firewall, then run `geyser connectiontest` externally. |
| Bedrock gets an authentication/key error | Geyser/Floodgate mismatch or bad key relationship. | Fully stop; confirm both plugins are current/compatible and generated keys/config paths are preserved; do not hand-copy private keys casually. |
| Geyser cannot join a 1.21.4 backend | Missing/disabled ViaVersion or untested current Geyser combination. | Confirm ViaVersion enabled and its build is stable/tested; review Geyser’s current supported-version guidance. [4] [6] |
| Names, homes, or groups look different for Bedrock | Floodgate prefix/UUID identity difference. | Keep prefix; configure permissions for Floodgate identities; do not assume Java and Bedrock data merge. |
| A plugin is absent from `/plugins` | Wrong JAR platform, nested file location, failed load, or version incompatibility. | Check the complete startup log; ensure the Paper/Bukkit JAR is directly under `plugins/`; use a full restart. |
| Kairu heartbeat/linking fails | Wrong Railway URL/server ID/key, network egress issue, or plugin release/config mismatch. | Verify HTTPS base URL, server ID, secret availability, and redacted logs; never paste a key into chat or issue trackers. |
| Lag after adding plugin/config | Capacity, world workload, or integration problem. | Take a bounded `/spark` profile, reproduce minimally, then test the change in staging. [11] |

## 13. Official-source and integrity matrix

The installers use the following sources. A raw URL not listed here should not become a deployment source simply because it is convenient.

| Artifact | Official source used by pack | Pin / selection | Hash behavior |
| --- | --- | --- | --- |
| Paper | Paper Downloads Service v3 | Newest API-returned `STABLE` 1.21.4 build; current verified reference 232 | Official SHA-256 required and verified. [2] |
| Purpur | Purpur API v2 | API `latest` 1.21.4 build | Official API MD5 required and verified; local SHA-256 inventory recorded. |
| Geyser-Spigot | GeyserMC download API | Official current Spigot artifact | Official API SHA-256 required and verified. [4] |
| Floodgate-Spigot | GeyserMC download API | Official current Spigot artifact | Official API SHA-256 required and verified. [5] |
| ViaVersion | Official Hangar API/CDN | Stable 5.11.0 | Official Hangar SHA-256 required and verified. [6] |
| EssentialsX | Official GitHub release | **2.21.0** | No official digest located; header checked and local SHA-256 recorded only. [8] |
| LuckPerms Bukkit | Official LuckPerms download | **5.5.81** | No official digest located; header checked and local SHA-256 recorded only. [7] |
| PlaceholderAPI | Official GitHub release | **2.12.3** | Official GitHub digest SHA-256 required and verified. [9] |
| Vault | Official GitHub release | **1.7.3** | No official digest located; header checked and local SHA-256 recorded only. [10] |
| spark | Paper-bundled | No standalone artifact | No download required. [11] |

## 14. References

[1]: https://docs.papermc.io/paper/getting-started/ "PaperMC Documentation: Getting Started"
[2]: https://fill.papermc.io/v3/projects/paper/versions/1.21.4/builds/232 "PaperMC Downloads Service: Paper 1.21.4 Build 232"
[3]: https://docs.papermc.io/paper/getting-started/ "PaperMC Documentation: Java Requirements"
[4]: https://geysermc.org/wiki/geyser/setup/ "GeyserMC Wiki: Geyser Setup"
[5]: https://geysermc.org/wiki/floodgate/setup/paper-spigot/ "GeyserMC Wiki: Floodgate Setup for Paper and Spigot"
[6]: https://github.com/ViaVersion/ViaVersion/wiki/Installation "ViaVersion Wiki: Installation"
[7]: https://luckperms.net/download "LuckPerms: Official Downloads"
[8]: https://github.com/EssentialsX/Essentials/releases/tag/2.21.0 "EssentialsX 2.21.0 Release"
[9]: https://github.com/PlaceholderAPI/PlaceholderAPI/releases/tag/2.12.3 "PlaceholderAPI 2.12.3 Release"
[10]: https://github.com/MilkBowl/Vault/releases/tag/1.7.3 "Vault 1.7.3 Release"
[11]: https://docs.papermc.io/paper/profiling/ "PaperMC Documentation: Profiling with spark"
[12]: https://docs.papermc.io/paper/reference/server-properties/ "PaperMC Documentation: server.properties Reference"
