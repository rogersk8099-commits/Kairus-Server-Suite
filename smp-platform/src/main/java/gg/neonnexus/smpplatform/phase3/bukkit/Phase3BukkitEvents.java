package gg.neonnexus.smpplatform.phase3.bukkit;

import gg.neonnexus.smpplatform.phase3.guild.Guild;
import gg.neonnexus.smpplatform.phase3.guild.GuildMember;
import gg.neonnexus.smpplatform.phase3.points.PointsDomain.PointTransaction;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import java.util.Objects;

/** Bukkit event classes. Fire only on the Paper primary thread after DB commit. */
public final class Phase3BukkitEvents {
    private Phase3BukkitEvents() { }
    public static final class NexusGuildCreateEvent extends Event { private static final HandlerList HANDLERS=new HandlerList(); private final Guild guild; public NexusGuildCreateEvent(Guild guild){this.guild=Objects.requireNonNull(guild);} public Guild guild(){return guild;} @Override public HandlerList getHandlers(){return HANDLERS;} public static HandlerList getHandlerList(){return HANDLERS;} }
    public static final class NexusGuildJoinEvent extends Event { private static final HandlerList HANDLERS=new HandlerList(); private final Guild guild; private final GuildMember member; public NexusGuildJoinEvent(Guild guild,GuildMember member){this.guild=Objects.requireNonNull(guild);this.member=Objects.requireNonNull(member);} public Guild guild(){return guild;} public GuildMember member(){return member;} @Override public HandlerList getHandlers(){return HANDLERS;} public static HandlerList getHandlerList(){return HANDLERS;} }
    public static final class NexusGuildLeaveEvent extends Event { private static final HandlerList HANDLERS=new HandlerList(); private final Guild guild; private final GuildMember member; public NexusGuildLeaveEvent(Guild guild,GuildMember member){this.guild=Objects.requireNonNull(guild);this.member=Objects.requireNonNull(member);} public Guild guild(){return guild;} public GuildMember member(){return member;} @Override public HandlerList getHandlers(){return HANDLERS;} public static HandlerList getHandlerList(){return HANDLERS;} }
    public static final class NexusPointsChangeEvent extends Event { private static final HandlerList HANDLERS=new HandlerList(); private final PointTransaction transaction; public NexusPointsChangeEvent(PointTransaction transaction){this.transaction=Objects.requireNonNull(transaction);} public PointTransaction transaction(){return transaction;} @Override public HandlerList getHandlers(){return HANDLERS;} public static HandlerList getHandlerList(){return HANDLERS;} }
}
