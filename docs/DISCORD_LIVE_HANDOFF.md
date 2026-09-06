# Kairu SMP Discord Live Handoff

**Verified 7 September 2026**

## Production endpoints and identifiers

| Item | Value |
| --- | --- |
| Discord server | **Kairu SMP** |
| Official invite | <https://discord.gg/cbBj6EvcV4> |
| Discord server ID | `1546281211876479050` |
| Discord application ID | `1546281826341879878` |
| Discord bot | **Kairu SMP#3221** |
| Website | <https://kairu-smp-website-production.up.railway.app> |
| Control plane | <https://kairu-control-plane-production.up.railway.app> |
| OAuth callback | `https://kairu-smp-website-production.up.railway.app/auth/discord/callback` |
| Railway bot service | `Kairu-Discord-Bot` |
| Railway control-plane service | `Kairu-Control-Plane` |

## Completed configuration

The Discord community has the generated Kairu icon and this server profile description:

> A cross-platform Minecraft community for Java and Bedrock players. Build, explore six unique worlds, join events, earn points, and forge your legend together.

The Railway bot gateway is active. Discord guild command registration completed with **46 slash commands**. The idempotent `/setup-server confirm:true` reconciliation finished with **Created 0, Reused 54, Updated 4, Failed 0**. Independent Discord REST verification matched all **58** managed resources: **15 roles, 9 categories, and 34 channels**. No expected resource was missing, and no unexpected category or channel replaced the blueprint.

The bot runs without Discord Administrator permission. Only **Server Members Intent** is enabled because the production source uses guild-member synchronization. Presence and Message Content intents remain disabled. Temporary Premium and Moderator bootstrap roles used to repair inherited private-category access were removed. The bot now retains only its managed application role and the blueprint Bots role. Explicit managed-role overwrites preserve access to **Premium Lounge** and **Staff Meeting**.

## Website and OAuth status

The website’s Join Discord actions now use the official permanent invite. Discord OAuth uses the same Kairu application and requests only the `identify` scope. The exact callback is registered in Discord, and the client secret is stored only as a masked Railway control-plane variable.

A live first-time authorization created and linked the KajiKairu platform identity, issued a secure host-only session, and redirected to `/portal`. Sign-out revoked the session and restored the public navigation state. A second authorization resolved to the existing platform account rather than creating a duplicate. The control plane rejects unauthenticated internal OAuth state requests with HTTP 401.

## Secret boundary

No Discord bot token, OAuth client secret, database password, website API secret, or session secret is included in this document, source control, release archives, or the local project folder. Discord and shared website credentials live in masked Railway variables. Generated shared website secrets are also held in a protected operator-only file outside the release tree for disaster recovery.

If a bot or OAuth credential may have been exposed, rotate it in Discord, update the matching masked Railway variable, redeploy the affected service, and rerun the relevant gateway or OAuth test before reopening access.
