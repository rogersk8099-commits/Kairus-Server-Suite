package gg.neonnexus.smpplatform.lifecycle.paper;

import gg.neonnexus.smpplatform.lifecycle.archive.ArchiveTourPolicy;
import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.*;
import org.bukkit.event.entity.*;
import org.bukkit.event.hanging.HangingBreakByEntityEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.*;
import org.bukkit.event.vehicle.VehicleDamageEvent;

/** Event-level archive guard, intentionally independent of Adventure mode and extensible by configured world name. */
public final class ArchiveProtectionListener implements Listener {
    private final ArchiveTourPolicy policy;
    public ArchiveProtectionListener(ArchiveTourPolicy policy) { this.policy = policy; }
    private boolean archive(Location location) { return location != null && location.getWorld() != null && policy.isTourWorld(location.getWorld().getName()); }
    private void cancelIfArchive(org.bukkit.event.Cancellable event, Location location) { if (archive(location)) event.setCancelled(true); }
    @EventHandler(ignoreCancelled = true) public void breakBlock(BlockBreakEvent event) { cancelIfArchive(event, event.getBlock().getLocation()); }
    @EventHandler(ignoreCancelled = true) public void placeBlock(BlockPlaceEvent event) { cancelIfArchive(event, event.getBlockPlaced().getLocation()); }
    @EventHandler(ignoreCancelled = true) public void interact(PlayerInteractEvent event) { Block block = event.getClickedBlock(); if (block != null) cancelIfArchive(event, block.getLocation()); }
    @EventHandler(ignoreCancelled = true) public void openInventory(InventoryOpenEvent event) {
        if (event.getInventory().getLocation() != null) cancelIfArchive(event, event.getInventory().getLocation());
    }
    @EventHandler(ignoreCancelled = true) public void interactEntity(PlayerInteractEntityEvent event) { cancelIfArchive(event, event.getRightClicked().getLocation()); }
    @EventHandler(ignoreCancelled = true) public void damageEntity(EntityDamageByEntityEvent event) { cancelIfArchive(event, event.getEntity().getLocation()); }
    @EventHandler(ignoreCancelled = true) public void entityExplode(EntityExplodeEvent event) { cancelIfArchive(event, event.getLocation()); }
    @EventHandler(ignoreCancelled = true) public void blockExplode(BlockExplodeEvent event) { cancelIfArchive(event, event.getBlock().getLocation()); }
    @EventHandler(ignoreCancelled = true) public void emptyBucket(PlayerBucketEmptyEvent event) { cancelIfArchive(event, event.getBlockClicked().getLocation()); }
    @EventHandler(ignoreCancelled = true) public void fillBucket(PlayerBucketFillEvent event) { cancelIfArchive(event, event.getBlockClicked().getLocation()); }
    @EventHandler(ignoreCancelled = true) public void hanging(HangingBreakByEntityEvent event) { cancelIfArchive(event, event.getEntity().getLocation()); }
    @EventHandler(ignoreCancelled = true) public void armorStand(PlayerArmorStandManipulateEvent event) { cancelIfArchive(event, event.getRightClicked().getLocation()); }
    @EventHandler(ignoreCancelled = true) public void vehicle(VehicleDamageEvent event) { cancelIfArchive(event, event.getVehicle().getLocation()); }
    @EventHandler(ignoreCancelled = true) public void entityChangesBlock(EntityChangeBlockEvent event) { cancelIfArchive(event, event.getBlock().getLocation()); }
    @EventHandler(ignoreCancelled = true) public void blockChanges(EntityBlockFormEvent event) { cancelIfArchive(event, event.getBlock().getLocation()); }
    @EventHandler(ignoreCancelled = true) public void ignite(BlockIgniteEvent event) { cancelIfArchive(event, event.getBlock().getLocation()); }
    @EventHandler(ignoreCancelled = true) public void burn(BlockBurnEvent event) { cancelIfArchive(event, event.getBlock().getLocation()); }
}
