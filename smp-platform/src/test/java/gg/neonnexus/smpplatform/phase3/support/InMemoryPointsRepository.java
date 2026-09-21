package gg.neonnexus.smpplatform.phase3.support;

import gg.neonnexus.smpplatform.phase3.common.Phase3Exception;
import gg.neonnexus.smpplatform.phase3.points.*;
import java.time.Instant;
import java.util.*;
import static gg.neonnexus.smpplatform.phase3.points.PointsDomain.*;

public final class InMemoryPointsRepository implements PointsRepository {
    private final Map<AccountKey,PointAccount> accounts=new HashMap<>(); private final Map<AccountKey,List<PointTransaction>> ledger=new HashMap<>();
    private final Set<String> automaticRewards=new HashSet<>();
    @Override public synchronized PointTransaction applyAtomically(BalanceChange change,boolean allowNegative,Instant now){PointAccount current=accounts.get(change.account());if(current==null)current=new PointAccount(UUID.randomUUID(),change.account(),0,0,now,now);long after=Math.addExact(current.balance(),change.amount());if(!allowNegative&&after<0)throw new Phase3Exception(Phase3Exception.Code.INSUFFICIENT_BALANCE,"Insufficient balance");PointTransaction tx=new PointTransaction(UUID.randomUUID(),current.id(),change.account().currencyId(),change.amount(),current.balance(),after,change.source(),change.reason(),change.metadata(),change.world(),change.actorId(),change.correlationId(),now);accounts.put(change.account(),new PointAccount(current.id(),current.key(),after,current.version()+1,current.createdAt(),now));ledger.computeIfAbsent(change.account(),k->new ArrayList<>()).add(tx);return tx;}
    public Optional<PointAccount> findAccount(AccountKey key){return Optional.ofNullable(accounts.get(key));} public List<PointTransaction> history(AccountKey key,int limit,Instant before){return ledger.getOrDefault(key,List.of()).stream().filter(t->t.occurredAt().isBefore(before)).sorted(Comparator.comparing(PointTransaction::occurredAt).reversed()).limit(limit).toList();}
    public List<LeaderboardEntry> leaderboard(String currency,OwnerType type,int limit){List<PointAccount>a=accounts.values().stream().filter(x->x.key().currencyId().equals(currency)&&x.key().ownerType()==type).sorted(Comparator.comparingLong(PointAccount::balance).reversed()).limit(limit).toList();List<LeaderboardEntry>out=new ArrayList<>();for(int i=0;i<a.size();i++)out.add(new LeaderboardEntry(a.get(i).key(),a.get(i).balance(),i+1));return out;}
    @Override public synchronized boolean claimAutomaticReward(String rewardKey,UUID playerId,Instant claimedAt){return automaticRewards.add(rewardKey+":"+playerId);}
}
