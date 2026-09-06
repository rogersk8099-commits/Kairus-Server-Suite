package com.kairu.bridge;

import com.kairu.bridge.api.ControlPlaneClient;
import com.kairu.bridge.integration.SoftIntegrations;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

/** Executes only an explicit allowlist of control-plane commands, on the Bukkit primary thread. */
public final class RemoteCommandProcessor {
    private final KairuBridgePlugin plugin;
    private final ControlPlaneClient client;
    private final SoftIntegrations integrations;
    public RemoteCommandProcessor(KairuBridgePlugin plugin, ControlPlaneClient client, SoftIntegrations integrations) { this.plugin = plugin; this.client = client; this.integrations = integrations; }

    public void execute(ControlPlaneClient.RemoteCommand command) {
        boolean success = false; String detail;
        try {
            switch (command.type()) {
                case "WHITELIST_ADD" -> { OfflinePlayer player = player(command.player()); player.setWhitelisted(true); success = true; detail = "Added to whitelist"; }
                case "WHITELIST_REMOVE" -> { OfflinePlayer player = player(command.player()); player.setWhitelisted(false); success = true; detail = "Removed from whitelist"; }
                case "NOTIFY" -> { Player player = onlinePlayer(command.player()); if (player == null) throw new IllegalArgumentException("Player is not online"); String message = command.message() == null ? "" : command.message(); player.sendMessage(ChatColor.AQUA + integrations.placeholders(player, message)); success = true; detail = "Player notified"; }
                default -> detail = "Unsupported command type";
            }
        } catch (RuntimeException exception) { detail = "Execution failed"; plugin.getLogger().warning("Remote command " + command.id() + " failed: " + exception.getClass().getSimpleName()); }
        final boolean ackSuccess = success; final String ackDetail = detail;
        client.acknowledge(command.id(), ackSuccess, ackDetail).exceptionally(error -> { plugin.getLogger().fine("Could not acknowledge remote command " + command.id()); return null; });
    }

    private static OfflinePlayer player(String name) { if (name == null || !name.matches("[A-Za-z0-9_]{3,16}")) throw new IllegalArgumentException("Unsafe player name"); return Bukkit.getOfflinePlayer(name); }
    private static Player onlinePlayer(String name) { if (name == null || !name.matches("[A-Za-z0-9_]{3,16}")) return null; return Bukkit.getPlayerExact(name); }
}
