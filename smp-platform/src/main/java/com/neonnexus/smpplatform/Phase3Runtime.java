package com.neonnexus.smpplatform;

import com.google.gson.Gson;
import com.neonnexus.smpplatform.outbox.JdbcOutboxRepository;
import com.neonnexus.smpplatform.outbox.OutboxEvent;
import com.neonnexus.smpplatform.outbox.OutboxRepository;
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
    private final Map<String, UUID> playersByName = new ConcurrentHashMap<>();

    private Phase3Runtime(JavaPlugin plugin, Executor io, GuildCommandHandler guilds,
                          PointsCommandHandler points) {
        this.plugin = plugin;
        this.io = io;
        this.guilds = guilds;
        this.points = points;
    }

    static Phase3Runtime start(JavaPlugin plugin, DataSource dataSource, Executor io, Clock clock) {
        JdbcTransactionRunner transactions = new JdbcTransactionRunner(dataSource);
        OutboxRepository outbox = new JdbcOutboxRepository(dataSource);
        Phase3EventPublisher publisher = new DurablePublisher(outbox, clock);
        AuditSink audit = new JdbcAuditSink(dataSource, plugin, clock);
        WorldPolicy policy = WorldPolicy.defaults();
        PlayerDirectory directory = new CachedPlayerDirectory();
        GuildService guildService = new GuildService(
                new JdbcGuildRepository(transactions), transactions, policy, clock, publisher, audit,
                Duration.ofHours(48));
        PointsService pointsService = new PointsService(
                new JdbcPointsRepository(transactions), transactions, policy, clock, publisher, audit,
                false);
        Phase3Runtime runtime = new Phase3Runtime(plugin, io,
                new GuildCommandHandler(guildService, directory),
                new PointsCommandHandler(pointsService, directory));
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

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        cache(event.getPlayer().getName(), event.getPlayer().getUniqueId());
    }

    private void cache(String name, UUID id) {
        playersByName.put(name.toLowerCase(Locale.ROOT), id);
    }

    private static Actor actor(Player player) {
        Set<String> permissions = new HashSet<>();
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
        private final Gson gson = new Gson();
        private DurablePublisher(OutboxRepository outbox, Clock clock) {
            this.outbox = outbox;
            this.clock = clock;
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
            outbox.enqueue(OutboxEvent.pending(type, id.toString(), event, key, gson.toJson(payload), now));
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
