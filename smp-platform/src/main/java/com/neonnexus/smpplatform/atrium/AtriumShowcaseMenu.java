package com.neonnexus.smpplatform.atrium;

import com.neonnexus.smpplatform.SMPPlatform;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;

/** Player-visible featured Atrium build gallery. A click delegates travel to PlotSquared as the player. */
public final class AtriumShowcaseMenu implements Listener {
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("uuuu-MM-dd 'UTC'").withZone(ZoneOffset.UTC);
    private final SMPPlatform plugin;
    public AtriumShowcaseMenu(SMPPlatform plugin) { this.plugin = plugin; }

    public void open(Player player) {
        AtriumSubmissionService service = plugin.atriumSubmissionService();
        if (service == null) { player.sendMessage("§cFeatured builds require a healthy PostgreSQL connection."); return; }
        UUID playerId = player.getUniqueId(); player.sendMessage("§dLoading featured Atrium builds…");
        plugin.io().execute(() -> {
            try { List<AtriumSubmissionService.FeaturedBuild> builds = service.featured(45); Bukkit.getScheduler().runTask(plugin, () -> { Player online = Bukkit.getPlayer(playerId); if (online != null) show(online, builds); }); }
            catch (RuntimeException exception) { Bukkit.getScheduler().runTask(plugin, () -> { Player online = Bukkit.getPlayer(playerId); if (online != null) online.sendMessage("§c" + exception.getMessage()); }); }
        });
    }

    private void show(Player player, List<AtriumSubmissionService.FeaturedBuild> builds) {
        Holder holder = new Holder(builds); Inventory inventory = Bukkit.createInventory(holder, 54, "Atrium featured builds"); holder.inventory = inventory;
        if (builds.isEmpty()) inventory.setItem(22, item(Material.PAINTING, "§dNo featured builds yet", List.of("§7Staff feature builds after review.")));
        for (int slot = 0; slot < builds.size(); slot++) { AtriumSubmissionService.FeaturedBuild build = builds.get(slot); inventory.setItem(slot, item(Material.NETHER_STAR, "§d" + build.title(), List.of("§7Plot: §f" + build.plotId(), "§7Featured: §f" + TIME.format(build.featuredAt()), "§eClick to visit with PlotSquared"))); }
        inventory.setItem(49, item(Material.BARRIER, "§cClose", List.of())); player.openInventory(inventory);
    }

    @EventHandler(ignoreCancelled = true) public void click(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof Holder holder)) return;
        event.setCancelled(true); if (!(event.getWhoClicked() instanceof Player player)) return;
        int slot = event.getRawSlot(); if (slot < 0 || slot >= holder.builds.size()) return;
        String plotId = holder.builds.get(slot).plotId();
        if (!plotId.matches("[A-Za-z0-9,;:_-]{1,160}")) { player.sendMessage("§cThat featured plot reference is invalid."); return; }
        player.closeInventory(); if (!player.performCommand("plot visit " + plotId)) player.sendMessage("§cPlotSquared could not open that featured build.");
    }

    private static ItemStack item(Material material, String name, List<String> lore) { ItemStack stack = new ItemStack(material); ItemMeta meta = stack.getItemMeta(); meta.setDisplayName(name); meta.setLore(lore); stack.setItemMeta(meta); return stack; }
    private static final class Holder implements InventoryHolder { private final List<AtriumSubmissionService.FeaturedBuild> builds; private Inventory inventory; Holder(List<AtriumSubmissionService.FeaturedBuild> builds) { this.builds = builds; } @Override public Inventory getInventory() { return inventory; } }
}
