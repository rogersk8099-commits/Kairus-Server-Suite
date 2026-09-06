package gg.neonnexus.smpplatform.integrations.api;

import java.net.URI;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

@FunctionalInterface
public interface HttpTransport {
    CompletableFuture<ApiResponse> post(URI uri, String jsonBody, Map<String, String> headers);
}
