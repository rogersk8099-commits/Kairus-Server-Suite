package network.neonnexus.smp.admin.player;

import network.neonnexus.smp.admin.domain.PermissionRouter;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/** Caches observed players rather than synchronously sweeping Bukkit's offline-player database. */
public final class PlayerDirectory {
    private final ConcurrentHashMap<UUID, PlayerProfile> profiles = new ConcurrentHashMap<>();
    private final EditionResolver editions;
    private final Clock clock;

    public PlayerDirectory(Plugin plugin, Clock clock) { this.editions = new EditionResolver(plugin); this.clock = clock; }

    public PlayerProfile observe(Player player) {
        Instant now = clock.instant();
        return profiles.compute(player.getUniqueId(), (id, old) -> new PlayerProfile(id, player.getName(), editions.resolve(player), true,
                player.getWorld().getName(), old == null ? "—" : old.guild(), old == null ? "—" : old.rank(),
                old == null ? now : old.firstSeen(), now, Math.max(player.getStatistic(org.bukkit.Statistic.PLAY_ONE_MINUTE) / 20L, old == null ? 0 : old.playtimeTicks()),
                player.hasPermission(PermissionRouter.ROOT), player));
    }

    public void markOffline(Player player) {
        profiles.computeIfPresent(player.getUniqueId(), (id, old) -> old.withPresence(false, "Offline", clock.instant(), player.getStatistic(org.bukkit.Statistic.PLAY_ONE_MINUTE) / 20L));
    }

    public Optional<PlayerProfile> get(UUID id) { return Optional.ofNullable(profiles.get(id)); }
    public Collection<PlayerProfile> allProfiles() { return ListCopy.copy(profiles.values()); }

    /** Supports command targets without a synchronous global scan; the queried profile is added only on demand. */
    public Optional<PlayerProfile> findOrResolve(String name, OfflinePlayer resolved) {
        Optional<PlayerProfile> cached = profiles.values().stream().filter(p -> p.username().equalsIgnoreCase(name)).findFirst();
        if (cached.isPresent()) return cached;
        if (resolved == null || !resolved.hasPlayedBefore() || resolved.getName() == null) return Optional.empty();
        Instant seen = resolved.getLastSeen() > 0 ? Instant.ofEpochMilli(resolved.getLastSeen()) : clock.instant();
        PlayerProfile created = new PlayerProfile(resolved.getUniqueId(), resolved.getName(), Edition.UNKNOWN, resolved.isOnline(),
                resolved.isOnline() && resolved.getPlayer() != null ? resolved.getPlayer().getWorld().getName() : "Offline", "—", "—", seen, seen, 0, false, resolved.getPlayer());
        profiles.putIfAbsent(created.uuid(), created);
        return Optional.ofNullable(profiles.get(created.uuid()));
    }

    public Page search(PlayerFilter filter, String query, int page, int pageSize) {
        String needle = query == null ? "" : query.toLowerCase(Locale.ROOT).trim();
        ArrayList<PlayerProfile> found = profiles.values().stream()
                .filter(profile -> filter.matches(profile))
                .filter(profile -> needle.isBlank() || profile.username().toLowerCase(Locale.ROOT).contains(needle)
                        || profile.world().toLowerCase(Locale.ROOT).contains(needle) || profile.guild().toLowerCase(Locale.ROOT).contains(needle))
                .sorted(Comparator.comparing(PlayerProfile::online).reversed().thenComparing(PlayerProfile::lastSeen).reversed().thenComparing(PlayerProfile::username, String.CASE_INSENSITIVE_ORDER))
                .collect(ArrayList::new, ArrayList::add, ArrayList::addAll);
        int totalPages = Math.max(1, (int) Math.ceil(found.size() / (double) pageSize));
        int safePage = Math.max(0, Math.min(page, totalPages - 1));
        int from = Math.min(safePage * pageSize, found.size());
        int to = Math.min(from + pageSize, found.size());
        return new Page(ListCopy.copy(found.subList(from, to)), safePage, totalPages, found.size());
    }

    public enum PlayerFilter {
        ONLINE { public boolean matches(PlayerProfile p) { return p.online(); } },
        RECENT { public boolean matches(PlayerProfile p) { return !p.online() && p.lastSeen().isAfter(Instant.now().minus(Duration.ofDays(30))); } },
        OFFLINE { public boolean matches(PlayerProfile p) { return !p.online(); } },
        BEDROCK { public boolean matches(PlayerProfile p) { return p.edition() == Edition.BEDROCK; } },
        JAVA { public boolean matches(PlayerProfile p) { return p.edition() == Edition.JAVA; } },
        STAFF { public boolean matches(PlayerProfile p) { return p.staff(); } };
        public abstract boolean matches(PlayerProfile profile);
    }
    public enum Edition { JAVA, BEDROCK, UNKNOWN }
    public record PlayerProfile(UUID uuid, String username, Edition edition, boolean online, String world, String guild, String rank,
                                Instant firstSeen, Instant lastSeen, long playtimeTicks, boolean staff, Player livePlayer) {
        PlayerProfile withPresence(boolean online, String world, Instant lastSeen, long playtime) {
            return new PlayerProfile(uuid, username, edition, online, world, guild, rank, firstSeen, lastSeen, playtime, staff, online ? livePlayer : null);
        }
        public long playtimeHours() { return playtimeTicks / (20L * 60L * 60L); }
    }
    public record Page(Collection<PlayerProfile> entries, int page, int totalPages, int totalEntries) { }

    private static final class ListCopy { static <T> java.util.List<T> copy(Collection<T> source) { return java.util.List.copyOf(source); } }
}
