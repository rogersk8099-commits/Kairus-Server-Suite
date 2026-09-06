package gg.neonnexus.smpplatform.integrations;

import gg.neonnexus.smpplatform.integrations.api.*;
import java.net.URI; import java.net.http.HttpHeaders; import java.util.*; import java.util.concurrent.*; import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AsyncBoundaryTest {
    @Test void clientDoesNotStartHttpUntilIoExecutorRuns() {
        QueueExecutor executor = new QueueExecutor(); AtomicBoolean transportCalled = new AtomicBoolean();
        CentralApiClient client = new CentralApiClient(URI.create("https://example.invalid"), (uri,body,headers) -> { transportCalled.set(true); return CompletableFuture.completedFuture(new ApiResponse(202, "", HttpHeaders.of(Map.of(), (a,b)->true))); }, executor, Map::of, new IntegrationHealth(), 4096);
        CompletableFuture<ApiResponse> future = client.playerSync(Map.of("player", "test"));
        assertFalse(transportCalled.get(), "HTTP must not start on the caller/event thread"); assertFalse(future.isDone());
        executor.runAll(); assertTrue(transportCalled.get()); assertEquals(202, future.join().statusCode());
    }
    private static final class QueueExecutor implements Executor { private final Queue<Runnable> queue = new ArrayDeque<>(); public void execute(Runnable task){queue.add(task);} void runAll(){while(!queue.isEmpty())queue.remove().run();} }
}
