# Deployment Status

**Kairu SMP Complete Suite · 6 September 2026**

## Current deployment

| Component | State | Location |
| --- | --- | --- |
| Community website | Live, OAuth-ready | <https://kairu-smp-website-production.up.railway.app> |
| Control-plane API | Live | <https://kairu-control-plane-production.up.railway.app> |
| PostgreSQL | Live and private | Railway `Postgres` service |
| Website OAuth schema/routes | Deployed, fail closed | Migration `004_website_auth.sql`; internal auth routes return `AUTH_NOT_CONFIGURED` until credentials are supplied |
| Dedicated Discord bot | Built and validated | `discord-bot/`; Railway `Kairu-Discord-Bot` service created but gateway not activated |
| KairuBridge | Compiled | `release/KairuBridge.jar` and `server-pack/plugins/KairuBridge.jar` |
| SMPPlatform | Compiled | `release/SMPPlatform.jar` and `server-pack/plugins/SMPPlatform.jar` |
| Paper/Purpur server pack | Built | `release/Kairu-Server-Pack.zip` |

The website source repository is <https://github.com/rogersk8099-commits/Kairus-Server-Website>. The complete suite repository is <https://github.com/rogersk8099-commits/Kairus-Server-Suite>. Final source synchronization is required after this release package is generated so GitHub remains the Railway source of truth.

The website and control plane return HTTP 200 on their public health/content routes. The favicon and login page are live. The login page exposes **Continue with Discord** in a disabled, credential-pending state. This prevents a broken or insecure provider request before an exact Discord application callback and matching service secrets exist.

## Activate website Discord sign-up/sign-in

The user's Discord Developer Portal was not authenticated during deployment, so no application or secret was created. After signing in, create or select one Kairu application and register exactly:

```text
https://kairu-smp-website-production.up.railway.app/auth/discord/callback
```

Configure the variables in `website/docs/DISCORD_OAUTH.md` on both Railway services. Deploy the control plane first, test callback/state/ticket/session/logout behavior in staging, then set `VITE_DISCORD_AUTH_ENABLED=true` on the website and redeploy.

## Activate the dedicated Discord bot

The bot compiles, passes 22 tests, and has zero reported npm audit vulnerabilities. Its HTTP health service remains available when `DISCORD_TOKEN` is absent; gateway jobs, registration, role synchronization, schedules, and commands do not start.

Required activation values are `DISCORD_CLIENT_ID`, `DISCORD_GUILD_ID`, `DATABASE_URL`, `API_URL`, and `API_SECRET`; `DISCORD_TOKEN` activates the gateway. Enable the Discord **Server Members** privileged intent, review least-privilege invite permissions, run `npm run register:commands`, and execute `/setup-server` twice to prove idempotency before production use. Twitch notifications require official EventSub delivery through the control plane. YouTube discovery requires a restricted API key and quota review. TikTok automated LIVE detection remains disabled until approved official capability is available.

## Connect the Minecraft server

Install Paper or Purpur 1.21.4 with Java 21. Install the server-pack files, then review and accept the Minecraft EULA yourself. Open TCP 25565 and UDP 19132 only on the actual game host. Configure KairuBridge with the supplied production control-plane URL and plugin key.

SMPPlatform targets the same Paper/Purpur server. Its single shaded JAR includes ten YAML files, two Flyway migrations, the exact six-world registry, and all module source/classes. Core, registry, identity, outbox, and PostgreSQL-backed guild/points commands are composed in the single runtime. Lifecycle, events/creative, and admin modules remain fail closed until their real backup/world/PlotSquared/staff-policy adapters pass staging; do not enable destructive workflows earlier.

> Paper 1.21.4 remains downloadable but officially unsupported. Pin the full dependency set, prove backups and restoration, and plan an upgrade to a supported release.

## References

[Discord OAuth2](https://discord.com/developers/docs/topics/oauth2), [Discord application commands](https://discord.com/developers/docs/interactions/application-commands), [Railway variables](https://docs.railway.com/guides/variables), [Paper downloads](https://fill-ui.papermc.io/projects/paper/version/1.21.4), and [Geyser setup](https://geysermc.org/wiki/geyser/setup/) are the authoritative external references.
