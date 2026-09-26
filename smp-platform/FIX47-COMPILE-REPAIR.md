# fix47 compile repair

This pass is based directly on the supplied Windows compiler logs.

Server:
- adds VaultAPI to the compile-only classpath;
- imports Bukkit GameMode;
- restores the 4-argument KairuClientGateway reply overload used by existing handlers.

Client:
- uses the real KairuControlBridge instead of the nonexistent KairuClientBridge;
- adds KairuControlBridge.sendAction(String) as a thin wrapper around the existing authoritative request path;
- moves all admin/auction/inventory/guild/world form state to KairuControlScreen class state so member composables can access it;
- keeps Material3 out of the Kairu-owned Compose files.

Re-run both Windows builds. This environment has not independently completed the Gradle builds.
