package com.kairu.bridge.api;

import com.kairu.bridge.config.BridgeConfig;
import com.kairu.bridge.payload.Json;

import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import java.util.logging.Level;
import java.util.logging.Logger;

/** HTTP boundary. Every operation starts with sendAsync and callbacks run off the Bukkit main thread. */
public final class ControlPlaneClient implements AutoCloseable {
    private final HttpClient client;
    private final BridgeConfig config;
    private final ScheduledExecutorService retryExecutor;
    private final Logger logger;
    private volatile boolean closed;

    public ControlPlaneClient(BridgeConfig config, Logger logger) {
        this(config, logger, HttpClient.newBuilder().connectTimeout(config.connectTimeout()).version(HttpClient.Version.HTTP_2).build(),
                Executors.newSingleThreadScheduledExecutor(r -> { Thread thread = new Thread(r, "KairuBridge-HTTP-Retry"); thread.setDaemon(true); return thread; }));
    }

    ControlPlaneClient(BridgeConfig config, Logger logger, HttpClient client, ScheduledExecutorService retryExecutor) {
        this.config = Objects.requireNonNull(config); this.logger = Objects.requireNonNull(logger); this.client = Objects.requireNonNull(client); this.retryExecutor = Objects.requireNonNull(retryExecutor);
    }

    public CompletableFuture<ApiResponse> post(String path, String json) {
        return request("POST", path, json);
    }

    public CompletableFuture<ApiResponse> get(String path) {
        return request("GET", path, null);
    }

    public CompletableFuture<List<RemoteCommand>> pollCommands() {
        return get("/api/plugin/commands").thenApply(response -> {
            if (!response.success()) throw new CompletionException(new ApiException(response.statusCode(), response.body()));
            try { return parseCommands(response.body()); }
            catch (RuntimeException exception) { throw new CompletionException(new IllegalArgumentException("Invalid remote command payload", exception)); }
        });
    }

    public CompletableFuture<ApiResponse> acknowledge(String id, boolean succeeded, String detail) {
        if (id == null || !id.matches("[A-Za-z0-9_-]{1,128}")) return CompletableFuture.failedFuture(new IllegalArgumentException("Unsafe command id"));
        String safeDetail = detail == null ? "" : detail.substring(0, Math.min(detail.length(), 240));
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status", succeeded ? "completed" : "failed");
        if (!succeeded) body.put("errorMessage", safeDetail.isBlank() ? "Command execution failed" : safeDetail);
        return post("/api/plugin/commands/" + id + "/ack", Json.object(body));
    }

    private CompletableFuture<ApiResponse> request(String method, String path, String json) {
        if (closed) return CompletableFuture.failedFuture(new IllegalStateException("KairuBridge client is closed"));
        if (!config.isConfigured()) return CompletableFuture.failedFuture(new IllegalStateException("KairuBridge is not configured"));
        return attempt(method, path, json, 0);
    }

    private CompletableFuture<ApiResponse> attempt(String method, String path, String json, int attempt) {
        HttpRequest.Builder builder = HttpRequest.newBuilder(config.endpoint(path)).timeout(config.requestTimeout());
        AuthHeaders.apply(builder, config.apiKey(), config.serverId());
        if (json == null) builder.method(method, HttpRequest.BodyPublishers.noBody());
        else builder.header("Content-Type", "application/json; charset=utf-8").method(method, HttpRequest.BodyPublishers.ofString(json, StandardCharsets.UTF_8));
        return client.sendAsync(builder.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8))
                .handle((response, throwable) -> new Attempt(response == null ? null : new ApiResponse(response.statusCode(), response.body()), throwable))
                .thenCompose(result -> {
                    boolean retry = shouldRetry(result.response, result.error) && attempt < config.maxRetries() && !closed;
                    if (!retry) {
                        if (result.error != null) return CompletableFuture.failedFuture(result.error);
                        return CompletableFuture.completedFuture(result.response);
                    }
                    long delay = retryDelay(attempt);
                    logger.fine(() -> "KairuBridge HTTP retry " + (attempt + 1) + " in " + delay + "ms for " + method + " " + path);
                    CompletableFuture<ApiResponse> delayed = new CompletableFuture<>();
                    retryExecutor.schedule(() -> attempt(method, path, json, attempt + 1).whenComplete((response, error) -> {
                        if (error != null) delayed.completeExceptionally(error); else delayed.complete(response);
                    }), delay, TimeUnit.MILLISECONDS);
                    return delayed;
                });
    }

    private long retryDelay(int attempt) {
        long multiplier = 1L << Math.min(attempt, 4);
        long base = Math.min(30_000L, config.retryBaseDelayMillis() * multiplier);
        long jitter = ThreadLocalRandom.current().nextLong(Math.max(1L, Math.min(250L, base / 4 + 1)));
        return Math.min(30_000L, base + jitter);
    }

    private static boolean shouldRetry(ApiResponse response, Throwable error) {
        if (error != null) return true;
        return response != null && (response.statusCode() == 408 || response.statusCode() == 429 || response.statusCode() >= 500);
    }

    @SuppressWarnings("unchecked")
    private static List<RemoteCommand> parseCommands(String body) {
        Object parsed = Json.parse(body); List<?> raw;
        if (parsed instanceof List<?> list) raw = list;
        else if (parsed instanceof Map<?, ?> map && map.get("commands") instanceof List<?> list) raw = list;
        else return List.of();
        List<RemoteCommand> commands = new ArrayList<>();
        for (Object entry : raw) {
            if (!(entry instanceof Map<?, ?> map) || commands.size() >= 100) continue;
            String id = text(map.get("id"));
            String contractType = text(map.get("commandType"));
            Map<?, ?> payload = map.get("payload") instanceof Map<?, ?> value ? value : Map.of();
            String action = text(payload.get("action"));
            String player = text(payload.get("player"));
            String message = text(payload.get("message"));
            String type = null;
            if ("notification".equals(contractType)) type = "NOTIFY";
            else if ("whitelist".equals(contractType) && "add".equalsIgnoreCase(action)) type = "WHITELIST_ADD";
            else if ("whitelist".equals(contractType) && "remove".equalsIgnoreCase(action)) type = "WHITELIST_REMOVE";
            if (id != null && id.matches("[A-Za-z0-9_-]{1,128}") && type != null) commands.add(new RemoteCommand(id, type, player, message));
        }
        return List.copyOf(commands);
    }

    private static String text(Object value) { return value instanceof String string && string.length() <= 512 ? string : null; }

    @Override public void close() {
        closed = true;
        retryExecutor.shutdownNow();
        try { if (!retryExecutor.awaitTermination(2, TimeUnit.SECONDS)) logger.warning("KairuBridge retry executor did not stop promptly"); }
        catch (InterruptedException exception) { Thread.currentThread().interrupt(); }
    }

    private record Attempt(ApiResponse response, Throwable error) { }
    public record ApiResponse(int statusCode, String body) { public boolean success() { return statusCode >= 200 && statusCode < 300; } }
    public record RemoteCommand(String id, String type, String player, String message) { }
    public static final class ApiException extends RuntimeException { public ApiException(int statusCode, String body) { super("Control plane returned HTTP " + statusCode + (body == null || body.isBlank() ? "" : ": " + body.substring(0, Math.min(160, body.length())))); } }
}
