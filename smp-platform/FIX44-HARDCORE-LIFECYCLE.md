# fix44 Hardcore lifecycle integration
Adds Obsidian Gate death capture, death-state transition, respawn/world-entry spectator enforcement, online revival gamemode handling, and an admin-configurable reset window.

Safety/integration notes:
- The world name is currently `obsidian-gate`; production should resolve this from World Registry instead of a literal.
- survival_seconds and season in automatic death records are placeholders until joined to the existing session/season service.
- Reset window is in-memory in this pass and should be persisted/config-backed next.
- Existing Hardcore migrations must be reconciled with V014 before production to avoid parallel schemas.
