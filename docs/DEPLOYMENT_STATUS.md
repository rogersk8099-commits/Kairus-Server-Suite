# Deployment Status

**Kairu SMP Complete Suite · 7 September 2026**

## Current deployment

| Component | State | Location |
| --- | --- | --- |
| Community website | Live, OAuth-ready | <https://kairu-smp-website-production.up.railway.app> |
| Control-plane API | Live | <https://kairu-control-plane-production.up.railway.app> |
| PostgreSQL | Live and private | Railway `Postgres` service |
| Website OAuth schema/routes | Live and verified | Discord `identify` authorization, PKCE, one-use state/ticket, secure session, logout, and returning sign-in passed in production |
| Dedicated Discord bot | Live and gateway-ready | Railway `Kairu-Discord-Bot`, sourced from GitHub `/discord-bot`; 46 guild commands registered |
| Discord community | Live | **Kairu SMP** · <https://discord.gg/cbBj6EvcV4> |
| KairuBridge | Compiled | `release/KairuBridge.jar` and `server-pack/plugins/KairuBridge.jar` |
| SMPPlatform | Compiled | `release/SMPPlatform.jar` and `server-pack/plugins/SMPPlatform.jar` |
| Paper/Purpur server pack | Built | `release/Kairu-Server-Pack.zip` |

The website source repository is <https://github.com/rogersk8099-commits/Kairus-Server-Website>. The complete suite repository is <https://github.com/rogersk8099-commits/Kairus-Server-Suite>. Final source synchronization is required after this release package is generated so GitHub remains the Railway source of truth.

The website and control plane return HTTP 200 on their public health/content routes. The favicon and login page are live. **Continue with Discord** is enabled and uses application `1546281826341879878`, the exact production callback, authorization code plus PKCE, and server-side Railway secrets. The website's Join Discord actions use the official permanent invite.

## Website Discord sign-up/sign-in

The Kairu application has this exact redirect registered:

```text
https://kairu-smp-website-production.up.railway.app/auth/discord/callback
```

The documented variables are active in Railway. A live first-time authorization created and linked the platform identity, logout revoked the secure session, and a returning authorization resolved to the existing account without duplication.

## Dedicated Discord bot

The bot compiles, passes 22 tests, has zero reported npm audit vulnerabilities, and is deployed successfully on Railway. Its gateway is ready, **Server Members Intent** is enabled, Presence and Message Content remain disabled, and 46 guild commands are registered.

The production application ID is `1546281826341879878` and guild ID is `1546281211876479050`. The bot is installed without Administrator permission. The final `/setup-server` result was **Created 0, Reused 54, Updated 4, Failed 0**; independent verification matched 15 roles, 9 categories, and 34 channels. Temporary recovery roles were removed after explicit managed-role access to private Premium and Staff voice channels was verified. Twitch notifications require official EventSub delivery through the control plane. YouTube discovery requires a restricted API key and quota review. TikTok automated LIVE detection remains disabled until approved official capability is available.

## Connect the Minecraft server

Install Paper or Purpur 1.21.4 with Java 21. Install the server-pack files, then review and accept the Minecraft EULA yourself. Open TCP 25565 and UDP 19132 only on the actual game host. Configure KairuBridge with the supplied production control-plane URL and plugin key.

SMPPlatform targets the same Paper/Purpur server. Its single shaded JAR includes ten YAML files, two Flyway migrations, the exact six-world registry, and all module source/classes. Core, registry, identity, outbox, and PostgreSQL-backed guild/points commands are composed in the single runtime. Lifecycle, events/creative, and admin modules remain fail closed until their real backup/world/PlotSquared/staff-policy adapters pass staging; do not enable destructive workflows earlier.

> Paper 1.21.4 remains downloadable but officially unsupported. Pin the full dependency set, prove backups and restoration, and plan an upgrade to a supported release.

## References

[Discord OAuth2](https://discord.com/developers/docs/topics/oauth2), [Discord application commands](https://discord.com/developers/docs/interactions/application-commands), [Railway variables](https://docs.railway.com/guides/variables), [Paper downloads](https://fill-ui.papermc.io/projects/paper/version/1.21.4), and [Geyser setup](https://geysermc.org/wiki/geyser/setup/) are the authoritative external references.
