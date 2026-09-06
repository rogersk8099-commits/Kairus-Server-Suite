package gg.neonnexus.smpplatform.lifecycle.paper;

import gg.neonnexus.smpplatform.lifecycle.admin.ConfirmationTokenService;
import gg.neonnexus.smpplatform.lifecycle.admin.HardcoreAdminService;
import gg.neonnexus.smpplatform.lifecycle.quarry.QuarryLifecycleService;
import java.time.Duration;
import java.util.UUID;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/** Minimal Bedrock-compatible command fallback for GUI calls: every dangerous operation is two-step confirmed. */
public final class LifecycleAdminCommand implements CommandExecutor {
    private final ConfirmationTokenService confirmations; private final HardcoreAdminService hardcore; private final QuarryLifecycleService quarry; private final String season;
    public LifecycleAdminCommand(ConfirmationTokenService c, HardcoreAdminService h, QuarryLifecycleService q, String season) { confirmations = c; hardcore = h; quarry = q; this.season = season; }
    @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player) || !sender.hasPermission("smpplatform.admin.lifecycle")) { sender.sendMessage("You do not have permission."); return true; }
        if (args.length == 2 && args[0].equalsIgnoreCase("confirm")) { sender.sendMessage("Confirmation: " + confirmations.confirm(player.getUniqueId(), args[1])); return true; }
        if (args.length >= 3 && args[0].equalsIgnoreCase("hardcore") && args[1].equalsIgnoreCase("revive")) {
            UUID target; try { target = UUID.fromString(args[2]); } catch (IllegalArgumentException e) { sender.sendMessage("Target must be a UUID."); return true; }
            String token = confirmations.issue(player.getUniqueId(), "HARDCORE_REVIVE", target.toString(), "obsidian-gate", "admin command", Duration.ofSeconds(120), () -> hardcore.revive(player.getUniqueId(), target, season, "admin command", UUID.randomUUID().toString()));
            sender.sendMessage("Dangerous action pending. Confirm with /nxlifecycle confirm " + token); return true;
        }
        if (args.length >= 2 && args[0].equalsIgnoreCase("quarry") && args[1].equalsIgnoreCase("reset")) {
            String token = confirmations.issue(player.getUniqueId(), "QUARRY_RESET", "quarry", "quarry", "admin command", Duration.ofSeconds(120), () -> quarry.resetAsync(UUID.randomUUID().toString()));
            sender.sendMessage("Destructive reset pending. Confirm with /nxlifecycle confirm " + token); return true;
        }
        sender.sendMessage("Usage: /nxlifecycle hardcore revive <uuid> | quarry reset | confirm <token>"); return true;
    }
}
