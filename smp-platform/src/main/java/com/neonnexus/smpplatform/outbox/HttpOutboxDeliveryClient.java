package com.neonnexus.smpplatform.outbox;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.concurrent.CompletionStage;

/** Sends event payloads to the Central API; retries remain owned by OutboxDispatcher. */
public final class HttpOutboxDeliveryClient implements OutboxDeliveryClient {
    private final HttpClient client; private final URI endpoint; private final String token;
    public HttpOutboxDeliveryClient(String baseUrl, String token) {
        if (token == null || token.isBlank()) throw new IllegalStateException("Central API token environment variable is required for outbox delivery");
        client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build(); endpoint = URI.create(baseUrl.replaceAll("/$", "") + "/v1/minecraft/events"); this.token = token;
    }
    @Override public CompletionStage<DeliveryReceipt> deliver(OutboxEvent event) {
        HttpRequest request = HttpRequest.newBuilder(endpoint).timeout(Duration.ofSeconds(15)).header("Authorization", "Bearer " + token).header("Content-Type", "application/json").header("Idempotency-Key", event.idempotencyKey()).POST(HttpRequest.BodyPublishers.ofString(event.payloadJson())).build();
        return client.sendAsync(request, HttpResponse.BodyHandlers.ofString()).thenApply(response -> response.statusCode() / 100 == 2 ? DeliveryReceipt.acknowledged() : new DeliveryReceipt(false, "HTTP " + response.statusCode()));
    }
}
