package gg.neonnexus.smpplatform.lifecycle.paper;

import com.neonnexus.smpplatform.multiverse.MultiverseWorldAdapter;
import com.neonnexus.smpplatform.world.WorldDefinition;
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
    private final Path templateRoot;
    private final Path worldContainer;
    private final int backupRetention;
    private final MultiverseWorldAdapter multiverse;
    private final WorldDefinition quarryDefinition;
    /** Captured before unload; Multiverse worlds are not always direct children of the server root. */
    private volatile Path activeQuarryFolder;
    private volatile boolean entryLocked;

    public PaperQuarryGateway(JavaPlugin plugin, String quarryWorld, String fallbackWorld, Path backupRoot) {
        this(plugin, quarryWorld, fallbackWorld, backupRoot, 24);
    }
    public PaperQuarryGateway(JavaPlugin plugin, String quarryWorld, String fallbackWorld, Path backupRoot, int backupRetention) {
        this(plugin, quarryWorld, fallbackWorld, backupRoot, backupRetention, null, null);
    }
    /**
     * A Quarry can be a Multiverse dimension instead of a direct child of the Paper world
     * container.  In that case Multiverse remains responsible for unloading and reloading the
     * registered folder while this gateway restores the contents of that exact folder.
     */
    public PaperQuarryGateway(JavaPlugin plugin, String quarryWorld, String fallbackWorld, Path backupRoot, int backupRetention,
                              MultiverseWorldAdapter multiverse, WorldDefinition quarryDefinition) {
        this.plugin = plugin; this.quarryWorld = quarryWorld; this.fallbackWorld = fallbackWorld; this.backupRoot = backupRoot;
        this.templateRoot = plugin.getDataFolder().toPath().resolve("quarry-template");
        this.worldContainer = plugin.getServer().getWorldContainer().toPath();
        this.backupRetention = Math.max(1, backupRetention);
        this.multiverse = multiverse;
        this.quarryDefinition = quarryDefinition;
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
    @Override public boolean saveQuarry() { return sync(() -> { World world = Bukkit.getWorld(quarryWorld); if (world == null) return false; activeQuarryFolder = world.getWorldFolder().toPath(); world.save(); return true; }); }
    @Override public BackupResult createBackup() {
        try {
            Path source = sync(() -> {
                World world = Bukkit.getWorld(quarryWorld);
                if (world != null) activeQuarryFolder = world.getWorldFolder().toPath();
                return activeQuarryFolder;
            });
            if (source == null || !Files.isDirectory(source) || !Files.isRegularFile(source.resolve("level.dat"))) return BackupResult.failed("active Quarry world folder or level.dat is missing");
            Path destination = backupRoot.resolve(quarryWorld + "-" + System.currentTimeMillis());
            copyTree(source, destination);
            boolean verified = Files.isRegularFile(destination.resolve("level.dat")) && Files.size(destination.resolve("level.dat")) > 0;
            if (verified) pruneOldBackups();
            return new BackupResult(destination, verified, verified ? "level.dat verified" : "backup level.dat verification failed");
        } catch (IOException exception) { return BackupResult.failed(exception.getClass().getSimpleName() + ": " + exception.getMessage()); }
    }
    @Override public boolean unloadQuarry() { return sync(() ->
            multiverse != null && quarryDefinition != null && multiverse.available()
                    ? multiverse.unload(quarryDefinition, true)
                    : Bukkit.unloadWorld(quarryWorld, true)); }
    @Override public boolean regenerateQuarry() {
        Path target = activeQuarryFolder == null ? worldContainer.resolve(quarryWorld) : activeQuarryFolder;
        try {
            if (!Files.isDirectory(templateRoot) || !Files.isRegularFile(templateRoot.resolve("level.dat"))) {
                plugin.getLogger().warning("Quarry reset stopped safely: template missing level.dat at " + templateRoot);
                return false;
            }
            if (templateRoot.toRealPath().equals(target.toRealPath())) {
                plugin.getLogger().warning("Quarry reset stopped safely: quarry-template must be a separate untouched copy.");
                return false;
            }
            deleteTree(target);
            copyTree(templateRoot, target);
            if (!Files.isRegularFile(target.resolve("level.dat")) || Files.size(target.resolve("level.dat")) == 0) {
                plugin.getLogger().warning("Quarry reset stopped safely: restored template level.dat could not be verified.");
                return false;
            }
            return true;
        } catch (IOException exception) {
            plugin.getLogger().warning("Quarry template restore failed: " + exception.getClass().getSimpleName() + ": " + exception.getMessage());
            return false;
        }
    }
    @Override public boolean loadQuarry() { return sync(() ->
            multiverse != null && quarryDefinition != null && multiverse.available()
                    ? multiverse.ensureLoaded(quarryDefinition)
                    : Bukkit.createWorld(new WorldCreator(quarryWorld)) != null); }
    @Override public boolean validateQuarry() { return sync(() -> {
        World world = Bukkit.getWorld(quarryWorld);
        if (world != null) activeQuarryFolder = world.getWorldFolder().toPath();
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
    private void pruneOldBackups() throws IOException {
        if (!Files.isDirectory(backupRoot)) return;
        try (var entries = Files.list(backupRoot)) {
            List<Path> backups = entries.filter(Files::isDirectory).filter(path -> path.getFileName().toString().startsWith(quarryWorld + "-"))
                    .sorted(java.util.Comparator.comparing(path -> path.getFileName().toString())).toList();
            for (int index = 0; index < Math.max(0, backups.size() - backupRetention); index++) deleteTree(backups.get(index));
        }
    }
}
