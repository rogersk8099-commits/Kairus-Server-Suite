package gg.neonnexus.smpplatform.integrations.messaging;

import gg.neonnexus.smpplatform.integrations.api.CentralApiClient;
import gg.neonnexus.smpplatform.integrations.api.CentralApiOperation;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import net.kyori.adventure.text.Component;

/** Rendering is Adventure-only. Outbound fan-out is async and contains structured MiniMessage, never legacy colour codes. */
public final class AnnouncementService {
    private final CentralApiClient api;
    public AnnouncementService(CentralApiClient api) { this.api = api; }
    public CompletableFuture<Boolean> announce(AnnouncementPayload payload, Consumer<Component> minecraftDelivery) {
        minecraftDelivery.accept(payload.bodyComponent());
        if (!payload.platform() && !payload.discord() && !payload.website()) return CompletableFuture.completedFuture(true);
        return api.send(CentralApiOperation.ANNOUNCEMENT, Map.of("id", payload.id().toString(), "actor", payload.actor(), "channel", payload.channel().name(), "title", payload.titleMiniMessage(), "body", payload.bodyMiniMessage(), "worldId", payload.world().map(w -> w.id()).orElse(""), "platform", payload.platform(), "discord", payload.discord(), "website", payload.website()), payload.id()).thenApply(response -> response.successful());
    }
}
