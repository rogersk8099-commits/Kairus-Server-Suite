package network.neonnexus.smp.admin.monitor;

import network.neonnexus.smp.admin.player.PlayerDirectory;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.plugin.Plugin;

import java.lang.management.ManagementFactory;
import java.util.ArrayList;
import java.util.List;

/** Isolated health probes keep optional integrations from becoming startup dependencies. */
public final class HealthAdapters {
    private HealthAdapters() { }
    public static List<HealthMetric> snapshot(java.util.Collection<PlayerDirectory.PlayerProfile> profiles) {
        ArrayList<HealthMetric> result = new ArrayList<>();
        double[] tps = readTps();
        double primaryTps = tps.length == 0 ? 0D : tps[0];
        result.add(new HealthMetric("TPS (1m)", String.format("%.2f", primaryTps), primaryTps >= 19D ? State.HEALTHY : primaryTps >= 16D ? State.WARNING : State.ERROR));
        result.add(new HealthMetric("MSPT", String.format("%.2f", readMspt()), readMspt() <= 50D ? State.HEALTHY : readMspt() <= 65D ? State.WARNING : State.ERROR));
        long used = Runtime.getRuntime().totalMemory() - Runtime.getRuntime().freeMemory();
        long max = Runtime.getRuntime().maxMemory();
        int percent = (int) Math.round(used * 100D / max);
        result.add(new HealthMetric("Heap memory", (used / 1_048_576) + " / " + (max / 1_048_576) + " MiB (" + percent + "%)", percent < 75 ? State.HEALTHY : percent < 90 ? State.WARNING : State.ERROR));
        long javaPlayers = profiles.stream().filter(PlayerDirectory.PlayerProfile::online).filter(profile -> profile.edition() == PlayerDirectory.Edition.JAVA).count();
        long bedrockPlayers = profiles.stream().filter(PlayerDirectory.PlayerProfile::online).filter(profile -> profile.edition() == PlayerDirectory.Edition.BEDROCK).count();
        result.add(new HealthMetric("Players", Bukkit.getOnlinePlayers().size() + " / " + Bukkit.getMaxPlayers(), State.HEALTHY));
        result.add(new HealthMetric("Java players", String.valueOf(javaPlayers), State.HEALTHY));
        result.add(new HealthMetric("Bedrock players", String.valueOf(bedrockPlayers), State.HEALTHY));
        long chunks = Bukkit.getWorlds().stream().mapToLong(world -> world.getLoadedChunks().length).sum();
        long entities = Bukkit.getWorlds().stream().mapToLong(World::getEntityCount).sum();
        result.add(new HealthMetric("Loaded worlds", String.valueOf(Bukkit.getWorlds().size()), State.HEALTHY));
        result.add(new HealthMetric("Loaded chunks", String.valueOf(chunks), chunks < 100_000 ? State.HEALTHY : State.WARNING));
        result.add(new HealthMetric("Entities", String.valueOf(entities), entities < 20_000 ? State.HEALTHY : State.WARNING));
        for (String integration : List.of("LuckPerms", "Geyser-Spigot", "floodgate", "Multiverse-Core", "Multiverse-Inventories", "PlotSquared", "PlaceholderAPI", "Skript")) {
            Plugin plugin = Bukkit.getPluginManager().getPlugin(integration);
            result.add(new HealthMetric(integration, plugin != null && plugin.isEnabled() ? "Available" : "Not installed", plugin != null && plugin.isEnabled() ? State.HEALTHY : State.WARNING));
        }
        result.add(new HealthMetric("Database", "Awaiting core health adapter", State.WARNING));
        result.add(new HealthMetric("Central API", "Awaiting integration adapter", State.WARNING));
        result.add(new HealthMetric("Discord Bot", "Via central API only", State.WARNING));
        return List.copyOf(result);
    }
    private static double[] readTps() {
        try { return (double[]) Bukkit.getServer().getClass().getMethod("getTPS").invoke(Bukkit.getServer()); }
        catch (ReflectiveOperationException ignored) { return new double[0]; }
    }
    private static double readMspt() {
        try { return ((Number) Bukkit.getServer().getClass().getMethod("getAverageTickTime").invoke(Bukkit.getServer())).doubleValue(); }
        catch (ReflectiveOperationException ignored) { return 0D; }
    }
    public enum State { HEALTHY("<green>●"), WARNING("<yellow>●"), ERROR("<red>●"); private final String icon; State(String icon) { this.icon = icon; } public String icon() { return icon; } }
    public record HealthMetric(String name, String value, State state) { }
}
