package gg.neonnexus.smpplatform.phase3.events;

import gg.neonnexus.smpplatform.phase3.guild.Guild;
import gg.neonnexus.smpplatform.phase3.guild.GuildMember;
import gg.neonnexus.smpplatform.phase3.points.PointsDomain.PointTransaction;

/** Publish only after the surrounding database transaction commits. */
public interface Phase3EventPublisher {
    Phase3EventPublisher NOOP = new Phase3EventPublisher() { };

    default void guildCreated(Guild guild) { }
    default void guildJoined(Guild guild, GuildMember member) { }
    default void guildLeft(Guild guild, GuildMember member) { }
    default void pointsChanged(PointTransaction transaction) { }
}
