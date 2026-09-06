package gg.neonnexus.smpplatform.integrations.messaging;

import gg.neonnexus.smpplatform.integrations.api.CentralApiClient;
import gg.neonnexus.smpplatform.integrations.api.CentralApiOperation;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/** Listener adapters call publish and immediately return; CentralApiClient owns asynchronous delivery. */
public final class WorldChatBridge {
    @FunctionalInterface public interface WorldChatPolicy { boolean enabled(WorldChatPayload payload); }
    private final CentralApiClient api; private final WorldChatPolicy policy;
    public WorldChatBridge(CentralApiClient api, WorldChatPolicy policy) { this.api = api; this.policy = policy; }
    public CompletableFuture<Boolean> publish(WorldChatPayload payload) {
        if (!payload.bridgeEnabledByDefault() || !policy.enabled(payload)) return CompletableFuture.completedFuture(false);
        return api.send(CentralApiOperation.WORLD_CHAT, Map.of("player", payload.playerName(), "platformUserId", payload.platformUserId() == null ? "" : payload.platformUserId(), "minecraftUuid", payload.minecraftUuid().toString(), "worldId", payload.world().id(), "message", payload.message(), "timestamp", payload.timestamp().toString()))
            .thenApply(response -> response.successful());
    }
}
