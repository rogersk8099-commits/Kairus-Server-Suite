package gg.neonnexus.smpplatform.integrations.adapters;

import gg.neonnexus.smpplatform.integrations.IntegrationHealth;
import java.util.LinkedHashMap;
import java.util.Map;

/** Central catalogue of soft dependencies; callers must consume adapters, not query plugins ad hoc. */
public final class OptionalAdapters {
    public static final String LUCKPERMS = "LuckPerms";
    public static final String PLACEHOLDER_API = "PlaceholderAPI";
    public static final String MULTIVERSE = "Multiverse-Core";
    public static final String MULTIVERSE_INVENTORIES = "Multiverse-Inventories";
    public static final String PLOT_SQUARED = "PlotSquared";
    public static final String VAULT = "Vault";
    public static final String GEYSER = "Geyser-Spigot";
    public static final String FLOODGATE = "floodgate";
    public static final String SKRIPT = "Skript";

    private final Map<String, SoftDependency> dependencies;
    public OptionalAdapters(PluginPresence presence, IntegrationHealth health) {
        Map<String, SoftDependency> mutable = new LinkedHashMap<>();
        for (String name : new String[]{LUCKPERMS, PLACEHOLDER_API, MULTIVERSE, MULTIVERSE_INVENTORIES, PLOT_SQUARED, VAULT, GEYSER, FLOODGATE, SKRIPT}) {
            mutable.put(name, new SoftDependency(name, presence, health));
        }
        dependencies = Map.copyOf(mutable);
    }
    public boolean available(String pluginName) {
        SoftDependency dependency = dependencies.get(pluginName);
        if (dependency == null) throw new IllegalArgumentException("Unknown integration " + pluginName);
        return dependency.available();
    }
    public Map<String, SoftDependency> dependencies() { return dependencies; }
}
