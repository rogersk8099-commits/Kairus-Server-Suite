package gg.neonnexus.smpplatform.integrations.api;

import gg.neonnexus.smpplatform.integrations.IntegrationHealth;
import java.net.URI;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;

/**
 * HTTP is started only after entering ioExecutor.  Bukkit listeners can invoke this safely because it never joins.
 * Auth is supplied by a host-owned header supplier; tokens are deliberately not stored, logged, or serialized here.
 */
public final class CentralApiClient {
    public static final String INTEGRATION = "Central API";
    @FunctionalInterface public interface HeaderSupplier { Map<String, String> headers(); }
    private final URI baseUri;
    private final HttpTransport transport;
    private final Executor ioExecutor;
    private final HeaderSupplier headerSupplier;
    private final IntegrationHealth health;
    private final int maxBodyChars;

    public CentralApiClient(URI baseUri, HttpTransport transport, Executor ioExecutor, HeaderSupplier headerSupplier,
                            IntegrationHealth health, int maxBodyChars) {
        this.baseUri = Objects.requireNonNull(baseUri); this.transport = Objects.requireNonNull(transport);
        this.ioExecutor = Objects.requireNonNull(ioExecutor); this.headerSupplier = Objects.requireNonNull(headerSupplier);
        this.health = Objects.requireNonNull(health);
        if (maxBodyChars < 256) throw new IllegalArgumentException("maxBodyChars must be at least 256");
        this.maxBodyChars = maxBodyChars;
    }
    public CompletableFuture<ApiResponse> send(CentralApiOperation operation, Map<String, ?> payload) {
        return send(operation, payload, null);
    }
    public CompletableFuture<ApiResponse> send(CentralApiOperation operation, Map<String, ?> payload, UUID idempotencyKey) {
        return sendJson(operation, JsonPayload.object(payload), idempotencyKey);
    }
    /** Sends an already validated JSON document; used only for durable outbox rows. */
    public CompletableFuture<ApiResponse> sendJson(CentralApiOperation operation, String json, UUID idempotencyKey) {
        Objects.requireNonNull(operation, "operation");
        Objects.requireNonNull(json, "json");
        if (json.length() > maxBodyChars) return CompletableFuture.failedFuture(new PayloadTooLargeException(json.length(), maxBodyChars));
        return CompletableFuture.supplyAsync(() -> {
            Map<String, String> headers = new LinkedHashMap<>();
            headers.put("Content-Type", "application/json"); headers.put("Accept", "application/json");
            headerSupplier.headers().forEach((key, value) -> { if (isSafeHeader(key, value)) headers.put(key, value); });
            if (idempotencyKey != null) headers.put("Idempotency-Key", idempotencyKey.toString());
            return headers;
        }, ioExecutor).thenCompose(headers -> transport.post(baseUri.resolve(operation.path()), json, headers))
          .whenComplete((response, error) -> {
              if (error != null) health.report(INTEGRATION, IntegrationHealth.State.WARNING, compact(error));
              else if (response.successful()) health.report(INTEGRATION, IntegrationHealth.State.HEALTHY, "HTTP " + response.statusCode());
              else health.report(INTEGRATION, IntegrationHealth.State.WARNING, "HTTP " + response.statusCode());
          });
    }
    public CompletableFuture<ApiResponse> playerSync(Map<String, ?> payload) { return send(CentralApiOperation.PLAYER_SYNC, payload); }
    public CompletableFuture<ApiResponse> worldSync(Map<String, ?> payload) { return send(CentralApiOperation.WORLD_SYNC, payload); }
    public CompletableFuture<ApiResponse> guildSync(Map<String, ?> payload) { return send(CentralApiOperation.GUILD_SYNC, payload); }
    public CompletableFuture<ApiResponse> pointsSync(Map<String, ?> payload) { return send(CentralApiOperation.POINTS_SYNC, payload); }
    public CompletableFuture<ApiResponse> event(Map<String, ?> payload, UUID eventId) { return send(CentralApiOperation.EVENT, payload, eventId); }
    public CompletableFuture<ApiResponse> achievement(Map<String, ?> payload) { return send(CentralApiOperation.ACHIEVEMENT, payload); }
    public CompletableFuture<ApiResponse> notification(Map<String, ?> payload) { return send(CentralApiOperation.NOTIFICATION, payload); }
    public CompletableFuture<ApiResponse> membership(Map<String, ?> payload) { return send(CentralApiOperation.MEMBERSHIP, payload); }
    public CompletableFuture<ApiResponse> accountLink(Map<String, ?> payload) { return send(CentralApiOperation.ACCOUNT_LINK, payload); }
    public CompletableFuture<ApiResponse> statistics(Map<String, ?> payload) { return send(CentralApiOperation.STATISTICS, payload); }
    public CompletableFuture<ApiResponse> serverStatus(Map<String, ?> payload) { return send(CentralApiOperation.SERVER_STATUS, payload); }
    private static boolean isSafeHeader(String name, String value) { return name != null && value != null && !name.contains("\\r") && !name.contains("\\n") && !value.contains("\\r") && !value.contains("\\n"); }
    private static String compact(Throwable error) {
        Throwable root = error instanceof CompletionException && error.getCause() != null ? error.getCause() : error;
        String message = root.getMessage(); return root.getClass().getSimpleName() + (message == null ? "" : ": " + message.substring(0, Math.min(160, message.length())));
    }
    public static final class PayloadTooLargeException extends RuntimeException {
        private final int actualChars;
        private final int maxChars;
        public PayloadTooLargeException(int actualChars, int maxChars) {
            super("Payload is " + actualChars + " chars; maximum is " + maxChars);
            this.actualChars = actualChars;
            this.maxChars = maxChars;
        }
        public int actualChars() { return actualChars; }
        public int maxChars() { return maxChars; }
    }
}
