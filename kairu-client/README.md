# Kairu Client UI

Optional Fabric client interface for Java players. It provides the Kairu dark, purple and neon-blue administration interface opened with **K** or `/smpadmin`.

## OneConfig UI runtime

The client now vendors the pinned OneConfig source in `oneconfig-source/` and packages its
Fabric 26.2 bootstrap *inside* the Kairu client. This gives the client the real OneConfig
runtime without making players install a second mod. The original upstream licence and
notice are retained in `oneconfig-source/LICENSE` and `ONECONFIG-NOTICE.md`.

## Authority boundary

The client is an interface only. It does not contain database credentials, Discord tokens, permission decisions, or gameplay logic. SMPPlatform remains authoritative and Bedrock players use server-side menus/forms.

Pressing **K** now first receives the server-authorised state and opens the real OneConfig
Kairu landing page. Its Player and Administration categories lead into the relevant Kairu
actions. The remaining detail pages are retained as a compatibility surface while they are
ported one by one; each continues to use the same server-checked request protocol.

## Current integration status

This UI uses the `KAIRU_ADMIN_V2` request/reply protocol, now handled by SMPPlatform's permission-checked `kairuadmin` gateway. It provides server state, permitted world travel, player/world administration actions, readable Atrium settings labels, the caller's guild profile and PostgreSQL-backed points balances. Do not ship the client as a replacement for SMPPlatform.

The Guilds page can also invite online players, manage members, change ranks, leave a guild, transfer ownership through a server-held 30-second two-step confirmation, and display the durable top-20 guild leaderboard. Every mutation is validated, audited and persisted by SMPPlatform; the client is never trusted as authority. The World admin page provides permission-checked time, weather, PvP, difficulty, selected gamerule, maintenance, and Multiverse load/unload controls. The Points page can display the caller's ledger and durable player leaderboards for each configured currency. Advanced PlotSquared editing remains a future gateway addition.

## Build

Use Java 25 and run the following from PowerShell on Windows:

```powershell
.\Build-OneConfig-Client.ps1
```

It builds the bundled OneConfig Fabric 26.2 bootstrap, embeds it as a nested mod, then
builds `build\libs\KairuSmpClient-0.5.0.jar`. Copy only that final Kairu JAR to the
player's `mods` folder. Both client and SMPPlatform use Gradle 9.5.1; this accepts
Temurin Java `25.0.4.1`. The expected Minecraft target is 26.2.
