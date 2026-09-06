package gg.neonnexus.smpplatform.integrations.placeholder;

import gg.neonnexus.smpplatform.integrations.NexusWorld;
import java.util.Optional;
import java.util.UUID;

/** Fast cache-only profile contract: PlaceholderAPI must not cause SQL or HTTP on its caller thread. */
public interface NexusPlaceholderValues {
    Optional<NexusWorld> world(UUID playerId);
    String guild(UUID playerId); String guildTag(UUID playerId); long points(UUID playerId, String currency);
    String hardcoreStatus(UUID playerId); String membership(UUID playerId); String platform(UUID playerId); String season(UUID playerId); long eventPoints(UUID playerId);
}
