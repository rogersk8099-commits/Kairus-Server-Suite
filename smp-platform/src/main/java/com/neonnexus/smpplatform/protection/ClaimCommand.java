package com.neonnexus.smpplatform.protection;

import org.bukkit.command.*;
import org.bukkit.entity.Player;

/** Player-facing land claims. A radius of 32 creates a 65×65 plot, a good SMP default. */
public final class ClaimCommand implements CommandExecutor {
    private final WorldProtectionService protection;
    public ClaimCommand(WorldProtectionService protection) { this.protection = protection; }
    @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) { sender.sendMessage("This command is for players."); return true; }
        try {
            if (args.length == 0) { var claim = protection.create(player, protection.defaultRadius()); player.sendMessage("Claim created: " + (claim.maxX()-claim.minX()+1) + "×" + (claim.maxZ()-claim.minZ()+1) + " blocks. Use /claim trust <player> to add builders."); return true; }
            switch (args[0].toLowerCase()) {
                case "trust" -> { if (args.length < 2) throw new IllegalArgumentException("Usage: /claim trust <player>"); protection.trust(player, player.getServer().getOfflinePlayer(args[1])); player.sendMessage(args[1] + " can now build in this claim."); }
                case "untrust" -> { if (args.length < 2) throw new IllegalArgumentException("Usage: /claim untrust <player>"); protection.untrust(player, player.getServer().getOfflinePlayer(args[1])); player.sendMessage(args[1] + " can no longer build in this claim."); }
                case "abandon" -> { protection.abandon(player); player.sendMessage("Your claim was abandoned."); }
                case "info" -> player.sendMessage(protection.describe(player));
                default -> { int radius; try { radius = Integer.parseInt(args[0]); } catch (NumberFormatException exception) { throw new IllegalArgumentException("Usage: /claim [radius] | trust <player> | untrust <player> | info | abandon"); } var claim = protection.create(player, radius); player.sendMessage("Claim created: " + (claim.maxX()-claim.minX()+1) + "×" + (claim.maxZ()-claim.minZ()+1) + " blocks."); }
            }
        } catch (IllegalArgumentException exception) { player.sendMessage("§c" + exception.getMessage()); }
        return true;
    }
}
