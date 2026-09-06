# Manual Integration Test Plan

| Area | Scenario | Expected result |
|---|---|---|
| Java | Join, chat, `/link`, points update | Gameplay responds normally; events are queued asynchronously. |
| Bedrock/Geyser/Floodgate | Join with a Floodgate identity and open inventory GUI | Identity resolves through Floodgate UUID; no edition-specific gameplay advantage; controls work in Geyser. |
| LuckPerms | Remove/install LuckPerms, test a world permission | Server starts either way; fallback effective Bukkit permission works when absent. |
| Multiverse | Load, unload, teleport to all six mapped worlds | Registry IDs stay canonical; runtime management is delegated to Multiverse. |
| Multiverse-Inventories | Set Quarry sharing on/off and visit Obsidian Gate | Quarry configuration changes as requested; Obsidian Gate never shares Ashfall inventory. |
| PlotSquared | Submit an Atrium plot | Plot ownership/mechanics remain PlotSquared; platform submission is queued. |
| Vault/claims | Remove each provider, then run balance/build checks | Integration reports unavailable; no crash or fabricated ownership/balance. |
| Skript | Load both example `.sk` files | Syntax registers only with Skript available; actions use queue/cache contract. |
| PostgreSQL | Stop database during link/outbox mutation | Staff sees warning; destructive mutations fail safe; server stays online. |
| Central API outage | Make endpoint unavailable, then create events and restore it | Outbox retains events, backs off, retries with idempotency UUID, and marks delivered after acknowledgement. |
| Linking | Expire, use twice, revoke, and rate-limit codes | Each prohibited state is rejected and audit entries are written; plaintext code is not persisted. |
| Chat | Send messages in all worlds | Canonical world ID appears in outbound payload; Verdance is disabled unless policy enables it. |
| Announcements | Send bounded title/action-bar/platform announcement | Adventure/MiniMessage renders Minecraft content and API fan-out is asynchronous. |
