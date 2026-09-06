package gg.neonnexus.smpplatform.integrations.adapters;

import java.util.LinkedHashMap;
import java.util.Map;

/** Plugin onEnable calls this once after config is loaded; no adapter is fatal. */
public final class IntegrationBootstrap {
    @FunctionalInterface public interface Initializer { AdapterResult<Boolean> initialize(); }
    private final Map<String, Initializer> initializers = new LinkedHashMap<>();
    public IntegrationBootstrap add(String name, Initializer initializer) { initializers.put(name, initializer); return this; }
    public Map<String, AdapterResult<Boolean>> initializeAll() {
        Map<String, AdapterResult<Boolean>> results = new LinkedHashMap<>();
        initializers.forEach((name, initializer) -> {
            try { results.put(name, initializer.initialize()); }
            catch (RuntimeException exception) { results.put(name, AdapterResult.failed("Initialization failure: " + exception.getClass().getSimpleName())); }
        });
        return Map.copyOf(results);
    }
}
