package gg.neonnexus.smpplatform.phase3;

import gg.neonnexus.smpplatform.phase3.common.*;
import gg.neonnexus.smpplatform.phase3.points.*;
import gg.neonnexus.smpplatform.phase3.points.PointsDomain.*;
import gg.neonnexus.smpplatform.phase3.support.InMemoryPointsRepository;
import gg.neonnexus.smpplatform.phase3.support.DirectTransactionRunner;
import gg.neonnexus.smpplatform.phase3.world.*;
import org.junit.jupiter.api.Test;
import java.time.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

class PointsServiceTest {
    private final InMemoryPointsRepository repo=new InMemoryPointsRepository(); private final Clock clock=Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"),ZoneOffset.UTC);
    private final PointsService service=new PointsService(repo,new DirectTransactionRunner(),WorldPolicy.defaults(),clock,null,null,false);
    private Actor actor(UUID id,String...p){return new Actor(id,"staff",NeonWorld.ASHFALL,Set.of(p));}
    @Test void everyBalanceChangeGetsOneImmutableArithmeticTransaction(){UUID staff=UUID.randomUUID(),player=UUID.randomUUID();PointTransaction first=service.add(actor(staff,"smpplatform.admin.points"),player,"NEXUS_POINTS",50,"event reward",Map.of());PointTransaction second=service.remove(actor(staff,"smpplatform.admin.points"),player,"NEXUS_POINTS",20,"store fee",Map.of());assertThat(first.balanceBefore()).isZero();assertThat(first.balanceAfter()).isEqualTo(50);assertThat(second.balanceBefore()).isEqualTo(50);assertThat(second.balanceAfter()).isEqualTo(30);assertThat(service.balance(player,"NEXUS_POINTS")).isEqualTo(30);assertThat(service.history(actor(player,"smpplatform.points.view"),player,"NEXUS_POINTS",10,null)).hasSize(2);}
    @Test void insufficientRemovalCannotSilentlyChangeAccountOrLedger(){UUID staff=UUID.randomUUID(),player=UUID.randomUUID();service.add(actor(staff,"smpplatform.admin.points"),player,"NEXUS_POINTS",10,"seed",Map.of());assertThatThrownBy(()->service.remove(actor(staff,"smpplatform.admin.points"),player,"NEXUS_POINTS",11,"bad",Map.of())).isInstanceOf(Phase3Exception.class);assertThat(service.balance(player,"NEXUS_POINTS")).isEqualTo(10);assertThat(service.history(actor(player,"smpplatform.points.view"),player,"NEXUS_POINTS",10,null)).hasSize(1);}
    @Test void ashfallRejectsHardcoreOnlyCurrency(){UUID staff=UUID.randomUUID(),player=UUID.randomUUID();assertThatThrownBy(()->service.add(actor(staff,"smpplatform.admin.points"),player,"HARDCORE_POINTS",1,"bad world",Map.of())).isInstanceOf(Phase3Exception.class);assertThat(service.balance(player,"HARDCORE_POINTS")).isZero();}
    @Test void automaticRewardKeyCanOnlyPayOnce(){UUID player=UUID.randomUUID();Actor gameplay=actor(player);assertThat(service.awardOnce(gameplay,player,"first-join-v1","NEXUS_POINTS",25,Source.GAMEPLAY,"First join",Map.of("automaticReward","first-join-v1"))).isPresent();assertThat(service.awardOnce(gameplay,player,"first-join-v1","NEXUS_POINTS",25,Source.GAMEPLAY,"First join",Map.of("automaticReward","first-join-v1"))).isEmpty();assertThat(service.balance(player,"NEXUS_POINTS")).isEqualTo(25);}
}
