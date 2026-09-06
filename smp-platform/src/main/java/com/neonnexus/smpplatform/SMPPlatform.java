package com.neonnexus.smpplatform;

import com.neonnexus.smpplatform.async.PlatformExecutors;
import com.neonnexus.smpplatform.config.PlatformConfiguration;
import com.neonnexus.smpplatform.config.PlatformConfigurationLoader;
import com.neonnexus.smpplatform.database.DatabaseService;
import com.neonnexus.smpplatform.identity.FloodgateIdentityAdapter;
import com.neonnexus.smpplatform.listeners.PlayerIdentityListener;
import com.neonnexus.smpplatform.multiverse.MultiverseInventoryAdapter;
import com.neonnexus.smpplatform.multiverse.MultiverseWorldAdapter;
import com.neonnexus.smpplatform.outbox.HttpOutboxDeliveryClient;
import com.neonnexus.smpplatform.outbox.JdbcOutboxRepository;
import com.neonnexus.smpplatform.outbox.OutboxDispatcher;
import com.neonnexus.smpplatform.world.DefaultWorlds;
import com.neonnexus.smpplatform.world.HttpWorldRegistryRemoteSource;
import com.neonnexus.smpplatform.world.RegistryDocument;
import com.neonnexus.smpplatform.world.WorldDefinition;
import com.neonnexus.smpplatform.world.WorldRegistry;
import com.neonnexus.smpplatform.world.WorldRegistryCache;
import com.neonnexus.smpplatform.world.WorldRegistrySynchronizer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/** Unified SMPPlatform bootstrap. Durable feature services activate only after PostgreSQL is healthy. */
public final class SMPPlatform extends JavaPlugin {
    private PlatformConfiguration configuration;
    private PlatformExecutors executors;
    private WorldRegistry registry;
    private DatabaseService database;
    private WorldRegistrySynchronizer synchronizer;
    private OutboxDispatcher outbox;
    private MultiverseWorldAdapter multiverse;
    private volatile Phase3Runtime phase3;

    @Override public void onEnable() {
        try {
            configuration = new PlatformConfigurationLoader(this).load();
            executors = new PlatformExecutors(configuration.core().async().ioThreads(), configuration.core().async().shutdownTimeout());
            RegistryDocument initial = applyWorldOverrides(DefaultWorlds.document());
            registry = new WorldRegistry(initial, new WorldRegistryCache(getDataFolder().toPath().resolve(configuration.worlds().cacheFile()), getLogger()), Clock.systemUTC());
            registry.bootstrapFromCache();
            configuration.core().inventory().policy().groupsFor(registry.snapshot().worlds().values());
            multiverse = new MultiverseWorldAdapter(getServer().getPluginManager(), getLogger());
            MultiverseInventoryAdapter inventories = new MultiverseInventoryAdapter(getServer().getPluginManager(), getLogger());
            inventories.validateDesiredGroups(registry.snapshot().worlds().values(), configuration.core().inventory().policy());
            FloodgateIdentityAdapter identities = FloodgateIdentityAdapter.discover(getServer().getPluginManager(), getLogger());
            getServer().getPluginManager().registerEvents(new PlayerIdentityListener(identities, getLogger()), this);
            database = new DatabaseService(configuration.core().database(), getLogger());
            executors.io().execute(this::startDatabaseServicesAsync);
            if (configuration.core().centralApi().enabled()) startCentralSync();
            getLogger().info("SMPPlatform core enabled with World Registry revision " + registry.snapshot().revision() + "; offline=" + registry.snapshot().offlineMode());
        } catch (Exception exception) {
            getLogger().severe("SMPPlatform cannot start safely: " + exception.getMessage());
            getServer().getPluginManager().disablePlugin(this);
        }
    }

    private void startDatabaseServicesAsync() {
        database.start();
        if (!database.isAvailable()) return;
        phase3 = Phase3Runtime.start(this, database.requireDataSource(), executors.io(), Clock.systemUTC());
        getLogger().info("Durable guild and points modules are active.");
        if (configuration.core().centralApi().enabled() && configuration.integrations().outbox().enabled()) {
            String token = System.getenv(configuration.core().centralApi().tokenEnvironment());
            try {
                outbox = new OutboxDispatcher(new JdbcOutboxRepository(database.requireDataSource()), new HttpOutboxDeliveryClient(configuration.core().centralApi().baseUrl(), token), executors.io(), Clock.systemUTC(), configuration.integrations().outbox().baseBackoff(), configuration.integrations().outbox().maxAttempts(), getLogger());
                long period = configuration.integrations().outbox().pollInterval().toSeconds();
                executors.scheduler().scheduleWithFixedDelay(() -> outbox.dispatchOnce(50), period, period, TimeUnit.SECONDS);
            } catch (RuntimeException exception) { getLogger().warning("Outbox delivery is disabled while preserving durable database records: " + exception.getMessage()); }
        }
    }

    private void startCentralSync() {
        String token = System.getenv(configuration.core().centralApi().tokenEnvironment());
        try {
            synchronizer = new WorldRegistrySynchronizer(registry, new HttpWorldRegistryRemoteSource(configuration.core().centralApi().baseUrl(), token), executors.io(), Clock.systemUTC(), getLogger());
            long period = configuration.core().centralApi().syncInterval().toSeconds();
            executors.scheduler().scheduleWithFixedDelay(() -> synchronizer.sync(), 0, period, TimeUnit.SECONDS);
        } catch (RuntimeException exception) {
            registry.setOffline("Central API configuration is invalid: " + exception.getMessage());
            getLogger().warning("Central World Registry sync is disabled; cached registry remains active: " + exception.getMessage());
        }
    }

    private RegistryDocument applyWorldOverrides(RegistryDocument bundled) {
        List<WorldDefinition> changed = new ArrayList<>();
        for (WorldDefinition world : bundled.worlds()) {
            PlatformConfiguration.WorldFile.WorldOverride override = configuration.worlds().overrides().get(world.id());
            changed.add(override == null ? world : new WorldDefinition(world.id(), override.minecraftWorldName(), world.displayName(), world.description(), world.type(), world.season(), world.status(), world.difficulty(), world.borderSize(), world.pvpMode(), world.guildsEnabled(), world.pointsEnabled(), world.currencyId(), world.claimsEnabled(), world.economyEnabled(), world.inventoryGroup(), world.resetPolicy(), world.archivePolicy(), world.discordEnabled(), world.websiteVisible(), world.mapVisible(), world.playerCount(), override.maintenanceMode(), world.accessPermission(), new com.neonnexus.smpplatform.world.SpawnLocation(override.minecraftWorldName(), world.spawnLocation().x(), world.spawnLocation().y(), world.spawnLocation().z(), world.spawnLocation().yaw(), world.spawnLocation().pitch())));
        }
        return new RegistryDocument(Math.max(bundled.revision(), configuration.worlds().initialRevision()), Instant.now(), changed);
    }

    @Override public void onDisable() {
        if (executors != null) executors.close();
        if (database != null) database.close();
        getLogger().info("SMPPlatform shutdown complete.");
    }

    @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String name = command.getName().toLowerCase(java.util.Locale.ROOT);
        if (name.equals("worlds")) {
            if (registry == null) { sender.sendMessage("SMPPlatform World Registry is not available."); return true; }
            var snapshot = registry.snapshot();
            sender.sendMessage("Neon Nexus worlds (revision " + snapshot.revision() + ", " + (snapshot.offlineMode() ? "cached/offline" : "synced") + "): ");
            snapshot.worlds().values().forEach(world -> sender.sendMessage(" - " + world.displayName() + " [" + world.id() + "] " + world.status()));
            return true;
        }
        if (name.equals("guild") || name.equals("points")) {
            Phase3Runtime runtime = phase3;
            if (runtime == null) {
                sender.sendMessage("§cDurable gameplay services are unavailable. No mutation was attempted.");
                return true;
            }
            return runtime.execute(sender, name, args);
        }
        sender.sendMessage("§cThis SMPPlatform module is not active because its required production adapter is unavailable.");
        return true;
    }

    public WorldRegistry worldRegistry() { return registry; }
    public MultiverseWorldAdapter multiverse() { return multiverse; }
}
