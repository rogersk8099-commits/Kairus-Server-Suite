package network.neonnexus.smp.admin.command;

import net.kyori.adventure.text.minimessage.MiniMessage;
import network.neonnexus.smp.admin.domain.AdminAction;
import network.neonnexus.smp.admin.gui.AdminGui;
import network.neonnexus.smp.admin.player.PlayerDirectory;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

public final class SmpAdminCommand implements CommandExecutor, TabCompleter {
    private final AdminGui gui;
    private static final MiniMessage MM = MiniMessage.miniMessage();
    public SmpAdminCommand(AdminGui gui) { this.gui = gui; }

    @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) { sender.sendMessage("This GUI command must be used by a player."); return true; }
        if (!player.hasPermission("smpplatform.admin")) { player.sendMessage(MM.deserialize("<red>You lack <white>smpplatform.admin</white>.")); return true; }
        if (args.length == 0) { gui.openHome(player); return true; }
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "players" -> { gui.openPlayers(player, PlayerDirectory.PlayerFilter.ONLINE, args.length > 1 ? String.join(" ", Arrays.copyOfRange(args, 1, args.length)) : "", 0); return true; }
            case "worlds" -> { gui.openWorlds(player); return true; }
            case "monitor" -> { gui.openMonitor(player); return true; }
            case "action" -> { dispatchAction(player, args); return true; }
            default -> { player.sendMessage(MM.deserialize("<yellow>Usage: <white>/smpadmin [players [query]|worlds|monitor|action <action> <player> [value] [--reason <reason>]]")); return true; }
        }
    }
    private void dispatchAction(Player player, String[] args) {
        if (args.length < 3) { player.sendMessage(MM.deserialize("<yellow>Usage: <white>/smpadmin action <action> <player> [value] [--reason <reason>]")); return; }
        AdminAction action = AdminAction.parse(args[1]).orElse(null);
        if (action == null) { player.sendMessage(MM.deserialize("<red>Unknown action. Use names such as <white>heal</white>, <white>temp_mute</white>, or <white>send_world</white>.")); return; }
        String[] tail = Arrays.copyOfRange(args, 3, args.length); String joined = String.join(" ", tail); String value = joined; String reason = "";
        int marker = joined.indexOf("--reason "); if (marker >= 0) { value = joined.substring(0, marker).trim(); reason = joined.substring(marker + 9).trim(); }
        gui.commandAction(player, action, args[2], value, reason);
    }
    @Override public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) return prefix(args[0], List.of("players", "worlds", "monitor", "action"));
        if (args.length == 2 && args[0].equalsIgnoreCase("action")) return prefix(args[1], Arrays.stream(AdminAction.values()).map(a -> a.name().toLowerCase(Locale.ROOT)).toList());
        return List.of();
    }
    private List<String> prefix(String input, List<String> options) { String lower = input.toLowerCase(Locale.ROOT); return options.stream().filter(s -> s.startsWith(lower)).toList(); }
}
