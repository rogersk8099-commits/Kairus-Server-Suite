# fix32 player administration
- Adds server-authoritative player profile and action service.
- Adds granular permission checks per player action.
- Online-only actions fail explicitly for offline targets.
- Implements teleport-to, bring, send-to-spawn/world, gamemode, heal, feed, clear-effects and kick primitives.
- Client Players page receives direct target UUID/world/gamemode controls.
- Search remains the preferred way to locate players; future pass will bind search result selection directly to the player profile and add inventory/freeze/moderation workflows.
