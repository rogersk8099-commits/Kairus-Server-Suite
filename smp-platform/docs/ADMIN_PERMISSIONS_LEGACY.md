# Administration permissions

`LuckPerms` remains the source of truth. `smpplatform.admin` gates entry; each screen and action also requires a granular node. A holder of the root node is accepted as a deliberate administrative override.

| Area | Permission |
|---|---|
| Player selector / inspector | `smpplatform.admin.players` |
| World Control | `smpplatform.admin.worlds` |
| Server Monitor | `smpplatform.admin.monitor` |
| Audit history | `smpplatform.admin.audit` |
| Teleport / bring / send world / spawn | `smpplatform.admin.player.teleport`, `.bring`, `.world`, `.spawn` |
| Gamemode, heal, feed, health, hunger, XP | `smpplatform.admin.player.gamemode`, `.heal`, `.feed`, `.health`, `.hunger`, `.xp` |
| Effects / inventory / ender chest / freeze | `smpplatform.admin.player.effects`, `.inventory`, `.enderchest`, `.freeze` |
| Warn / kick / mute / temp mute / ban / temp ban | `smpplatform.admin.moderation.warn`, `.kick`, `.mute`, `.ban` |

The action router checks the action node even when a caller reaches an action through a GUI. It never relies on a GUI visibility check as authorization.
