package gg.neonnexus.smpplatform.phase3.guild;

import gg.neonnexus.smpplatform.phase3.common.Actor;
import gg.neonnexus.smpplatform.phase3.common.AuditSink;
import gg.neonnexus.smpplatform.phase3.common.Phase3Exception;
import gg.neonnexus.smpplatform.phase3.common.TransactionRunner;
import gg.neonnexus.smpplatform.phase3.events.Phase3EventPublisher;
import gg.neonnexus.smpplatform.phase3.world.WorldPolicy;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Guild application service. All methods are blocking because repository work is blocking; dispatch
 * them to the database executor and marshal only responses/event delivery back to Paper's scheduler.
 */
public final class GuildService {
    private static final Duration DEFAULT_INVITE_TTL = Duration.ofHours(48);
    private final GuildRepository repository;
    private final TransactionRunner transactions;
    private final WorldPolicy policy;
    private final Clock clock;
    private final Phase3EventPublisher events;
    private final AuditSink audit;
    private final Duration inviteTtl;

    public GuildService(GuildRepository repository, TransactionRunner transactions, WorldPolicy policy,
                        Clock clock, Phase3EventPublisher events, AuditSink audit, Duration inviteTtl) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.transactions = Objects.requireNonNull(transactions, "transactions");
        this.policy = Objects.requireNonNull(policy, "policy");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.events = events == null ? Phase3EventPublisher.NOOP : events;
        this.audit = audit == null ? AuditSink.NOOP : audit;
        this.inviteTtl = inviteTtl == null ? DEFAULT_INVITE_TTL : inviteTtl;
        if (this.inviteTtl.isNegative() || this.inviteTtl.isZero()) throw new IllegalArgumentException("inviteTtl must be positive");
    }

    public Guild create(Actor actor, String name, String tag, String description) {
        return create(actor, name, tag, description, null);
    }

    /** The optional charge runs inside the same transaction as the guild write. */
    public Guild create(Actor actor, String name, String tag, String description, Runnable creationCharge) {
        requirePolicy(actor); require(actor.has("smpplatform.guild.create") || actor.isGuildAdmin(), "Missing smpplatform.guild.create");
        Instant now = clock.instant();
        Guild guild = transactions.required(() -> {
            require(repository.findByPlayer(actor.playerId()).isEmpty(), "You are already in a guild");
            require(repository.findByNameOrTag(name).isEmpty(), "Guild name or tag is already taken");
            if (creationCharge != null) creationCharge.run();
            Guild created = new Guild(UUID.randomUUID(), name, tag, description, actor.playerId(), now, 0, 0,
                    List.of(new GuildMember(actor.playerId(), GuildRank.LEADER, now)));
            return repository.insert(created);
        });
        events.guildCreated(guild);
        record(actor, "GUILD_CREATED", guild.id().toString(), Map.of("name", guild.name(), "tag", guild.tag()));
        return guild;
    }

    public GuildInvite invite(Actor actor, UUID targetPlayerId) {
        requirePolicy(actor); requireNotSelf(actor.playerId(), targetPlayerId);
        Instant now = clock.instant();
        return transactions.required(() -> {
            Guild guild = requireGuild(repository.findByPlayer(actor.playerId()));
            requireCanManage(actor, guild, GuildRank.OFFICER, "invite members");
            require(repository.findByPlayer(targetPlayerId).isEmpty(), "That player is already in a guild");
            GuildInvite invite = new GuildInvite(UUID.randomUUID(), guild.id(), targetPlayerId, actor.playerId(), now, now.plus(inviteTtl));
            repository.insertInvite(invite);
            return invite;
        });
    }

    public Guild accept(Actor actor, UUID guildId) {
        requirePolicy(actor);
        Instant now = clock.instant();
        Guild changed = transactions.required(() -> {
            require(repository.findByPlayer(actor.playerId()).isEmpty(), "You are already in a guild");
            Guild guild = requireGuild(repository.findById(guildId));
            GuildInvite invite = repository.findInvite(guildId, actor.playerId(), now)
                    .orElseThrow(() -> new Phase3Exception(Phase3Exception.Code.NOT_FOUND, "No active invite from that guild"));
            if (invite.isExpiredAt(now)) throw new Phase3Exception(Phase3Exception.Code.NOT_FOUND, "That invite has expired");
            List<GuildMember> members = new ArrayList<>(guild.members());
            GuildMember member = new GuildMember(actor.playerId(), GuildRank.RECRUIT, now);
            members.add(member);
            Guild evolved = evolve(guild, guild.ownerId(), guild.description(), guild.tag(), members);
            repository.replace(evolved, guild.version());
            repository.deleteInvite(guildId, actor.playerId());
            return evolved;
        });
        GuildMember member = Objects.requireNonNull(changed.member(actor.playerId()));
        events.guildJoined(changed, member);
        record(actor, "GUILD_JOINED", changed.id().toString(), Map.of("rank", member.rank().name()));
        return changed;
    }

    public void decline(Actor actor, UUID guildId) {
        requirePolicy(actor);
        transactions.required(() -> { repository.deleteInvite(guildId, actor.playerId()); return null; });
    }

    public Guild leave(Actor actor) {
        requirePolicy(actor);
        Guild changed = transactions.required(() -> {
            Guild guild = requireGuild(repository.findByPlayer(actor.playerId()));
            GuildMember member = Objects.requireNonNull(guild.member(actor.playerId()));
            require(member.rank() != GuildRank.LEADER, "Transfer ownership or disband before leaving your guild");
            List<GuildMember> members = new ArrayList<>(guild.members());
            members.remove(member);
            Guild evolved = evolve(guild, guild.ownerId(), guild.description(), guild.tag(), members);
            repository.replace(evolved, guild.version());
            return evolved;
        });
        events.guildLeft(changed, new GuildMember(actor.playerId(), GuildRank.MEMBER, clock.instant()));
        record(actor, "GUILD_LEFT", changed.id().toString(), Map.of());
        return changed;
    }

    public Guild kick(Actor actor, UUID targetPlayerId) {
        requirePolicy(actor); requireNotSelf(actor.playerId(), targetPlayerId);
        Guild changed = transactions.required(() -> {
            Guild guild = requireGuild(repository.findByPlayer(actor.playerId()));
            GuildMember target = requireMember(guild, targetPlayerId);
            requireCanManage(actor, guild, GuildRank.OFFICER, "kick members");
            requireMayActOn(actor, guild, target, "kick");
            List<GuildMember> members = new ArrayList<>(guild.members()); members.remove(target);
            Guild evolved = evolve(guild, guild.ownerId(), guild.description(), guild.tag(), members);
            repository.replace(evolved, guild.version());
            return evolved;
        });
        record(actor, "GUILD_MEMBER_KICKED", changed.id().toString(), Map.of("target", targetPlayerId.toString()));
        return changed;
    }

    public Guild promote(Actor actor, UUID targetPlayerId) { return changeRank(actor, targetPlayerId, true); }
    public Guild demote(Actor actor, UUID targetPlayerId) { return changeRank(actor, targetPlayerId, false); }

    private Guild changeRank(Actor actor, UUID targetPlayerId, boolean promote) {
        requirePolicy(actor); requireNotSelf(actor.playerId(), targetPlayerId);
        Guild changed = transactions.required(() -> {
            Guild guild = requireGuild(repository.findByPlayer(actor.playerId()));
            GuildMember target = requireMember(guild, targetPlayerId);
            requireCanManage(actor, guild, GuildRank.OFFICER, promote ? "promote members" : "demote members");
            requireMayActOn(actor, guild, target, promote ? "promote" : "demote");
            GuildRank next = promote ? higher(target.rank()) : lower(target.rank());
            require(next != null && next != GuildRank.LEADER, "Use transfer ownership for the Leader rank");
            List<GuildMember> members = replaceMember(guild.members(), new GuildMember(target.playerId(), next, target.joinedAt()));
            Guild evolved = evolve(guild, guild.ownerId(), guild.description(), guild.tag(), members);
            repository.replace(evolved, guild.version());
            return evolved;
        });
        record(actor, promote ? "GUILD_MEMBER_PROMOTED" : "GUILD_MEMBER_DEMOTED", changed.id().toString(), Map.of("target", targetPlayerId.toString()));
        return changed;
    }

    public Guild transferOwnership(Actor actor, UUID successorId) {
        requirePolicy(actor); requireNotSelf(actor.playerId(), successorId);
        Guild changed = transactions.required(() -> {
            Guild guild = requireGuild(repository.findByPlayer(actor.playerId()));
            require(actor.isGuildAdmin() || guild.ownerId().equals(actor.playerId()), "Only the guild leader can transfer ownership");
            GuildMember successor = requireMember(guild, successorId);
            List<GuildMember> members = replaceMember(guild.members(), new GuildMember(actor.playerId(), GuildRank.OFFICER,
                    requireMember(guild, actor.playerId()).joinedAt()));
            members = replaceMember(members, new GuildMember(successor.playerId(), GuildRank.LEADER, successor.joinedAt()));
            Guild evolved = evolve(guild, successorId, guild.description(), guild.tag(), members);
            repository.replace(evolved, guild.version());
            return evolved;
        });
        record(actor, "GUILD_OWNERSHIP_TRANSFERRED", changed.id().toString(), Map.of("successor", successorId.toString()));
        return changed;
    }

    public Guild edit(Actor actor, String description, String tag) {
        requirePolicy(actor);
        Guild changed = transactions.required(() -> {
            Guild guild = requireGuild(repository.findByPlayer(actor.playerId()));
            requireCanManage(actor, guild, GuildRank.OFFICER, "edit the guild");
            Guild existing = repository.findByNameOrTag(tag).orElse(null);
            require(existing == null || existing.id().equals(guild.id()), "Guild tag is already taken");
            Guild evolved = evolve(guild, guild.ownerId(), description, tag, guild.members());
            repository.replace(evolved, guild.version());
            return evolved;
        });
        record(actor, "GUILD_UPDATED", changed.id().toString(), Map.of());
        return changed;
    }

    public void disband(Actor actor) {
        requirePolicy(actor);
        Guild removed = transactions.required(() -> {
            Guild guild = requireGuild(repository.findByPlayer(actor.playerId()));
            require(actor.isGuildAdmin() || guild.ownerId().equals(actor.playerId()), "Only the guild leader can disband this guild");
            repository.delete(guild.id(), guild.version());
            return guild;
        });
        record(actor, "GUILD_DISBANDED", removed.id().toString(), Map.of("name", removed.name()));
    }

    public Guild info(String nameOrTag) { return transactions.required(() -> requireGuild(repository.findByNameOrTag(nameOrTag))); }
    public Guild byId(UUID guildId) { return transactions.required(() -> requireGuild(repository.findById(guildId))); }
    /** Read-only lookup used by the optional client gateway; run this on the database executor. */
    public java.util.Optional<Guild> byPlayer(UUID playerId) { return transactions.required(() -> repository.findByPlayer(playerId)); }
    /** Active invitations are player-private but may be displayed by the optional client. */
    public List<GuildInvite> invites(UUID playerId) { return transactions.required(() -> repository.findInvitesFor(playerId, clock.instant())); }
    public List<GuildMember> members(String nameOrTag) { return info(nameOrTag).members(); }
    public List<Guild> top(int limit) { return transactions.required(() -> repository.topByPoints(validLimit(limit))); }

    private Guild evolve(Guild old, UUID owner, String description, String tag, List<GuildMember> members) {
        return new Guild(old.id(), old.name(), tag, description, owner, old.createdAt(), old.points(), old.version() + 1, members);
    }
    private void requirePolicy(Actor actor) { require(policy.permitsGuildMutation(actor.world()), "Guild changes are unavailable in " + actor.world().displayName()); }
    private void requireCanManage(Actor actor, Guild guild, GuildRank minimum, String action) {
        if (actor.isGuildAdmin()) return;
        GuildMember member = requireMember(guild, actor.playerId());
        require(member.rank().authority() >= minimum.authority(), "You must be an Officer or Leader to " + action);
    }
    private void requireMayActOn(Actor actor, Guild guild, GuildMember target, String action) {
        if (actor.isGuildAdmin()) return;
        GuildMember actorMember = requireMember(guild, actor.playerId());
        require(actorMember.rank().outranks(target.rank()), "You cannot " + action + " an equal or higher rank");
    }
    private static GuildRank higher(GuildRank rank) { return switch (rank) { case RECRUIT -> GuildRank.MEMBER; case MEMBER -> GuildRank.OFFICER; case OFFICER, LEADER -> null; }; }
    private static GuildRank lower(GuildRank rank) { return switch (rank) { case OFFICER -> GuildRank.MEMBER; case MEMBER -> GuildRank.RECRUIT; case RECRUIT, LEADER -> null; }; }
    private static List<GuildMember> replaceMember(List<GuildMember> members, GuildMember replacement) {
        return members.stream().map(member -> member.playerId().equals(replacement.playerId()) ? replacement : member).sorted(Comparator.comparing(GuildMember::joinedAt)).toList();
    }
    private static Guild requireGuild(java.util.Optional<Guild> guild) { return guild.orElseThrow(() -> new Phase3Exception(Phase3Exception.Code.NOT_FOUND, "Guild not found")); }
    private static GuildMember requireMember(Guild guild, UUID playerId) { GuildMember member = guild.member(playerId); if (member == null) throw new Phase3Exception(Phase3Exception.Code.NOT_FOUND, "Player is not in this guild"); return member; }
    private static void requireNotSelf(UUID actor, UUID target) { require(!actor.equals(target), "You cannot target yourself"); }
    private static void require(boolean condition, String message) { if (!condition) throw new Phase3Exception(Phase3Exception.Code.FORBIDDEN, message); }
    private static int validLimit(int limit) { if (limit < 1 || limit > 100) throw new Phase3Exception(Phase3Exception.Code.INVALID_ARGUMENT, "Limit must be between 1 and 100"); return limit; }
    private void record(Actor actor, String action, String target, Map<String, String> metadata) { audit.record(new AuditSink.AuditEntry(actor.playerId(), action, target, actor.world(), metadata, clock.instant(), UUID.randomUUID())); }
}
