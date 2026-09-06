package gg.neonnexus.smpplatform.integrations;

import gg.neonnexus.smpplatform.integrations.api.*;
import gg.neonnexus.smpplatform.integrations.messaging.*;
import java.net.URI; import java.net.http.HttpHeaders; import java.time.Instant; import java.util.*; import java.util.concurrent.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class PayloadBoundsTest {
    @Test void chatAndAnnouncementPayloadsRejectOversizedContent() {
        assertThrows(IllegalArgumentException.class, () -> new WorldChatPayload(UUID.randomUUID(), null, "Player", NexusWorld.ASHFALL, "x".repeat(257), Instant.now()));
        assertThrows(IllegalArgumentException.class, () -> new AnnouncementPayload(UUID.randomUUID(), "staff", AnnouncementPayload.Channel.CHAT, "", "x".repeat(513), Optional.empty(), false, false, false));
    }
    @Test void apiClientRejectsPayloadBeforeTransport() {
        CentralApiClient client = new CentralApiClient(URI.create("https://example.invalid"), (u,b,h) -> fail("transport must not run"), Runnable::run, Map::of, new IntegrationHealth(), 256);
        CompletionException error = assertThrows(CompletionException.class, () -> client.send(CentralApiOperation.EVENT, Map.of("large", "x".repeat(300))).join());
        assertInstanceOf(CentralApiClient.PayloadTooLargeException.class, error.getCause());
    }
}
