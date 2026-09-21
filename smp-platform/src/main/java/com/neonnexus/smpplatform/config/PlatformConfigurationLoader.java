package com.neonnexus.smpplatform.config;

import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Loads all declared configuration files. Missing or malformed operational config aborts enable safely. */
public final class PlatformConfigurationLoader {
    private static final List<String> REQUIRED_FILES = List.of("config.yml", "worlds.yml", "guilds.yml", "points.yml", "hardcore.yml", "events.yml", "gui.yml", "messages.yml", "integrations.yml");
    private final JavaPlugin plugin;

    public PlatformConfigurationLoader(JavaPlugin plugin) { this.plugin = Objects.requireNonNull(plugin); }

    public PlatformConfiguration load() {
        copyDefaults();
        YamlConfiguration config = yaml("config.yml");
        YamlConfiguration worlds = yaml("worlds.yml");
        YamlConfiguration guilds = yaml("guilds.yml");
        YamlConfiguration points = yaml("points.yml");
        YamlConfiguration hardcore = yaml("hardcore.yml");
        YamlConfiguration events = yaml("events.yml");
        YamlConfiguration gui = yaml("gui.yml");
        YamlConfiguration messages = yaml("messages.yml");
        YamlConfiguration integrations = yaml("integrations.yml");

        Path dataFolder = plugin.getDataFolder().toPath().toAbsolutePath().normalize();
        Path passwordFile = dataFolder.resolve(config.getString("storage.password-file", "database-password.txt")).normalize();
        if (!passwordFile.startsWith(dataFolder)) throw new IllegalArgumentException("storage.password-file must remain inside the SMPPlatform data folder");
        var database = new PlatformConfiguration.Core.Database(
                environmentOr(config, "storage.jdbc-url", "SMPPLATFORM_DB_JDBC_URL"), environmentOr(config, "storage.username", "SMPPLATFORM_DB_USERNAME"),
                "SMPPLATFORM_DB_PASSWORD", passwordFile, positive(config, "storage.hikari.maximum-pool-size"),
                millis(config, "storage.hikari.connection-timeout-ms"), millis(config, "storage.hikari.validation-timeout-ms"));
        var central = new PlatformConfiguration.Core.CentralApi(
                integrations.getBoolean("integrations.central-api.enabled"), environmentOr(integrations, "integrations.central-api.base-url", "SMPPLATFORM_API_BASE_URL"),
                "SMPPLATFORM_API_TOKEN", Duration.ofSeconds(60));
        var async = new PlatformConfiguration.Core.Async(positive(config, "runtime.async.io-threads"), Duration.ofSeconds(15));
        var core = new PlatformConfiguration.Core(config.getString("server.network-name", "Neon Nexus"), database, central, async,
                new PlatformConfiguration.Core.Inventory(config.getBoolean("inventory.share-quarry-with-ashfall", true)));

        Map<String, PlatformConfiguration.WorldFile.WorldOverride> overrides = new LinkedHashMap<>();
        ConfigurationSection section = requireSection(worlds, "worlds");
        for (String id : section.getKeys(false)) {
            String prefix = "worlds." + id;
            overrides.put(id, new PlatformConfiguration.WorldFile.WorldOverride(required(worlds, prefix + ".minecraft-world-name"), worlds.getBoolean(prefix + ".maintenance-mode")));
        }
        var worldFile = new PlatformConfiguration.WorldFile("world-registry-cache.json", 1L, Map.copyOf(overrides));
        var guildConfig = new PlatformConfiguration.Guilds(guilds.getBoolean("guilds.enabled", true), stringMap(requireSection(guilds, "guilds.world-policy")));
        var firstJoin = new PlatformConfiguration.Points.AutomaticReward(points.getBoolean("points.automatic-rewards.first-join.enabled", false), points.getString("points.automatic-rewards.first-join.currency", "NEXUS_POINTS"), points.getLong("points.automatic-rewards.first-join.amount", 0L), points.getString("points.automatic-rewards.first-join.reason", "First Kairu SMP join"), points.getStringList("points.automatic-rewards.first-join.worlds"));
        if (firstJoin.enabled() && firstJoin.amount() <= 0) throw new IllegalArgumentException("points.automatic-rewards.first-join.amount must be positive when enabled");
        var pointsConfig = new PlatformConfiguration.Points(points.getBoolean("points.enabled", true), new ArrayList<>(requireSection(points, "points.currencies").getKeys(false)), firstJoin);
        var hardConfig = new PlatformConfiguration.Hardcore(hardcore.getBoolean("hardcore.enabled", true), required(hardcore, "hardcore.world-id"),
                new PlatformConfiguration.Hardcore.ResetWindow(hardcore.getBoolean("hardcore.reset.enabled", true), required(hardcore, "hardcore.reset.day"), required(hardcore, "hardcore.reset.time"), required(hardcore, "hardcore.reset.timezone")));
        var eventConfig = new PlatformConfiguration.Events(events.getBoolean("events.enabled", true), required(events, "events.world-id"), events.getInt("events.event-types.GAUNTLET.max-players", 64), new ArrayList<>(requireSection(events, "events.event-types").getKeys(false)));
        var guiConfig = new PlatformConfiguration.Gui(required(gui, "gui.renderer"), stringMap(requireSection(gui, "gui.theme")), gui.getBoolean("gui.custom-model-data.enabled"), true);
        var messageConfig = new PlatformConfiguration.Messages(required(messages, "messages.prefix"), required(messages, "messages.world-unavailable"), required(messages, "messages.world-unavailable"), required(messages, "messages.database-degraded"));
        Map<String, Boolean> switches = new LinkedHashMap<>();
        for (String name : List.of("multiverse", "luckperms", "placeholderapi", "floodgate", "geyser", "plotsquared", "skript")) switches.put(name, integrations.getBoolean("integrations." + name + ".enabled", false));
        var outbox = new PlatformConfiguration.Integrations.Outbox(integrations.getBoolean("integrations.central-api.outbox.enabled", true), Duration.ofSeconds(10), integrations.getInt("integrations.central-api.retries.max-attempts", 8), Duration.ofSeconds(1));
        return new PlatformConfiguration(core, worldFile, guildConfig, pointsConfig, hardConfig, eventConfig, guiConfig, messageConfig, new PlatformConfiguration.Integrations(Map.copyOf(switches), outbox));
    }

    private void copyDefaults() {
        for (String file : REQUIRED_FILES) {
            File target = new File(plugin.getDataFolder(), file);
            if (!target.exists()) plugin.saveResource(file, false);
        }
    }
    private YamlConfiguration yaml(String file) {
        Path path = plugin.getDataFolder().toPath().resolve(file);
        if (!Files.isRegularFile(path)) throw new IllegalStateException("Required configuration missing: " + file);
        return YamlConfiguration.loadConfiguration(path.toFile());
    }
    private static String environmentOr(YamlConfiguration yaml, String path, String environment) {
        String runtime = System.getenv(environment);
        if (runtime != null && !runtime.isBlank()) return runtime.trim();
        String configured = yaml.getString(path);
        if (configured == null || configured.isBlank() || configured.startsWith("${")) {
            return switch (environment) {
                case "SMPPLATFORM_DB_JDBC_URL" -> "jdbc:postgresql://127.0.0.1:5432/smpplatform";
                case "SMPPLATFORM_DB_USERNAME" -> "smpplatform";
                case "SMPPLATFORM_API_BASE_URL" -> "https://api.invalid";
                default -> throw new IllegalArgumentException("Missing configuration value: " + path);
            };
        }
        return configured.trim();
    }

    private static ConfigurationSection requireSection(YamlConfiguration yaml, String path) {
        ConfigurationSection section = yaml.getConfigurationSection(path);
        if (section == null) throw new IllegalArgumentException("Missing configuration section: " + path);
        return section;
    }
    private static String required(YamlConfiguration yaml, String path) {
        String value = yaml.getString(path);
        if (value == null || value.isBlank()) throw new IllegalArgumentException("Missing configuration value: " + path);
        return value.trim();
    }
    private static int positive(YamlConfiguration yaml, String path) { int value = yaml.getInt(path, -1); if (value <= 0) throw new IllegalArgumentException(path + " must be positive"); return value; }
    private static long nonNegative(YamlConfiguration yaml, String path) { long value = yaml.getLong(path, -1); if (value < 0) throw new IllegalArgumentException(path + " must not be negative"); return value; }
    private static Duration millis(YamlConfiguration yaml, String path) { return Duration.ofMillis(positive(yaml, path)); }
    private static Duration seconds(YamlConfiguration yaml, String path) { return Duration.ofSeconds(positive(yaml, path)); }
    private static List<String> requiredStringList(YamlConfiguration yaml, String path) {
        List<String> values = new ArrayList<>(yaml.getStringList(path));
        if (values.isEmpty() || values.stream().anyMatch(value -> value == null || value.isBlank())) throw new IllegalArgumentException(path + " must contain nonblank values");
        return List.copyOf(values);
    }
    private static Map<String, String> stringMap(ConfigurationSection section) {
        Map<String, String> values = new LinkedHashMap<>();
        for (String key : section.getKeys(false)) values.put(key, Objects.requireNonNull(section.getString(key), "Value for " + key));
        return Map.copyOf(values);
    }
}
