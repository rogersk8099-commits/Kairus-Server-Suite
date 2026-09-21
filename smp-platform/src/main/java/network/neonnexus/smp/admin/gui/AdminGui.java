package network.neonnexus.smp.admin.gui;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import network.neonnexus.smp.admin.domain.AdminAction;
import network.neonnexus.smp.admin.domain.AdminActionDispatcher;
import network.neonnexus.smp.admin.domain.AdminActionRequest;
import network.neonnexus.smp.admin.domain.InMemoryAuditSink;
import network.neonnexus.smp.admin.domain.PermissionRouter;
import network.neonnexus.smp.admin.monitor.HealthAdapters;
import network.neonnexus.smp.admin.player.PlayerDirectory;
import network.neonnexus.smp.admin.world.NeonWorld;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.plugin.Plugin;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Inventory renderer for all staff-facing flows. Controls are one-click actions
 * with chat prompts for text because that interaction is reliable on Geyser.
 */
public final class AdminGui implements Listener {
    private static final MiniMessage MM = MiniMessage.miniMessage();
    private final Plugin plugin;
    private final PlayerDirectory directory;
    private final PermissionRouter permissions;
    private final AdminActionDispatcher dispatcher;
    private final InMemoryAuditSink audit;
    private final Clock clock;
    private final Map<UUID, Deque<ScreenState>> history = new ConcurrentHashMap<>();
    private final Map<UUID, PendingInput> prompts = new ConcurrentHashMap<>();
    private final Map<UUID, Inventory> readOnlyViews = new ConcurrentHashMap<>();

    public AdminGui(Plugin plugin, PlayerDirectory directory, PermissionRouter permissions, AdminActionDispatcher dispatcher,
                    InMemoryAuditSink audit, Clock clock) {
        this.plugin = plugin; this.directory = directory; this.permissions = permissions; this.dispatcher = dispatcher; this.audit = audit; this.clock = clock;
    }

    public void openHome(Player player) { renderHome(player, false); }
    public void openPlayers(Player player, PlayerDirectory.PlayerFilter filter, String query, int page) { renderPlayers(player, filter, query, page, false); }
    public void openWorlds(Player player) { renderWorlds(player, false); }
    public void openMonitor(Player player) { renderMonitor(player, false); }

    private boolean allowed(Player player, String permission) {
        if (permissions.canOpen(player::hasPermission, permission)) return true;
        player.sendMessage(MM.deserialize("<red>You lack <white>" + permission + "</white>.")); return false;
    }
    private void renderHome(Player player, boolean push) {
        if (!allowed(player, PermissionRouter.ROOT)) return;
        AdminScreen screen = screen(player, "home", 54, "Admin Control", push, ScreenState.home());
        border(screen);
        button(screen, 10, NeonItems.item(Material.PLAYER_HEAD, NeonItems.MAGENTA + "<bold>Player Administration", NeonItems.MUTED + "Selector · Inspector · Moderation", NeonItems.BLUE + "Click to browse players"), event -> renderPlayers(player, PlayerDirectory.PlayerFilter.ONLINE, "", 0, true));
        button(screen, 12, NeonItems.item(Material.RECOVERY_COMPASS, NeonItems.PURPLE + "<bold>World Control", NeonItems.MUTED + "Six registered Neon Nexus worlds", NeonItems.BLUE + "Runtime cards and controls"), event -> renderWorlds(player, true));
        button(screen, 14, NeonItems.item(Material.COMPARATOR, NeonItems.BLUE + "<bold>Server Monitor", NeonItems.MUTED + "TPS · MSPT · memory · integrations", NeonItems.BLUE + "Live health snapshot"), event -> renderMonitor(player, true));
        button(screen, 16, NeonItems.item(Material.WRITABLE_BOOK, NeonItems.MAGENTA + "<bold>Audit History", NeonItems.MUTED + "Attributable staff action records", NeonItems.BLUE + "Recent in-process audit stream"), event -> renderAudit(player, true));
        locked(screen, 28, Material.SHIELD, "Moderation", "Use Player Inspector");
        locked(screen, 30, Material.DRAGON_HEAD, "Hardcore", "Phase module integration");
        locked(screen, 32, Material.NETHERITE_INGOT, "Points", "Phase module integration");
        locked(screen, 34, Material.TOTEM_OF_UNDYING, "Guilds & Events", "Phase module integration");
        button(screen, 49, NeonItems.close(), event -> player.closeInventory());
        open(player, screen);
    }

    private void renderPlayers(Player player, PlayerDirectory.PlayerFilter filter, String query, int requestedPage, boolean push) {
        if (!allowed(player, "smpplatform.admin.players")) return;
        PlayerDirectory.Page page = directory.search(filter, query, requestedPage, 28);
        ScreenState state = ScreenState.players(filter, query, page.page());
        AdminScreen screen = screen(player, "players", 54, "Player Selector", push, state); border(screen);
        int[] tabs = {0, 1, 2, 3, 4, 5}; PlayerDirectory.PlayerFilter[] filters = PlayerDirectory.PlayerFilter.values();
        for (int i = 0; i < filters.length; i++) {
            PlayerDirectory.PlayerFilter tab = filters[i];
            String colour = tab == filter ? NeonItems.MAGENTA : NeonItems.MUTED;
            button(screen, tabs[i], NeonItems.item(tab == filter ? Material.PURPLE_STAINED_GLASS_PANE : Material.GRAY_STAINED_GLASS_PANE,
                    colour + "<bold>" + tab.name(), NeonItems.MUTED + "Filter selector"), event -> renderPlayers(player, tab, query, 0, true));
        }
        button(screen, 6, NeonItems.item(Material.NAME_TAG, NeonItems.BLUE + "<bold>Search", NeonItems.MUTED + (query.isBlank() ? "All cached player records" : "Query: <white>" + safe(query)), NeonItems.BLUE + "Click, then send a chat message"), event -> beginSearch(player));
        button(screen, 7, NeonItems.item(Material.CLOCK, NeonItems.PURPLE + "<bold>Cached profiles", NeonItems.MUTED + "No blocking offline-player sweep"), event -> {});
        int slot = 9;
        for (PlayerDirectory.PlayerProfile profile : page.entries()) {
            if (slot >= 45) break;
            button(screen, slot++, playerHead(profile), event -> renderInspector(player, profile.uuid(), true));
        }
        if (page.totalEntries() == 0) screen.getInventory().setItem(22, NeonItems.item(Material.BARRIER, NeonItems.WARN + "<bold>No matching cached players", NeonItems.MUTED + "Players populate on join and direct lookup."));
        if (page.page() > 0) button(screen, 45, NeonItems.item(Material.ARROW, NeonItems.PURPLE + "<bold>Previous", NeonItems.MUTED + "Page " + page.page()), event -> renderPlayers(player, filter, query, page.page() - 1, true));
        screen.getInventory().setItem(49, NeonItems.item(Material.PAPER, NeonItems.BLUE + "<bold>Page " + (page.page() + 1) + "/" + page.totalPages(), NeonItems.MUTED + page.totalEntries() + " matched player(s)"));
        if (page.page() + 1 < page.totalPages()) button(screen, 53, NeonItems.item(Material.ARROW, NeonItems.PURPLE + "<bold>Next", NeonItems.MUTED + "Page " + (page.page() + 2)), event -> renderPlayers(player, filter, query, page.page() + 1, true));
        navigation(screen, player);
        open(player, screen);
    }

    private ItemStack playerHead(PlayerDirectory.PlayerProfile profile) {
        ItemStack stack = NeonItems.item(Material.PLAYER_HEAD, (profile.online() ? NeonItems.SUCCESS : NeonItems.MUTED) + "<bold>" + safe(profile.username()),
                NeonItems.BLUE + "Platform: <white>" + profile.edition(), NeonItems.MUTED + "World: <white>" + safe(profile.world()),
                NeonItems.MAGENTA + "Guild: <white>" + safe(profile.guild()), NeonItems.PURPLE + "Rank: <white>" + safe(profile.rank()),
                NeonItems.MUTED + "Playtime: <white>" + profile.playtimeHours() + "h", profile.online() ? NeonItems.SUCCESS + "● ONLINE" : NeonItems.MUTED + "● OFFLINE", NeonItems.BLUE + "Click to inspect");
        SkullMeta meta = (SkullMeta) stack.getItemMeta(); OfflinePlayer offline = Bukkit.getOfflinePlayer(profile.uuid()); meta.setOwningPlayer(offline); stack.setItemMeta(meta); return stack;
    }

    private void renderInspector(Player player, UUID targetId, boolean push) {
        if (!allowed(player, "smpplatform.admin.players")) return;
        PlayerDirectory.PlayerProfile target = directory.get(targetId).orElse(null);
        if (target == null) { player.sendMessage(MM.deserialize("<red>That player profile is no longer cached.")); renderPlayers(player, PlayerDirectory.PlayerFilter.ONLINE, "", 0, false); return; }
        AdminScreen screen = screen(player, "inspector", 54, "Player Inspector", push, ScreenState.inspector(targetId)); border(screen);
        screen.getInventory().setItem(4, playerHead(target));
        summary(screen, 10, Material.COMPASS, "PROFILE", "UUID: " + target.uuid(), "Edition: " + target.edition(), "Rank: " + target.rank());
        summary(screen, 11, Material.MAP, "WORLD DATA", "Current: " + target.world(), "Guild: " + target.guild());
        summary(screen, 12, Material.TOTEM_OF_UNDYING, "HARDCORE STATUS", "Provided by Hardcore module", "No phase-2 mutation here");
        summary(screen, 13, Material.GOLD_INGOT, "POINTS", "Provided by Points module", "No phase-2 mutation here");
        summary(screen, 14, Material.BEEHIVE, "GUILD", "Platform identity", "Provided by Guild module");
        summary(screen, 15, Material.NETHER_STAR, "EVENT HISTORY", "Provided by Events module", "No phase-2 mutation here");
        summary(screen, 16, Material.AMETHYST_SHARD, "MEMBERSHIP", "Central-platform source", "Read-only in phase 2");
        summary(screen, 19, Material.NAME_TAG, "ACCOUNT LINKS", "UUID-based identity", "Edition: " + target.edition());
        summary(screen, 21, Material.IRON_BARS, "MODERATION HISTORY", "Recent warnings, mutes, and bans", "See audit history for phase-2 records");
        button(screen, 20, NeonItems.item(Material.WRITABLE_BOOK, NeonItems.MAGENTA + "<bold>AUDIT HISTORY", NeonItems.MUTED + "Recent actions for this player", NeonItems.BLUE + "Click to open"), event -> renderTargetAudit(player, target.uuid(), true));
        button(screen, 22, NeonItems.item(Material.ENDER_CHEST, NeonItems.PURPLE + "<bold>INVENTORY", NeonItems.MUTED + "View / edit / clear", NeonItems.BLUE + "Click to open actions"), event -> renderActionSection(player, targetId, "Inventory", true));
        button(screen, 23, NeonItems.item(Material.ENDER_EYE, NeonItems.PURPLE + "<bold>ENDER CHEST", NeonItems.MUTED + "View ender chest", NeonItems.BLUE + "Click to open actions"), event -> requestAction(player, target, AdminAction.VIEW_ENDER_CHEST));
        button(screen, 24, NeonItems.item(Material.GLISTERING_MELON_SLICE, NeonItems.MAGENTA + "<bold>HEALTH / FOOD", NeonItems.MUTED + "Heal · feed · health · hunger", NeonItems.BLUE + "Click to open actions"), event -> renderActionSection(player, targetId, "Player state", true));
        button(screen, 25, NeonItems.item(Material.EXPERIENCE_BOTTLE, NeonItems.BLUE + "<bold>XP & EFFECTS", NeonItems.MUTED + "Set XP · clear / apply effects", NeonItems.BLUE + "Click to open actions"), event -> renderMixedSection(player, targetId, true));
        button(screen, 28, NeonItems.item(Material.ENDER_PEARL, NeonItems.MAGENTA + "<bold>TELEPORTATION", NeonItems.MUTED + "To · bring · world · spawn", NeonItems.BLUE + "Click to open actions"), event -> renderActionSection(player, targetId, "Teleportation", true));
        button(screen, 29, NeonItems.item(Material.DIAMOND_SWORD, NeonItems.PURPLE + "<bold>GAMEMODE", NeonItems.MUTED + "Set a gamemode", NeonItems.BLUE + "Click to open action"), event -> requestAction(player, target, AdminAction.SET_GAMEMODE));
        button(screen, 31, NeonItems.item(Material.PACKED_ICE, NeonItems.BLUE + "<bold>FREEZE", NeonItems.MUTED + "Freeze / unfreeze", NeonItems.BLUE + "Click to open actions"), event -> renderActionSection(player, targetId, "Moderation", true));
        button(screen, 32, NeonItems.item(Material.IRON_BARS, NeonItems.ERROR + "<bold>MODERATION", NeonItems.MUTED + "Warn · kick · mute · ban", NeonItems.ERROR + "Click to open actions"), event -> renderActionSection(player, targetId, "Moderation", true));
        navigation(screen, player); open(player, screen);
    }

    private void renderActionSection(Player player, UUID targetId, String section, boolean push) {
        PlayerDirectory.PlayerProfile target = directory.get(targetId).orElse(null); if (target == null) return;
        AdminScreen screen = screen(player, "actions-" + section, 54, section + " Actions", push, ScreenState.inspector(targetId)); border(screen);
        screen.getInventory().setItem(4, playerHead(target)); int slot = 10;
        for (AdminAction action : AdminAction.values()) if (action.section().equals(section)) {
            button(screen, slot++, actionItem(action), event -> requestAction(player, target, action));
        }
        navigation(screen, player); open(player, screen);
    }
    private void renderMixedSection(Player player, UUID targetId, boolean push) {
        PlayerDirectory.PlayerProfile target = directory.get(targetId).orElse(null); if (target == null) return;
        AdminScreen screen = screen(player, "effects", 54, "XP & Effects", push, ScreenState.inspector(targetId)); border(screen); screen.getInventory().setItem(4, playerHead(target));
        int slot = 20; for (AdminAction action : List.of(AdminAction.SET_XP, AdminAction.CLEAR_EFFECTS, AdminAction.APPLY_EFFECT)) button(screen, slot++, actionItem(action), event -> requestAction(player, target, action));
        navigation(screen, player); open(player, screen);
    }
    private ItemStack actionItem(AdminAction action) {
        Material icon = switch (action) {
            case TELEPORT_TO, BRING, SEND_WORLD, SEND_SPAWN -> Material.ENDER_PEARL;
            case HEAL, SET_HEALTH -> Material.GLISTERING_MELON_SLICE;
            case FEED, SET_HUNGER -> Material.COOKED_BEEF;
            case SET_XP -> Material.EXPERIENCE_BOTTLE;
            case APPLY_EFFECT, CLEAR_EFFECTS -> Material.POTION;
            case VIEW_INVENTORY, EDIT_INVENTORY, CLEAR_INVENTORY -> Material.CHEST;
            case VIEW_ENDER_CHEST -> Material.ENDER_CHEST;
            case FREEZE, UNFREEZE -> Material.PACKED_ICE;
            case KICK, WARN, MUTE, TEMP_MUTE, BAN, TEMP_BAN -> Material.IRON_BARS;
            default -> Material.COMMAND_BLOCK;
        };
        String danger = action.destructive() ? NeonItems.ERROR + "<bold>CONFIRM REQUIRED" : NeonItems.SUCCESS + "<bold>Audited action";
        return NeonItems.item(icon, (action.destructive() ? NeonItems.ERROR : NeonItems.MAGENTA) + "<bold>" + action.displayName(), NeonItems.MUTED + "Permission: <white>" + action.permission(), action.needsValue() ? NeonItems.WARN + "Value requested in chat" : NeonItems.MUTED + "No value required", action.needsReason() ? NeonItems.WARN + "Reason requested in chat" : NeonItems.MUTED + "Reason optional", danger);
    }

    private void requestAction(Player player, PlayerDirectory.PlayerProfile target, AdminAction action) {
        if (!permissions.canPerform(player::hasPermission, action)) { player.sendMessage(MM.deserialize("<red>You lack <white>" + action.permission() + "</white>.")); return; }
        if (action.needsValue() || action.needsReason()) { beginInput(player, target, action); return; }
        dispatch(player, target, action, "", "");
    }
    private void beginInput(Player player, PlayerDirectory.PlayerProfile target, AdminAction action) {
        prompts.put(player.getUniqueId(), new PendingInput(target.uuid(), action)); player.closeInventory();
        String instruction = action.needsValue() && action.needsReason() ? "<value> | <reason>" : action.needsValue() ? "a value" : "a reason";
        player.sendMessage(MM.deserialize(NeonItems.BLUE + "<bold>" + action.displayName() + "</bold> for <white>" + safe(target.username()) + "</white>: enter " + instruction + " in chat, or type <red>cancel</red>."));
        if (action == AdminAction.APPLY_EFFECT) player.sendMessage(MM.deserialize(NeonItems.MUTED + "Format: <white>EFFECT:seconds[:amplifier]</white>, e.g. SPEED:60:1"));
        if (action == AdminAction.TEMP_MUTE || action == AdminAction.TEMP_BAN) player.sendMessage(MM.deserialize(NeonItems.MUTED + "Duration formats: <white>15m, 2h, 7d, 1w</white>."));
    }
    private void beginSearch(Player player) { prompts.put(player.getUniqueId(), PendingInput.forSearch()); player.closeInventory(); player.sendMessage(MM.deserialize(NeonItems.BLUE + "Enter player, world, or guild text in chat; type <red>cancel</red> to abort.")); }
    private void dispatch(Player player, PlayerDirectory.PlayerProfile target, AdminAction action, String value, String reason) {
        AdminActionRequest request = new AdminActionRequest(player.getUniqueId(), player.getName(), target.uuid(), target.username(), action, value, reason, clock.instant(), UUID.randomUUID());
        AdminActionDispatcher.DispatchOutcome outcome = dispatcher.request(player::hasPermission, request); showOutcome(player, outcome, target.uuid());
    }
    private void showOutcome(Player player, AdminActionDispatcher.DispatchOutcome outcome, UUID targetId) {
        switch (outcome.status()) {
            case EXECUTED -> { player.sendMessage(MM.deserialize(NeonItems.SUCCESS + outcome.message())); renderInspector(player, targetId, false); }
            case CONFIRMATION_REQUIRED -> renderConfirmation(player, outcome.confirmationId(), targetId, outcome.message());
            default -> player.sendMessage(MM.deserialize(NeonItems.ERROR + outcome.message()));
        }
    }
    private void renderConfirmation(Player player, UUID confirmationId, UUID targetId, String message) {
        AdminScreen screen = screen(player, "confirm", 27, "Confirm Action", false, ScreenState.inspector(targetId)); border(screen);
        screen.getInventory().setItem(4, NeonItems.item(Material.REDSTONE_BLOCK, NeonItems.ERROR + "<bold>Dangerous Action", NeonItems.WARN + safe(message), NeonItems.MUTED + "Expires in 30 seconds", NeonItems.MAGENTA + "Actor-bound confirmation"));
        button(screen, 11, NeonItems.item(Material.LIME_CONCRETE, NeonItems.SUCCESS + "<bold>Confirm", NeonItems.MUTED + "Execute once and audit"), event -> showOutcome(player, dispatcher.confirm(player::hasPermission, player.getUniqueId(), confirmationId), targetId));
        button(screen, 15, NeonItems.item(Material.RED_CONCRETE, NeonItems.ERROR + "<bold>Cancel", NeonItems.MUTED + "Do not execute"), event -> { dispatcher.cancel(player.getUniqueId()); renderInspector(player, targetId, false); });
        button(screen, 22, NeonItems.close(), event -> { dispatcher.cancel(player.getUniqueId()); player.closeInventory(); }); open(player, screen);
    }

    private void renderWorlds(Player player, boolean push) {
        if (!allowed(player, "smpplatform.admin.worlds")) return;
        AdminScreen screen = screen(player, "worlds", 54, "World Control", push, ScreenState.worlds()); border(screen); int slot = 10;
        for (NeonWorld world : NeonWorld.values()) button(screen, slot++, worldItem(world), event -> renderWorldDetail(player, world, true));
        navigation(screen, player); open(player, screen);
    }
    private ItemStack worldItem(NeonWorld world) {
        Material material = switch (world) { case SPAWN_HUB -> Material.RECOVERY_COMPASS; case ASHFALL -> Material.MAGMA_BLOCK; case OBSIDIAN_GATE -> Material.CRYING_OBSIDIAN; case ATRIUM -> Material.QUARTZ_BLOCK; case COLOSSEUM -> Material.DIAMOND_SWORD; case QUARRY -> Material.IRON_PICKAXE; case VERDANCE -> Material.MOSS_BLOCK; };
        org.bukkit.World runtime = Bukkit.getWorld(world.id()); int players = runtime == null ? 0 : runtime.getPlayers().size();
        String status = runtime == null ? NeonItems.WARN + "Not loaded" : NeonItems.SUCCESS + "Loaded";
        return NeonItems.item(material, NeonItems.MAGENTA + "<bold>" + world.displayName(), NeonItems.BLUE + world.type() + " · " + world.season(), NeonItems.MUTED + "Status: <white>" + world.status() + " · " + status, NeonItems.MUTED + "Players: <white>" + players + "  Difficulty: <white>" + world.difficulty(), NeonItems.MUTED + "Border: <white>" + (runtime == null ? "—" : String.format("%.0f", runtime.getWorldBorder().getSize())), NeonItems.BLUE + "Click for runtime detail");
    }
    private void renderWorldDetail(Player player, NeonWorld world, boolean push) {
        AdminScreen screen = screen(player, "world-" + world.id(), 54, world.displayName(), push, ScreenState.worlds()); border(screen); org.bukkit.World runtime = Bukkit.getWorld(world.id());
        screen.getInventory().setItem(13, worldItem(world));
        if (runtime != null) {
            summary(screen, 29, Material.CLOCK, "TIME & WEATHER", "Time: " + runtime.getTime(), "Weather: " + (runtime.hasStorm() ? "Storm" : "Clear"));
            summary(screen, 31, Material.GLOBE_BANNER_PATTERN, "RUNTIME", "Players: " + runtime.getPlayers().size(), "PVP: " + runtime.getPVP(), "Difficulty: " + runtime.getDifficulty());
            summary(screen, 33, Material.BARRIER, "WORLD BORDER", "Size: " + String.format("%.0f", runtime.getWorldBorder().getSize()), "Spawn: " + runtime.getSpawnLocation().getBlockX() + ", " + runtime.getSpawnLocation().getBlockZ());
        } else summary(screen, 31, Material.REDSTONE, "RUNTIME", "World is not currently loaded", "Managed by World Registry / Multiverse");
        screen.getInventory().setItem(40, NeonItems.item(Material.BOOK, NeonItems.PURPLE + "<bold>World policy", NeonItems.MUTED + safe(world.description()), NeonItems.MUTED + "Configuration changes belong to the central World Registry."));
        navigation(screen, player); open(player, screen);
    }

    private void renderMonitor(Player player, boolean push) {
        if (!allowed(player, "smpplatform.admin.monitor")) return;
        AdminScreen screen = screen(player, "monitor", 54, "Server Monitor", push, ScreenState.monitor()); border(screen); int slot = 9;
        for (HealthAdapters.HealthMetric metric : HealthAdapters.snapshot(directory.allProfiles())) { if (slot >= 45) break; screen.getInventory().setItem(slot++, NeonItems.item(Material.COMPARATOR, metric.state().icon() + " <bold>" + metric.name(), NeonItems.MUTED + safe(metric.value()))); }
        button(screen, 49, NeonItems.item(Material.CLOCK, NeonItems.BLUE + "<bold>Refresh", NeonItems.MUTED + "Capture a current snapshot"), event -> renderMonitor(player, false)); navigation(screen, player); open(player, screen);
    }
    private void renderAudit(Player player, boolean push) {
        if (!allowed(player, "smpplatform.admin.audit")) return;
        AdminScreen screen = screen(player, "audit", 54, "Audit History", push, ScreenState.audit()); border(screen); fillAudit(screen, audit.recent(28)); navigation(screen, player); open(player, screen);
    }
    private void renderTargetAudit(Player player, UUID targetId, boolean push) {
        if (!allowed(player, "smpplatform.admin.audit")) return;
        AdminScreen screen = screen(player, "target-audit", 54, "Player Audit", push, ScreenState.inspector(targetId)); border(screen); fillAudit(screen, audit.recentFor(targetId, 28)); navigation(screen, player); open(player, screen);
    }
    private void fillAudit(AdminScreen screen, List<AdminActionDispatcher.AuditRecord> records) {
        int slot = 9; for (AdminActionDispatcher.AuditRecord record : records) { if (slot >= 45) break; String color = record.outcome().equals("EXECUTED") ? NeonItems.SUCCESS : record.outcome().contains("CONFIRM") ? NeonItems.WARN : NeonItems.ERROR; screen.getInventory().setItem(slot++, NeonItems.item(Material.PAPER, color + "<bold>" + record.action() + " · " + record.outcome(), NeonItems.MUTED + "Actor: <white>" + safe(record.actorName()), NeonItems.MUTED + "Target: <white>" + safe(record.targetName()), NeonItems.MUTED + "Reason: <white>" + safe(record.reason()), NeonItems.MUTED + safe(record.detail()))); }
        if (records.isEmpty()) screen.getInventory().setItem(22, NeonItems.item(Material.BOOK, NeonItems.MUTED + "<bold>No audit records in this runtime", NeonItems.MUTED + "Persistent audit_logs adapter is supplied by core composition."));
    }

    @EventHandler(ignoreCancelled = false) public void click(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        Inventory readOnly = readOnlyViews.get(player.getUniqueId());
        if (readOnly != null && event.getView().getTopInventory().equals(readOnly)) { event.setCancelled(true); return; }
        if (!(event.getView().getTopInventory().getHolder(false) instanceof AdminScreen screen)) return;
        if (!screen.viewerId().equals(player.getUniqueId())) { event.setCancelled(true); return; }
        event.setCancelled(true); if (event.getRawSlot() >= 0 && event.getRawSlot() < event.getView().getTopInventory().getSize()) screen.click(event.getRawSlot(), event);
    }
    @EventHandler public void close(InventoryCloseEvent event) { readOnlyViews.remove(event.getPlayer().getUniqueId()); }
    @EventHandler(ignoreCancelled = true) public void chat(AsyncPlayerChatEvent event) {
        PendingInput input = prompts.remove(event.getPlayer().getUniqueId()); if (input == null) return; event.setCancelled(true); String text = event.getMessage().trim();
        Bukkit.getScheduler().runTask(plugin, () -> handleInput(event.getPlayer(), input, text));
    }
    private void handleInput(Player player, PendingInput input, String text) {
        if (text.equalsIgnoreCase("cancel")) { player.sendMessage(MM.deserialize(NeonItems.MUTED + "Admin input cancelled.")); return; }
        if (input.search()) { renderPlayers(player, PlayerDirectory.PlayerFilter.ONLINE, text, 0, false); return; }
        PlayerDirectory.PlayerProfile target = directory.get(input.targetId()).orElse(null); if (target == null) { player.sendMessage(MM.deserialize("<red>Target profile is unavailable.")); return; }
        String value = "", reason = "";
        if (input.action().needsValue() && input.action().needsReason()) { String[] parts = text.split("\\|", 2); if (parts.length != 2 || parts[0].isBlank() || parts[1].isBlank()) { player.sendMessage(MM.deserialize("<red>Use <white><value> | <reason></white>.")); return; } value = parts[0].trim(); reason = parts[1].trim(); }
        else if (input.action().needsValue()) value = text;
        else reason = text;
        dispatch(player, target, input.action(), value, reason);
    }

    /** Command bridge for operator accessibility; still routes through checks and confirmation. */
    public void commandAction(Player player, AdminAction action, String targetName, String value, String reason) {
        PlayerDirectory.PlayerProfile target = directory.findOrResolve(targetName, Bukkit.getOfflinePlayerIfCached(targetName)).orElse(null);
        if (target == null) { player.sendMessage(MM.deserialize("<red>Unknown player. They must be cached through a prior join or profile lookup.")); return; }
        if ((action.needsValue() && value.isBlank()) || (action.needsReason() && reason.isBlank())) { beginInput(player, target, action); return; }
        dispatch(player, target, action, value, reason);
    }
    public void openReadOnlyInventory(Player actor, Inventory inventory) { readOnlyViews.put(actor.getUniqueId(), inventory); actor.openInventory(inventory); }
    public boolean isReadOnlyViewer(Player viewer) { return readOnlyViews.containsKey(viewer.getUniqueId()); }

    private AdminScreen screen(Player player, String key, int size, String title, boolean push, ScreenState state) {
        if (push) history.computeIfAbsent(player.getUniqueId(), unused -> new ArrayDeque<>()).push(state); AdminScreen screen = new AdminScreen(player.getUniqueId(), key); Inventory inventory = Bukkit.createInventory(screen, size, NeonItems.title(title)); screen.inventory(inventory); return screen;
    }
    private void open(Player player, AdminScreen screen) { player.openInventory(screen.getInventory()); }
    private void border(AdminScreen screen) { for (int i = 0; i < screen.getInventory().getSize(); i++) { if (i < 9 || i >= screen.getInventory().getSize() - 9 || i % 9 == 0 || i % 9 == 8) screen.getInventory().setItem(i, NeonItems.filler()); } }
    private void button(AdminScreen screen, int slot, ItemStack item, AdminScreen.GuiAction action) { screen.getInventory().setItem(slot, item); screen.button(slot, action); }
    private void locked(AdminScreen screen, int slot, Material material, String name, String detail) { screen.getInventory().setItem(slot, NeonItems.item(material, NeonItems.MUTED + "<bold>" + name, NeonItems.MUTED + detail, NeonItems.WARN + "Available through its phase module")); }
    private void summary(AdminScreen screen, int slot, Material material, String title, String... lore) { String[] formatted = java.util.Arrays.stream(lore).map(line -> NeonItems.MUTED + safe(line)).toArray(String[]::new); screen.getInventory().setItem(slot, NeonItems.item(material, NeonItems.BLUE + "<bold>" + title, formatted)); }
    private void navigation(AdminScreen screen, Player player) { button(screen, 45, NeonItems.back(), event -> goBack(player)); button(screen, 49, NeonItems.home(), event -> renderHome(player, false)); button(screen, 53, NeonItems.close(), event -> player.closeInventory()); }
    private void goBack(Player player) { Deque<ScreenState> stack = history.get(player.getUniqueId()); if (stack == null || stack.isEmpty()) { renderHome(player, false); return; } render(stack.pop(), player); }
    private void render(ScreenState state, Player player) { switch (state.kind()) { case HOME -> renderHome(player, false); case PLAYERS -> renderPlayers(player, state.filter(), state.query(), state.page(), false); case INSPECTOR -> renderInspector(player, state.targetId(), false); case WORLDS -> renderWorlds(player, false); case MONITOR -> renderMonitor(player, false); case AUDIT -> renderAudit(player, false); } }
    private static String safe(String raw) { return raw == null ? "—" : raw.replace("<", "\\<"); }
    private record PendingInput(UUID targetId, AdminAction action, boolean search) { static PendingInput forSearch() { return new PendingInput(null, null, true); } PendingInput(UUID targetId, AdminAction action) { this(targetId, action, false); } }
    private record ScreenState(Kind kind, PlayerDirectory.PlayerFilter filter, String query, int page, UUID targetId) {
        static ScreenState home() { return new ScreenState(Kind.HOME, null, "", 0, null); } static ScreenState players(PlayerDirectory.PlayerFilter f, String q, int p) { return new ScreenState(Kind.PLAYERS, f, q, p, null); } static ScreenState inspector(UUID id) { return new ScreenState(Kind.INSPECTOR, null, "", 0, id); } static ScreenState worlds() { return new ScreenState(Kind.WORLDS, null, "", 0, null); } static ScreenState monitor() { return new ScreenState(Kind.MONITOR, null, "", 0, null); } static ScreenState audit() { return new ScreenState(Kind.AUDIT, null, "", 0, null); }
    }
    private enum Kind { HOME, PLAYERS, INSPECTOR, WORLDS, MONITOR, AUDIT }
}
