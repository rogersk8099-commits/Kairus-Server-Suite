# Deployment Status

**Kairu SMP Complete Suite · Manus AI · 6 September 2026**

## Current deployment

| Component | State | Location |
| --- | --- | --- |
| Community website | Live | `https://kairu-smp-website-production.up.railway.app` |
| Control-plane API | Live | `https://kairu-control-plane-production.up.railway.app` |
| PostgreSQL | Live and private | Railway `Postgres` service |
| Discord bot source | Built and deployed | `control-plane/src/bot/` in the `Kairu-Control-Plane` service |
| Discord bot process | Waiting for credentials | Starts automatically after Discord variables are configured |
| Minecraft bridge | Compiled | `release/KairuBridge.jar` and `server-pack/plugins/KairuBridge.jar` |
| Paper/Purpur server pack | Built | `release/Kairu-Server-Pack.zip` |

The website source is synchronized to `https://github.com/rogersk8099-commits/Kairus-Server-Website`. The complete multi-component source is synchronized to `https://github.com/rogersk8099-commits/Kairus-Server-Suite`.

The control plane passed its production health check and all public endpoints returned HTTP 200 against PostgreSQL. Database migrations run idempotently before application startup. The Discord bot intentionally remains dormant because no Discord bot token or application ID was supplied. The API remains online without those credentials.

## Activating the Discord bot

Create or select a Discord application in the Discord Developer Portal. Add the following variables to the Railway `Kairu-Control-Plane` service:

```text
DISCORD_BOT_TOKEN=<private bot token>
DISCORD_APPLICATION_ID=<application ID>
DISCORD_GUILD_ID=<optional development server ID>
MEMBERSHIP_ROLE_MAP={"VIP":"role-id","MVP":"role-id"}
```

Redeploy the service, then run `npm run register-commands` with the same Railway variables. A guild ID registers development commands immediately; global commands may take longer to propagate.[1]

The bot provides `/link`, `/status`, `/players`, `/events`, administrator-only `/sync`, and confirmed `/unlink` commands. It shares the control plane and PostgreSQL database with the website and KairuBridge.

## Connecting the Minecraft server

Install Paper or Purpur 1.21.4 with Java 21. Install the files from `Kairu-Server-Pack.zip`, review and accept the Minecraft EULA yourself, and open TCP 25565 plus UDP 19132. The server pack downloads ViaVersion, Geyser, Floodgate, LuckPerms, and EssentialsX from their official project sources. It includes the compiled KairuBridge plugin.

Copy the private `KairuBridge-PRODUCTION-config.yml` delivered beside the suite ZIP to:

```text
plugins/KairuBridge/config.yml
```

Restart the server and run `/kairu status`. A successful connection causes `/api/server/status` to report live telemetry and makes the website's dynamic integration ready.

> Paper 1.21.4 is still downloadable but officially unsupported. Stage-test the exact plugin set and plan an upgrade to a supported Paper version.[2]

## References

[1]: https://discord.com/developers/docs/interactions/application-commands "Discord application commands"
[2]: https://fill-ui.papermc.io/projects/paper/version/1.21.4 "Paper 1.21.4 official version listing"
[3]: https://docs.railway.com/guides/variables "Railway variables documentation"
[4]: https://geysermc.org/wiki/geyser/setup/ "Geyser setup documentation"
