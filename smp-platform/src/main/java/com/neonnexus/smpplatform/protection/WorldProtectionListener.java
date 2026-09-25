package com.neonnexus.smpplatform.protection;

import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.player.PlayerInteractEvent;

/** Covers normal building, containers and explosion block lists (including TNT). */
public final class WorldProtectionListener implements Listener {
    private final WorldProtectionService protection;
    public WorldProtectionListener(WorldProtectionService protection) { this.protection = protection; }
    @EventHandler(ignoreCancelled = true) public void place(BlockPlaceEvent event) { if (!protection.canBuild(event.getPlayer(), event.getBlockPlaced().getLocation())) { event.setCancelled(true); deny(event.getPlayer()); return; } protection.recordPlaced(event.getPlayer(), event.getBlockPlaced().getLocation()); }
    @EventHandler(ignoreCancelled = true) public void breakBlock(BlockBreakEvent event) { if (!protection.canBuild(event.getPlayer(), event.getBlock().getLocation()) || !protection.canBreakPlaced(event.getPlayer(), event.getBlock().getLocation())) { event.setCancelled(true); deny(event.getPlayer()); return; } protection.removePlaced(event.getBlock().getLocation()); }
    @EventHandler(ignoreCancelled = true) public void interact(PlayerInteractEvent event) { Block block = event.getClickedBlock(); if (block != null && !protection.canUse(event.getPlayer(), block.getLocation())) { event.setCancelled(true); deny(event.getPlayer()); } }
    @EventHandler(ignoreCancelled = true) public void entityExplode(EntityExplodeEvent event) { event.blockList().removeIf(block -> protection.explosionProtected(block.getLocation())); }
    @EventHandler(ignoreCancelled = true) public void blockExplode(BlockExplodeEvent event) { event.blockList().removeIf(block -> protection.explosionProtected(block.getLocation())); }
    private static void deny(org.bukkit.entity.Player player) { player.sendActionBar(net.kyori.adventure.text.Component.text("This build is protected.")); }
}
