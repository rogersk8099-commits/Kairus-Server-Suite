package gg.neonnexus.smpplatform.integrations.adapters;

@FunctionalInterface
public interface PluginPresence {
    boolean isEnabled(String pluginName);
}
