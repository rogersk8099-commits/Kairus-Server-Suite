package com.neonnexus.smpplatform.multiverse;

import com.neonnexus.smpplatform.world.WorldDefinition;
import org.bukkit.Bukkit;
import org.bukkit.Difficulty;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.logging.Logger;

/** Higher-level registry-to-Multiverse bridge. It does not replace Multiverse world lifecycle ownership. */
public final class MultiverseWorldAdapter {
    private final Plugin multiverse;
    private final Logger logger;

    public MultiverseWorldAdapter(PluginManager plugins, Logger logger) {
        this.multiverse = plugins.getPlugin("Multiverse-Core");
        this.logger = Objects.requireNonNull(logger);
    }

    public boolean available() { return multiverse != null && multiverse.isEnabled(); }
    public Optional<World> loadedWorld(WorldDefinition definition) { return Optional.ofNullable(Bukkit.getWorld(definition.minecraftWorldName())); }

    /** Must be called through a Paper main-thread boundary because world loading changes server state. */
    public boolean ensureLoaded(WorldDefinition definition) {
        if (loadedWorld(definition).isPresent()) return true;
        if (!available()) { logger.warning("Cannot load " + definition.id() + ": Multiverse-Core is unavailable"); return false; }
        try {
            Object core = worldManager();
            Method loadWorld = core.getClass().getMethod("loadWorld", String.class);
            return (boolean) loadWorld.invoke(core, definition.minecraftWorldName());
        } catch (ReflectiveOperationException exception) {
            // Multiverse 5 changed its public manager accessor.  Its console commands are
            // deliberately used as the final compatibility path, with the loaded-world state
            // checked afterwards rather than trusting dispatchCommand's return value.
            logger.fine("Multiverse API load fallback for " + definition.id() + ": " + exception.getMessage());
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "mv load " + definition.minecraftWorldName());
            return Bukkit.getWorld(definition.minecraftWorldName()) != null;
        }
    }

    /** Must be called through a Paper main-thread boundary because unloading changes server state. */
    public boolean unload(WorldDefinition definition, boolean save) {
        if (!available()) return false;
        try {
            Object core = worldManager();
            return (boolean) core.getClass().getMethod("unloadWorld", String.class, boolean.class).invoke(core, definition.minecraftWorldName(), save);
        } catch (ReflectiveOperationException exception) {
            logger.fine("Multiverse API unload fallback for " + definition.id() + ": " + exception.getMessage());
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "mv unload " + definition.minecraftWorldName());
            return Bukkit.getWorld(definition.minecraftWorldName()) == null;
        }
    }

    /** Never block with Future.join() on the Paper server thread. */
    public CompletableFuture<Boolean> teleport(Player player, WorldDefinition definition) {
        Objects.requireNonNull(player);
        if (!definition.isAvailableForPlayers() || !ensureLoaded(definition)) return CompletableFuture.completedFuture(false);
        World world = loadedWorld(definition).orElseThrow();
        world.setPVP(definition.pvpMode().name().equals("ENABLED"));
        applyDifficulty(world, definition.difficulty());
        Location spawn = new Location(world, definition.spawnLocation().x(), definition.spawnLocation().y(), definition.spawnLocation().z(), definition.spawnLocation().yaw(), definition.spawnLocation().pitch());
        return player.teleportAsync(spawn);
    }

    private static void applyDifficulty(World world, String raw) {
        try { world.setDifficulty(Difficulty.valueOf(raw.toUpperCase(java.util.Locale.ROOT))); }
        catch (IllegalArgumentException ignored) { /* Brutal/Event policies are enforced by higher-level world modules. */ }
    }

    private Object worldManager() throws ReflectiveOperationException {
        for (String name : List.of("getWorldManager", "getMVWorldManager")) {
            try { return multiverse.getClass().getMethod(name).invoke(multiverse); }
            catch (NoSuchMethodException ignored) { /* try the next Multiverse generation */ }
        }
        throw new NoSuchMethodException(multiverse.getClass().getName() + " has no supported world-manager accessor");
    }
}
