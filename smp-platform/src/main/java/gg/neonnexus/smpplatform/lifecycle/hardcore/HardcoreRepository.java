package gg.neonnexus.smpplatform.lifecycle.hardcore;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** PostgreSQL boundary for hardcore_players and hardcore_deaths. Never invoke a JDBC implementation on Paper's main thread. */
public interface HardcoreRepository {
    Optional<HardcorePlayer> find(UUID playerId, String season);
    List<HardcorePlayer> findByState(String season, HardcoreState state);
    void savePlayer(HardcorePlayer player);
    void appendDeath(HardcoreDeathRecord death);
}
