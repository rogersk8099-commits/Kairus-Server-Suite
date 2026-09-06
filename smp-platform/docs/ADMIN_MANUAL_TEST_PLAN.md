# Phase-2 administration manual integration plan

Run this on Paper/Purpur **1.21.4** and Java **21**. Install Geyser and Floodgate for the Bedrock cases; all integrations are soft dependencies.

| Area | Test | Expected result |
|---|---|---|
| Command & permission | Test `/smpadmin` as an unprivileged player, then a staff member with only `smpplatform.admin.players`. | First is denied; second reaches only permitted player functionality. Rendering a button never substitutes for the dispatch-time permission check. |
| Java selector | Join several Java accounts, open Player Administration, switch every tab and paginate/search. | Heads show UUID-backed cached profiles with edition, world, guild, rank, playtime, and presence. |
| Bedrock selector | Join via Geyser/Floodgate, open BEDROCK and inspect the account. | Floodgate UUID resolution marks the profile BEDROCK; no username prefix is used. Inventory and chat prompts are usable. |
| Input prompts | On Java and Bedrock, execute `Set XP`, `Send to World`, `Warn`, and `Temp Mute`. | Value/reason prompt is chat based; `cancel` aborts. Invalid values do not mutate state. |
| Confirmation | Attempt Clear Inventory, Kick, Mute, Ban, and Temp Ban. Test a different staff account clicking an open confirmation and wait >30 seconds. | No mutation happens until the owner confirms; other staff cannot consume it; expired confirmation is rejected and audited. |
| Audit | Execute heal, warn, and a confirmed destructive action. | Inspector Audit History includes actor, target, action, outcome, reason, correlation ID in the composition path, and timestamp. |
| World Control | Load the six registered worlds and open World Control. | Exactly Ashfall, Obsidian Gate, The Atrium, Neon Colosseum, The Quarry, and Verdance appear with website-aligned metadata and runtime values. |
| Monitor | Load/unload optional integrations and create a controlled TPS/memory warning in a staging environment. | Health cards show green/yellow/red semantics; unavailable integrations remain warnings, never prevent plugin startup. |
| Safety | Restart during active mute/freeze and during confirmation. | Phase-2 in-process moderation and confirmation state clears as documented; production composition must persist moderation/audit through phase-1 PostgreSQL before production deployment. |

Do not test bans, inventory clearing, or destructive world controls against production accounts/worlds. Use staging identities and disposable worlds.
