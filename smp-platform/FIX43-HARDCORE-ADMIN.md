# fix43 Obsidian Gate Hardcore administration
Adds admin lookup, death-history read model, state transitions for ALIVE/DEAD/SPECTATING/RESET_ELIGIBLE/LOCKED, granular permissions, reasoned audit records, and a dedicated client panel.

Important integration boundary: this admin state table must be reconciled with any existing Hardcore gameplay repository before production. It is intentionally not claimed to enforce spectator/death/reset behavior by itself. The next pass should bind state transitions into the existing Obsidian Gate lifecycle/reset service rather than creating parallel enforcement.
