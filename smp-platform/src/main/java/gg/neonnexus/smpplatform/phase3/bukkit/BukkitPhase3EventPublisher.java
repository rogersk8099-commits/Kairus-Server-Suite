package gg.neonnexus.smpplatform.phase3.bukkit;

import gg.neonnexus.smpplatform.phase3.events.Phase3EventPublisher;
import gg.neonnexus.smpplatform.phase3.guild.Guild;
import gg.neonnexus.smpplatform.phase3.guild.GuildMember;
import gg.neonnexus.smpplatform.phase3.points.PointsDomain.PointTransaction;
import org.bukkit.Bukkit;
import org.bukkit.plugin.Plugin;
import java.util.Objects;

/** Schedules event dispatch so database threads never call Bukkit directly. */
public final class BukkitPhase3EventPublisher implements Phase3EventPublisher {
    private final Plugin plugin;
    public BukkitPhase3EventPublisher(Plugin plugin) { this.plugin=Objects.requireNonNull(plugin); }
    private void fire(org.bukkit.event.Event event){ Bukkit.getScheduler().runTask(plugin,()->Bukkit.getPluginManager().callEvent(event)); }
    @Override public void guildCreated(Guild guild){fire(new Phase3BukkitEvents.NexusGuildCreateEvent(guild));}
    @Override public void guildJoined(Guild guild,GuildMember member){fire(new Phase3BukkitEvents.NexusGuildJoinEvent(guild,member));}
    @Override public void guildLeft(Guild guild,GuildMember member){fire(new Phase3BukkitEvents.NexusGuildLeaveEvent(guild,member));}
    @Override public void pointsChanged(PointTransaction tx){fire(new Phase3BukkitEvents.NexusPointsChangeEvent(tx));}
}
