package com.kairu.bridge;

import com.kairu.bridge.api.ControlPlaneClient;
import com.kairu.bridge.chat.BridgeEventType;
import com.kairu.bridge.chat.ChatBridgeListener;
import com.kairu.bridge.chat.ChatBridgeService;
import com.kairu.bridge.command.KairuCommand;
import com.kairu.bridge.config.BridgeConfig;
import com.kairu.bridge.config.ChatBridgeConfig;
import com.kairu.bridge.integration.SoftIntegrations;
import com.kairu.bridge.payload.Payloads;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.HandlerList;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletionException;
import java.util.concurrent.atomic.AtomicReference;

/** KairuBridge entry point. Bukkit access is synchronous; all HTTP is dispatched through HttpClient.sendAsync. */
public final class KairuBridgePlugin extends JavaPlugin {
    private final List<BukkitTask> scheduledTasks = new ArrayList<>();
    private final AtomicReference<String> lastHeartbeat = new AtomicReference<>("not sent yet");
    private final AtomicReference<String> lastCommandPoll = new AtomicReference<>("not polled yet");
    private volatile boolean stopping;
    private BridgeConfig bridgeConfig;
    private ControlPlaneClient client;
    private SoftIntegrations integrations;
    private TelemetryService telemetry;
    private ChatBridgeConfig chatBridgeConfig;
    private ChatBridgeService chatBridge;
    private ChatBridgeListener chatBridgeListener;

    @Override public void onEnable() {
        saveDefaultConfig();
        if (!installConfiguration()) {
            getLogger().severe("KairuBridge did not start. Correct config.yml and use /kairu reload.");
        }
        KairuCommand handler = new KairuCommand(this);
        if (getCommand("kairu") != null) { getCommand("kairu").setExecutor(handler); getCommand("kairu").setTabCompleter(handler); }
        getLogger().info("KairuBridge enabled. " + (isConfigured() ? "Control plane scheduling active." : "Awaiting secure configuration."));
    }

    @Override public void onDisable() {
        if (chatBridge != null) {
            chatBridge.publishLifecycle(BridgeEventType.SERVER_STOPPING, "Minecraft server bridge stopping", java.util.Map.of());
            chatBridge.stop();
        }
        if (chatBridgeListener != null) HandlerList.unregisterAll(chatBridgeListener);
        stopping = true;
        cancelTasks();
        ControlPlaneClient closing = client;
        client = null;
        if (closing != null) closing.close();
        getLogger().info("KairuBridge shut down safely.");
    }

    /** Reload atomically validates a new config before stopping the active bridge. */
    public boolean reloadBridge() {
        try {
            reloadConfig();
            BridgeConfig candidate = BridgeConfig.from(getConfig());
            ChatBridgeConfig candidateChatConfig = ChatBridgeConfig.from(getConfig());
            ControlPlaneClient candidateClient = new ControlPlaneClient(candidate, getLogger());
            SoftIntegrations candidateIntegrations = new SoftIntegrations(getLogger());
            TelemetryService candidateTelemetry = new TelemetryService(candidate.serverId(), getDescription().getVersion(), candidateIntegrations);
            ChatBridgeService candidateChatBridge = new ChatBridgeService(this, candidateClient, candidateChatConfig);
            ChatBridgeListener candidateChatListener = new ChatBridgeListener(this, candidateChatBridge);
            cancelTasks();
            if (chatBridge != null) chatBridge.stop();
            if (chatBridgeListener != null) HandlerList.unregisterAll(chatBridgeListener);
            ControlPlaneClient old = client;
            bridgeConfig = candidate;
            chatBridgeConfig = candidateChatConfig;
            client = candidateClient;
            integrations = candidateIntegrations;
            telemetry = candidateTelemetry;
            chatBridge = candidateChatBridge;
            chatBridgeListener = candidateChatListener;
            if (old != null) old.close();
            if (candidate.isConfigured()) {
                scheduleTasks();
                getServer().getPluginManager().registerEvents(chatBridgeListener, this);
                chatBridge.start();
                chatBridge.publishLifecycle(BridgeEventType.SERVER_STARTED, "Minecraft server bridge configuration reloaded", java.util.Map.of("reloaded", "true"));
            } else getLogger().warning("KairuBridge configuration loaded but still contains placeholders; network traffic remains disabled.");
            return true;
        } catch (RuntimeException exception) {
            getLogger().severe("KairuBridge reload rejected: " + exception.getMessage());
            return false;
        }
    }

    private boolean installConfiguration() {
        try {
            bridgeConfig = BridgeConfig.from(getConfig());
            chatBridgeConfig = ChatBridgeConfig.from(getConfig());
            integrations = new SoftIntegrations(getLogger());
            telemetry = new TelemetryService(bridgeConfig.serverId(), getDescription().getVersion(), integrations);
            client = new ControlPlaneClient(bridgeConfig, getLogger());
            chatBridge = new ChatBridgeService(this, client, chatBridgeConfig);
            chatBridgeListener = new ChatBridgeListener(this, chatBridge);
            if (bridgeConfig.isConfigured()) {
                scheduleTasks();
                getServer().getPluginManager().registerEvents(chatBridgeListener, this);
                chatBridge.start();
                chatBridge.publishLifecycle(BridgeEventType.SERVER_STARTED, "Minecraft server bridge started", java.util.Map.of("version", getDescription().getVersion()));
            } else getLogger().warning("KairuBridge config uses placeholders; no API requests will be sent until server-id, api-base-url, and api-key are configured.");
            return true;
        } catch (RuntimeException exception) {
            getLogger().severe("Invalid KairuBridge configuration: " + exception.getMessage());
            return false;
        }
    }

    private void scheduleTasks() {
        if (bridgeConfig.heartbeatEnabled()) scheduledTasks.add(getServer().getScheduler().runTaskTimer(this, this::sendHeartbeat, 20L, bridgeConfig.heartbeatIntervalTicks()));
        if (bridgeConfig.snapshotsEnabled()) scheduledTasks.add(getServer().getScheduler().runTaskTimer(this, this::sendOnlineSnapshots, 100L, bridgeConfig.snapshotIntervalTicks()));
        if (bridgeConfig.commandsEnabled()) scheduledTasks.add(getServer().getScheduler().runTaskTimerAsynchronously(this, this::pollCommands, 60L, bridgeConfig.commandPollIntervalTicks()));
    }

    private void cancelTasks() { scheduledTasks.forEach(BukkitTask::cancel); scheduledTasks.clear(); }

    private void sendHeartbeat() {
        if (stopping || client == null || telemetry == null) return;
        String json = telemetry.heartbeat().toJson(); // Bukkit reads remain on the primary thread.
        client.post("/api/plugin/heartbeat", json).whenComplete((response, error) -> {
            if (error == null && response.success()) lastHeartbeat.set("success at " + Instant.now());
            else { lastHeartbeat.set("failed at " + Instant.now()); logFailure("heartbeat", response, error); }
        });
    }

    private void sendOnlineSnapshots() {
        if (stopping || client == null || telemetry == null) return;
        for (Player player : getServer().getOnlinePlayers()) sendSnapshot(player, null);
    }

    public void syncPlayer(Player player, CommandSender sender) {
        if (!isConfigured()) { sender.sendMessage(ChatColor.RED + "Kairu: bridge configuration is incomplete."); return; }
        sendSnapshot(player, sender);
        sender.sendMessage(ChatColor.GRAY + "Kairu: snapshot queued for " + player.getName() + ".");
    }

    public void notifyMaintenance(String message, boolean restarting) {
        ChatBridgeService active = chatBridge;
        if (stopping || active == null || !isConfigured()) return;
        Runnable notification = () -> active.notifyMaintenance(message, restarting);
        if (org.bukkit.Bukkit.isPrimaryThread()) notification.run();
        else getServer().getScheduler().runTask(this, notification);
    }

    private void sendSnapshot(Player player, CommandSender sender) {
        if (stopping || client == null || telemetry == null) return;
        String json = telemetry.snapshot(player).toJson();
        client.post("/api/plugin/player-snapshot", json).whenComplete((response, error) -> {
            boolean success = error == null && response.success();
            if (!success) logFailure("player snapshot", response, error);
            if (sender != null && !stopping) runOnPrimary(() -> sender.sendMessage(success ? ChatColor.GREEN + "Kairu: player snapshot synchronized." : ChatColor.RED + "Kairu: snapshot failed; check console."));
        });
    }

    public void completeLink(Payloads.LinkCompletion request, Player player) {
        if (!isConfigured()) { player.sendMessage(ChatColor.RED + "Kairu: bridge configuration is incomplete."); return; }
        ControlPlaneClient active = client;
        active.post("/api/link-codes/complete", request.toJson()).whenComplete((response, error) -> {
            boolean success = error == null && response.success();
            if (!success) logFailure("link completion", response, error);
            if (stopping) return;
            runOnPrimary(() -> {
                if (!player.isOnline()) return;
                if (success) {
                    player.sendMessage(ChatColor.GREEN + "Kairu: your Discord account is now linked.");
                    if (bridgeConfig != null && bridgeConfig.snapshotAfterLink()) sendSnapshot(player, null);
                } else if (response != null && (response.statusCode() == 400 || response.statusCode() == 404 || response.statusCode() == 409 || response.statusCode() == 410)) {
                    player.sendMessage(ChatColor.RED + "Kairu: that link code is invalid, expired, or already used.");
                } else player.sendMessage(ChatColor.RED + "Kairu: link service unavailable; please try again shortly.");
            });
        });
    }

    private void pollCommands() {
        if (stopping || client == null) return;
        ControlPlaneClient active = client;
        active.pollCommands().whenComplete((commands, error) -> {
            if (error != null) { lastCommandPoll.set("failed at " + Instant.now()); getLogger().fine("Queued command poll failed: " + rootMessage(error)); return; }
            lastCommandPoll.set("success at " + Instant.now() + " (" + commands.size() + " command(s))");
            if (!commands.isEmpty() && !stopping) runOnPrimary(() -> {
                if (stopping || active != client) return;
                RemoteCommandProcessor processor = new RemoteCommandProcessor(this, active, integrations);
                commands.forEach(processor::execute);
            });
        });
    }

    private void runOnPrimary(Runnable task) { getServer().getScheduler().runTask(this, task); }
    private void logFailure(String operation, ControlPlaneClient.ApiResponse response, Throwable error) {
        String status = response == null ? "no HTTP response" : "HTTP " + response.statusCode();
        String cause = error == null ? status : rootMessage(error);
        getLogger().warning("KairuBridge " + operation + " failed (" + cause + "). API keys and response bodies are not logged.");
    }
    private static String rootMessage(Throwable error) { Throwable current = error; while (current instanceof CompletionException && current.getCause() != null) current = current.getCause(); return current.getClass().getSimpleName(); }

    public boolean isConfigured() { return bridgeConfig != null && bridgeConfig.isConfigured() && client != null && !stopping; }
    public String heartbeatStatus() { return lastHeartbeat.get(); }
    public String commandPollStatus() { return lastCommandPoll.get(); }
    public String integrationSummary() { return integrations == null ? "not initialized" : integrations.summary(); }
    public String bedrockXuid(Player player) { return integrations == null ? null : integrations.bedrockXuid(player); }
}
