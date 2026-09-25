package com.neonnexus.smpplatform;

import com.neonnexus.smpplatform.async.PlatformExecutors;
import com.neonnexus.smpplatform.auction.AuctionService;
import com.neonnexus.smpplatform.atrium.AtriumPlotFlagsMenu;
import com.neonnexus.smpplatform.atrium.AtriumReviewMenu;
import com.neonnexus.smpplatform.atrium.AtriumShowcaseMenu;
import com.neonnexus.smpplatform.atrium.AtriumSubmissionService;
import com.neonnexus.smpplatform.client.KairuClientGateway;
import com.neonnexus.smpplatform.config.PlatformConfiguration;
import com.neonnexus.smpplatform.config.PlatformConfigurationLoader;
import com.neonnexus.smpplatform.database.DatabaseService;
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
    private volatile AtriumSubmissionService atriumSubmissions;
    private AtriumReviewMenu atriumReviewMenu;
    private AtriumShowcaseMenu atriumShowcaseMenu;
    private KairuClientGateway clientGateway;
    private AuctionService auctionService;
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
            new com.neonnexus.smpplatform.luckperms.LuckPermsProvisioner(this).provision();
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
        database.start();
        if (!database.isAvailable()) return;
        phase3 = Phase3Runtime.start(this, database.requireDataSource(), executors.io(), Clock.systemUTC(), configuration.points().firstJoinReward(), configuration.points().featuredBuildReward(), configuration.guilds());
        auctionService = new AuctionService(this, database.requireDataSource(), executors.io());
        executors.scheduler().scheduleWithFixedDelay(auctionService::settleExpiredAuctions, 15, 15, TimeUnit.SECONDS);
        atriumSubmissions = new AtriumSubmissionService(database.requireDataSource(), registry.require("atrium").minecraftWorldName());
        getLogger().info("Durable guild and points modules are active.");
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
        if (name.equals("auction")) {
            if (auctionService == null) { sender.sendMessage("§cAuction service is unavailable."); return true; }
            if (!(sender instanceof Player player)) { sender.sendMessage("§cAuction commands must be run in-game."); return true; }
            auctionService.execute(player, args);
            return true;
        }
        if (name.equals("search")) return searchPlayers(sender, args);
        if (name.equals("guild") || name.equals("points")) {
            Phase3Runtime runtime = phase3;
            if (runtime == null) {
                sender.sendMessage("§cDurable gameplay services are unavailable. No mutation was attempted.");
                return true;
            }
            return runtime.execute(sender, name, args);
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

    private boolean searchPlayers(CommandSender sender, String[] args) {
        if (!sender.hasPermission("smpplatform.search.use") && !sender.isOp()) {
            sender.sendMessage("§cYou do not have permission to search players.");
            return true;
        }
        if (args.length == 0 || args[0].isBlank()) {
            sender.sendMessage("§d/search <name>");
            return true;
        }
        String query = String.join(" ", args).trim().toLowerCase(java.util.Locale.ROOT);
        boolean seeHidden = sender.hasPermission("smpplatform.search.see-hidden") || sender.isOp();
        int found = 0;
        for (org.bukkit.OfflinePlayer target : Bukkit.getOfflinePlayers()) {
            String name = target.getName();
            if (name == null || !name.toLowerCase(java.util.Locale.ROOT).contains(query)) continue;
            if (!seeHidden && !target.isOnline()) continue;
            if (found++ >= 20) break;
            String status = target.isOnline() ? "§aonline" : "§7offline";
            sender.sendMessage("§f" + name + " §8(" + status + "§8)");
        }
        if (found == 0) sender.sendMessage("§7No matching players found.");
        else if (found >= 20) sender.sendMessage("§7Showing the first 20 matches.");
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
    public AuctionService auctionService() { return auctionService; }

    public void clientView(Player player, String view, Consumer<JsonObject> callback) {
        Phase3Runtime runtime = phase3;
        if (runtime == null) { JsonObject unavailable = new JsonObject(); unavailable.addProperty("error", "Guilds and points require a healthy PostgreSQL connection."); callback.accept(unavailable); return; }
        runtime.clientView(player, view, callback);
    }
    public void clientGuildAction(Player player, String action, UUID targetId, Consumer<JsonObject> callback) {
        Phase3Runtime runtime = phase3;
        if (runtime == null) { JsonObject unavailable = new JsonObject(); unavailable.addProperty("error", "Guilds require a healthy PostgreSQL connection."); callback.accept(unavailable); return; }
        runtime.clientGuildAction(player, action, targetId, callback);
    }
    public void clientGuildCreate(Player player, String name, String tag, String description, Consumer<JsonObject> callback) {
        Phase3Runtime runtime = phase3;
        if (runtime == null) { JsonObject unavailable = new JsonObject(); unavailable.addProperty("error", "Guilds and points require a healthy PostgreSQL connection."); callback.accept(unavailable); return; }
        runtime.clientGuildCreate(player, name, tag, description, callback);
    }
    public void clientPointsView(Player player, String view, String currency, Consumer<JsonObject> callback) {
        Phase3Runtime runtime = phase3;
        if (runtime == null) { JsonObject unavailable = new JsonObject(); unavailable.addProperty("error", "Points require a healthy PostgreSQL connection."); callback.accept(unavailable); return; }
        runtime.clientPointsView(player, view, currency, callback);
    }
}
