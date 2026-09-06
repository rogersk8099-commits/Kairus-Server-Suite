package gg.neonnexus.smpplatform.lifecycle.paper;

import gg.neonnexus.smpplatform.lifecycle.quarry.BackupResult;
import gg.neonnexus.smpplatform.lifecycle.quarry.QuarryGateway;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

/** Paper + filesystem implementation; Bukkit calls are synchronized to the primary thread, copying occurs on the reset worker. */
public final class PaperQuarryGateway implements QuarryGateway {
    private final JavaPlugin plugin;
    private final String quarryWorld;
    private final String fallbackWorld;
    private final Path backupRoot;
    private final Path worldContainer;
    private volatile boolean entryLocked;

    public PaperQuarryGateway(JavaPlugin plugin, String quarryWorld, String fallbackWorld, Path backupRoot) {
        this.plugin = plugin; this.quarryWorld = quarryWorld; this.fallbackWorld = fallbackWorld; this.backupRoot = backupRoot;
        this.worldContainer = plugin.getServer().getWorldContainer().toPath();
    }
    public boolean isEntryLocked() { return entryLocked; }
    @Override public boolean isFallbackDestinationAvailable() { return sync(() -> Bukkit.getWorld(fallbackWorld) != null); }
    @Override public boolean lockEntry() { entryLocked = true; return true; }
    @Override public void unlockEntry() { entryLocked = false; }
    @Override public Collection<UUID> playersInQuarry() { return sync(() -> {
        World world = Bukkit.getWorld(quarryWorld);
        return world == null ? List.<UUID>of() : world.getPlayers().stream().map(Player::getUniqueId).toList();
    }); }
    @Override public boolean teleportToFallback(UUID playerId) { return sync(() -> {
        Player player = Bukkit.getPlayer(playerId); World destination = Bukkit.getWorld(fallbackWorld);
        return player == null || destination != null && player.teleport(destination.getSpawnLocation());
    }); }
    @Override public boolean saveQuarry() { return sync(() -> { World world = Bukkit.getWorld(quarryWorld); if (world == null) return false; world.save(); return true; }); }
    @Override public BackupResult createBackup() {
        try {
            Path source = worldContainer.resolve(quarryWorld);
            if (!Files.isDirectory(source) || !Files.isRegularFile(source.resolve("level.dat"))) return BackupResult.failed("source world or level.dat is missing");
            Path destination = backupRoot.resolve(quarryWorld + "-" + System.currentTimeMillis());
            copyTree(source, destination);
            boolean verified = Files.isRegularFile(destination.resolve("level.dat")) && Files.size(destination.resolve("level.dat")) > 0;
            return new BackupResult(destination, verified, verified ? "level.dat verified" : "backup level.dat verification failed");
        } catch (IOException exception) { return BackupResult.failed(exception.getClass().getSimpleName() + ": " + exception.getMessage()); }
    }
    @Override public boolean unloadQuarry() { return sync(() -> Bukkit.unloadWorld(quarryWorld, true)); }
    @Override public boolean regenerateQuarry() {
        try { deleteTree(worldContainer.resolve(quarryWorld)); return true; }
        catch (IOException exception) { return false; }
    }
    @Override public boolean loadQuarry() { return sync(() -> Bukkit.createWorld(new WorldCreator(quarryWorld)) != null); }
    @Override public boolean validateQuarry() { return sync(() -> {
        World world = Bukkit.getWorld(quarryWorld);
        return world != null && Files.isRegularFile(world.getWorldFolder().toPath().resolve("level.dat"));
    }); }
    private <T> T sync(Callable<T> operation) {
        try {
            if (Bukkit.isPrimaryThread()) return operation.call();
            return Bukkit.getScheduler().callSyncMethod(plugin, operation).get();
        } catch (Exception exception) { throw new IllegalStateException("Paper operation failed", exception); }
    }
    private static void copyTree(Path source, Path destination) throws IOException {
        try (var paths = Files.walk(source)) {
            for (Path from : paths.toList()) { Path to = destination.resolve(source.relativize(from)); if (Files.isDirectory(from)) Files.createDirectories(to); else Files.copy(from, to, StandardCopyOption.COPY_ATTRIBUTES); }
        }
    }
    private static void deleteTree(Path root) throws IOException {
        if (!Files.exists(root)) return;
        try (var paths = Files.walk(root)) { for (Path path : paths.sorted(java.util.Comparator.reverseOrder()).toList()) Files.delete(path); }
    }
}
