package gg.neonnexus.smpplatform.integrations.messaging;

import gg.neonnexus.smpplatform.integrations.NexusWorld;
import java.util.Optional;
import java.util.UUID;

/** Cache-only lookup used on chat event threads. Refresh platform identity outside the chat pipeline. */
public interface WorldIdentityResolver {
    Optional<NexusWorld> worldForMinecraftWorld(String minecraftWorldName);
    Optional<String> platformUserId(UUID minecraftUuid);
}
