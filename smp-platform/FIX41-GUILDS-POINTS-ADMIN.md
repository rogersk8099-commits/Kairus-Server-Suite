# fix41 Guilds + Points administration
Adds a client administration facade and UI controls for points inspect/add/remove/set and guild info/rank/member removal/disband. The facade intentionally routes through the existing guild/points command handlers instead of creating a second persistence implementation, so current transaction ledger, validation and guild rules remain authoritative.

Existing guild/points Java sources found in this package:
gg/neonnexus/smpplatform/integrations/skript/EffNexusGuildEvent.java
gg/neonnexus/smpplatform/integrations/skript/ExprNexusGuild.java
gg/neonnexus/smpplatform/integrations/skript/ExprNexusPoints.java
gg/neonnexus/smpplatform/integrations/skript/EffNexusPoints.java
gg/neonnexus/smpplatform/integrations/events/NexusGuildJoinEvent.java
gg/neonnexus/smpplatform/integrations/events/NexusGuildCreateEvent.java
gg/neonnexus/smpplatform/integrations/events/NexusGuildLeaveEvent.java
gg/neonnexus/smpplatform/integrations/events/NexusPointsChangeEvent.java
gg/neonnexus/smpplatform/phase3/command/PointsCommandHandler.java
gg/neonnexus/smpplatform/phase3/command/GuildCommandHandler.java
gg/neonnexus/smpplatform/phase3/guild/GuildInvite.java
gg/neonnexus/smpplatform/phase3/guild/GuildRepository.java
gg/neonnexus/smpplatform/phase3/guild/GuildText.java
gg/neonnexus/smpplatform/phase3/guild/Guild.java
gg/neonnexus/smpplatform/phase3/guild/GuildService.java
gg/neonnexus/smpplatform/phase3/guild/GuildMember.java
gg/neonnexus/smpplatform/phase3/guild/GuildRank.java
gg/neonnexus/smpplatform/phase3/jdbc/JdbcGuildRepository.java
gg/neonnexus/smpplatform/phase3/jdbc/JdbcGuildPointsProjection.java
gg/neonnexus/smpplatform/phase3/jdbc/JdbcPointsRepository.java
gg/neonnexus/smpplatform/phase3/points/PointsRepository.java
gg/neonnexus/smpplatform/phase3/points/GuildPointsProjection.java
gg/neonnexus/smpplatform/phase3/points/PointsDomain.java
gg/neonnexus/smpplatform/phase3/points/PointsService.java

Important: exact command compatibility must be compile/staging verified against the current command handlers. No claim is made that every admin subcommand already exists in the older handler.
