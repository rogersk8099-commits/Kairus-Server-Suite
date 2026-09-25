package com.neonnexus.smpplatform.protection;

import java.io.File;
import java.io.IOException;
import java.util.*;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

/** Lightweight, server-owned world claims and placed-block protection. Designed for smaller SMP worlds. */
public final class WorldProtectionService {
    private final JavaPlugin plugin; private final File file; private final YamlConfiguration data;
    private final Map<String, List<Claim>> claims = new HashMap<>(); private final Map<BlockKey, UUID> placed = new HashMap<>();
    public WorldProtectionService(JavaPlugin plugin) {
        this.plugin = plugin; this.file = new File(plugin.getDataFolder(), "claims.yml");
        if (!file.exists()) plugin.saveResource("claims.yml", false);
        this.data = YamlConfiguration.loadConfiguration(file); load();
    }
    public boolean enabled(String world) { return data.getBoolean("protection.worlds." + world + ".enabled", false); }
    public boolean trackPlaced(String world) { return enabled(world) && data.getBoolean("protection.worlds." + world + ".placed-block-protection", true); }
    public int defaultRadius() { return Math.max(1, data.getInt("protection.claims.default-radius", 32)); }
    public int maxRadius() { return Math.max(defaultRadius(), data.getInt("protection.claims.max-radius", 64)); }
    public void setEnabled(String world, boolean value) { data.set("protection.worlds." + world + ".enabled", value); save(); }
    public void setTrackPlaced(String world, boolean value) { data.set("protection.worlds." + world + ".placed-block-protection", value); save(); }
    public boolean canBuild(Player player, Location location) { Claim claim = claimAt(location); return claim == null || claim.allows(player.getUniqueId()) || bypass(player); }
    public boolean canUse(Player player, Location location) { return canBuild(player, location); }
    public boolean canBreakPlaced(Player player, Location location) { UUID owner = placed.get(BlockKey.of(location)); return owner == null || owner.equals(player.getUniqueId()) || canBuild(player, location) || bypass(player); }
    public boolean explosionProtected(Location location) { return enabled(location.getWorld().getName()) && (claimAt(location) != null || placed.containsKey(BlockKey.of(location))); }
    public void recordPlaced(Player player, Location location) { if (trackPlaced(location.getWorld().getName())) { placed.put(BlockKey.of(location), player.getUniqueId()); save(); } }
    public void removePlaced(Location location) { if (placed.remove(BlockKey.of(location)) != null) save(); }
    public Claim create(Player owner, int radius) {
        Location at = owner.getLocation(); String world = at.getWorld().getName();
        if (!enabled(world)) throw new IllegalArgumentException("Claims are not enabled in this world.");
        if (radius < 1 || radius > maxRadius()) throw new IllegalArgumentException("Claim radius must be between 1 and " + maxRadius() + " blocks.");
        Claim claim = new Claim(UUID.randomUUID(), world, owner.getUniqueId(), at.getBlockX() - radius, at.getBlockX() + radius, at.getBlockZ() - radius, at.getBlockZ() + radius, new HashSet<>());
        if (claims.getOrDefault(world, List.of()).stream().anyMatch(existing -> existing.overlaps(claim))) throw new IllegalArgumentException("That land overlaps an existing claim.");
        claims.computeIfAbsent(world, ignored -> new ArrayList<>()).add(claim); save(); return claim;
    }
    public Claim ownClaimAt(Player player) { Claim claim = claimAt(player.getLocation()); if (claim == null || (!claim.owner.equals(player.getUniqueId()) && !bypass(player))) throw new IllegalArgumentException("Stand inside your own claim first."); return claim; }
    public void trust(Player owner, OfflinePlayer target) { Claim claim = ownClaimAt(owner); if (target.getUniqueId().equals(claim.owner)) throw new IllegalArgumentException("The owner already has access."); claim.members.add(target.getUniqueId()); save(); }
    public void untrust(Player owner, OfflinePlayer target) { ownClaimAt(owner).members.remove(target.getUniqueId()); save(); }
    public void abandon(Player owner) { Claim claim = ownClaimAt(owner); claims.getOrDefault(claim.world, List.of()).remove(claim); save(); }
    public String describe(Player player) { Claim claim = claimAt(player.getLocation()); return claim == null ? "This land is unclaimed." : "Claim " + claim.id + " • owner " + ownerName(claim.owner) + " • " + (claim.maxX-claim.minX+1) + "×" + (claim.maxZ-claim.minZ+1) + " blocks • members " + claim.members.size(); }
    private String ownerName(UUID id) { OfflinePlayer player = plugin.getServer().getOfflinePlayer(id); return player.getName() == null ? id.toString() : player.getName(); }
    private boolean bypass(Player player) { return player.hasPermission("smpplatform.protection.bypass"); }
    private Claim claimAt(Location location) { if (location == null || location.getWorld() == null || !enabled(location.getWorld().getName())) return null; return claims.getOrDefault(location.getWorld().getName(), List.of()).stream().filter(claim -> claim.contains(location.getBlockX(), location.getBlockZ())).findFirst().orElse(null); }
    private void load() {
        var section = data.getConfigurationSection("claims"); if (section == null) return;
        for (String id : section.getKeys(false)) { String base = "claims." + id; try { UUID claimId = UUID.fromString(id), owner = UUID.fromString(data.getString(base + ".owner")); String world = data.getString(base + ".world"); Set<UUID> members = new HashSet<>(); for (String member : data.getStringList(base + ".members")) members.add(UUID.fromString(member)); Claim claim = new Claim(claimId, world, owner, data.getInt(base+".min-x"), data.getInt(base+".max-x"), data.getInt(base+".min-z"), data.getInt(base+".max-z"), members); claims.computeIfAbsent(world, ignored -> new ArrayList<>()).add(claim); } catch (Exception ignored) { plugin.getLogger().warning("Skipped malformed claim " + id); } }
        var placedSection = data.getConfigurationSection("placed-blocks"); if (placedSection == null) return;
        for (String key : placedSection.getKeys(false)) try { String[] parts = key.split("_"); placed.put(new BlockKey(parts[0], Integer.parseInt(parts[1]), Integer.parseInt(parts[2]), Integer.parseInt(parts[3])), UUID.fromString(data.getString("placed-blocks." + key))); } catch (Exception ignored) { }
    }
    private void save() { data.set("claims", null); for (List<Claim> list : claims.values()) for (Claim claim : list) { String base="claims."+claim.id; data.set(base+".world",claim.world); data.set(base+".owner",claim.owner.toString()); data.set(base+".min-x",claim.minX); data.set(base+".max-x",claim.maxX); data.set(base+".min-z",claim.minZ); data.set(base+".max-z",claim.maxZ); data.set(base+".members",claim.members.stream().map(UUID::toString).toList()); } data.set("placed-blocks", null); for (var entry : placed.entrySet()) data.set("placed-blocks."+entry.getKey().yamlKey(), entry.getValue().toString()); try { data.save(file); } catch (IOException exception) { throw new IllegalStateException("Could not save claims.yml", exception); } }
    public record Claim(UUID id, String world, UUID owner, int minX, int maxX, int minZ, int maxZ, Set<UUID> members) { boolean contains(int x, int z) { return x >= minX && x <= maxX && z >= minZ && z <= maxZ; } boolean overlaps(Claim other) { return minX <= other.maxX && maxX >= other.minX && minZ <= other.maxZ && maxZ >= other.minZ; } boolean allows(UUID id) { return owner.equals(id) || members.contains(id); } }
    private record BlockKey(String world, int x, int y, int z) { static BlockKey of(Location value) { return new BlockKey(value.getWorld().getName(), value.getBlockX(), value.getBlockY(), value.getBlockZ()); } String yamlKey() { return world + "_" + x + "_" + y + "_" + z; } }
}
