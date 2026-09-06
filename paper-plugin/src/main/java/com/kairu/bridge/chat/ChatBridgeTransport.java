package com.kairu.bridge.chat;

import com.kairu.bridge.payload.BridgeEventPayload;

import java.util.List;
import java.util.concurrent.CompletableFuture;

/** Network boundary for chat bridge operations; implementations must never call Bukkit APIs. */
interface ChatBridgeTransport {
    CompletableFuture<Void> publish(BridgeEventPayload payload, int maximumPayloadBytes);
    CompletableFuture<List<QueuedDiscordMessage>> poll(int limit);
    CompletableFuture<Void> acknowledge(String messageId, String status, String detail);
}
