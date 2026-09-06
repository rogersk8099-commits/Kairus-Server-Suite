package com.neonnexus.smpplatform.config;

import com.neonnexus.smpplatform.inventory.InventoryGroupPolicy;
import java.time.Duration;
import java.util.List;
import java.util.Map;

/** All phase-one configuration is parsed once during startup, then passed as typed data. */
public record PlatformConfiguration(
        Core core,
        WorldFile worlds,
        Guilds guilds,
        Points points,
        Hardcore hardcore,
        Events events,
        Gui gui,
        Messages messages,
        Integrations integrations
) {
    public record Core(String serverId, Database database, CentralApi centralApi, Async async, Inventory inventory) {
        public record Database(String jdbcUrl, String username, String passwordEnvironment, int poolSize,
                               Duration connectionTimeout, Duration validationTimeout) { }
        public record CentralApi(boolean enabled, String baseUrl, String tokenEnvironment, Duration syncInterval) { }
        public record Async(int ioThreads, Duration shutdownTimeout) { }
        public record Inventory(boolean shareQuarryWithAshfall) {
            public InventoryGroupPolicy policy() { return new InventoryGroupPolicy(shareQuarryWithAshfall); }
        }
    }

    public record WorldFile(String cacheFile, long initialRevision, Map<String, WorldOverride> overrides) {
        public record WorldOverride(String minecraftWorldName, boolean maintenanceMode) { }
    }
    public record Guilds(boolean enabled, Map<String, String> worldPolicy) { }
    public record Points(boolean enabled, List<String> currencies) { }
    public record Hardcore(boolean enabled, String worldId, ResetWindow reset) {
        public record ResetWindow(boolean enabled, String day, String time, String timezone) { }
    }
    public record Events(boolean enabled, String worldId, int maximumGauntletParticipants, List<String> eventTypes) { }
    public record Gui(String theme, Map<String, String> colors, boolean customModelDataEnabled, boolean bedrockFormsEnabled) { }
    public record Messages(String prefix, String worldOffline, String worldMaintenance, String registryOffline) { }
    public record Integrations(Map<String, Boolean> enabled, Outbox outbox) {
        public record Outbox(boolean enabled, Duration pollInterval, int maxAttempts, Duration baseBackoff) { }
        public boolean enabled(String integration) { return enabled.getOrDefault(integration, false); }
    }
}
