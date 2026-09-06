package com.neonnexus.smpplatform.listeners;

import com.neonnexus.smpplatform.identity.FloodgateIdentityAdapter;
import com.neonnexus.smpplatform.identity.PlayerIdentity;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import java.util.Objects;
import java.util.logging.Logger;

/** Persistence is deliberately deferred until PostgreSQL health is established; no player data is written to YAML. */
public final class PlayerIdentityListener implements Listener {
    private final FloodgateIdentityAdapter identities; private final Logger logger;
    public PlayerIdentityListener(FloodgateIdentityAdapter identities, Logger logger) { this.identities = Objects.requireNonNull(identities); this.logger = Objects.requireNonNull(logger); }
    @EventHandler public void onPlayerJoin(PlayerJoinEvent event) {
        PlayerIdentity identity = identities.resolve(event.getPlayer());
        logger.fine(() -> "Resolved " + identity.edition() + " identity for " + identity.minecraftUuid() + identity.xuidOptional().map(xuid -> " xuid=" + xuid).orElse(""));
    }
}
