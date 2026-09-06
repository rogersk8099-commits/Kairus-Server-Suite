package gg.neonnexus.smpplatform.integrations.adapters;

import gg.neonnexus.smpplatform.integrations.IntegrationHealth;

/** Never links optional plugin classes during bootstrap; only enabled plugins are activated. */
public final class SoftDependency {
    private final String pluginName;
    private final PluginPresence presence;
    private final IntegrationHealth health;
    public SoftDependency(String pluginName, PluginPresence presence, IntegrationHealth health) {
        this.pluginName = pluginName; this.presence = presence; this.health = health;
    }
    public boolean available() {
        boolean enabled = presence.isEnabled(pluginName);
        health.report(pluginName, enabled ? IntegrationHealth.State.HEALTHY : IntegrationHealth.State.UNAVAILABLE,
            enabled ? "Optional dependency enabled" : "Optional dependency not installed or disabled");
        return enabled;
    }
    public String pluginName() { return pluginName; }
}
