package com.neonnexus.smpplatform;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.neonnexus.smpplatform.outbox.JdbcOutboxRepository;
import com.neonnexus.smpplatform.outbox.OutboxEvent;
import com.neonnexus.smpplatform.outbox.OutboxRepository;
import com.neonnexus.smpplatform.config.PlatformConfiguration;
import gg.neonnexus.smpplatform.phase3.command.CommandReply;
import gg.neonnexus.smpplatform.phase3.command.GuildCommandHandler;
import gg.neonnexus.smpplatform.phase3.command.PlayerDirectory;
import gg.neonnexus.smpplatform.phase3.command.PointsCommandHandler;
import gg.neonnexus.smpplatform.phase3.common.Actor;
import gg.neonnexus.smpplatform.phase3.common.AuditSink;
import gg.neonnexus.smpplatform.phase3.events.Phase3EventPublisher;
import gg.neonnexus.smpplatform.phase3.guild.Guild;
import gg.neonnexus.smpplatform.phase3.guild.GuildMember;
import gg.neonnexus.smpplatform.phase3.guild.GuildService;
import gg.neonnexus.smpplatform.phase3.jdbc.JdbcGuildRepository;
import gg.neonnexus.smpplatform.phase3.jdbc.JdbcPointsRepository;
import gg.neonnexus.smpplatform.phase3.jdbc.JdbcTransactionRunner;
import gg.neonnexus.smpplatform.phase3.points.PointsDomain.PointTransaction;
import gg.neonnexus.smpplatform.phase3.points.PointsDomain.Source;
import gg.neonnexus.smpplatform.phase3.points.PointsService;
import gg.neonnexus.smpplatform.phase3.world.NeonWorld;
import gg.neonnexus.smpplatform.phase3.world.WorldPolicy;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.plugin.java.JavaPlugin;

import javax.sql.DataSource;
import java.sql.PreparedStatement;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.logging.Level;

/**
 * Production bridge for the Phase 3 guild and points modules. Bukkit state is captured on the
 * primary thread, all service/JDBC work runs on the platform IO executor, and only immutable
 * replies are marshalled back to Paper's scheduler.
 */
final class Phase3Runtime implements Listener {
    private static final Set<String> ACTOR_PERMISSIONS = Set.of(
            "smpplatform.admin", "smpplatform.admin.guilds", "smpplatform.admin.points",
            "smpplatform.guild.manage", "smpplatform.guild.create", "smpplatform.guild.use",
            "smpplatform.points.view");

    private final JavaPlugin plugin;
    private final Executor io;
    private final GuildCommandHandler guilds;
    private final PointsCommandHandler points;
    private final GuildService guildService;
    private final PointsService pointsService;
    private final String guildCreationCurrency;
    private final long guildCreationCost;
    private final Map<String, UUID> playersByName = new ConcurrentHashMap<>();
    private final Map<UUID, PendingGuildTransfer> pendingGuildTransfers = new ConcurrentHashMap<>();
    private final PlatformConfiguration.Points.AutomaticReward firstJoinReward;
    private final PlatformConfiguration.Points.AutomaticReward featuredBuildReward;
    private static final Duration GUILD_TRANSFER_CONFIRMATION_TTL = Duration.ofSeconds(30);
    private record PendingGuildTransfer(UUID successorId, Instant expiresAt) { }

    private Phase3Runtime(JavaPlugin plugin, Executor io, GuildCommandHandler guilds,
                          PointsCommandHandler points, GuildService guildService, PointsService pointsService, PlatformConfiguration.Points.AutomaticReward firstJoinReward, PlatformConfiguration.Points.AutomaticReward featuredBuildReward, PlatformConfiguration.Guilds guildConfiguration) {
        this.plugin = plugin;
        this.io = io;
        this.guilds = guilds;
        this.points = points;
        this.guildService = guildService;
        this.pointsService = pointsService;
        this.firstJoinReward = firstJoinReward;
        this.featuredBuildReward = featuredBuildReward;
        this.guildCreationCurrency = guildConfiguration.creationCurrency();
        this.guildCreationCost = guildConfiguration.creationCost();
    }

    static Phase3Runtime start(JavaPlugin plugin, DataSource dataSource, Executor io, Clock clock, PlatformConfiguration.Points.AutomaticReward firstJoinReward, PlatformConfiguration.Points.AutomaticReward featuredBuildReward, PlatformConfiguration.Guilds guildConfiguration) {
        JdbcTransactionRunner transactions = new JdbcTransactionRunner(dataSource);
        OutboxRepository outbox = new JdbcOutboxRepository(dataSource);
        Phase3EventPublisher publisher = new DurablePublisher(outbox, clock, plugin.getLogger());
        AuditSink audit = new JdbcAuditSink(dataSource, plugin, clock);
        WorldPolicy policy = WorldPolicy.defaults();
        PlayerDirectory directory = new CachedPlayerDirectory();
        GuildService guildService = new GuildService(
                new JdbcGuildRepository(transactions), transactions, policy, clock, publisher, audit,
                Duration.ofHours(48));
        PointsService pointsService = new PointsService(
                new JdbcPointsRepository(transactions), transactions, policy, clock, publisher, audit,
                false);
        Phase3Runtime[] holder = new Phase3Runtime[1];
        GuildCommandHandler guildCommands = new GuildCommandHandler(guildService, directory,
                (actor, name, tag, description) -> holder[0].createGuild(actor, name, tag, description));
        Phase3Runtime runtime = new Phase3Runtime(plugin, io, guildCommands,
                new PointsCommandHandler(pointsService, directory), guildService, pointsService, firstJoinReward, featuredBuildReward, guildConfiguration);
        holder[0] = runtime;
        CachedPlayerDirectory.owner = runtime.playersByName;
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            for (OfflinePlayer player : Bukkit.getOfflinePlayers()) {
                if (player.getName() != null) runtime.cache(player.getName(), player.getUniqueId());
            }
            plugin.getServer().getPluginManager().registerEvents(runtime, plugin);
        });
        return runtime;
    }

    boolean execute(CommandSender sender, String commandName, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("This command requires an in-game player identity.");
            return true;
        }
        cache(player.getName(), player.getUniqueId());
        Actor actor = actor(player);
        io.execute(() -> {
            CommandReply reply;
            try {
                reply = commandName.equals("guild")
                        ? guilds.execute(actor, args)
                        : points.execute(actor, args);
            } catch (RuntimeException exception) {
                plugin.getLogger().log(Level.WARNING, "Feature command failed safely", exception);
                reply = CommandReply.error("The platform database could not complete that request. No partial mutation was kept.");
            }
            CommandReply immutableReply = reply;
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                for (String line : immutableReply.lines()) {
                    player.sendMessage((immutableReply.success() ? "§d" : "§c") + line);
                }
            });
        });
        return true;
    }

    /** Fetches only the caller's read-only dashboard data without blocking Paper's primary thread. */
    void clientView(Player player, String view, Consumer<JsonObject> callback) {
        cache(player.getName(), player.getUniqueId());
        Actor actor = actor(player);
        io.execute(() -> {
            JsonObject result = new JsonObject();
            try {
                if (view.equals("guild-summary")) guildSummary(actor, result);
                else if (view.equals("guild-top")) guildTop(result);
                else if (view.equals("guild-invites")) guildInvites(actor, result);
                else if (view.equals("points-summary")) pointsSummary(actor, result);
                else throw new IllegalArgumentException("Unknown client view.");
            } catch (RuntimeException exception) { result.addProperty("error", "Persistent data is currently unavailable."); }
            callback.accept(result);
        });
    }

    /** Reads a caller's ledger or a public player leaderboard without blocking the Paper thread. */
    void clientPointsView(Player player, String view, String currency, Consumer<JsonObject> callback) {
        cache(player.getName(), player.getUniqueId());
        Actor actor = actor(player);
        io.execute(() -> {
            JsonObject result = new JsonObject();
            try {
                String currencyId = gg.neonnexus.smpplatform.phase3.points.PointsDomain.requireCurrency(currency);
                result.addProperty("currency", currencyId);
                if (view.equals("points-history")) {
                    JsonArray history = new JsonArray();
                    for (PointTransaction transaction : pointsService.history(actor, actor.playerId(), currencyId, 20, null)) {
                        JsonObject row = new JsonObject();
                        row.addProperty("amount", transaction.amount()); row.addProperty("balance", transaction.balanceAfter());
                        row.addProperty("source", transaction.source().name()); row.addProperty("reason", transaction.reason());
                        row.addProperty("occurredAt", transaction.occurredAt().toString()); history.add(row);
                    }
                    result.add("history", history);
                } else if (view.equals("points-top")) {
                    JsonArray leaderboard = new JsonArray();
                    for (var entry : pointsService.top(currencyId, gg.neonnexus.smpplatform.phase3.points.PointsDomain.OwnerType.PLAYER, 20)) {
                        JsonObject row = new JsonObject(); row.addProperty("rank", entry.rank()); row.addProperty("playerId", entry.account().ownerId().toString()); row.addProperty("balance", entry.balance()); leaderboard.add(row);
                    }
                    result.add("leaderboard", leaderboard);
                } else throw new IllegalArgumentException("Unknown points view.");
            } catch (RuntimeException exception) { result.addProperty("error", safeMessage(exception)); }
            callback.accept(result);
        });
    }

    /** Performs one guild action on the database executor, then returns a fresh caller summary. */
    void clientGuildAction(Player player, String action, UUID targetId, Consumer<JsonObject> callback) {
        cache(player.getName(), player.getUniqueId());
        Actor actor = actor(player);
        io.execute(() -> {
            JsonObject result = new JsonObject();
            try {
                switch (action) {
                    case "guild-invite" -> guildService.invite(actor, requireTarget(targetId));
                    case "guild-kick" -> guildService.kick(actor, requireTarget(targetId));
                    case "guild-promote" -> guildService.promote(actor, requireTarget(targetId));
                    case "guild-demote" -> guildService.demote(actor, requireTarget(targetId));
                    case "guild-leave" -> guildService.leave(actor);
                    case "guild-transfer-arm" -> armGuildTransfer(actor, requireTarget(targetId));
                    case "guild-transfer-confirm" -> confirmGuildTransfer(actor, requireTarget(targetId));
                    case "guild-accept" -> guildService.accept(actor, requireTarget(targetId));
                    default -> throw new IllegalArgumentException("Unknown guild action.");
                }
                guildSummary(actor, result);
                result.addProperty("message", action.equals("guild-transfer-arm") ? "Ownership transfer is armed. Confirm within 30 seconds." : "Guild updated.");
            } catch (RuntimeException exception) {
                result.addProperty("error", safeMessage(exception));
            }
            callback.accept(result);
        });
    }

    /** Decodes only validated client input; all guild and points rules remain in the services. */
    void clientGuildCreate(Player player, String name, String tag, String description, Consumer<JsonObject> callback) {
        cache(player.getName(), player.getUniqueId());
        Actor actor = actor(player);
        io.execute(() -> {
            JsonObject result = new JsonObject();
            try {
                Guild created = createGuild(actor, name, tag, description);
                guildSummary(actor, result);
                result.addProperty("message", "Created " + created.name() + " [" + created.tag() + "] for " + guildCreationCost + " " + guildCreationCurrency + ".");
            } catch (RuntimeException exception) { result.addProperty("error", safeMessage(exception)); }
            callback.accept(result);
        });
    }

    private static UUID requireTarget(UUID targetId) {
        if (targetId == null) throw new IllegalArgumentException("Select a guild member.");
        return targetId;
    }

    private Guild createGuild(Actor actor, String name, String tag, String description) {
        return guildService.create(actor, name, tag, description, () -> {
            if (guildCreationCost > 0) pointsService.spend(actor, actor.playerId(), guildCreationCurrency, guildCreationCost,
                    Source.GUILD, "Guild creation", Map.of("guildName", name, "guildTag", tag));
        });
    }

    private void armGuildTransfer(Actor actor, UUID successorId) {
        pendingGuildTransfers.put(actor.playerId(), new PendingGuildTransfer(successorId, Instant.now().plus(GUILD_TRANSFER_CONFIRMATION_TTL)));
    }

    private void confirmGuildTransfer(Actor actor, UUID successorId) {
        PendingGuildTransfer pending = pendingGuildTransfers.remove(actor.playerId());
        if (pending == null || pending.expiresAt().isBefore(Instant.now()) || !pending.successorId().equals(successorId)) {
            throw new IllegalArgumentException("Ownership transfer confirmation has expired. Start again.");
        }
        guildService.transferOwnership(actor, successorId);
    }

    private static String safeMessage(RuntimeException exception) {
        String message = exception.getMessage();
        return message == null || message.isBlank() ? "Guild action could not be completed." : message;
    }

    private void guildSummary(Actor actor, JsonObject result) {
        result.addProperty("creationCurrency", guildCreationCurrency);
        result.addProperty("creationCost", guildCreationCost);
        guildTop(result);
        Guild guild = guildService.byPlayer(actor.playerId()).orElse(null);
        result.addProperty("inGuild", guild != null);
        if (guild == null) return;
        result.addProperty("name", guild.name()); result.addProperty("tag", guild.tag()); result.addProperty("description", guild.description()); result.addProperty("points", guild.points());
        GuildMember self = guild.member(actor.playerId()); result.addProperty("rank", self == null ? "MEMBER" : self.rank().name());
        JsonArray members = new JsonArray();
        for (GuildMember member : guild.members()) { JsonObject row = new JsonObject(); row.addProperty("id", member.playerId().toString()); row.addProperty("rank", member.rank().name()); members.add(row); }
        result.add("members", members);
    }

    private void guildTop(JsonObject result) {
        JsonArray leaderboard = new JsonArray(); int rank = 0;
        for (Guild guild : guildService.top(20)) {
            JsonObject row = new JsonObject(); row.addProperty("rank", ++rank); row.addProperty("name", guild.name());
            row.addProperty("tag", guild.tag()); row.addProperty("points", guild.points()); row.addProperty("members", guild.members().size()); leaderboard.add(row);
        }
        result.add("leaderboard", leaderboard);
    }

    private void guildInvites(Actor actor, JsonObject result) {
        JsonArray invites = new JsonArray();
        for (var invite : guildService.invites(actor.playerId())) {
            Guild guild = guildService.byId(invite.guildId());
            JsonObject row = new JsonObject(); row.addProperty("guildId", guild.id().toString()); row.addProperty("name", guild.name());
            row.addProperty("tag", guild.tag()); row.addProperty("expiresAt", invite.expiresAt().toString()); invites.add(row);
        }
        result.add("invites", invites);
    }

    private void pointsSummary(Actor actor, JsonObject result) {
        JsonArray balances = new JsonArray();
        for (String currency : java.util.List.of("KAIRU_POINTS", "NEXUS_POINTS", "HARDCORE_POINTS", "BUILD_POINTS", "EVENT_POINTS", "SEASON_POINTS")) {
            JsonObject row = new JsonObject(); row.addProperty("currency", currency); row.addProperty("balance", pointsService.balance(actor.playerId(), currency)); balances.add(row);
        }
        result.add("balances", balances);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        cache(event.getPlayer().getName(), event.getPlayer().getUniqueId());
        if (!firstJoinReward.enabled() || event.getPlayer().hasPlayedBefore()) return;
        Player player = event.getPlayer(); Actor actor = actor(player);
        if (!firstJoinReward.worlds().contains(actor.world().id())) return;
        io.execute(() -> {
            try {
                pointsService.awardOnce(actor, player.getUniqueId(), "first-join-v1", firstJoinReward.currency(), firstJoinReward.amount(), gg.neonnexus.smpplatform.phase3.points.PointsDomain.Source.GAMEPLAY, firstJoinReward.reason(), Map.of("automaticReward", "first-join-v1"));
            } catch (RuntimeException exception) { plugin.getLogger().log(Level.WARNING, "Configured first-join reward was not applied", exception); }
        });
    }

    /** Durable, idempotent build-point reward; it is intentionally independent from the staff member's current world. */
    void awardFeaturedBuild(UUID submissionId, UUID recipient) {
        if (!featuredBuildReward.enabled()) return;
        Actor system = new Actor(new UUID(0L, 1L), "SMPPlatform", NeonWorld.ATRIUM, Set.of("*"));
        try {
            pointsService.awardOnce(system, recipient, "atrium-featured-" + submissionId, featuredBuildReward.currency(), featuredBuildReward.amount(), Source.GAMEPLAY, featuredBuildReward.reason(), Map.of("automaticReward", "featured-build", "submissionId", submissionId.toString()));
        } catch (RuntimeException exception) { plugin.getLogger().log(Level.WARNING, "Featured build reward was not applied; the feature state was preserved", exception); }
    }

    private void cache(String name, UUID id) {
        playersByName.put(name.toLowerCase(Locale.ROOT), id);
    }

    private static Actor actor(Player player) {
        Set<String> permissions = new HashSet<>();
        // Paper operators remain full platform administrators even before a
        // LuckPerms group has been assigned during first-time server setup.
        if (player.isOp()) permissions.add("smpplatform.admin");
        for (String permission : ACTOR_PERMISSIONS) {
            if (player.hasPermission(permission)) permissions.add(permission);
        }
        NeonWorld world = BukkitWorldMapping.fromMinecraftName(player.getWorld().getName());
        return new Actor(player.getUniqueId(), player.getName(), world, permissions);
    }

    private static final class CachedPlayerDirectory implements PlayerDirectory {
        private static volatile Map<String, UUID> owner = Map.of();
        @Override public java.util.Optional<UUID> findUuid(String playerName) {
            return java.util.Optional.ofNullable(owner.get(playerName.toLowerCase(Locale.ROOT)));
        }
    }

    private static final class BukkitWorldMapping {
        private static NeonWorld fromMinecraftName(String name) {
            String normalized = name.toLowerCase(Locale.ROOT).replace('_', '-');
            return NeonWorld.fromId(normalized).orElseGet(() -> {
                if (normalized.contains("hardcore") || normalized.contains("obsidian")) return NeonWorld.OBSIDIAN_GATE;
                if (normalized.contains("creative") || normalized.contains("atrium")) return NeonWorld.ATRIUM;
                if (normalized.contains("event") || normalized.contains("colosseum")) return NeonWorld.COLOSSEUM;
                if (normalized.contains("quarry") || normalized.contains("resource")) return NeonWorld.QUARRY;
                if (normalized.contains("archive") || normalized.contains("verdance")) return NeonWorld.VERDANCE;
                return NeonWorld.ASHFALL;
            });
        }
    }

    private static final class DurablePublisher implements Phase3EventPublisher {
        private final OutboxRepository outbox;
        private final Clock clock;
        private final java.util.logging.Logger logger;
        private final Gson gson = new Gson();
        private DurablePublisher(OutboxRepository outbox, Clock clock, java.util.logging.Logger logger) {
            this.outbox = outbox;
            this.clock = clock;
            this.logger = logger;
        }
        @Override public void guildCreated(Guild guild) {
            enqueue("guild", guild.id(), "GUILD_CREATED", "guild-created:" + guild.id(),
                    Map.of("guildId", guild.id(), "name", guild.name(), "tag", guild.tag()));
        }
        @Override public void guildJoined(Guild guild, GuildMember member) {
            enqueue("guild", guild.id(), "GUILD_MEMBER_JOINED",
                    "guild-joined:" + guild.id() + ":" + member.playerId() + ":" + member.joinedAt(),
                    Map.of("guildId", guild.id(), "playerId", member.playerId(), "rank", member.rank()));
        }
        @Override public void guildLeft(Guild guild, GuildMember member) {
            enqueue("guild", guild.id(), "GUILD_MEMBER_LEFT",
                    "guild-left:" + guild.id() + ":" + member.playerId() + ":" + clock.instant(),
                    Map.of("guildId", guild.id(), "playerId", member.playerId()));
        }
        @Override public void pointsChanged(PointTransaction transaction) {
            enqueue("points", transaction.accountId(), "POINTS_CHANGED",
                    "points:" + transaction.id(),
                    Map.of("transactionId", transaction.id(), "currencyId", transaction.currencyId(),
                            "amount", transaction.amount(), "balanceAfter", transaction.balanceAfter()));
        }
        private void enqueue(String type, UUID id, String event, String key, Map<String, ?> payload) {
            Instant now = clock.instant();
            try {
                outbox.enqueue(OutboxEvent.pending(type, id.toString(), event, key, gson.toJson(payload), now));
            } catch (RuntimeException exception) {
                // Local gameplay has already been committed. A Control Plane transport problem must not undo or misreport it.
                logger.warning("Local " + event + " was committed, but its bridge event could not be queued: " + exception.getClass().getSimpleName());
            }
        }
    }

    private static final class JdbcAuditSink implements AuditSink {
        private final DataSource dataSource;
        private final JavaPlugin plugin;
        private final Clock clock;
        private final Gson gson = new Gson();
        private JdbcAuditSink(DataSource dataSource, JavaPlugin plugin, Clock clock) {
            this.dataSource = dataSource;
            this.plugin = plugin;
            this.clock = clock;
        }
        @Override public void record(AuditEntry entry) {
            String sql = "INSERT INTO smp_audit_logs (id,actor_id,action,target_type,target_id,world_id,before_state,after_state,reason,correlation_id,metadata,created_at) VALUES (?,?,?,?,?,?,?,?,?,?,CAST(? AS jsonb),?)";
            try (var connection = dataSource.getConnection(); PreparedStatement statement = connection.prepareStatement(sql)) {
                statement.setObject(1, UUID.randomUUID());
                statement.setObject(2, entry.actorId());
                statement.setString(3, entry.action());
                statement.setString(4, "phase3");
                statement.setString(5, entry.target());
                statement.setString(6, entry.world().id());
                statement.setNull(7, java.sql.Types.OTHER);
                statement.setNull(8, java.sql.Types.OTHER);
                statement.setString(9, entry.metadata().getOrDefault("reason", ""));
                statement.setString(10, entry.correlationId().toString());
                statement.setString(11, gson.toJson(entry.metadata()));
                statement.setTimestamp(12, Timestamp.from(entry.occurredAt() == null ? clock.instant() : entry.occurredAt()));
                statement.executeUpdate();
            } catch (Exception exception) {
                plugin.getLogger().log(Level.SEVERE, "Unable to write mandatory feature audit record", exception);
                throw new IllegalStateException("Mandatory audit persistence failed", exception);
            }
        }
    }
}
