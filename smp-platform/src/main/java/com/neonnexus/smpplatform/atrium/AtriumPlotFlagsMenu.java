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
        Inventory menu = org.bukkit.Bukkit.createInventory(null, 27, "Atrium Plot Settings Guide");
        menu.setItem(10, card(Material.BRICKS, "§bBuilding", "Can visitors place or break blocks?", "PlotSquared flag: use or build"));
        menu.setItem(11, card(Material.CHEST, "§bContainers & doors", "Can visitors open chests, doors and buttons?", "PlotSquared flag: use"));
        menu.setItem(12, card(Material.IRON_SWORD, "§cPlayer combat", "Can players damage one another in this plot?", "PlotSquared flag: pvp"));
        menu.setItem(13, card(Material.CREEPER_HEAD, "§6Explosions", "Can TNT, creepers and other explosions affect the plot?", "PlotSquared flag: explosion"));
        menu.setItem(14, card(Material.ZOMBIE_HEAD, "§aMob spawning", "Can hostile or passive mobs spawn here?", "PlotSquared flag: mob-spawning"));
        menu.setItem(15, card(Material.FLINT_AND_STEEL, "§6Fire spread", "Can fire spread between blocks?", "PlotSquared flag: fire-spread"));
        menu.setItem(16, card(Material.REDSTONE, "§cRedstone", "Can visitors operate redstone components?", "PlotSquared flag: use-redstone"));
        menu.setItem(22, card(Material.KNOWLEDGE_BOOK, "§dChanging a setting", "Plot owners use the PlotSquared commands available to them.", "Staff use the Admin → Atrium controls for advanced flags."));
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
