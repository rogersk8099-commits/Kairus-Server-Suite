package uk.kairu.client;

import org.polyfrost.oneconfig.api.config.v1.Config;
import org.polyfrost.oneconfig.api.config.v1.ConfigManager;
import org.polyfrost.oneconfig.api.config.v1.annotations.Button;
import org.polyfrost.oneconfig.utils.v1.dsl.ScreensKt;

/**
 * Real OneConfig landing page for the Kairu client.
 *
 * It only provides navigation. All player, guild, world and PlotSquared mutations
 * continue through the Kairu request/reply gateway and are authorised by SMPPlatform.
 */
public final class KairuOneConfig extends Config {
    private static final KairuOneConfig INSTANCE = new KairuOneConfig();
    private static boolean initialised;

    private KairuOneConfig() { super("kairu_smp", "Kairu SMP", Category.OTHER); }

    public static void open() {
        if (KairuClient.state == null) return;
        try {
            if (!initialised) {
                INSTANCE.initialize(false);
                initialised = true;
            }
            ScreensKt.openUI(INSTANCE);
        } catch (Throwable error) {
            // A missing/incompatible embedded runtime must not strand the user.
            KairuClient.openNativePage(KairuClient.admin() ? "overview" : "travel");
            KairuClient.notice("OneConfig could not open; using the Kairu fallback menu.");
        }
    }

    @Button(title = "World travel", description = "Travel only to worlds your account may access.", category = "Player", text = "Open")
    private static void travel() { KairuClient.openNativePage("travel"); }

    @Button(title = "Guilds", description = "Create, join and manage your guild.", category = "Player", text = "Open")
    private static void guilds() { KairuClient.openNativePage("guilds"); }

    @Button(title = "Points & currencies", description = "View balances, history and leaderboards.", category = "Player", text = "Open")
    private static void points() { KairuClient.openNativePage("points"); }

    @Button(title = "Atrium plots", description = "PlotSquared controls are available while you are in The Atrium.", category = "Player", text = "Open")
    private static void plots() { KairuClient.openNativePage("plots"); }

    @Button(title = "Server overview", description = "Authorised administration overview and server status.", category = "Administration", text = "Open")
    private static void overview() { if (KairuClient.admin()) KairuClient.openNativePage("overview"); }

    @Button(title = "Player management", description = "Select a player before applying an authorised action.", category = "Administration", text = "Open")
    private static void players() { if (KairuClient.admin()) KairuClient.openNativePage("players"); }

    @Button(title = "World controls", description = "Maintenance, time, weather and gamerule controls.", category = "Administration", text = "Open")
    private static void worlds() { if (KairuClient.admin()) KairuClient.openNativePage("worlds"); }
}
