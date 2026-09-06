package com.kairu.bridge.config;

import org.bukkit.configuration.file.FileConfiguration;

import java.net.URI;
import java.time.Duration;
import java.util.Locale;

/** Immutable, validated plugin settings. Secrets are deliberately never rendered by this class. */
public record BridgeConfig(
        String serverId,
        URI apiBaseUri,
        String apiKey,
        Duration connectTimeout,
        Duration requestTimeout,
        int maxRetries,
        long retryBaseDelayMillis,
        boolean heartbeatEnabled,
        long heartbeatIntervalTicks,
        boolean snapshotsEnabled,
        long snapshotIntervalTicks,
        boolean commandsEnabled,
        long commandPollIntervalTicks,
        boolean snapshotAfterLink
) {
    private static final long MIN_INTERVAL_SECONDS = 5;

    public static BridgeConfig from(FileConfiguration config) {
        String serverId = value(config.getString("server-id"), "replace-with-a-stable-server-id");
        String rawUrl = value(config.getString("api-base-url"), "https://your-control-plane.example");
        URI base = parseBaseUri(rawUrl);
        String key = value(config.getString("api-key"), "CHANGE_ME");
        if (key.contains("\r") || key.contains("\n")) {
            throw new IllegalArgumentException("api-key must not contain line breaks");
        }
        int connectSeconds = bounded(config.getInt("http.connect-timeout-seconds", 10), 1, 60);
        int requestSeconds = bounded(config.getInt("http.request-timeout-seconds", 15), 1, 120);
        int retries = bounded(config.getInt("http.max-retries", 3), 0, 5);
        long retryDelay = boundedLong(config.getLong("http.retry-base-delay-millis", 500), 100, 10_000);
        return new BridgeConfig(
                serverId, base, key, Duration.ofSeconds(connectSeconds), Duration.ofSeconds(requestSeconds), retries, retryDelay,
                config.getBoolean("heartbeat.enabled", true), ticks(config.getLong("heartbeat.interval-seconds", 30)),
                config.getBoolean("snapshots.enabled", true), ticks(config.getLong("snapshots.interval-seconds", 300)),
                config.getBoolean("commands.enabled", true), ticks(config.getLong("commands.poll-interval-seconds", 15)),
                config.getBoolean("link.snapshot-after-link", true)
        );
    }

    public boolean isConfigured() {
        return !serverId.isBlank() && !serverId.startsWith("replace-with")
                && !apiKey.isBlank() && !apiKey.equalsIgnoreCase("CHANGE_ME")
                && !apiBaseUri.getHost().equalsIgnoreCase("your-control-plane.example");
    }

    public URI endpoint(String path) {
        if (path == null || !path.startsWith("/")) throw new IllegalArgumentException("endpoint must begin with /");
        String root = apiBaseUri.toString();
        return URI.create((root.endsWith("/") ? root.substring(0, root.length() - 1) : root) + path);
    }

    private static String value(String value, String fallback) { return value == null ? fallback : value.trim(); }
    private static int bounded(int value, int min, int max) { return Math.max(min, Math.min(max, value)); }
    private static long boundedLong(long value, long min, long max) { return Math.max(min, Math.min(max, value)); }
    private static long ticks(long seconds) { return boundedLong(seconds, MIN_INTERVAL_SECONDS, 86_400) * 20L; }

    private static URI parseBaseUri(String input) {
        try {
            URI uri = URI.create(input);
            String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
            if (!(scheme.equals("https") || scheme.equals("http")) || uri.getHost() == null || uri.getUserInfo() != null) {
                throw new IllegalArgumentException("api-base-url must be an absolute HTTP(S) URL without credentials");
            }
            return uri;
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Invalid api-base-url: " + exception.getMessage(), exception);
        }
    }
}
