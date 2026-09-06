package gg.neonnexus.smpplatform.integrations.placeholder;

import java.util.UUID;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;

/** Register only when PlaceholderAPI is detected; resolve reads a local cache and never performs I/O. */
public final class NexusPlaceholderExpansion extends PlaceholderExpansion {
    private final NexusPlaceholders resolver;
    public NexusPlaceholderExpansion(NexusPlaceholders resolver) { this.resolver = resolver; }
    @Override public String getIdentifier() { return "nexus"; }
    @Override public String getAuthor() { return "Neon Nexus"; }
    @Override public String getVersion() { return "0.1.0"; }
    @Override public boolean persist() { return true; }
    @Override public String onRequest(OfflinePlayer player, String params) { return player == null ? "" : resolver.resolve(player.getUniqueId(), params); }
}
