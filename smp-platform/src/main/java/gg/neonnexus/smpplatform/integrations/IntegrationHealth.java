package gg.neonnexus.smpplatform.integrations;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

public final class IntegrationHealth {
    public enum State { HEALTHY, WARNING, ERROR, UNAVAILABLE }
    public record Status(String integration, State state, String detail, Instant checkedAt) {
        public Status {
            if (integration == null || integration.isBlank()) throw new IllegalArgumentException("integration is required");
            if (detail == null) detail = "";
            if (checkedAt == null) checkedAt = Instant.now();
        }
    }
    private final Map<String, Status> statuses = new ConcurrentHashMap<>();
    public void report(String integration, State state, String detail) {
        statuses.put(integration, new Status(integration, state, detail, Instant.now()));
    }
    public Status status(String integration) {
        return statuses.getOrDefault(integration, new Status(integration, State.UNAVAILABLE, "Not checked", Instant.now()));
    }
    public Map<String, Status> snapshot() { return Map.copyOf(statuses); }
    public Optional<Status> find(String integration) { return Optional.ofNullable(statuses.get(integration)); }
}
