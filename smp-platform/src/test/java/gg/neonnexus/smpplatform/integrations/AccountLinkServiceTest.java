package gg.neonnexus.smpplatform.integrations;

import gg.neonnexus.smpplatform.integrations.link.*;
import java.security.SecureRandom;
import java.time.*;
import java.util.*;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AccountLinkServiceTest {
    @Test void linkCodeIsSingleUseAndExpiredCodesCannotBeConsumed() {
        MutableClock clock = new MutableClock(Instant.parse("2026-01-01T00:00:00Z")); FakeRepository repository = new FakeRepository();
        AccountLinkService service = service(repository, clock, Duration.ofMinutes(10)); UUID player = UUID.randomUUID();
        LinkCode code = service.generate(player, "player", "c1");
        assertTrue(service.consume(code.code(), "website", "c2").isPresent());
        assertTrue(service.consume(code.code(), "website", "c3").isEmpty());
        LinkCode expires = service.generate(player, "player", "c4"); clock.advance(Duration.ofMinutes(11));
        assertTrue(service.consume(expires.code(), "website", "c5").isEmpty());
    }
    @Test void generationRateLimitAndRevokeAreAudited() {
        MutableClock clock = new MutableClock(Instant.parse("2026-01-01T00:00:00Z")); FakeRepository repository = new FakeRepository(); AccountLinkService service = service(repository, clock, Duration.ofMinutes(10)); UUID player = UUID.randomUUID();
        LinkCode first = service.generate(player, "player", "a"); service.generate(player, "player", "b"); service.generate(player, "player", "c");
        assertThrows(AccountLinkService.LinkRateLimitedException.class, () -> service.generate(player, "player", "d"));
        assertTrue(service.revoke(first.id(), player, "staff", "e", "requested")); assertTrue(service.consume(first.code(), "website", "f").isEmpty());
        assertTrue(repository.audits.stream().anyMatch(a -> a.action().equals("LINK_CODE_REVOKED")));
    }
    private static AccountLinkService service(FakeRepository r, Clock c, Duration ttl) { return new AccountLinkService(r, new CodeHasher(new SecretKeySpec(new byte[32], "HmacSHA256")), new SecureRandom(), c, ttl); }
    private static final class MutableClock extends Clock { Instant now; MutableClock(Instant now){this.now=now;} void advance(Duration d){now=now.plus(d);} public ZoneId getZone(){return ZoneOffset.UTC;} public Clock withZone(ZoneId z){return this;} public Instant instant(){return now;} }
    private static final class FakeRepository implements LinkRepository { final Map<String,LinkRecord> byHash=new HashMap<>(); final List<LinkAudit> audits=new ArrayList<>(); public long countGeneratedSince(UUID id,Instant since){return byHash.values().stream().filter(r->r.minecraftUuid().equals(id)&&!r.expiresAt().isBefore(since)).count();} public void create(LinkRecord r){byHash.put(r.codeHash(),r);} public Optional<LinkRecord> consumeActiveByHash(String hash,Instant now){LinkRecord r=byHash.get(hash);if(r==null||!r.activeAt(now))return Optional.empty();LinkRecord used=new LinkRecord(r.id(),r.minecraftUuid(),r.codeHash(),r.expiresAt(),now,null,null);byHash.put(hash,used);return Optional.of(used);} public boolean revoke(UUID id,Instant now,String reason){for(var e:byHash.entrySet()){LinkRecord r=e.getValue();if(r.id().equals(id)&&r.usedAt()==null&&r.revokedAt()==null){e.setValue(new LinkRecord(r.id(),r.minecraftUuid(),r.codeHash(),r.expiresAt(),null,now,reason));return true;}}return false;} public void audit(LinkAudit a){audits.add(a);} }
}
