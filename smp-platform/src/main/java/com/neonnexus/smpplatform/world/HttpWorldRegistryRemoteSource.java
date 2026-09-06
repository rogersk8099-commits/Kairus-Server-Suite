package com.neonnexus.smpplatform.world;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

/** Production HTTP adapter; API keys come only from the configured environment variable. */
public final class HttpWorldRegistryRemoteSource implements WorldRegistryRemoteSource {
    private final HttpClient client; private final URI endpoint; private final String token; private final WorldRegistryJsonCodec codec = new WorldRegistryJsonCodec();
    public HttpWorldRegistryRemoteSource(String baseUrl, String token) {
        if (token == null || token.isBlank()) throw new IllegalStateException("Central API token environment variable is required when central-api.enabled is true");
        this.client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build(); this.endpoint = URI.create(Objects.requireNonNull(baseUrl).replaceAll("/$", "") + "/v1/minecraft/world-registry"); this.token = token;
    }
    @Override public CompletionStage<RegistryDocument> fetch(long knownRevision) {
        HttpRequest request = HttpRequest.newBuilder(endpoint).timeout(Duration.ofSeconds(15)).header("Accept","application/json").header("Authorization","Bearer " + token).header("If-None-Match", Long.toString(knownRevision)).GET().build();
        return client.sendAsync(request, HttpResponse.BodyHandlers.ofString()).thenApply(response -> { if (response.statusCode() / 100 != 2) throw new IllegalStateException("World Registry API returned HTTP " + response.statusCode()); return codec.decodeDocument(response.body()); });
    }
}
