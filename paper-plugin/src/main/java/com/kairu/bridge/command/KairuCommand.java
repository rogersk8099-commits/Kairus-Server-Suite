package com.kairu.bridge.command;

import com.kairu.bridge.KairuBridgePlugin;
import com.kairu.bridge.payload.Payloads;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

public final class KairuCommand implements CommandExecutor, TabCompleter {
    private final KairuBridgePlugin plugin;
    public KairuCommand(KairuBridgePlugin plugin) { this.plugin = plugin; }

    @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) { help(sender, label); return true; }
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "link" -> link(sender, label, args);
            case "status" -> status(sender);
            case "reload" -> reload(sender);
            case "sync" -> sync(sender, label, args);
            case "maintenance" -> maintenance(sender, label, args);
            default -> help(sender, label);
        }
        return true;
    }

    private void link(CommandSender sender, String label, String[] args) {
        if (!sender.hasPermission("kairu.link")) { denied(sender); return; }
        if (!(sender instanceof Player player)) { sender.sendMessage(error("Only an in-game player can link an account.")); return; }
        if (args.length != 2) { sender.sendMessage(error("Usage: /" + label + " link <code>")); return; }
        String code = args[1];
        try {
            Payloads.LinkCompletion request = new Payloads.LinkCompletion(code, player.getUniqueId(), player.getName(), plugin.bedrockXuid(player));
            sender.sendMessage(ChatColor.GRAY + "Kairu: completing your Discord link…");
            plugin.completeLink(request, player);
        } catch (IllegalArgumentException exception) { sender.sendMessage(error("That link code is not valid.")); }
    }

    private void status(CommandSender sender) {
        if (!sender.hasPermission("kairu.status")) { denied(sender); return; }
        sender.sendMessage(ChatColor.AQUA + "KairuBridge " + plugin.getDescription().getVersion());
        sender.sendMessage(ChatColor.GRAY + "Configuration: " + (plugin.isConfigured() ? ChatColor.GREEN + "ready" : ChatColor.RED + "needs setup"));
        sender.sendMessage(ChatColor.GRAY + "Last heartbeat: " + plugin.heartbeatStatus());
        sender.sendMessage(ChatColor.GRAY + "Last command poll: " + plugin.commandPollStatus());
        sender.sendMessage(ChatColor.GRAY + "Integrations: " + plugin.integrationSummary());
    }

    private void reload(CommandSender sender) {
        if (!sender.hasPermission("kairu.admin")) { denied(sender); return; }
        if (plugin.reloadBridge()) sender.sendMessage(ChatColor.GREEN + "KairuBridge configuration reloaded.");
        else sender.sendMessage(error("Reload failed. Check the console; existing configuration remains active."));
    }

    private void sync(CommandSender sender, String label, String[] args) {
        if (!sender.hasPermission("kairu.admin")) { denied(sender); return; }
        if (args.length != 2) { sender.sendMessage(error("Usage: /" + label + " sync <player>")); return; }
        Player target = plugin.getServer().getPlayerExact(args[1]);
        if (target == null) { sender.sendMessage(error("That player must be online to synchronize.")); return; }
        plugin.syncPlayer(target, sender);
    }

    private void maintenance(CommandSender sender, String label, String[] args) {
        if (!sender.hasPermission("kairu.admin")) { denied(sender); return; }
        if (args.length < 2) { sender.sendMessage(error("Usage: /" + label + " maintenance <message>")); return; }
        String message = String.join(" ", Arrays.copyOfRange(args, 1, args.length)).trim();
        if (message.isEmpty() || message.codePointCount(0, message.length()) > 240) { sender.sendMessage(error("Maintenance message must contain 1 to 240 characters.")); return; }
        plugin.notifyMaintenance(message, true);
        sender.sendMessage(ChatColor.GREEN + "Kairu: maintenance notice queued.");
    }

    private void help(CommandSender sender, String label) { sender.sendMessage(ChatColor.AQUA + "KairuBridge: /" + label + " link <code>, /" + label + " status, /" + label + " reload, /" + label + " sync <player>, /" + label + " maintenance <message>"); }
    private static void denied(CommandSender sender) { sender.sendMessage(error("You do not have permission.")); }
    private static String error(String text) { return ChatColor.RED + "Kairu: " + text; }

    @Override public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) return Arrays.asList("link", "status", "reload", "sync", "maintenance").stream().filter(x -> x.startsWith(args[0].toLowerCase(Locale.ROOT))).collect(Collectors.toList());
        if (args.length == 2 && args[0].equalsIgnoreCase("sync") && sender.hasPermission("kairu.admin")) return null;
        return Collections.emptyList();
    }
}
