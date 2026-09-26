# fix45
Hardcore reset windows are now persisted in PostgreSQL and loaded at startup. The physical Obsidian Gate world is config-driven (`hardcore.physical-world`) rather than hard-coded. Automatic death records now derive survival seconds from runtime world-entry timing and season from `season.current`.

Revival now rejects an ALIVE/UNKNOWN player and requires DEAD, SPECTATING, RESET_ELIGIBLE or LOCKED state.

Remaining production integration:
- Prefer the remote World Registry's physical-world mapping over the local config fallback once its current Java API is verified.
- Runtime survival timing resets on server restart; authoritative play-session persistence should replace it.
- Reconcile V014/V015 against existing Hardcore migrations before deployment.
