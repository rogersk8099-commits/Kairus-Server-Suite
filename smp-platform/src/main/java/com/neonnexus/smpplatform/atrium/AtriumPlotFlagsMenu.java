package com.neonnexus.smpplatform.atrium;

import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;

/** Player-facing explanation layer. PlotSquared remains the authority for the actual flag values. */
public final class AtriumPlotFlagsMenu {
    private AtriumPlotFlagsMenu() { }

    public static void open(Player player) {
        Inventory menu = org.bukkit.Bukkit.createInventory(null, 54, "Atrium Plot Settings Guide");
        menu.setItem(10, card(Material.IRON_PICKAXE, "§bGuest block breaking", "Lets guests break only materials you list.", "PlotSquared: break (material list)"));
        menu.setItem(11, card(Material.BRICKS, "§bGuest block placing", "Lets guests place only materials you list.", "PlotSquared: place (material list)"));
        menu.setItem(12, card(Material.CHEST, "§bContainers & doors", "Lets guests use only blocks you list.", "PlotSquared: use (material list)"));
        menu.setItem(13, card(Material.IRON_SWORD, "§cPlayer combat", "Allow or block players damaging one another.", "PlotSquared: pvp (on/off)"));
        menu.setItem(14, card(Material.CREEPER_HEAD, "§6Explosions", "Allow or block explosions in the plot.", "PlotSquared: explosion (on/off)"));
        menu.setItem(15, card(Material.FEATHER, "§bFlight", "Allow or block flight in the plot.", "PlotSquared: fly (on/off)"));
        menu.setItem(16, card(Material.REDSTONE, "§cRedstone", "Allow or block redstone behaviour.", "PlotSquared: redstone (on/off)"));
        menu.setItem(19, card(Material.OAK_DOOR, "§aGuest visits", "Allow or block visitors who are not plot members.", "PlotSquared: untrusted-visit (on/off)"));
        menu.setItem(20, card(Material.FLINT_AND_STEEL, "§6Block ignition", "Allow or block blocks being set on fire.", "PlotSquared: block-ignition (on/off)"));
        menu.setItem(21, card(Material.CAMPFIRE, "§6Block burning", "Allow or block blocks burning in the plot.", "PlotSquared: block-burn (on/off)"));
        menu.setItem(22, card(Material.NAME_TAG, "§dWelcome message", "Text shown when a player enters the plot.", "PlotSquared: greeting (text)"));
        menu.setItem(23, card(Material.WRITABLE_BOOK, "§dGoodbye message", "Text shown when a player leaves the plot.", "PlotSquared: farewell (text)"));
        menu.setItem(24, card(Material.CLOCK, "§eLocal plot time", "A simulated time that applies inside the plot.", "PlotSquared: time (number)"));
        menu.setItem(25, card(Material.COMPASS, "§ePlot gamemode", "The gamemode that applies inside the plot.", "PlotSquared: gamemode (choice)"));
        menu.setItem(40, card(Material.KNOWLEDGE_BOOK, "§dChanging a setting", "PlotSquared always checks your ownership and permissions.", "Use the Kairu client for supported switches; typed settings remain command/staff managed."));
        player.openInventory(menu);
    }

    private static ItemStack card(Material material, String title, String meaning, String technical) {
        ItemStack stack = new ItemStack(material);
        ItemMeta meta = stack.getItemMeta();
        meta.setDisplayName(title);
        meta.setLore(List.of("§7" + meaning, "", "§8" + technical));
        stack.setItemMeta(meta);
        return stack;
    }
}
