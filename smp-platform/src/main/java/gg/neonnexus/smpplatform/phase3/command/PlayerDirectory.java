package gg.neonnexus.smpplatform.phase3.command;

import java.util.Optional;
import java.util.UUID;

/** Parent identity layer resolves Java/Bedrock players by stable UUID rather than name prefixes. */
public interface PlayerDirectory {
    Optional<UUID> findUuid(String playerName);
}
