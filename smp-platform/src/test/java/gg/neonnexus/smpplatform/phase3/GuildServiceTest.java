package gg.neonnexus.smpplatform.phase3;

import gg.neonnexus.smpplatform.phase3.common.*;
import gg.neonnexus.smpplatform.phase3.guild.*;
import gg.neonnexus.smpplatform.phase3.support.InMemoryGuildRepository;
import gg.neonnexus.smpplatform.phase3.support.DirectTransactionRunner;
import gg.neonnexus.smpplatform.phase3.world.*;
import org.junit.jupiter.api.Test;
import java.time.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

class GuildServiceTest {
    private final Clock clock=Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"),ZoneOffset.UTC);
    private final InMemoryGuildRepository repo=new InMemoryGuildRepository();
    private final GuildService service=new GuildService(repo,new DirectTransactionRunner(),WorldPolicy.defaults(),clock,null,null,Duration.ofHours(48));
    private Actor actor(UUID id,NeonWorld world,String...permissions){return new Actor(id,"player",world,Set.of(permissions));}
    @Test void officerCannotPromoteEqualRankAndLeaderCannotLeaveBeforeTransfer(){UUID leader=UUID.randomUUID(),officer=UUID.randomUUID(),recruit=UUID.randomUUID();Guild g=service.create(actor(leader,NeonWorld.ASHFALL,"smpplatform.guild.create"),"Aurora","AUR","");service.invite(actor(leader,NeonWorld.ASHFALL),officer);service.accept(actor(officer,NeonWorld.ASHFALL),g.id());service.promote(actor(leader,NeonWorld.ASHFALL),officer);service.invite(actor(leader,NeonWorld.ASHFALL),recruit);service.accept(actor(recruit,NeonWorld.ASHFALL),g.id());assertThatThrownBy(()->service.promote(actor(officer,NeonWorld.ASHFALL),recruit)).isInstanceOf(Phase3Exception.class);assertThatThrownBy(()->service.leave(actor(leader,NeonWorld.ASHFALL))).isInstanceOf(Phase3Exception.class);}
    @Test void ownershipTransferSwapsExactlyOneLeader(){UUID old=UUID.randomUUID(),next=UUID.randomUUID();Guild g=service.create(actor(old,NeonWorld.ASHFALL,"smpplatform.guild.create"),"Aurora","AUR","");service.invite(actor(old,NeonWorld.ASHFALL),next);service.accept(actor(next,NeonWorld.ASHFALL),g.id());Guild transferred=service.transferOwnership(actor(old,NeonWorld.ASHFALL),next);assertThat(transferred.ownerId()).isEqualTo(next);assertThat(transferred.members().stream().filter(m->m.rank()==GuildRank.LEADER)).hasSize(1);assertThat(transferred.member(old).rank()).isEqualTo(GuildRank.OFFICER);}
    @Test void obsidianGateDefaultsOffAndArchiveAlwaysReadOnly(){UUID id=UUID.randomUUID();assertThatThrownBy(()->service.create(actor(id,NeonWorld.OBSIDIAN_GATE,"smpplatform.guild.create"),"Gate Guild","GATE","")).isInstanceOf(Phase3Exception.class);assertThatThrownBy(()->service.create(actor(id,NeonWorld.VERDANCE,"smpplatform.guild.create"),"Tour Guild","TOUR","")).isInstanceOf(Phase3Exception.class);}
}
