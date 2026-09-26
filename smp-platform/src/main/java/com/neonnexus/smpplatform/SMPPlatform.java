package com.neonnexus.smpplatform;

import com.neonnexus.smpplatform.integrations.api.PlatformGuildPointsClient;
import com.neonnexus.smpplatform.async.PlatformExecutors;
import com.neonnexus.smpplatform.atrium.AtriumPlotFlagsMenu;
import com.neonnexus.smpplatform.atrium.AtriumReviewMenu;
import com.neonnexus.smpplatform.atrium.AtriumShowcaseMenu;
import com.neonnexus.smpplatform.atrium.AtriumSubmissionService;
import com.neonnexus.smpplatform.client.KairuClientGateway;
import com.neonnexus.smpplatform.config.PlatformConfiguration;
import com.neonnexus.smpplatform.config.PlatformConfigurationLoader;
import com.neonnexus.smpplatform.database.DatabaseService;
import com.neonnexus.smpplatform.search.PlayerSearchService;
import com.neonnexus.smpplatform.auction.AuctionService;
import com.neonnexus.smpplatform.auction.AuctionIdentityResolver;
import com.neonnexus.smpplatform.auction.AuctionConfirmations;
import com.neonnexus.smpplatform.identity.FloodgateIdentityAdapter;
import com.neonnexus.smpplatform.listeners.PlayerIdentityListener;
import com.neonnexus.smpplatform.listeners.ControlPlaneBridgeListener;
import com.neonnexus.smpplatform.listeners.DiscordChatBridgeListener;
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
import org.bukkit.entity.Player;
import org.bukkit.Bukkit;
import org.bukkit.Difficulty;
import org.bukkit.GameRule;
import org.bukkit.GameMode;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.WorldType;
import java.nio.file.Files;
import java.nio.file.Path;
import java.io.IOException;
import java.time.Clock;
import java.time.Instant;
import java.time.Duration;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.UUID;
import com.google.gson.JsonParser;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.neonnexus.smpplatform.protection.ClaimCommand;
import com.neonnexus.smpplatform.protection.WorldProtectionListener;
import com.neonnexus.smpplatform.protection.WorldProtectionService;
import gg.neonnexus.smpplatform.lifecycle.events.FileDurableEventOutbox;
import gg.neonnexus.smpplatform.lifecycle.paper.IntervalQuarryResetScheduler;
import gg.neonnexus.smpplatform.lifecycle.paper.PaperQuarryGateway;
import gg.neonnexus.smpplatform.lifecycle.paper.QuarryEntryListener;
import gg.neonnexus.smpplatform.lifecycle.quarry.InMemoryQuarryResetRepository;
import gg.neonnexus.smpplatform.lifecycle.quarry.QuarryLifecycleService;
import gg.neonnexus.smpplatform.lifecycle.quarry.QuarryResetCoordinator;
import gg.neonnexus.smpplatform.lifecycle.quarry.QuarryResetState;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.configuration.file.YamlConfiguration;
import java.util.concurrent.atomic.AtomicBoolean;
import com.neonnexus.smpplatform.administration.ClientPlayerAdminService;
import com.neonnexus.smpplatform.administration.ModerationRepository;
import com.neonnexus.smpplatform.administration.ModerationListener;
import com.neonnexus.smpplatform.administration.AdminConfirmations;
import com.neonnexus.smpplatform.administration.InventoryAuditRepository;
import com.neonnexus.smpplatform.administration.ClientWorldAdminService;
import com.neonnexus.smpplatform.administration.WorldMaintenanceService;
import com.neonnexus.smpplatform.administration.GuildPointsAdminFacade;
import com.neonnexus.smpplatform.administration.GuildPointsQueryService;
import com.neonnexus.smpplatform.administration.HardcoreAdminRepository; 
import com.neonnexus.smpplatform.administration.HardcoreLifecycleListener;
import com.neonnexus.smpplatform.administration.HardcoreResetWindowService;

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
    private volatile PlatformGuildPointsClient platformGuildPoints;
    private volatile AtriumSubmissionService atriumSubmissions;
    private volatile PlayerSearchService playerSearch;
    private volatile AuctionService auctionService;
    private volatile AuctionIdentityResolver auctionIdentity;
    private volatile ClientPlayerAdminService clientPlayerAdmin;
    private volatile ModerationRepository moderationRepository;
    private final ModerationListener moderationListener = new ModerationListener();
    private final AdminConfirmations adminConfirmations = new AdminConfirmations();
    private volatile InventoryAuditRepository inventoryAudit;
    private volatile ClientWorldAdminService clientWorldAdmin;
    private final WorldMaintenanceService worldMaintenance = new WorldMaintenanceService();
    private volatile GuildPointsAdminFacade guildPointsAdmin;
    private volatile GuildPointsQueryService guildPointsQuery;
    private volatile HardcoreAdminRepository hardcoreAdmin;
    private volatile HardcoreResetWindowService hardcoreResetWindow;
    private final AuctionConfirmations auctionConfirmations = new AuctionConfirmations();
    private AtriumReviewMenu atriumReviewMenu;
    private AtriumShowcaseMenu atriumShowcaseMenu;
    private KairuClientGateway clientGateway;
    private volatile String centralApiToken;
    private Instant startedAt;
    private QuarryLifecycleService quarryLifecycle;
    private IntervalQuarryResetScheduler quarryResetSchedule;
    private BukkitTask quarryResetTask;
    private final AtomicBoolean quarryResetRunning = new AtomicBoolean();
    private WorldProtectionService worldProtection;

    @Override public void onEnable() {
        try {
            startedAt = Instant.now();
            configuration = new PlatformConfigurationLoader(this).load();
            executors = new PlatformExecutors(configuration.core().async().ioThreads(), configuration.core().async().shutdownTimeout());
            RegistryDocument initial = applyWorldOverrides(DefaultWorlds.document());
            registry = new WorldRegistry(initial, new WorldRegistryCache(getDataFolder().toPath().resolve(configuration.worlds().cacheFile()), getLogger()), Clock.systemUTC());
            registry.bootstrapFromCache();
            configuration.core().inventory().policy().groupsFor(registry.snapshot().worlds().values());
            multiverse = new MultiverseWorldAdapter(getServer().getPluginManager(), getLogger());
            clientGateway = new KairuClientGateway(this);
            atriumReviewMenu = new AtriumReviewMenu(this);
            getServer().getPluginManager().registerEvents(atriumReviewMenu, this);
            atriumShowcaseMenu = new AtriumShowcaseMenu(this);
            getServer().getPluginManager().registerEvents(atriumShowcaseMenu, this);
            MultiverseInventoryAdapter inventories = new MultiverseInventoryAdapter(getServer().getPluginManager(), getLogger());
            inventories.validateDesiredGroups(registry.snapshot().worlds().values(), configuration.core().inventory().policy());
            FloodgateIdentityAdapter identities = FloodgateIdentityAdapter.discover(getServer().getPluginManager(), getLogger());
            getServer().getPluginManager().registerEvents(new PlayerIdentityListener(identities, getLogger()), this);
            getServer().getPluginManager().registerEvents(new ControlPlaneBridgeListener(this), this);
            getServer().getPluginManager().registerEvents(new DiscordChatBridgeListener(this), this);
            worldProtection = new WorldProtectionService(this);
            getServer().getPluginManager().registerEvents(new WorldProtectionListener(worldProtection), this);
            java.util.Objects.requireNonNull(getCommand("claim"), "claim command").setExecutor(new ClaimCommand(worldProtection));
            startQuarryResetAutomation();
            database = new DatabaseService(configuration.core().database(), getLogger());
            executors.io().execute(this::startDatabaseServicesAsync);
            if (configuration.core().centralApi().enabled()) {
                startCentralSync();
                startHeartbeat();
                startIncomingDiscordChat();
                publishBridgeEvent("SERVER_STARTED", null, null, "Kairu SMP started", java.util.Map.of("version", getDescription().getVersion()));
            }
            getLogger().info("SMPPlatform core enabled with World Registry revision " + registry.snapshot().revision() + "; offline=" + registry.snapshot().offlineMode());
        } catch (Exception exception) {
            getLogger().severe("SMPPlatform cannot start safely: " + exception.getMessage());
            getServer().getPluginManager().disablePlugin(this);
        }
    }

    private void startDatabaseServicesAsync() {
        // Control Plane-backed services must not be gated by the optional local JDBC database.
        if (configuration.core().centralApi().enabled()) {
            try {
                auctionService = new AuctionService(configuration.core().centralApi().baseUrl(), resolveCentralApiToken(), configuration.core().serverId());
                auctionIdentity = new AuctionIdentityResolver();
                platformGuildPoints = new PlatformGuildPointsClient(configuration.core().centralApi().baseUrl(), resolveCentralApiToken(), configuration.core().serverId());
                getLogger().info("Auction, Guilds and Points connected through Control Plane API.");
            } catch (RuntimeException exception) {
                getLogger().warning("Auction Control Plane client could not start: " + exception.getMessage());
            }
        } else {
            getLogger().warning("Auction disabled: Control Plane API is not enabled.");
        }

        database.start();
        if (!database.isAvailable()) {
            getLogger().warning("Local PostgreSQL is unavailable; local-only legacy modules are disabled. Control Plane-backed services remain available.");
            return;
        }
        // Guilds and Points are Control Plane-owned. Do not start the legacy JDBC Phase3 runtime.
        phase3 = null;
        atriumSubmissions = new AtriumSubmissionService(database.requireDataSource(), registry.require("atrium").minecraftWorldName());
        playerSearch = new PlayerSearchService(database.requireDataSource());
        clientPlayerAdmin = new ClientPlayerAdminService(this);
        moderationRepository = new ModerationRepository(database.requireDataSource());
        inventoryAudit = new InventoryAuditRepository(database.requireDataSource());
        clientWorldAdmin = new ClientWorldAdminService(this);
        guildPointsAdmin = null;
        guildPointsQuery = null;
        hardcoreAdmin = new HardcoreAdminRepository(database.requireDataSource());
        hardcoreResetWindow = new HardcoreResetWindowService(database.requireDataSource());
        String hardcorePhysicalWorld = getConfig().getString("hardcore.physical-world","obsidian-gate");
        getServer().getPluginManager().registerEvents(new HardcoreLifecycleListener(this,database.requireDataSource(),hardcoreAdmin,hardcorePhysicalWorld),this);
        getServer().getPluginManager().registerEvents(moderationListener, this);
        getLogger().info("Local Minecraft persistence is active; Guilds and Points remain Control Plane-owned.");
        if (configuration.core().centralApi().enabled() && configuration.integrations().outbox().enabled()) {
            try {
                String token = resolveCentralApiToken();
                outbox = new OutboxDispatcher(new JdbcOutboxRepository(database.requireDataSource()), new HttpOutboxDeliveryClient(configuration.core().centralApi().baseUrl(), token), executors.io(), Clock.systemUTC(), configuration.integrations().outbox().baseBackoff(), configuration.integrations().outbox().maxAttempts(), getLogger());
                long period = configuration.integrations().outbox().pollInterval().toSeconds();
                executors.scheduler().scheduleWithFixedDelay(() -> outbox.dispatchOnce(50), period, period, TimeUnit.SECONDS);
            } catch (RuntimeException exception) { getLogger().warning("Outbox delivery is disabled while preserving durable database records: " + exception.getMessage()); }
        }
    }

    private void startCentralSync() {
        try {
            String token = resolveCentralApiToken();
            synchronizer = new WorldRegistrySynchronizer(registry, new HttpWorldRegistryRemoteSource(configuration.core().centralApi().baseUrl(), token), executors.io(), Clock.systemUTC(), getLogger());
            long period = configuration.core().centralApi().syncInterval().toSeconds();
            executors.scheduler().scheduleWithFixedDelay(() -> synchronizer.sync(), 0, period, TimeUnit.SECONDS);
        } catch (RuntimeException exception) {
            registry.setOffline("Central API configuration is invalid: " + exception.getMessage());
            getLogger().warning("Central World Registry sync is disabled; cached registry remains active: " + exception.getMessage());
        }
    }

    private String resolveCentralApiToken() {
        if (centralApiToken != null) return centralApiToken;
        String environmentToken = System.getenv(configuration.core().centralApi().tokenEnvironment());
        if (environmentToken != null && !environmentToken.isBlank()) return centralApiToken = environmentToken.trim();
        Path tokenFile = configuration.core().centralApi().tokenFile();
        try {
            if (!Files.isRegularFile(tokenFile)) {
                throw new IllegalStateException("environment variable " + configuration.core().centralApi().tokenEnvironment() + " and protected token file are absent");
            }
            String token = Files.readString(tokenFile).trim();
            if (token.isBlank()) throw new IllegalStateException("protected token file is blank");
            getLogger().info("Central API token loaded from the protected SMPPlatform data-file fallback.");
            return centralApiToken = token;
        } catch (IOException exception) {
            throw new IllegalStateException("protected Central API token file cannot be read", exception);
        }
    }

    private RegistryDocument applyWorldOverrides(RegistryDocument bundled) {
        List<WorldDefinition> changed = new ArrayList<>();
        for (WorldDefinition world : bundled.worlds()) {
            PlatformConfiguration.WorldFile.WorldOverride override = configuration.worlds().overrides().get(world.id());
            if (override == null) { changed.add(world); continue; }
            String worldName = normalizeLegacyNxWorldName(world.id(), override.minecraftWorldName());
            if (!worldName.equals(override.minecraftWorldName())) getLogger().warning("Normalised legacy world mapping " + override.minecraftWorldName() + " to " + worldName + "; nx_ world folders are not used by Kairu SMP.");
            changed.add(new WorldDefinition(world.id(), worldName, world.displayName(), world.description(), world.type(), world.season(), world.status(), world.difficulty(), world.borderSize(), world.pvpMode(), world.guildsEnabled(), world.pointsEnabled(), world.currencyId(), world.claimsEnabled(), world.economyEnabled(), world.inventoryGroup(), world.resetPolicy(), world.archivePolicy(), world.discordEnabled(), world.websiteVisible(), world.mapVisible(), world.playerCount(), override.maintenanceMode(), world.accessPermission(), new com.neonnexus.smpplatform.world.SpawnLocation(worldName, world.spawnLocation().x(), world.spawnLocation().y(), world.spawnLocation().z(), world.spawnLocation().yaw(), world.spawnLocation().pitch())));
        }
        return new RegistryDocument(Math.max(bundled.revision(), configuration.worlds().initialRevision()), Instant.now(), changed);
    }

    /** One-way compatibility mapping for the short-lived nx_ bootstrap names; it never changes other custom names. */
    private static String normalizeLegacyNxWorldName(String worldId, String configuredName) {
        if (worldId.equals("ashfall") && configuredName.equalsIgnoreCase("nx_ashfall")) return "ashfall";
        if (worldId.equals("obsidian-gate") && configuredName.equalsIgnoreCase("nx_obsidian_gate")) return "obsidian-gate";
        if (worldId.equals("atrium") && configuredName.equalsIgnoreCase("nx_atrium")) return "atrium";
        if (worldId.equals("colosseum") && configuredName.equalsIgnoreCase("nx_colosseum")) return "colosseum";
        if (worldId.equals("quarry") && configuredName.equalsIgnoreCase("nx_quarry")) return "quarry";
        if (worldId.equals("verdance") && configuredName.equalsIgnoreCase("nx_verdance")) return "verdance";
        return configuredName;
    }

    @Override public void onDisable() {
        publishBridgeEvent("SERVER_STOPPING", null, null, "Kairu SMP stopping", java.util.Map.of());
        if (quarryResetTask != null) quarryResetTask.cancel();
        if (executors != null) executors.close();
        if (database != null) database.close();
        getLogger().info("SMPPlatform shutdown complete.");
    }

    /**
     * The scheduled reset deliberately does not call Minekeep. Minekeep remains the operator's
     * manual, off-server backup; this local verified snapshot is only the reset safety net.
     */
    private void startQuarryResetAutomation() {
        try {
            Path worldsFile = getDataFolder().toPath().resolve("worlds.yml");
            YamlConfiguration worlds = YamlConfiguration.loadConfiguration(worldsFile.toFile());
            String prefix = "worlds.quarry.reset-policy.";
            if (!worlds.getBoolean(prefix + "automatic-enabled", true)) {
                getLogger().info("Quarry automatic reset is disabled in worlds.yml.");
                return;
            }
            long intervalMinutes = Math.max(5L, worlds.getLong(prefix + "interval-minutes", 60L));
            List<Duration> warnings = worlds.getIntegerList(prefix + "warning-minutes").stream()
                    .filter(value -> value > 0).map(value -> Duration.ofMinutes(value.longValue())).toList();
            if (warnings.isEmpty()) warnings = List.of(Duration.ofMinutes(10), Duration.ofMinutes(5), Duration.ofMinutes(1));
            WorldDefinition quarryDefinition = registry.require("quarry");
            String quarryWorld = quarryDefinition.minecraftWorldName();
            String hubWorld = registry.require("spawn-hub").minecraftWorldName();
            int backupRetention = Math.max(1, worlds.getInt(prefix + "local-backup-retention", 24));
            PaperQuarryGateway gateway = new PaperQuarryGateway(this, quarryWorld, hubWorld, getDataFolder().toPath().resolve("quarry-reset-backups"), backupRetention, multiverse, quarryDefinition);
            quarryLifecycle = new QuarryLifecycleService(new QuarryResetCoordinator(gateway, new InMemoryQuarryResetRepository(),
                    new FileDurableEventOutbox(getDataFolder().toPath().resolve("lifecycle-events.jsonl"))), executors.io());
            getServer().getPluginManager().registerEvents(new QuarryEntryListener(gateway, quarryWorld, hubWorld), this);
            quarryResetSchedule = new IntervalQuarryResetScheduler(Duration.ofMinutes(intervalMinutes), warnings, Instant.now());
            quarryResetTask = Bukkit.getScheduler().runTaskTimer(this, this::pollQuarryResetSchedule, 20L, 20L);
            getLogger().info("The Quarry will regenerate every " + intervalMinutes + " minutes; next reset " + quarryResetSchedule.nextReset() + ". Players are evacuated to Spawn Hub; " + backupRetention + " local reset backups are retained.");
        } catch (RuntimeException exception) {
            getLogger().warning("Quarry reset automation is disabled: " + exception.getMessage());
        }
    }

    private void pollQuarryResetSchedule() {
        if (quarryLifecycle == null || quarryResetSchedule == null) return;
        IntervalQuarryResetScheduler.Poll poll = quarryResetSchedule.poll(Instant.now());
        for (Duration warning : poll.warnings()) sendQuarryNotice("§6The Quarry regenerates in " + conciseDuration(warning) + ". Please leave safely.");
        if (!poll.resetDue() || !quarryResetRunning.compareAndSet(false, true)) return;
        sendQuarryNotice("§cThe Quarry is now resetting. You are being moved to Spawn Hub.");
        quarryLifecycle.scheduledResetAsync().whenComplete((snapshot, error) -> Bukkit.getScheduler().runTask(this, () -> {
            quarryResetRunning.set(false);
            if (error != null || snapshot == null) {
                getLogger().warning("Scheduled Quarry reset failed: " + (error == null ? "no result" : error.getClass().getSimpleName()));
                Bukkit.broadcastMessage("§cThe Quarry reset did not complete; entry remains protected.");
            } else if (snapshot.state() == QuarryResetState.COMPLETED) {
                Bukkit.broadcastMessage("§aThe Quarry has regenerated and is open again.");
            } else {
                getLogger().warning("Scheduled Quarry reset ended in " + snapshot.state() + ": " + snapshot.detail());
                Bukkit.broadcastMessage("§cThe Quarry reset was stopped safely: " + snapshot.detail());
            }
        }));
    }

    private void sendQuarryNotice(String message) {
        World quarry = Bukkit.getWorld(registry.require("quarry").minecraftWorldName());
        if (quarry != null) for (Player player : quarry.getPlayers()) player.sendMessage(message);
    }

    private static String conciseDuration(Duration duration) {
        long minutes = duration.toMinutes();
        return minutes > 0 ? minutes + " minute" + (minutes == 1 ? "" : "s") : duration.toSeconds() + " seconds";
    }

    public WorldProtectionService worldProtection() { return worldProtection; }

    /** Called by the staff menu only after the gateway has checked administrator permission. */
    public String requestManualQuarryReset() {
        if (quarryLifecycle == null) throw new IllegalStateException("Quarry reset automation is not available.");
        if (!quarryResetRunning.compareAndSet(false, true)) throw new IllegalStateException("A Quarry reset is already running.");
        Bukkit.broadcastMessage("§cThe Quarry is being reset by an administrator. Players are being moved to Spawn Hub.");
        quarryLifecycle.scheduledResetAsync().whenComplete((snapshot, error) -> Bukkit.getScheduler().runTask(this, () -> {
            quarryResetRunning.set(false);
            if (error != null || snapshot == null) Bukkit.broadcastMessage("§cThe Quarry reset did not complete; entry remains protected.");
            else if (snapshot.state() == QuarryResetState.COMPLETED) Bukkit.broadcastMessage("§aThe Quarry has regenerated and is open again.");
            else Bukkit.broadcastMessage("§cThe Quarry reset was stopped safely: " + snapshot.detail());
        }));
        return "Quarry reset started. Players are being moved to Spawn Hub.";
    }

    private void startHeartbeat() {
        try { resolveCentralApiToken(); }
        catch (RuntimeException exception) { getLogger().warning("Control Plane heartbeat is disabled: " + exception.getMessage()); return; }
        Bukkit.getScheduler().runTaskTimer(this, () -> {
            JsonObject payload = new JsonObject();
            payload.addProperty("online", true); payload.addProperty("tps", Bukkit.getTPS()[0]);
            payload.addProperty("playerCount", Bukkit.getOnlinePlayers().size()); payload.addProperty("version", getDescription().getVersion());
            payload.addProperty("uptimeSeconds", Math.max(0L, Duration.between(startedAt, Instant.now()).toSeconds()));
            JsonArray worlds = new JsonArray(); for (World world : Bukkit.getWorlds()) worlds.add(world.getName()); payload.add("worlds", worlds);
            JsonArray worldPlayers = new JsonArray();
            for (WorldDefinition definition : registry.snapshot().worlds().values()) {
                World world = Bukkit.getWorld(definition.minecraftWorldName());
                JsonObject detail = new JsonObject();
                detail.addProperty("id", definition.id()); detail.addProperty("name", definition.displayName());
                detail.addProperty("playerCount", world == null ? 0 : world.getPlayers().size()); detail.addProperty("status", definition.status().name());
                worldPlayers.add(detail);
            }
            payload.add("worldPlayers", worldPlayers);
            JsonArray players = new JsonArray(); for (Player player : Bukkit.getOnlinePlayers()) players.add(player.getName()); payload.add("players", players);
            postControlPlane("/api/plugin/heartbeat", payload);
        }, 20L, 1_200L);
    }

    /** Called only after Bukkit event data has been captured on the primary thread. */
    public void publishBridgeEvent(String eventType, String worldName, Player player, String content, java.util.Map<String, String> details) {
        if (configuration == null || !configuration.core().centralApi().enabled() || executors == null) return;
        JsonObject payload = new JsonObject();
        payload.addProperty("eventId", UUID.randomUUID().toString()); payload.addProperty("eventType", eventType); payload.addProperty("occurredAt", Instant.now().toString());
        if (worldName != null && !worldName.isBlank()) payload.addProperty("worldName", worldName);
        if (player != null) { payload.addProperty("minecraftUuid", player.getUniqueId().toString()); payload.addProperty("minecraftName", player.getName()); }
        payload.addProperty("content", content == null ? "" : content);
        JsonObject attributes = new JsonObject(); for (var entry : details.entrySet()) attributes.addProperty(entry.getKey(), entry.getValue()); payload.add("details", attributes);
        postControlPlane("/api/plugin/bridge-events", payload);
    }

    /** Minecraft to Discord goes only through the Control Plane event bus. */
    public void publishMinecraftChat(Player player, String content, String chatType) {
        if (content.length() > 500) content = content.substring(0, 500);
        WorldDefinition world = registry.snapshot().worlds().values().stream().filter(definition -> definition.minecraftWorldName().equalsIgnoreCase(player.getWorld().getName())).findFirst().orElse(null);
        if (world == null || world.id().equals("spawn-hub") || world.id().equals("verdance")) return;
        publishBridgeEvent("CHAT", world.id(), player, content, java.util.Map.of("chatType", chatType, "worldId", world.id()));
    }

    private void startIncomingDiscordChat() {
        Bukkit.getScheduler().runTaskTimerAsynchronously(this, this::pollIncomingDiscordChat, 100L, 100L);
    }

    private void pollIncomingDiscordChat() {
        if (configuration == null || !configuration.core().centralApi().enabled()) return;
        try {
            String base = configuration.core().centralApi().baseUrl().replaceAll("/$", "");
            HttpRequest request = HttpRequest.newBuilder(URI.create(base + "/api/plugin/chat/queued?limit=20"))
                    .header("Authorization", "Bearer " + resolveCentralApiToken()).header("X-Kairu-Server-Id", configuration.core().serverId()).GET().build();
            HttpResponse<String> response = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() / 100 != 2) { getLogger().warning("Control Plane queued chat returned HTTP " + response.statusCode()); return; }
            JsonArray messages = JsonParser.parseString(response.body()).getAsJsonObject().getAsJsonArray("messages");
            for (var item : messages) {
                JsonObject message = item.getAsJsonObject(); String id = message.get("id").getAsString();
                String content = message.get("content").getAsString(); String displayName = message.has("displayName") ? message.get("displayName").getAsString() : "Discord";
                String targetWorld = message.has("targetWorld") ? message.get("targetWorld").getAsString() : null;
                Bukkit.getScheduler().callSyncMethod(this, () -> { deliverDiscordChat(displayName, content, targetWorld); return null; }).get(5, TimeUnit.SECONDS);
                acknowledgeIncomingChat(base, id, "delivered", "Broadcast to Minecraft");
            }
        } catch (Exception exception) { getLogger().warning("Control Plane queued chat delivery failed: " + exception.getClass().getSimpleName()); }
    }

    private void deliverDiscordChat(String displayName, String content, String targetWorld) {
        String safeName = displayName.replaceAll("[§\\r\\n]", "").trim(); String safeContent = content.replaceAll("[§\\r\\n]", " ").trim();
        if (safeName.isBlank()) safeName = "Discord"; if (safeContent.isBlank()) return;
        String rendered = "§9[DISCORD] §b" + safeName.substring(0, Math.min(48, safeName.length())) + "§7: " + safeContent.substring(0, Math.min(500, safeContent.length()));
        if (targetWorld == null || targetWorld.isBlank()) Bukkit.broadcastMessage(rendered);
        else for (Player player : Bukkit.getOnlinePlayers()) if (player.getWorld().getName().equalsIgnoreCase(targetWorld) || registry.find(targetWorld).map(definition -> definition.minecraftWorldName().equalsIgnoreCase(player.getWorld().getName())).orElse(false)) player.sendMessage(rendered);
    }

    private void acknowledgeIncomingChat(String base, String id, String status, String detail) {
        try {
            JsonObject payload = new JsonObject(); payload.addProperty("status", status); payload.addProperty("detail", detail);
            HttpRequest request = HttpRequest.newBuilder(URI.create(base + "/api/plugin/chat/" + id + "/ack")).header("Content-Type", "application/json").header("Authorization", "Bearer " + resolveCentralApiToken()).header("X-Kairu-Server-Id", configuration.core().serverId()).POST(HttpRequest.BodyPublishers.ofString(payload.toString(), StandardCharsets.UTF_8)).build();
            HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.discarding());
        } catch (Exception exception) { getLogger().warning("Control Plane chat acknowledgement failed: " + exception.getClass().getSimpleName()); }
    }

    private void postControlPlane(String path, JsonObject payload) {
        executors.io().execute(() -> {
            try {
                String token = resolveCentralApiToken(); String base = configuration.core().centralApi().baseUrl().replaceAll("/$", "");
                HttpRequest request = HttpRequest.newBuilder(URI.create(base + path)).header("Content-Type", "application/json")
                        .header("Authorization", "Bearer " + token).header("X-Kairu-Server-Id", configuration.core().serverId())
                        .POST(HttpRequest.BodyPublishers.ofString(payload.toString(), StandardCharsets.UTF_8)).build();
                int status = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.discarding()).statusCode();
                if (status / 100 != 2) getLogger().warning("Control Plane " + path + " returned HTTP " + status);
            } catch (Exception exception) { getLogger().warning("Control Plane " + path + " delivery failed: " + exception.getClass().getSimpleName()); }
        });
    }

    @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String name = command.getName().toLowerCase(java.util.Locale.ROOT);
        if (name.equals("kairuadmin")) {
            if (args.length > 0 && args[0].equalsIgnoreCase("setup")) return setup(sender, args);
            return clientGateway != null && clientGateway.handle(sender, args);
        }
        if (name.equals("kairu")) return linkDiscordAccount(sender, args);
        if (name.equals("chat") || name.equals("world") || name.equals("global")) { sender.sendMessage("§7Chat bridge is active. Ordinary chat is shared with Discord where its channel mapping is configured."); return true; }
        if (name.equals("worlds")) {
            if (registry == null) { sender.sendMessage("SMPPlatform World Registry is not available."); return true; }
            var snapshot = registry.snapshot();
            sender.sendMessage("Neon Nexus worlds (revision " + snapshot.revision() + ", " + (snapshot.offlineMode() ? "cached/offline" : "synced") + "): ");
            snapshot.worlds().values().forEach(world -> sender.sendMessage(" - " + world.displayName() + " [" + world.id() + "] " + world.status()));
            return true;
        }
        if (name.equals("guild") || name.equals("points")) {
            if (!(sender instanceof Player player)) { sender.sendMessage("§cGuilds and points are player services."); return true; }
            PlatformGuildPointsClient service=platformGuildPoints;
            if(service==null){sender.sendMessage("§cPlatform services are unavailable. Check the Control Plane connection.");return true;}
            UUID id=player.getUniqueId();
            executors.io().execute(() -> {
                try {
                    JsonObject result;
                    if(name.equals("guild")){
                        if(args.length==0 || args[0].equalsIgnoreCase("info")) result=service.view(id,"guild-summary");
                        else if(args[0].equalsIgnoreCase("top")) result=service.view(id,"guild-top");
                        else if(args[0].equalsIgnoreCase("create") && args.length>=3) result=service.createGuild(id,args[1],args[2],args.length>3?String.join(" ",java.util.Arrays.copyOfRange(args,3,args.length)):"");
                        else if(args[0].equalsIgnoreCase("leave")) result=service.guildAction(id,"guild-leave",null);
                        else { Bukkit.getScheduler().runTask(this,()->player.sendMessage("§d/guild create <name> <tag> [description] §7| §d/guild top §7| §d/guild leave")); return; }
                    } else {
                        String currency=args.length>1?args[1]:"KAIRU_POINTS";
                        if(args.length==0 || args[0].equalsIgnoreCase("balance")) result=service.view(id,"points-summary");
                        else if(args[0].equalsIgnoreCase("history")) result=service.pointsView(id,"points-history",currency);
                        else if(args[0].equalsIgnoreCase("top")) result=service.pointsView(id,"points-top",currency);
                        else { Bukkit.getScheduler().runTask(this,()->player.sendMessage("§d/points balance [currency] §7| §d/points history [currency] §7| §d/points top [currency]")); return; }
                    }
                    Bukkit.getScheduler().runTask(this,()->player.sendMessage("§d"+result.toString()));
                } catch(Exception e){Bukkit.getScheduler().runTask(this,()->player.sendMessage("§c"+e.getMessage()));}
            });
            return true;
        }
        if (name.equals("plotflags")) {
            if (!(sender instanceof org.bukkit.entity.Player player)) { sender.sendMessage("Open plot settings in-game."); return true; }
            if (!isAtriumWorld(player.getWorld())) { player.sendMessage("§cThe Atrium plot settings guide is available only in The Atrium."); return true; }
            AtriumPlotFlagsMenu.open(player);
            return true;
        }
        if (name.equals("build")) return build(sender, args);
        sender.sendMessage("§cThis SMPPlatform module is not active because its required production adapter is unavailable.");
        return true;
    }

    /** Plot authority is verified on the main thread; PostgreSQL work is then performed asynchronously. */
    private boolean build(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) { sender.sendMessage("§cAtrium build commands must be run in-game."); return true; }
        AtriumSubmissionService submissions = atriumSubmissions;
        if (submissions == null) { player.sendMessage("§cBuild submissions require a healthy PostgreSQL connection. No submission was created."); return true; }
        if (args.length == 0) { player.sendMessage("§d/build submit <title> [description] §7— submit your current claimed Atrium plot."); if (player.hasPermission("smpplatform.admin.creative")) player.sendMessage("§d/build review <submission-id> <under_review|featured|rejected|archived> <note>"); return true; }
        if (args[0].equalsIgnoreCase("featured")) { openAtriumShowcase(player); return true; }
        if (args[0].equalsIgnoreCase("submit")) {
            if (args.length < 2) { player.sendMessage("§cUsage: /build submit <title> [description]"); return true; }
            String title = args[1]; String description = args.length > 2 ? String.join(" ", java.util.Arrays.copyOfRange(args, 2, args.length)) : title;
            try { submitAtriumBuild(player, title, description); }
            catch (IllegalArgumentException exception) { player.sendMessage("§c" + exception.getMessage()); }
            return true;
        }
        if (args[0].equalsIgnoreCase("review")) {
            if (!player.hasPermission("smpplatform.admin.creative")) { player.sendMessage("§cYou do not have permission to review Atrium submissions."); return true; }
            if (args.length == 1) { atriumReviewMenu.openQueue(player); return true; }
            if (args.length < 4) { player.sendMessage("§cUsage: /build review <submission-id> <under_review|featured|rejected|archived> <note>"); return true; }
            final UUID submissionId;
            try { submissionId = UUID.fromString(args[1]); } catch (IllegalArgumentException exception) { player.sendMessage("§cSubmission ID must be a UUID."); return true; }
            String state = args[2]; String note = String.join(" ", java.util.Arrays.copyOfRange(args, 3, args.length)); UUID playerId = player.getUniqueId();
            player.sendMessage("§dSaving build review…");
            executors.io().execute(() -> {
                try {
                    AtriumSubmissionService.Submission reviewed = reviewAtriumBuild(playerId, submissionId, state, note);
                    Bukkit.getScheduler().runTask(this, () -> { Player online = Bukkit.getPlayer(playerId); if (online != null) { online.sendMessage("§aBuild review saved: §f" + reviewed.title() + " §7→ §f" + reviewed.state()); publishBridgeEvent("BUILD_" + reviewed.state(), "atrium", online, "Build review: " + reviewed.title(), java.util.Map.of("submissionId", reviewed.id().toString(), "state", reviewed.state())); } });
                } catch (RuntimeException exception) { Bukkit.getScheduler().runTask(this, () -> { Player online = Bukkit.getPlayer(playerId); if (online != null) online.sendMessage("§cBuild review failed: " + rootMessage(exception)); }); }
            });
            return true;
        }
        player.sendMessage("§cUnknown build command. Use /build."); return true;
    }

    private static String rootMessage(Throwable error) { Throwable current = error; while (current.getCause() != null) current = current.getCause(); String message = current.getMessage(); return message == null || message.isBlank() ? "database error" : message; }

    /** Entry point used by both /build and the optional Fabric menu; the server still verifies PlotSquared authority. */
    public String submitAtriumBuild(Player player, String title, String description) {
        AtriumSubmissionService submissions = atriumSubmissions;
        if (submissions == null) throw new IllegalArgumentException("Build submissions require a healthy PostgreSQL connection. No submission was created.");
        AtriumSubmissionService.SubmissionContext context = submissions.verifyCurrentPlot(player);
        String effectiveDescription = description == null || description.isBlank() ? title : description;
        UUID playerId = player.getUniqueId(); String playerWorld = player.getWorld().getName();
        player.sendMessage("§dSaving your Atrium build submission…");
        executors.io().execute(() -> {
            try {
                AtriumSubmissionService.Submission saved = submissions.submit(context, title, effectiveDescription);
                Bukkit.getScheduler().runTask(this, () -> { Player online = Bukkit.getPlayer(playerId); if (online != null) { online.sendMessage("§aBuild submitted for review: §f" + saved.title() + " §7(" + saved.id() + ")"); publishBridgeEvent("BUILD_SUBMITTED", playerWorld, online, "Build submitted: " + saved.title(), java.util.Map.of("submissionId", saved.id().toString(), "plotId", saved.plotId())); } });
            } catch (RuntimeException exception) { Bukkit.getScheduler().runTask(this, () -> { Player online = Bukkit.getPlayer(playerId); if (online != null) online.sendMessage("§cBuild submission failed: " + rootMessage(exception)); }); }
        });
        return "Build submission sent. The server will confirm when it is saved.";
    }

    /** Must run on the IO executor. Feature rewards use a durable per-submission idempotency key. */
    public AtriumSubmissionService.Submission reviewAtriumBuild(UUID staffId, UUID submissionId, String state, String note) {
        AtriumSubmissionService service = atriumSubmissions;
        if (service == null) throw new IllegalStateException("Build reviews require a healthy PostgreSQL connection.");
        UUID submitter = service.submitterId(submissionId);
        AtriumSubmissionService.Submission reviewed = service.review(staffId, submissionId, state, note);
        if (reviewed.state().equals("FEATURED")) awardFeaturedBuild(submissionId, submitter);
        return reviewed;
    }

    /** Completes a Discord-issued, single-use linking code without exposing the Control Plane credential to players. */
    private boolean linkDiscordAccount(CommandSender sender, String[] args) {
        if (!(sender instanceof Player player)) { sender.sendMessage("§cLink your account while you are in-game."); return true; }
        if (args.length != 2 || !args[0].equalsIgnoreCase("link")) {
            player.sendMessage("§dDiscord link: run §f/link§d in Discord, then §f/kairu link <code>§d here within ten minutes.");
            return true;
        }
        String code = args[1].trim().toUpperCase(java.util.Locale.ROOT);
        if (!code.matches("[A-Z0-9_-]{20,128}")) { player.sendMessage("§cThat link code format is invalid. Generate a new code with /link in Discord."); return true; }
        if (configuration == null || !configuration.core().centralApi().enabled()) { player.sendMessage("§cDiscord linking is not configured on this server yet."); return true; }
        final String token;
        try { token = resolveCentralApiToken(); }
        catch (RuntimeException exception) { player.sendMessage("§cDiscord linking is temporarily unavailable. Ask a server administrator to check the Control Plane connection."); return true; }
        player.sendMessage("§dLinking your Minecraft account to Discord…");
        executors.io().execute(() -> completeDiscordLink(player.getUniqueId(), player.getName(), code, token));
        return true;
    }

    private void completeDiscordLink(UUID playerId, String playerName, String code, String token) {
        String outcome;
        try {
            JsonObject payload = new JsonObject();
            payload.addProperty("code", code); payload.addProperty("minecraftUuid", playerId.toString()); payload.addProperty("javaUsername", playerName);
            String base = configuration.core().centralApi().baseUrl().replaceAll("/$", "");
            HttpRequest request = HttpRequest.newBuilder(URI.create(base + "/api/link-codes/complete"))
                    .header("Content-Type", "application/json").header("Authorization", "Bearer " + token)
                    .header("X-Kairu-Server-Id", configuration.core().serverId())
                    .POST(HttpRequest.BodyPublishers.ofString(payload.toString(), StandardCharsets.UTF_8)).build();
            int status = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.discarding()).statusCode();
            outcome = status == 201 ? "§aDiscord account linked successfully." : status == 409 ? "§cThat code has expired, was already used, or this Minecraft account is already linked." : "§cDiscord linking could not be completed right now (HTTP " + status + ").";
        } catch (Exception exception) {
            getLogger().warning("Discord account link request failed: " + exception.getClass().getSimpleName());
            outcome = "§cDiscord linking could not reach the Control Plane. Please try again later.";
        }
        String message = outcome;
        Bukkit.getScheduler().runTask(this, () -> {
            Player online = Bukkit.getPlayer(playerId);
            if (online != null) online.sendMessage(message);
        });
    }

    /** Safe bootstrap for the seven registered worlds. Preview never changes the server; apply creates only missing worlds. */
    private boolean setup(CommandSender sender, String[] args) {
        if (!sender.isOp() && !sender.hasPermission("smpplatform.admin.setup")) {
            sender.sendMessage("§cYou do not have permission to run SMPPlatform setup.");
            return true;
        }
        String mode = args.length > 1 ? args[1].toLowerCase(java.util.Locale.ROOT) : "help";
        if (!mode.equals("preview") && !mode.equals("apply")) {
            sender.sendMessage("§dKairu SMP setup: use §f/kairuadmin setup preview §dor §f/kairuadmin setup apply§d.");
            return true;
        }
        if (registry == null) { sender.sendMessage("§cWorld Registry is not ready yet."); return true; }
        sender.sendMessage("§dKairu SMP setup " + mode + ":");
        int missing = 0;
        for (WorldDefinition definition : registry.snapshot().worlds().values()) {
            boolean loaded = Bukkit.getWorld(definition.minecraftWorldName()) != null;
            if (!loaded) missing++;
            sender.sendMessage("§7 - " + definition.displayName() + ": " + (loaded ? "§aavailable" : mode.equals("preview") ? "§emissing — will be created" : "§ecreating"));
            if (mode.equals("apply") && !loaded) createSetupWorld(definition, sender);
        }
        if (mode.equals("preview")) {
            sender.sendMessage("§7Missing worlds: §f" + missing + "§7. Apply creates only missing worlds; existing worlds are never overwritten.");
            sender.sendMessage("§7Spawn Hub policy, registered permissions and integration checks will be applied on §fapply§7.");
            return true;
        }
        World hub = Bukkit.getWorld(registry.require("spawn-hub").minecraftWorldName());
        if (hub != null) applySpawnHubPolicy(hub);
        sender.sendMessage("§aSetup applied. Spawn Hub policy is active; existing worlds were preserved.");
        sender.sendMessage("§7Atrium is created only as a superflat PlotSquared world. An existing Atrium is never converted or replaced.");
        return true;
    }

    private void createSetupWorld(WorldDefinition definition, CommandSender sender) {
        try {
            if (definition.id().equals("spawn-hub")) {
                // Bukkit always has a primary level. It is the Kairu Spawn Hub;
                // never make a second hub world just because its folder is named world.
                World primary = Bukkit.getWorlds().isEmpty() ? null : Bukkit.getWorlds().getFirst();
                if (primary != null) {
                    applySpawnHubPolicy(primary);
                    sender.sendMessage("§a   Adopted the server default world as Spawn Hub (" + primary.getName() + ").");
                    return;
                }
            }
            if (definition.id().equals("atrium")) {
                if (!definition.minecraftWorldName().equalsIgnoreCase("atrium") && Bukkit.getWorld("atrium") != null) {
                    sender.sendMessage("§e   An existing Atrium world is loaded. It was not replaced and no duplicate world was created.");
                    sender.sendMessage("§7   Back up and remove the old world deliberately before recreating The Atrium.");
                    return;
                }
                if (multiverse == null || !multiverse.available() || Bukkit.getPluginManager().getPlugin("PlotSquared") == null) {
                    sender.sendMessage("§c   The Atrium requires both Multiverse-Core and PlotSquared; it was not created as a normal world.");
                    return;
                }
                String worldName = definition.minecraftWorldName();
                boolean accepted = Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "mv create " + worldName + " normal --world-type flat -g PlotSquared");
                if (accepted && Bukkit.getWorld(worldName) != null) { sender.sendMessage("§a   Created The Atrium as a superflat PlotSquared world (128x128 plots). "); return; }
                sender.sendMessage("§c   PlotSquared did not create The Atrium. Check /mv generators and the server log; no normal fallback was used.");
                return;
            }
            WorldCreator creator = new WorldCreator(definition.minecraftWorldName());
            if (definition.id().equals("spawn-hub")) creator.type(WorldType.FLAT);
            World created = Bukkit.createWorld(creator);
            if (created == null) sender.sendMessage("§c   Could not create " + definition.displayName() + ". Check the server log.");
            else sender.sendMessage("§a   Created " + definition.displayName() + ".");
        } catch (RuntimeException exception) {
            getLogger().warning("Setup could not create " + definition.id() + ": " + exception.getMessage());
            sender.sendMessage("§c   Could not create " + definition.displayName() + "; see console.");
        }
    }

    private boolean isAtriumWorld(World world) {
        if (registry == null || world == null) return false;
        return registry.find("atrium").map(definition -> definition.minecraftWorldName().equalsIgnoreCase(world.getName())).orElse(false);
    }

    @SuppressWarnings("removal")
    private static void applySpawnHubPolicy(World hub) {
        hub.setPVP(false);
        hub.setDifficulty(Difficulty.PEACEFUL);
        hub.setGameRule(GameRule.DO_MOB_SPAWNING, false);
        hub.setGameRule(GameRule.DO_DAYLIGHT_CYCLE, false);
        hub.setGameRule(GameRule.DO_WEATHER_CYCLE, false);
    }

    public WorldRegistry worldRegistry() { return registry; }
    public MultiverseWorldAdapter multiverse() { return multiverse; }
    public AtriumSubmissionService atriumSubmissionService() { return atriumSubmissions; }
    public java.util.concurrent.ExecutorService io() { return executors.io(); }
    public void openAtriumReviewQueue(Player player) { if (atriumReviewMenu != null) atriumReviewMenu.openQueue(player); }
    public void openAtriumShowcase(Player player) { if (atriumShowcaseMenu != null) atriumShowcaseMenu.open(player); }
    public void awardFeaturedBuild(UUID submissionId, UUID recipient) { Phase3Runtime runtime = phase3; if (runtime != null) runtime.awardFeaturedBuild(submissionId, recipient); }
    public void clientView(Player player, String view, Consumer<JsonObject> callback) {
        PlatformGuildPointsClient service=platformGuildPoints;
        if(service==null){JsonObject x=new JsonObject();x.addProperty("error","Platform services are unavailable. Check the Control Plane connection.");callback.accept(x);return;}
        executors.io().execute(()->{try{callback.accept(service.view(player.getUniqueId(),view));}catch(Exception e){JsonObject x=new JsonObject();x.addProperty("error",e.getMessage());callback.accept(x);}});
    }
    public void clientGuildAction(Player player, String action, UUID targetId, Consumer<JsonObject> callback) {
        PlatformGuildPointsClient service=platformGuildPoints;
        if(service==null){JsonObject x=new JsonObject();x.addProperty("error","Guilds are unavailable because the Control Plane cannot be reached.");callback.accept(x);return;}
        executors.io().execute(()->{try{callback.accept(service.guildAction(player.getUniqueId(),action,targetId));}catch(Exception e){JsonObject x=new JsonObject();x.addProperty("error",e.getMessage());callback.accept(x);}});
    }
    public void clientGuildCreate(Player player, String name, String tag, String description, Consumer<JsonObject> callback) {
        PlatformGuildPointsClient service=platformGuildPoints;
        if(service==null){JsonObject x=new JsonObject();x.addProperty("error","Guilds are unavailable because the Control Plane cannot be reached.");callback.accept(x);return;}
        executors.io().execute(()->{try{callback.accept(service.createGuild(player.getUniqueId(),name,tag,description));}catch(Exception e){JsonObject x=new JsonObject();x.addProperty("error",e.getMessage());callback.accept(x);}});
    }
    public JsonObject clientPlayerProfile(UUID targetId) {
        if(clientPlayerAdmin==null){JsonObject out=new JsonObject();out.addProperty("error","Player administration is unavailable.");return out;}
        return clientPlayerAdmin.profile(targetId);
    }

    public JsonObject clientHardcoreResetStatus(){return hardcoreResetWindow.status();}
    public String clientHardcoreResetConfigure(Player actor,String open,String close){
        if(!actor.hasPermission("smpplatform.admin.hardcore.reset-window")&&!actor.hasPermission("smpplatform.admin.hardcore.*"))
            throw new SecurityException("Missing permission: smpplatform.admin.hardcore.reset-window");
        hardcoreResetWindow.configure(java.time.Instant.parse(open),java.time.Instant.parse(close));
        return "Hardcore reset window updated.";
    }

    public JsonObject clientHardcoreLookup(UUID target)throws Exception{return hardcoreAdmin.lookup(target);}
    public String clientHardcoreState(Player actor,UUID target,String state,String reason)throws Exception{
        String perm="smpplatform.admin.hardcore."+state.toLowerCase(java.util.Locale.ROOT);
        if(!actor.hasPermission(perm)&&!actor.hasPermission("smpplatform.admin.hardcore.*"))throw new SecurityException("Missing permission: "+perm);
        if(state.equals("RESET_ELIGIBLE") && !hardcoreResetWindow.isOpen())
            throw new IllegalStateException("The Hardcore reset window is closed.");
        JsonObject previous=hardcoreAdmin.lookup(target);
        String previousState=previous.has("state")?previous.get("state").getAsString():"UNKNOWN";
        if(state.equals("ALIVE") && !(previousState.equals("DEAD")||previousState.equals("SPECTATING")||previousState.equals("RESET_ELIGIBLE")||previousState.equals("LOCKED")))
            throw new IllegalStateException("Revival requires a dead, spectating, reset-eligible or locked Hardcore state.");
        hardcoreAdmin.setState(target,state,actor.getUniqueId(),reason);
        Player online=Bukkit.getPlayer(target);
        if(online!=null){
            if(state.equals("ALIVE")){online.setGameMode(GameMode.SURVIVAL);online.setHealth(Math.min(online.getMaxHealth(),20.0));}
            else if(state.equals("DEAD")||state.equals("SPECTATING")||state.equals("LOCKED"))online.setGameMode(GameMode.SPECTATOR);
        }
        return "Hardcore state set to "+state+".";
    }

    public JsonObject clientPointHistory(String target,String currency,int limit)throws Exception{if(guildPointsQuery==null)throw new IllegalStateException("Legacy local admin query is disabled; use the Control Plane player points view.");return guildPointsQuery.pointHistory(target,currency,limit);}
    public JsonObject clientPointLeaderboard(String currency,int limit)throws Exception{if(guildPointsQuery==null)throw new IllegalStateException("Legacy local admin leaderboard is disabled; use the Control Plane points view.");return guildPointsQuery.leaderboard(currency,limit);}
    public JsonObject clientGuildSearch(String query,int limit)throws Exception{if(guildPointsQuery==null)throw new IllegalStateException("Legacy local guild search is disabled; use the Control Plane guild directory.");return guildPointsQuery.guildSearch(query,limit);}

    public JsonObject clientGuildPointsTarget(UUID target){return guildPointsAdmin.playerSummary(target);}
    public String clientGuildPointsAdmin(Player actor,String area,String operation,String target,String value,String reason){
        return guildPointsAdmin.dispatch(actor,area,operation,target,value,reason);
    }

    public String clientWorldMaintenance(Player actor,String world,boolean enabled){
        if(Bukkit.getWorld(world)==null)throw new IllegalArgumentException("World is not loaded.");
        worldMaintenance.set(actor,world,enabled);
        if(enabled){
            for(Player p:new java.util.ArrayList<>(Bukkit.getWorld(world).getPlayers()))
                if(!p.hasPermission("smpplatform.admin.worlds.maintenance.bypass"))
                    p.teleport(Bukkit.getWorlds().get(0).getSpawnLocation());
        }
        return "Maintenance "+(enabled?"enabled":"disabled")+" for "+world+".";
    }

    public JsonObject clientWorldAdminSummary(){return clientWorldAdmin.summary();}
    public String clientWorldAdminAction(Player actor,String world,String action,String argument){
        return clientWorldAdmin.execute(actor,world,action,argument==null?"":argument);
    }

    public JsonObject clientInventorySlot(UUID target, boolean ender, int slot){return clientPlayerAdmin.inventorySlot(target,ender,slot);}

    public String clientInventoryEdit(Player actor,UUID target,boolean ender,String edit,int from,Integer to,String fingerprint){
        String result;
        if(edit.equals("remove")) result=clientPlayerAdmin.removeInventorySlot(actor,target,ender,from,fingerprint);
        else if(edit.equals("move")&&to!=null) result=clientPlayerAdmin.moveInventorySlot(actor,target,ender,from,to,fingerprint);
        else throw new IllegalArgumentException("Unsupported inventory edit.");
        String type=ender?"ENDER_CHEST":"INVENTORY";
        executors.io().execute(()->{try{inventoryAudit.record(actor.getUniqueId(),target,type,edit,from,to,fingerprint);}catch(Exception ignored){}});
        return result;
    }

    public String clientAdminPrepare(Player actor, UUID target, String action, String argument){
        adminConfirmations.prepare(actor.getUniqueId(),target,action,argument==null?"":argument);
        return "Confirm "+action+" within 30 seconds.";
    }

    public String clientAdminConfirm(Player actor, UUID target, String action){
        var pending=adminConfirmations.consume(actor.getUniqueId(),target,action);
        if(pending==null)return "No matching confirmation is active.";
        if(action.equals("clear-inventory"))return clientPlayerAdminAction(actor,target,"clear-inventory","");
        if(action.startsWith("inventory-remove:")){
            String[] parts=action.split(":",4);
            boolean ender=Boolean.parseBoolean(parts[1]); int slot=Integer.parseInt(parts[2]); String fingerprint=parts[3];
            return clientInventoryEdit(actor,target,ender,"remove",slot,null,fingerprint);
        }
        throw new IllegalArgumentException("Unsupported confirmed action.");
    }

    public void clientModerationHistory(UUID target, java.util.function.Consumer<JsonObject> callback){
        executors.io().execute(()->callback.accept(moderationRepository.history(target,50)));
    }

    public void clientModerate(Player actor, UUID target, String action, String reason, long minutes, java.util.function.Consumer<String> callback){
        String perm="smpplatform.admin.players."+action;
        if(!actor.hasPermission(perm) && !actor.hasPermission("smpplatform.admin.players.*")){callback.accept("Missing permission: "+perm);return;}
        UUID actorId=actor.getUniqueId();
        java.time.Instant expiry=minutes>0?java.time.Instant.now().plusSeconds(minutes*60):null;
        Bukkit.getScheduler().runTask(this,()->{
            try{
                org.bukkit.OfflinePlayer offline=Bukkit.getOfflinePlayer(target);
                switch(action){
                    case "warn" -> { var p=offline.getPlayer(); if(p!=null)p.sendMessage("Staff warning: "+reason); }
                    case "mute","temp-mute" -> moderationListener.mute(target,expiry);
                    case "unmute" -> moderationListener.unmute(target);
                    case "kick" -> { var p=offline.getPlayer(); if(p==null)throw new IllegalStateException("Kick requires the player to be online."); p.kickPlayer(reason); }
                    case "ban","temp-ban" -> {
                        java.util.Date expires=expiry==null?null:java.util.Date.from(expiry);
                        Bukkit.getBanList(org.bukkit.BanList.Type.NAME).addBan(offline.getName(),reason,expires,actor.getName());
                        var p=offline.getPlayer();if(p!=null)p.kickPlayer(reason);
                    }
                    case "unban" -> { if(offline.getName()!=null)Bukkit.getBanList(org.bukkit.BanList.Type.NAME).pardon(offline.getName()); }
                    default -> throw new IllegalArgumentException("Unsupported moderation action.");
                }
                executors.io().execute(()->{
                    try{moderationRepository.record(target,actorId,action,reason,expiry);
                        Bukkit.getScheduler().runTask(this,()->callback.accept("Moderation action recorded: "+action+"."));
                    }catch(Exception e){Bukkit.getScheduler().runTask(this,()->callback.accept("Action applied, but audit persistence failed."));}
                });
            }catch(Exception e){callback.accept(e.getMessage()==null?"Moderation action failed.":e.getMessage());}
        });
    }

    public JsonObject clientPlayerInventory(UUID targetId, boolean ender) {
        if(clientPlayerAdmin==null){JsonObject out=new JsonObject();out.addProperty("error","Player administration is unavailable.");return out;}
        return clientPlayerAdmin.inventory(targetId,ender);
    }

    public String clientPlayerAdminAction(Player actor, UUID targetId, String action, String argument) {
        if(clientPlayerAdmin==null) throw new IllegalStateException("Player administration is unavailable.");
        return clientPlayerAdmin.execute(actor,targetId,action,argument);
    }

    public String clientAuctionBidPrepare(Player player, String listingId, double amount) {
        if(amount<1)return "Bid must be at least 1.";
        auctionConfirmations.put(player.getUniqueId(),"bid",listingId+"|"+amount);
        return "Bid "+amount+"? Press Confirm Bid within 30 seconds.";
    }

    public void clientAuctionBidConfirm(Player player, java.util.function.Consumer<String> callback) {
        var pending=auctionConfirmations.consume(player.getUniqueId(),"bid");
        if(pending==null){callback.accept("No active bid confirmation.");return;}
        String[] bits=pending.payload().split("\\|",2);
        if(bits.length!=2){callback.accept("The saved bid confirmation is invalid. Start the bid again.");return;}
        UUID listingId;
        double amount;
        try{listingId=UUID.fromString(bits[0]);amount=Double.parseDouble(bits[1]);}
        catch(IllegalArgumentException exception){callback.accept("The saved bid confirmation is invalid. Start the bid again.");return;}
        UUID playerId=player.getUniqueId();
        executors.io().execute(()->{
            com.neonnexus.smpplatform.auction.AuctionService.BidResult result=null;
            try{
                UUID bidder=auctionIdentity.minecraftAccountId(playerId);
                result=auctionService.placeBid(listingId,bidder,amount);
                var economy=com.neonnexus.smpplatform.auction.AuctionService.economy();
                Player online=Bukkit.getPlayer(playerId);
                if(economy==null || online==null){
                    auctionService.revertBid(listingId,bidder,amount,result.previousBidder(),result.previousBid());
                    throw new IllegalStateException("Vault economy/player connection is unavailable.");
                }
                var withdrawal=economy.withdrawPlayer(online,amount);
                if(!withdrawal.transactionSuccess()){
                    auctionService.revertBid(listingId,bidder,amount,result.previousBidder(),result.previousBid());
                    throw new IllegalStateException("Bid payment failed: "+withdrawal.errorMessage);
                }
                Bukkit.getScheduler().runTask(this,()->callback.accept("Bid accepted for "+amount+". Outbid funds are returned through Collect."));
            }catch(Exception e){Bukkit.getScheduler().runTask(this,()->callback.accept(e.getMessage()));}
        });
    }

    public String clientAuctionSellPrepare(Player player, double price) {
        if(price<1) return "Price must be at least 1.";
        var held=player.getInventory().getItemInMainHand();
        if(held==null || held.getType().isAir()) return "Hold the item you want to sell.";
        String payload=price+"|"+java.util.Base64.getEncoder().encodeToString(held.serializeAsBytes());
        auctionConfirmations.put(player.getUniqueId(),"sell",payload);
        return "Sell "+held.getAmount()+"x "+held.getType().getKey().asString()+" for "+price+"? Press Sell again within 30 seconds to confirm.";
    }

    public void clientAuctionSellConfirm(Player player, java.util.function.Consumer<String> callback) {
        var pending=auctionConfirmations.consume(player.getUniqueId(),"sell");
        if(pending==null){callback.accept("No active sell confirmation. Enter a price and press Sell first.");return;}
        String[] parts=pending.payload().split("\\|",2);
        if(parts.length!=2){callback.accept("The saved sale confirmation is invalid. Enter the price again.");return;}
        double price;
        byte[] expected;
        try{
            price=Double.parseDouble(parts[0]);
            expected=java.util.Base64.getDecoder().decode(parts[1]);
        }catch(IllegalArgumentException exception){
            callback.accept("The saved sale confirmation is invalid. Enter the price again.");
            return;
        }
        var current=player.getInventory().getItemInMainHand();
        if(current==null || current.getType().isAir() || !java.util.Arrays.equals(expected,current.serializeAsBytes())){
            callback.accept("The held item changed. Sale cancelled."); return;
        }
        UUID playerId=player.getUniqueId(); var removed=current.clone();
        player.getInventory().setItemInMainHand(null);
        executors.io().execute(() -> {
            try{
                UUID account=auctionIdentity.minecraftAccountId(playerId);
                UUID listing=auctionService.createFixed(account,removed,price,java.time.Instant.now().plus(java.time.Duration.ofHours(48)));
                Bukkit.getScheduler().runTask(this,()->callback.accept("Listing created: "+listing+"."));
            }catch(Exception e){
                Bukkit.getScheduler().runTask(this,()->{
                    Player online=Bukkit.getPlayer(playerId);
                    if(online!=null){
                        var leftovers=online.getInventory().addItem(removed);
                        leftovers.values().forEach(item->online.getWorld().dropItemNaturally(online.getLocation(),item));
                    }
                    callback.accept("Listing failed; your item was returned. "+e.getMessage());
                });
            }
        });
    }

    public void clientAuctionCancel(Player player, UUID listingId, java.util.function.Consumer<String> callback) {
        UUID playerId=player.getUniqueId();
        executors.io().execute(() -> {
            try{
                UUID account=auctionIdentity.minecraftAccountId(playerId);
                JsonObject result=auctionService.cancel(listingId,account);
                Bukkit.getScheduler().runTask(this,()->callback.accept(result.get("message").getAsString()));
            }catch(Exception e){Bukkit.getScheduler().runTask(this,()->callback.accept(e.getMessage()));}
        });
    }

    public String clientAuctionBuyPrepare(Player player, String listingId) {
        auctionConfirmations.put(player.getUniqueId(),"buy",listingId);
        return "Purchase prepared. Press Buy again within 30 seconds to confirm.";
    }

    public void clientAuctionBuyConfirm(Player player, java.util.function.Consumer<String> callback) {
        var pending=auctionConfirmations.consume(player.getUniqueId(),"buy");
        if(pending==null){callback.accept("No active purchase confirmation. Select Buy first.");return;}
        UUID playerId=player.getUniqueId();
        executors.io().execute(() -> {
            com.neonnexus.smpplatform.auction.AuctionService.PurchaseReservation reservation=null;
            try{
                UUID buyer=auctionIdentity.minecraftAccountId(playerId);
                reservation=auctionService.reserveFixedPurchase(UUID.fromString(pending.payload()),buyer);
                var economy=com.neonnexus.smpplatform.auction.AuctionService.economy();
                if(economy==null){auctionService.releaseReservation(reservation.listingId()); throw new IllegalStateException("Vault economy is unavailable.");}
                Player online=Bukkit.getPlayer(playerId);
                if(online==null){auctionService.releaseReservation(reservation.listingId()); throw new IllegalStateException("You disconnected before the purchase completed.");}
                var withdrawal=economy.withdrawPlayer(online,reservation.price());
                if(!withdrawal.transactionSuccess()){auctionService.releaseReservation(reservation.listingId()); throw new IllegalStateException("Payment failed: "+withdrawal.errorMessage);}
                try{auctionService.completeFixedPurchase(reservation,buyer);}
                catch(Exception databaseFailure){
                    economy.depositPlayer(online,reservation.price());
                    auctionService.releaseReservation(reservation.listingId());
                    throw databaseFailure;
                }
                Bukkit.getScheduler().runTask(this,()->callback.accept("Purchase complete. Use Collect to receive the item."));
            }catch(Exception e){
                var reserved=reservation;
                if(reserved!=null) try{auctionService.releaseReservation(reserved.listingId());}catch(Exception ignored){}
                Bukkit.getScheduler().runTask(this,()->callback.accept(e.getMessage()));
            }
        });
    }

    public void clientAuctionMine(Player player, java.util.function.Consumer<JsonObject> callback) {
        executors.io().execute(() -> {
            try { callback.accept(auctionService.myListings(auctionIdentity.minecraftAccountId(player.getUniqueId()),25)); }
            catch(Exception e){ JsonObject out=new JsonObject(); out.addProperty("error",e.getMessage()); out.add("auctionListings",new com.google.gson.JsonArray()); callback.accept(out); }
        });
    }

    public void clientAuctionCollect(Player player, java.util.function.Consumer<String> callback) {
        UUID playerId=player.getUniqueId();
        executors.io().execute(() -> {
            UUID account=null;
            try {
                account=auctionIdentity.minecraftAccountId(playerId);
                java.util.List<org.bukkit.inventory.ItemStack> items=auctionService.claimPendingItems(account);
                double money=auctionService.claimPendingMoney(account);
                UUID finalAccount=account;
                Bukkit.getScheduler().runTask(this, () -> {
                    Player online=Bukkit.getPlayer(playerId);
                    if(online==null){
                        executors.io().execute(()->{try{auctionService.finishClaims(finalAccount,false);auctionService.finishMoneyClaims(finalAccount,false);}catch(Exception ignored){}});
                        return;
                    }
                    boolean moneyOk=true;
                    if(money>0){
                        var economy=com.neonnexus.smpplatform.auction.AuctionService.economy();
                        if(economy==null) moneyOk=false;
                        else moneyOk=economy.depositPlayer(online,money).transactionSuccess();
                    }
                    for(var item:items){
                        var leftovers=online.getInventory().addItem(item);
                        leftovers.values().forEach(left->online.getWorld().dropItemNaturally(online.getLocation(),left));
                    }
                    boolean finalMoneyOk=moneyOk;
                    executors.io().execute(()->{try{auctionService.finishClaims(finalAccount,true);auctionService.finishMoneyClaims(finalAccount,finalMoneyOk);}catch(Exception ignored){}});
                    callback.accept("Collected "+items.size()+" item delivery(s)"+(money>0?(moneyOk?" and "+money+" in proceeds.":"; money remains pending because Vault payment failed.") : "."));
                });
            } catch(Exception e){ Bukkit.getScheduler().runTask(this,()->callback.accept(e.getMessage())); }
        });
    }

    public void clientAuctionBrowse(String query, java.util.function.Consumer<JsonObject> callback) {
        AuctionService auction=auctionService;
        if(auction==null){ JsonObject unavailable=new JsonObject(); unavailable.addProperty("error","Auction requires the Control Plane API."); unavailable.add("auctionListings",new com.google.gson.JsonArray()); callback.accept(unavailable); return; }
        executors.io().execute(() -> callback.accept(auction.browse(query,25)));
    }

    public void clientPlayerSearch(String query, String filter, java.util.function.Consumer<JsonObject> callback) {
        PlayerSearchService search = playerSearch;
        if (search == null) {
            JsonObject unavailable = new JsonObject();
            unavailable.addProperty("error", "Player search requires a healthy PostgreSQL connection.");
            unavailable.add("searchResults", new com.google.gson.JsonArray());
            callback.accept(unavailable);
            return;
        }
        executors.io().execute(() -> callback.accept(search.search(query, filter, 25)));
    }

    public void clientPointsView(Player player, String view, String currency, Consumer<JsonObject> callback) {
        PlatformGuildPointsClient service=platformGuildPoints;
        if(service==null){JsonObject x=new JsonObject();x.addProperty("error","Points are unavailable because the Control Plane cannot be reached.");callback.accept(x);return;}
        executors.io().execute(()->{try{callback.accept(service.pointsView(player.getUniqueId(),view,currency));}catch(Exception e){JsonObject x=new JsonObject();x.addProperty("error",e.getMessage());callback.accept(x);}});
    }

    public JsonObject clientAuctionDatabaseStatus(Player actor) {
        if (!actor.hasPermission("smpplatform.auction.browse") && !actor.hasPermission("smpplatform.auction.admin"))
            throw new SecurityException("You do not have permission to inspect the auction.");
        if (auctionService == null) {
            JsonObject out=new JsonObject();
            out.addProperty("connected",false);
            out.addProperty("schemaReady",false);
            out.addProperty("error","Auction service is not active because the Control Plane API is unavailable.");
            return out;
        }
        return auctionService.databaseStatus();
    }

}
