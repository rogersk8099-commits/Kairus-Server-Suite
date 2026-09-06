package gg.neonnexus.smpplatform.integrations.api;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/** Must be invoked by CentralApiClient's IO executor, never a Bukkit event thread. */
public final class JavaHttpTransport implements HttpTransport {
    private final HttpClient client;
    private final Duration requestTimeout;
    public JavaHttpTransport(HttpClient client, Duration requestTimeout) {
        this.client = client; this.requestTimeout = requestTimeout;
    }
    @Override public CompletableFuture<ApiResponse> post(URI uri, String jsonBody, Map<String, String> headers) {
        HttpRequest.Builder request = HttpRequest.newBuilder(uri).timeout(requestTimeout)
            .POST(HttpRequest.BodyPublishers.ofString(jsonBody));
        headers.forEach(request::header);
        return client.sendAsync(request.build(), HttpResponse.BodyHandlers.ofString())
            .thenApply(response -> new ApiResponse(response.statusCode(), response.body(), response.headers()));
    }
}
