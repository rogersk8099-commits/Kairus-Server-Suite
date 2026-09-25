package com.neonnexus.smpplatform.luckperms;

import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;
import java.util.Map;

/**
 * Idempotent bootstrap for the Kairu SMP LuckPerms hierarchy.
 * Uses the LuckPerms console command API so LuckPerms remains an optional dependency.
 */
public final class LuckPermsProvisioner {
    private static final Map<String, Integer> WEIGHTS = Map.of(
            "default", 10, "member", 20, "trusted", 30,
            "moderator", 40, "admin", 50, "owner", 60);

    private static final Map<String, List<String>> PERMISSIONS = Map.of(
            "default", List.of(
                    "smpplatform.play", "kairubridge.receive",
                    "minecraft.command.help", "minecraft.command.msg"),
            "member", List.of(
                    "smpplatform.points.view", "smpplatform.auction.browse",
                    "smpplatform.search.use", "essentials.home", "essentials.tpa"),
            "trusted", List.of(
                    "smpplatform.auction.create", "smpplatform.auction.bid",
                    "essentials.warp"),
            "moderator", List.of(
                    "smpplatform.admin.audit", "smpplatform.auction.remove",
                    "smpplatform.search.see-hidden", "essentials.kick",
                    "essentials.mute"),
            "admin", List.of("smpplatform.*", "kairubridge.admin", "luckperms.*"),
            "owner", List.of());

    private final JavaPlugin plugin;

    public LuckPermsProvisioner(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    public void provision() {
        if (Bukkit.getPluginManager().getPlugin("LuckPerms") == null) {
            plugin.getLogger().warning("LuckPerms is not installed; Kairu role provisioning was skipped.");
            return;
        }

        for (String group : WEIGHTS.keySet()) {
            dispatch("lp creategroup " + group);
            dispatch("lp group " + group + " setweight " + WEIGHTS.get(group));
        }

        dispatch("lp group member parent add default");
        dispatch("lp group trusted parent add member");
        dispatch("lp group moderator parent add trusted");
        dispatch("lp group admin parent add moderator");
        dispatch("lp group owner parent add admin");

        for (Map.Entry<String, List<String>> entry : PERMISSIONS.entrySet()) {
            for (String permission : entry.getValue()) {
                dispatch("lp group " + entry.getKey() + " permission set " + permission + " true");
            }
        }

        plugin.getLogger().info("LuckPerms Kairu role hierarchy verified: default < member < trusted < moderator < admin < owner.");
    }

    private void dispatch(String command) {
        if (!Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command)) {
            plugin.getLogger().warning("LuckPerms command was rejected: " + command);
        }
    }
}
