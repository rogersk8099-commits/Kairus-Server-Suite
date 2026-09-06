package network.neonnexus.smp.admin.action;

import net.kyori.adventure.text.minimessage.MiniMessage;
import network.neonnexus.smp.admin.domain.AdminAction;
import network.neonnexus.smp.admin.domain.AdminActionDispatcher;
import network.neonnexus.smp.admin.domain.AdminActionRequest;
import network.neonnexus.smp.admin.gui.AdminGui;
import network.neonnexus.smp.admin.moderation.DurationParser;
import network.neonnexus.smp.admin.moderation.ModerationState;
import org.bukkit.BanList;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.plugin.Plugin;

import java.time.Instant;
import java.util.Locale;
import java.util.Optional;

/** Bukkit mutations are intentionally reached only via AdminActionDispatcher. */
public final class BukkitActionExecutor implements AdminActionDispatcher.ActionExecutor {
    private final Plugin plugin;
    private final ModerationState moderation;
    private AdminGui gui;
    private static final MiniMessage MM = MiniMessage.miniMessage();

    public BukkitActionExecutor(Plugin plugin, ModerationState moderation) { this.plugin = plugin; this.moderation = moderation; }
    public void gui(AdminGui gui) { this.gui = gui; }

    @Override public AdminActionDispatcher.ActionResult execute(AdminActionRequest request) {
        Player actor = Bukkit.getPlayer(request.actorId());
        Player target = Bukkit.getPlayer(request.targetId());
        if (request.action().targetMustBeOnline() && target == null) return fail("Target must be online for this action.");
        try {
            return switch (request.action()) {
                case TELEPORT_TO -> { if (actor == null) yield fail("Actor must be online."); actor.teleportAsync(target.getLocation()); yield ok(request, "Teleported to " + target.getName()); }
                case BRING -> { if (actor == null) yield fail("Actor must be online."); target.teleportAsync(actor.getLocation()); yield ok(request, "Brought " + target.getName()); }
                case SEND_WORLD -> sendWorld(request, target);
                case SEND_SPAWN -> { target.teleportAsync(target.getWorld().getSpawnLocation()); yield ok(request, "Sent to world spawn."); }
                case SET_GAMEMODE -> setGameMode(request, target);
                case HEAL -> { target.setHealth(target.getMaxHealth()); target.setFireTicks(0); yield ok(request, "Healed player."); }
                case FEED -> { target.setFoodLevel(20); target.setSaturation(20F); yield ok(request, "Fed player."); }
                case SET_HEALTH -> setHealth(request, target);
                case SET_HUNGER -> setHunger(request, target);
                case SET_XP -> setXp(request, target);
                case CLEAR_EFFECTS -> { target.getActivePotionEffects().forEach(effect -> target.removePotionEffect(effect.getType())); yield ok(request, "Cleared effects."); }
                case APPLY_EFFECT -> applyEffect(request, target);
                case VIEW_INVENTORY -> openInventory(request, actor, target, false, true);
                case EDIT_INVENTORY -> openInventory(request, actor, target, false, false);
                case VIEW_ENDER_CHEST -> openInventory(request, actor, target, true, true);
                case CLEAR_INVENTORY -> { target.getInventory().clear(); yield ok(request, "Cleared inventory."); }
                case FREEZE -> { moderation.freeze(target.getUniqueId()); yield ok(request, "Froze player."); }
                case UNFREEZE -> { moderation.unfreeze(target.getUniqueId()); yield ok(request, "Unfroze player."); }
                case KICK -> { target.kick(MM.deserialize("<red>You were removed by staff.</red><gray> " + escape(request.reason()) + "</gray>")); yield ok(request, "Kicked player."); }
                case WARN -> { target.sendMessage(MM.deserialize("<yellow><bold>Staff warning:</bold> " + escape(request.reason()))); yield ok(request, "Warned player."); }
                case MUTE -> { moderation.mute(request.targetId(), Instant.MAX); yield ok(request, "Muted player."); }
                case TEMP_MUTE -> tempMute(request);
                case BAN -> ban(request, null);
                case TEMP_BAN -> tempBan(request);
            };
        } catch (IllegalArgumentException exception) { return fail(exception.getMessage()); }
    }

    private AdminActionDispatcher.ActionResult sendWorld(AdminActionRequest request, Player target) { World world = Bukkit.getWorld(request.value()); if (world == null) return fail("World '" + request.value() + "' is not loaded."); target.teleportAsync(world.getSpawnLocation()); return ok(request, "Sent to " + world.getName() + "."); }
    private AdminActionDispatcher.ActionResult setGameMode(AdminActionRequest request, Player target) { try { target.setGameMode(GameMode.valueOf(request.value().toUpperCase(Locale.ROOT))); return ok(request, "Gamemode updated."); } catch (IllegalArgumentException e) { return fail("Gamemode must be survival, creative, adventure, or spectator."); } }
    private AdminActionDispatcher.ActionResult setHealth(AdminActionRequest r, Player p) { double health = Double.parseDouble(r.value()); if (health < 0 || health > p.getMaxHealth()) return fail("Health must be 0–" + p.getMaxHealth() + "."); p.setHealth(health); return ok(r, "Health updated."); }
    private AdminActionDispatcher.ActionResult setHunger(AdminActionRequest r, Player p) { int hunger = Integer.parseInt(r.value()); if (hunger < 0 || hunger > 20) return fail("Hunger must be 0–20."); p.setFoodLevel(hunger); return ok(r, "Hunger updated."); }
    private AdminActionDispatcher.ActionResult setXp(AdminActionRequest r, Player p) { int level = Integer.parseInt(r.value()); if (level < 0 || level > 100000) return fail("XP level must be 0–100000."); p.setLevel(level); p.setExp(0F); return ok(r, "XP level updated."); }
    private AdminActionDispatcher.ActionResult applyEffect(AdminActionRequest r, Player p) { String[] parts = r.value().split(":"); if (parts.length < 2 || parts.length > 3) return fail("Effect format: EFFECT:seconds[:amplifier]."); PotionEffectType type = PotionEffectType.getByName(parts[0].toUpperCase(Locale.ROOT)); if (type == null) return fail("Unknown potion effect."); int seconds = Integer.parseInt(parts[1]); int amp = parts.length == 3 ? Integer.parseInt(parts[2]) : 0; if (seconds < 1 || seconds > 86400 || amp < 0 || amp > 255) return fail("Effect duration/amplifier is outside safe limits."); p.addPotionEffect(new PotionEffect(type, seconds * 20, amp)); return ok(r, "Applied effect."); }
    private AdminActionDispatcher.ActionResult openInventory(AdminActionRequest r, Player actor, Player target, boolean ender, boolean readOnly) { if (actor == null) return fail("Actor must be online."); if (readOnly && gui != null) gui.openReadOnlyInventory(actor, ender ? target.getEnderChest() : target.getInventory()); else actor.openInventory(ender ? target.getEnderChest() : target.getInventory()); return ok(r, "Opened " + (readOnly ? "read-only " : "editable ") + (ender ? "ender chest" : "inventory") + "."); }
    private AdminActionDispatcher.ActionResult tempMute(AdminActionRequest r) { Optional<java.time.Duration> duration = DurationParser.parse(r.value()); if (duration.isEmpty()) return fail("Duration format: 15m, 2h, 7d, or 1w."); moderation.mute(r.targetId(), Instant.now().plus(duration.get())); return ok(r, "Temporarily muted player."); }
    private AdminActionDispatcher.ActionResult tempBan(AdminActionRequest r) { Optional<java.time.Duration> duration = DurationParser.parse(r.value()); if (duration.isEmpty()) return fail("Duration format: 15m, 2h, 7d, or 1w."); return ban(r, java.util.Date.from(Instant.now().plus(duration.get()))); }
    private AdminActionDispatcher.ActionResult ban(AdminActionRequest r, java.util.Date expiry) { Bukkit.getBanList(BanList.Type.NAME).addBan(r.targetName(), r.reason(), expiry, r.actorName()); Player online = Bukkit.getPlayer(r.targetId()); if (online != null) online.kick(MM.deserialize("<red>You have been banned.</red><gray> " + escape(r.reason()) + "</gray>")); return ok(r, expiry == null ? "Banned player." : "Temporarily banned player."); }
    private AdminActionDispatcher.ActionResult ok(AdminActionRequest r, String detail) { plugin.getLogger().fine(r.action() + " " + r.targetName()); return AdminActionDispatcher.ActionResult.success(detail); }
    private AdminActionDispatcher.ActionResult fail(String detail) { return AdminActionDispatcher.ActionResult.failure(detail == null ? "Action could not be completed." : detail); }
    private static String escape(String raw) { return raw == null ? "" : raw.replace("<", "\\<"); }
}
