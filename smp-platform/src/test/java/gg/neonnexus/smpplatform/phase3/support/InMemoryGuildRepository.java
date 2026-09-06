package gg.neonnexus.smpplatform.phase3.support;

import gg.neonnexus.smpplatform.phase3.common.Phase3Exception;
import gg.neonnexus.smpplatform.phase3.guild.*;
import java.time.Instant;
import java.util.*;

public final class InMemoryGuildRepository implements GuildRepository {
    private final Map<UUID,Guild> guilds=new HashMap<>(); private final Map<UUID,GuildInvite> invites=new HashMap<>();
    public Optional<Guild> findById(UUID id){return Optional.ofNullable(guilds.get(id));}
    public Optional<Guild> findByNameOrTag(String input){return guilds.values().stream().filter(g->g.name().equalsIgnoreCase(input)||g.tag().equalsIgnoreCase(input)).findFirst();}
    public Optional<Guild> findByPlayer(UUID player){return guilds.values().stream().filter(g->g.hasMember(player)).findFirst();}
    public List<Guild> topByPoints(int limit){return guilds.values().stream().sorted(Comparator.comparingLong(Guild::points).reversed()).limit(limit).toList();}
    public Guild insert(Guild guild){if(findByNameOrTag(guild.name()).isPresent())throw new Phase3Exception(Phase3Exception.Code.ALREADY_EXISTS,"Duplicate guild");guilds.put(guild.id(),guild);return guild;}
    public void replace(Guild guild,long expected){Guild old=guilds.get(guild.id());if(old==null)throw new Phase3Exception(Phase3Exception.Code.NOT_FOUND,"Guild missing");if(old.version()!=expected)throw new Phase3Exception(Phase3Exception.Code.CONCURRENT_MODIFICATION,"Stale guild");guilds.put(guild.id(),guild);}
    public void delete(UUID id,long expected){Guild old=guilds.get(id);if(old==null||old.version()!=expected)throw new Phase3Exception(Phase3Exception.Code.CONCURRENT_MODIFICATION,"Stale guild");guilds.remove(id);}
    public void insertInvite(GuildInvite invite){invites.put(invite.id(),invite);} public Optional<GuildInvite> findInvite(UUID guild,UUID target,Instant now){return invites.values().stream().filter(i->i.guildId().equals(guild)&&i.targetPlayerId().equals(target)&&!i.isExpiredAt(now)).findFirst();}
    public void deleteInvite(UUID guild,UUID target){invites.values().removeIf(i->i.guildId().equals(guild)&&i.targetPlayerId().equals(target));} public void deleteExpiredInvites(Instant now){invites.values().removeIf(i->i.isExpiredAt(now));}
}
