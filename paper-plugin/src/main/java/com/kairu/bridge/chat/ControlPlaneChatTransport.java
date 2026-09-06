package com.kairu.bridge.chat;

import com.kairu.bridge.api.ControlPlaneClient;
import com.kairu.bridge.payload.BridgeEventPayload;
import com.kairu.bridge.payload.Json;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

/**
 * Uses only ControlPlaneClient.get/post, retaining its request timeouts, async execution, retries,
 * authentication headers, and close semantics. It has no Discord token or direct Discord dependency.
 */
final class ControlPlaneChatTransport implements ChatBridgeTransport {
    static final String EVENT_PATH = "/api/plugin/bridge-events";
    static final String QUEUE_PATH = "/api/plugin/chat/queued";
    private final ControlPlaneClient client;

    ControlPlaneChatTransport(ControlPlaneClient client) { this.client = Objects.requireNonNull(client, "client"); }

    @Override public CompletableFuture<Void> publish(BridgeEventPayload payload, int maximumPayloadBytes) {
        return client.post(EVENT_PATH, payload.toJson(maximumPayloadBytes)).thenApply(ControlPlaneChatTransport::requireSuccess);
    }

    @Override public CompletableFuture<List<QueuedDiscordMessage>> poll(int limit) {
        return client.get(QUEUE_PATH + "?limit=" + limit).thenApply(response -> {
            requireSuccess(response);
            try {
                Object parsed = Json.parse(response.body());
                if (!(parsed instanceof Map<?, ?> envelope) || !(envelope.get("messages") instanceof List<?> raw)) {
                    throw new IllegalArgumentException("Queued chat response lacks messages");
                }
                List<QueuedDiscordMessage> output = new ArrayList<>();
                for (Object entry : raw) {
                    if (output.size() >= limit) break;
                    if (entry instanceof Map<?, ?> map) output.add(QueuedDiscordMessage.fromMap(map));
                }
                return List.copyOf(output);
            } catch (RuntimeException exception) {
                throw new CompletionException(new IllegalArgumentException("Invalid queued chat response", exception));
            }
        });
    }

    @Override public CompletableFuture<Void> acknowledge(String messageId, String status, String detail) {
        if (!LoopMarkers.isSafeMessageId(messageId)) return CompletableFuture.failedFuture(new IllegalArgumentException("Unsafe queued chat id"));
        if (!("delivered".equals(status) || "rejected".equals(status))) return CompletableFuture.failedFuture(new IllegalArgumentException("Unsafe queued chat acknowledgement"));
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", status);
        body.put("detail", ChatSanitizer.sanitize(detail, 160));
        return client.post("/api/plugin/chat/" + messageId + "/ack", Json.object(body)).thenApply(ControlPlaneChatTransport::requireSuccess);
    }

    private static Void requireSuccess(ControlPlaneClient.ApiResponse response) {
        if (!response.success()) throw new CompletionException(new ControlPlaneClient.ApiException(response.statusCode(), response.body()));
        return null;
    }
}
