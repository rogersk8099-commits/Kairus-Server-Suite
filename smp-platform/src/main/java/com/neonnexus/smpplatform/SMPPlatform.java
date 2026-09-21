package com.neonnexus.smpplatform;

import com.neonnexus.smpplatform.async.PlatformExecutors;
import com.neonnexus.smpplatform.atrium.AtriumPlotFlagsMenu;
import com.neonnexus.smpplatform.client.KairuClientGateway;
import com.neonnexus.smpplatform.config.PlatformConfiguration;
import com.neonnexus.smpplatform.config.PlatformConfigurationLoader;
import com.neonnexus.smpplatform.database.DatabaseService;
import com.neonnexus.smpplatform.identity.FloodgateIdentityAdapter;
import com.neonnexus.smpplatform.listeners.PlayerIdentityListener;
import com.neonnexus.smpplatform.listeners.ControlPlaneBridgeListener;
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
import com.google.gson.JsonObject;

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
    private KairuClientGateway clientGateway;
    private volatile String centralApiToken;
    private Instant startedAt;

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
            MultiverseInventoryAdapter inventories = new MultiverseInventoryAdapter(getServer().getPluginManager(), getLogger());
            inventories.validateDesiredGroups(registry.snapshot().worlds().values(), configuration.core().inventory().policy());
            FloodgateIdentityAdapter identities = FloodgateIdentityAdapter.discover(getServer().getPluginManager(), getLogger());
            getServer().getPluginManager().registerEvents(new PlayerIdentityListener(identities, getLogger()), this);
            getServer().getPluginManager().registerEvents(new ControlPlaneBridgeListener(this), this);
            database = new DatabaseService(configuration.core().database(), getLogger());
            executors.io().execute(this::startDatabaseServicesAsync);
            if (configuration.core().centralApi().enabled()) {
                startCentralSync();
                startHeartbeat();
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
        phase3 = Phase3Runtime.start(this, database.requireDataSource(), executors.io(), Clock.systemUTC(), configuration.points().firstJoinReward(), configuration.guilds());
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
            changed.add(override == null ? world : new WorldDefinition(world.id(), override.minecraftWorldName(), world.displayName(), world.description(), world.type(), world.season(), world.status(), world.difficulty(), world.borderSize(), world.pvpMode(), world.guildsEnabled(), world.pointsEnabled(), world.currencyId(), world.claimsEnabled(), world.economyEnabled(), world.inventoryGroup(), world.resetPolicy(), world.archivePolicy(), world.discordEnabled(), world.websiteVisible(), world.mapVisible(), world.playerCount(), override.maintenanceMode(), world.accessPermission(), new com.neonnexus.smpplatform.world.SpawnLocation(override.minecraftWorldName(), world.spawnLocation().x(), world.spawnLocation().y(), world.spawnLocation().z(), world.spawnLocation().yaw(), world.spawnLocation().pitch())));
        }
        return new RegistryDocument(Math.max(bundled.revision(), configuration.worlds().initialRevision()), Instant.now(), changed);
    }

    @Override public void onDisable() {
        publishBridgeEvent("SERVER_STOPPING", null, null, "Kairu SMP stopping", java.util.Map.of());
        if (executors != null) executors.close();
        if (database != null) database.close();
        getLogger().info("SMPPlatform shutdown complete.");
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
        if (name.equals("plotflags")) {
            if (!(sender instanceof org.bukkit.entity.Player player)) { sender.sendMessage("Open plot settings in-game."); return true; }
            if (!player.getWorld().getName().equalsIgnoreCase("atrium")) { player.sendMessage("§cThe Atrium plot settings guide is available only in The Atrium."); return true; }
            AtriumPlotFlagsMenu.open(player);
            return true;
        }
        sender.sendMessage("§cThis SMPPlatform module is not active because its required production adapter is unavailable.");
        return true;
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
        World hub = Bukkit.getWorld("spawn-hub");
        if (hub != null) applySpawnHubPolicy(hub);
        sender.sendMessage("§aSetup applied. Spawn Hub policy is active; existing worlds were preserved.");
        sender.sendMessage("§7PlotSquared: verify Atrium uses the PlotSquared generator before building. LuckPerms groups remain administrator-managed.");
        return true;
    }

    private void createSetupWorld(WorldDefinition definition, CommandSender sender) {
        try {
            if (definition.id().equals("atrium") && multiverse != null && multiverse.available()) {
                boolean accepted = Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "mv create atrium normal -g PlotSquared");
                if (accepted && Bukkit.getWorld("atrium") != null) { sender.sendMessage("§a   Created The Atrium with PlotSquared generator."); return; }
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
    public void clientPointsView(Player player, String view, String currency, Consumer<JsonObject> callback) {
        Phase3Runtime runtime = phase3;
        if (runtime == null) { JsonObject unavailable = new JsonObject(); unavailable.addProperty("error", "Points require a healthy PostgreSQL connection."); callback.accept(unavailable); return; }
        runtime.clientPointsView(player, view, currency, callback);
    }
}
