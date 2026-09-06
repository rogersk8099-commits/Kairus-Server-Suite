package com.kairu.bridge.integration;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.ServicesManager;

import java.lang.reflect.Method;
import java.util.*;
import java.util.logging.Logger;

/** Optional integrations are accessed only after presence checks; no optional API is linked at class load. */
public final class SoftIntegrations {
    private final Logger logger;
    private final boolean vault, luckPerms, placeholderApi, geyser, floodgate;
    private volatile Object vaultEconomy;

    public SoftIntegrations(Logger logger) {
        this.logger = logger;
        this.vault = exists("Vault"); this.luckPerms = exists("LuckPerms"); this.placeholderApi = exists("PlaceholderAPI");
        this.geyser = exists("Geyser-Spigot") || exists("Geyser"); this.floodgate = exists("floodgate") || exists("Floodgate");
        if (vault) this.vaultEconomy = findVaultEconomy();
        logger.info("Soft integrations: Vault=" + (vaultEconomy != null) + ", LuckPerms=" + luckPerms + ", PlaceholderAPI=" + placeholderApi + ", Geyser=" + geyser + ", Floodgate=" + floodgate);
    }

    public String summary() { return "Vault=" + (vaultEconomy != null) + ", LuckPerms=" + luckPerms + ", PlaceholderAPI=" + placeholderApi + ", Geyser=" + geyser + ", Floodgate=" + floodgate; }

    public Double balance(Player player) {
        Object economy = vaultEconomy; if (economy == null) return null;
        try { return ((Number) economy.getClass().getMethod("getBalance", OfflinePlayer.class).invoke(economy, player)).doubleValue(); }
        catch (ReflectiveOperationException | ClassCastException exception) { logOnce("Vault balance unavailable", exception); return null; }
    }

    public String rank(Player player) {
        if (!luckPerms) return null;
        try {
            Class<?> provider = Class.forName("net.luckperms.api.LuckPermsProvider"); Object lp = provider.getMethod("get").invoke(null);
            Object user = lp.getClass().getMethod("getUserManager").invoke(lp).getClass().getMethod("getUser", UUID.class).invoke(lp.getClass().getMethod("getUserManager").invoke(lp), player.getUniqueId());
            if (user == null) return null;
            Object cachedData = user.getClass().getMethod("getCachedData").invoke(user);
            Object metaData = cachedData.getClass().getMethod("getMetaData").invoke(cachedData);
            Object primaryGroup = metaData.getClass().getMethod("getPrimaryGroup").invoke(metaData);
            return primaryGroup instanceof String group && !group.isBlank() ? group : null;
        } catch (ReflectiveOperationException exception) { logOnce("LuckPerms rank unavailable", exception); return null; }
    }

    public String placeholders(Player player, String text) {
        if (!placeholderApi) return text;
        try { return (String) Class.forName("me.clip.placeholderapi.PlaceholderAPI").getMethod("setPlaceholders", Player.class, String.class).invoke(null, player, text); }
        catch (ReflectiveOperationException exception) { logOnce("PlaceholderAPI unavailable", exception); return text; }
    }

    public String bedrockXuid(Player player) {
        if (!floodgate) return null;
        try {
            Class<?> apiClass = Class.forName("org.geysermc.floodgate.api.FloodgateApi"); Object api = apiClass.getMethod("getInstance").invoke(null);
            boolean bedrock = (boolean) apiClass.getMethod("isFloodgatePlayer", UUID.class).invoke(api, player.getUniqueId());
            if (!bedrock) return null;
            Object floodgatePlayer = apiClass.getMethod("getPlayer", UUID.class).invoke(api, player.getUniqueId());
            if (floodgatePlayer == null) return null;
            Object xuid = floodgatePlayer.getClass().getMethod("getXuid").invoke(floodgatePlayer);
            return xuid == null ? null : String.valueOf(xuid);
        } catch (ReflectiveOperationException exception) { logOnce("Floodgate XUID unavailable", exception); return null; }
    }

    private Object findVaultEconomy() {
        try {
            Class<?> economy = Class.forName("net.milkbowl.vault.economy.Economy"); ServicesManager manager = Bukkit.getServicesManager();
            RegisteredServiceProvider<?> registration = manager.getRegistration(economy); return registration == null ? null : registration.getProvider();
        } catch (ClassNotFoundException exception) { return null; }
    }
    private boolean exists(String name) { Plugin plugin = Bukkit.getPluginManager().getPlugin(name); return plugin != null && plugin.isEnabled(); }
    private void logOnce(String label, Exception exception) { logger.fine(label + ": " + exception.getClass().getSimpleName()); }
}
