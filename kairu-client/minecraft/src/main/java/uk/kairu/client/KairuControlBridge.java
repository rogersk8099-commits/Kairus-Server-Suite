/* Kairu SMP Client UI fork: temporary action bridge while Compose pages are added. */
package uk.kairu.client;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import java.util.UUID;

/**
 * Transport boundary for the Compose UI.  It accepts only correlated response
 * envelopes from SMPPlatform and sends only normal server commands; permissions
 * and all mutations remain server-side.
 */
public final class KairuControlBridge {
    private static final String PREFIX = "KAIRU_ADMIN_V2:";
    private static String pendingId;
    private static String[] queuedAction;
    private static JsonObject state;
    private static String currentSection = "overview";
    private static String message = "Connect to SMPPlatform to load controls.";
    private static Runnable refreshListener = () -> { };

    private KairuControlBridge() { }
    public static JsonObject state() { return state; }
    public static String message() { return message; }
    public static String currentSection() { return currentSection; }
    /** Re-fetches the page the player was using; reopening the menu must not reset it to travel. */
    public static void refreshCurrent() { open(currentSection); }
    public static boolean can(String permission) {
        if (state == null || !state.has("admin") || !state.get("admin").getAsBoolean() || !state.has("permissions")) return false;
        for (var value : state.getAsJsonArray("permissions")) if (permission.equals(value.getAsString())) return true;
        return false;
    }

    /** True only when the server has granted at least one staff/administration capability. */
    public static boolean hasAdministrationAccess() {
        return can("players") || can("worlds") || can("moderation") || can("moderator") || can("kick") || can("roles") || can("monitor");
    }
    public static void setRefreshListener(Runnable listener) { refreshListener = listener == null ? () -> { } : listener; }

    public static void open(String section) {
        currentSection = section;
        switch (section) {
            case "guilds" -> request("guild-summary");
            case "points" -> request("points-summary");
            case "auction" -> request("auction-list");
            default -> request("status");
        }
    }

    public static void request(String... action) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.level == null || minecraft.getConnection() == null || minecraft.hasSingleplayerServer()) {
            message = "Join the Kairu SMP server to use this control.";
            return;
        }
        // The first status request can still be in flight when a player chooses
        // Guilds or Points.  Keep the latest choice and send it immediately after
        // that response instead of silently losing the click.
        if (pendingId != null) {
            queuedAction = action.clone();
            message = "Loading selected Kairu page…";
            refreshListener.run();
            return;
        }
        pendingId = UUID.randomUUID().toString();
        message = "Loading from SMPPlatform…";
        refreshListener.run();
        minecraft.getConnection().sendCommand("kairuadmin " + pendingId + " " + String.join(" ", action));
    }

    /** Fabric message hook. Returning false removes the private response from normal chat. */
    public static boolean accept(String raw, boolean overlay) {
        if (overlay || !raw.startsWith(PREFIX)) return true;
        // Fabric can invoke receive hooks outside Compose's client/render execution context.
        // Apply the state transition on Minecraft's client thread so an open page recomposes now,
        // rather than waiting for the player to switch tabs.
        Minecraft.getInstance().execute(() -> acceptOnClientThread(raw));
        return false;
    }

    private static void acceptOnClientThread(String raw) {
        try {
            JsonObject response = JsonParser.parseString(raw.substring(PREFIX.length())).getAsJsonObject();
            if (pendingId == null || !pendingId.equals(response.get("id").getAsString())) return;
            pendingId = null;
            boolean authorized = response.get("authorized").getAsBoolean();
            message = response.get("message").getAsString();
            // Page actions are incremental: a leaderboard, a status refresh, or a
            // points-history request must not erase the balances/guild summary that
            // the current screen is rendering.
            state = authorized ? merge(state, response) : null;
            refreshListener.run();
            Minecraft minecraft = Minecraft.getInstance();
            if (minecraft.player != null) minecraft.gui.hud.setOverlayMessage(Component.literal(message), true);
            String[] queued = queuedAction;
            queuedAction = null;
            if (queued != null) request(queued);
        } catch (RuntimeException ignored) {
            // A malformed response is ignored and can never grant an action surface.
        }
    }

    private static JsonObject merge(JsonObject previous, JsonObject incoming) {
        JsonObject merged = previous == null ? new JsonObject() : previous.deepCopy();
        for (var entry : incoming.entrySet()) {
            if (merged.has(entry.getKey()) && merged.get(entry.getKey()).isJsonObject() && entry.getValue().isJsonObject()) {
                merged.add(entry.getKey(), merge(merged.getAsJsonObject(entry.getKey()), entry.getValue().getAsJsonObject()));
            } else merged.add(entry.getKey(), entry.getValue());
        }
        return merged;
    }
}
