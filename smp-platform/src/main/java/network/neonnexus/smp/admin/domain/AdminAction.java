package network.neonnexus.smp.admin.domain;

import java.util.Arrays;
import java.util.Locale;
import java.util.Optional;

/** Immutable policy catalogue for every phase-2 player operation. */
public enum AdminAction {
    TELEPORT_TO("Teleport to player", "smpplatform.admin.player.teleport", false, false, false, true, "Teleportation"),
    BRING("Bring player", "smpplatform.admin.player.bring", false, false, false, true, "Teleportation"),
    SEND_WORLD("Send to world", "smpplatform.admin.player.world", false, true, false, true, "Teleportation"),
    SEND_SPAWN("Send to spawn", "smpplatform.admin.player.spawn", false, false, false, true, "Teleportation"),
    SET_GAMEMODE("Set gamemode", "smpplatform.admin.player.gamemode", false, true, false, true, "Player state"),
    HEAL("Heal", "smpplatform.admin.player.heal", false, false, false, true, "Player state"),
    FEED("Feed", "smpplatform.admin.player.feed", false, false, false, true, "Player state"),
    SET_HEALTH("Set health", "smpplatform.admin.player.health", false, true, false, true, "Player state"),
    SET_HUNGER("Set hunger", "smpplatform.admin.player.hunger", false, true, false, true, "Player state"),
    SET_XP("Set XP", "smpplatform.admin.player.xp", false, true, false, true, "Player state"),
    CLEAR_EFFECTS("Clear effects", "smpplatform.admin.player.effects", false, false, false, true, "Effects"),
    APPLY_EFFECT("Apply effect", "smpplatform.admin.player.effects", false, true, false, true, "Effects"),
    VIEW_INVENTORY("View inventory", "smpplatform.admin.player.inventory", false, false, false, true, "Inventory"),
    EDIT_INVENTORY("Edit inventory", "smpplatform.admin.player.inventory", true, false, false, true, "Inventory"),
    VIEW_ENDER_CHEST("View ender chest", "smpplatform.admin.player.enderchest", false, false, false, true, "Inventory"),
    CLEAR_INVENTORY("Clear inventory", "smpplatform.admin.player.inventory", true, false, false, true, "Inventory"),
    FREEZE("Freeze", "smpplatform.admin.player.freeze", false, false, false, true, "Moderation"),
    UNFREEZE("Unfreeze", "smpplatform.admin.player.freeze", false, false, false, true, "Moderation"),
    KICK("Kick", "smpplatform.admin.moderation.kick", true, false, true, true, "Moderation"),
    WARN("Warn", "smpplatform.admin.moderation.warn", false, false, true, false, "Moderation"),
    MUTE("Mute", "smpplatform.admin.moderation.mute", true, false, true, false, "Moderation"),
    TEMP_MUTE("Temporarily mute", "smpplatform.admin.moderation.mute", true, true, true, false, "Moderation"),
    BAN("Ban", "smpplatform.admin.moderation.ban", true, false, true, false, "Moderation"),
    TEMP_BAN("Temporarily ban", "smpplatform.admin.moderation.ban", true, true, true, false, "Moderation");

    private final String displayName;
    private final String permission;
    private final boolean destructive;
    private final boolean needsValue;
    private final boolean needsReason;
    private final boolean targetMustBeOnline;
    private final String section;

    AdminAction(String displayName, String permission, boolean destructive, boolean needsValue, boolean needsReason,
                boolean targetMustBeOnline, String section) {
        this.displayName = displayName;
        this.permission = permission;
        this.destructive = destructive;
        this.needsValue = needsValue;
        this.needsReason = needsReason;
        this.targetMustBeOnline = targetMustBeOnline;
        this.section = section;
    }

    public String displayName() { return displayName; }
    public String permission() { return permission; }
    public boolean destructive() { return destructive; }
    public boolean needsValue() { return needsValue; }
    public boolean needsReason() { return needsReason; }
    public boolean targetMustBeOnline() { return targetMustBeOnline; }
    public String section() { return section; }

    public static Optional<AdminAction> parse(String input) {
        String normalized = input.trim().toUpperCase(Locale.ROOT).replace('-', '_').replace(' ', '_');
        return Arrays.stream(values()).filter(action -> action.name().equals(normalized)).findFirst();
    }
}
