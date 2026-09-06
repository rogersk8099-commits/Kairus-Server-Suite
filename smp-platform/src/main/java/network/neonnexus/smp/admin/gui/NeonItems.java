package network.neonnexus.smp.admin.gui;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;

/** Brand primitives: dark panes, purple navigation, magenta action, electric-blue information. */
public final class NeonItems {
    public static final String DARK = "<dark_gray>", PURPLE = "<dark_purple>", MAGENTA = "<light_purple>", BLUE = "<aqua>", MUTED = "<gray>", SUCCESS = "<green>", WARN = "<yellow>", ERROR = "<red>";
    private static final MiniMessage MM = MiniMessage.miniMessage();
    private NeonItems() { }

    public static ItemStack item(Material material, String name, String... lore) {
        ItemStack stack = new ItemStack(material);
        ItemMeta meta = stack.getItemMeta();
        meta.displayName(MM.deserialize(name).decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false));
        meta.lore(List.of(lore).stream().map(line -> MM.deserialize(line).decoration(net.kyori.adventure.text.format.TextDecoration.ITALIC, false)).toList());
        stack.setItemMeta(meta);
        return stack;
    }
    public static ItemStack filler() { return item(Material.BLACK_STAINED_GLASS_PANE, "<black> "); }
    public static ItemStack home() { return item(Material.NETHER_STAR, BLUE + "<bold>Home", MUTED + "Return to Neon Nexus Admin"); }
    public static ItemStack back() { return item(Material.ARROW, PURPLE + "<bold>Back", MUTED + "Return to previous screen"); }
    public static ItemStack close() { return item(Material.BARRIER, ERROR + "<bold>Close", MUTED + "Close administration"); }
    public static Component title(String text) { return MM.deserialize(PURPLE + "<bold>NEON NEXUS</bold> " + BLUE + text); }
}
