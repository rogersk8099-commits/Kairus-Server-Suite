package com.kairu.bridge.api;

import java.net.http.HttpRequest;
import java.util.Objects;

public final class AuthHeaders {
    private AuthHeaders() { }

    public static HttpRequest.Builder apply(HttpRequest.Builder request, String apiKey, String serverId) {
        Objects.requireNonNull(request, "request");
        if (apiKey == null || apiKey.isBlank() || apiKey.indexOf('\r') >= 0 || apiKey.indexOf('\n') >= 0) {
            throw new IllegalArgumentException("Plugin API key is missing or unsafe");
        }
        if (serverId == null || serverId.isBlank() || serverId.indexOf('\r') >= 0 || serverId.indexOf('\n') >= 0) {
            throw new IllegalArgumentException("Server ID is missing or unsafe");
        }
        return request.header("Authorization", "Bearer " + apiKey)
                .header("X-Kairu-Server-Id", serverId)
                .header("Accept", "application/json")
                .header("User-Agent", "KairuBridge/1.0");
    }
}
