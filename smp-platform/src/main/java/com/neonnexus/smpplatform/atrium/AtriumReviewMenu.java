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

/** Bedrock-friendly chest GUI for staff review. Every state mutation is delegated to AtriumSubmissionService. */
public final class AtriumReviewMenu implements Listener {
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm 'UTC'").withZone(ZoneOffset.UTC);
    private final SMPPlatform plugin;
    public AtriumReviewMenu(SMPPlatform plugin) { this.plugin = plugin; }

    public void openQueue(Player staff) {
        if (!staff.hasPermission("smpplatform.admin.creative")) { staff.sendMessage("§cYou do not have permission to review Atrium submissions."); return; }
        AtriumSubmissionService service = plugin.atriumSubmissionService();
        if (service == null) { staff.sendMessage("§cThe build review queue requires a healthy PostgreSQL connection."); return; }
        staff.sendMessage("§dLoading Atrium review queue…"); UUID staffId = staff.getUniqueId();
        plugin.io().execute(() -> {
            try {
                List<AtriumSubmissionService.PendingSubmission> pending = service.pending(45);
                Bukkit.getScheduler().runTask(plugin, () -> { Player online = Bukkit.getPlayer(staffId); if (online != null) showQueue(online, pending); });
            } catch (RuntimeException exception) { Bukkit.getScheduler().runTask(plugin, () -> { Player online = Bukkit.getPlayer(staffId); if (online != null) online.sendMessage("§c" + exception.getMessage()); }); }
        });
    }

    private void showQueue(Player player, List<AtriumSubmissionService.PendingSubmission> pending) {
        QueueHolder holder = new QueueHolder(pending); Inventory inventory = Bukkit.createInventory(holder, 54, "Atrium build review"); holder.inventory = inventory;
        if (pending.isEmpty()) inventory.setItem(22, item(Material.LIME_DYE, "§aNo submissions waiting", List.of("§7The Atrium review queue is clear.")));
        for (int slot = 0; slot < pending.size(); slot++) {
            AtriumSubmissionService.PendingSubmission submission = pending.get(slot);
            inventory.setItem(slot, item(Material.MAP, "§d" + submission.title(), List.of("§7Plot: §f" + submission.plotId(), "§7Submitted: §f" + TIME.format(submission.submittedAt()), "§eClick to review")));
        }
        inventory.setItem(49, item(Material.BARRIER, "§cClose", List.of())); player.openInventory(inventory);
    }

    private void showSubmission(Player player, AtriumSubmissionService.PendingSubmission submission) {
        SubmissionHolder holder = new SubmissionHolder(submission); Inventory inventory = Bukkit.createInventory(holder, 27, "Review: " + clip(submission.title())); holder.inventory = inventory;
        inventory.setItem(4, item(Material.MAP, "§d" + submission.title(), List.of("§7Plot: §f" + submission.plotId(), "§7Submitted: §f" + TIME.format(submission.submittedAt()))));
        inventory.setItem(11, item(Material.CLOCK, "§eMark under review", List.of("§7Keeps it in the queue.")));
        inventory.setItem(13, item(Material.NETHER_STAR, "§aFeature build", List.of("§7Adds it to the featured showcase.")));
        inventory.setItem(15, item(Material.BARRIER, "§cReject build", List.of("§7Final decision.")));
        inventory.setItem(22, item(Material.ARROW, "§7Back to queue", List.of())); player.openInventory(inventory);
    }

    @EventHandler(ignoreCancelled = true) public void click(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof Holder holder)) return;
        event.setCancelled(true); if (!(event.getWhoClicked() instanceof Player player) || !player.hasPermission("smpplatform.admin.creative")) return;
        if (holder instanceof QueueHolder queue) { if (event.getRawSlot() >= 0 && event.getRawSlot() < queue.pending.size()) showSubmission(player, queue.pending.get(event.getRawSlot())); return; }
        SubmissionHolder submission = (SubmissionHolder) holder;
        if (event.getRawSlot() == 22) { openQueue(player); return; }
        String state = switch (event.getRawSlot()) { case 11 -> "UNDER_REVIEW"; case 13 -> "FEATURED"; case 15 -> "REJECTED"; default -> null; };
        if (state == null) return; review(player, submission.submission.id(), state);
    }

    private void review(Player staff, UUID submissionId, String state) {
        AtriumSubmissionService service = plugin.atriumSubmissionService(); if (service == null) { staff.sendMessage("§cThe build review queue requires a healthy PostgreSQL connection."); return; }
        UUID staffId = staff.getUniqueId(); staff.closeInventory(); staff.sendMessage("§dSaving Atrium review…");
        plugin.io().execute(() -> {
            try {
                AtriumSubmissionService.Submission reviewed = plugin.reviewAtriumBuild(staffId, submissionId, state, "Reviewed from the in-game staff menu.");
                Bukkit.getScheduler().runTask(plugin, () -> { Player online = Bukkit.getPlayer(staffId); if (online != null) { online.sendMessage("§a" + reviewed.title() + " → " + reviewed.state()); plugin.publishBridgeEvent("BUILD_" + reviewed.state(), "atrium", online, "Build review: " + reviewed.title(), java.util.Map.of("submissionId", reviewed.id().toString(), "state", reviewed.state())); } });
            } catch (RuntimeException exception) { Bukkit.getScheduler().runTask(plugin, () -> { Player online = Bukkit.getPlayer(staffId); if (online != null) online.sendMessage("§c" + exception.getMessage()); }); }
        });
    }

    private static ItemStack item(Material material, String name, List<String> lore) { ItemStack stack = new ItemStack(material); ItemMeta meta = stack.getItemMeta(); meta.setDisplayName(name); meta.setLore(lore); stack.setItemMeta(meta); return stack; }
    private static String clip(String value) { return value.length() <= 20 ? value : value.substring(0, 20); }
    private sealed interface Holder extends InventoryHolder permits QueueHolder, SubmissionHolder { }
    private static final class QueueHolder implements Holder { private final List<AtriumSubmissionService.PendingSubmission> pending; private Inventory inventory; QueueHolder(List<AtriumSubmissionService.PendingSubmission> pending) { this.pending = pending; } @Override public Inventory getInventory() { return inventory; } }
    private static final class SubmissionHolder implements Holder { private final AtriumSubmissionService.PendingSubmission submission; private Inventory inventory; SubmissionHolder(AtriumSubmissionService.PendingSubmission submission) { this.submission = submission; } @Override public Inventory getInventory() { return inventory; } }
}
