/* Kairu SMP Client UI fork: Kairu-owned configuration tree. */
package uk.kairu.client;

import org.polyfrost.oneconfig.api.config.v1.Config;
import org.polyfrost.oneconfig.api.config.v1.annotations.Button;
import org.polyfrost.oneconfig.api.config.v1.annotations.Info;
import org.polyfrost.oneconfig.api.config.v1.annotations.Keybind;
import org.polyfrost.oneconfig.api.ui.v1.keybind.KeybindHelper;
import org.polyfrost.oneconfig.api.ui.v1.keybind.OneConfigKeybind;
import com.mojang.blaze3d.platform.InputConstants;

/** The initial Kairu card in the Compose shell. Server data/actions are bound by KairuControlBridge. */
public final class KairuControlConfig extends Config {
    private static KairuControlConfig INSTANCE;

    @Keybind(title = "Open Kairu SMP", description = "Change this key in the Kairu client settings.")
    private final OneConfigKeybind open = KeybindHelper.builder().key(InputConstants.KEY_K)
        // The Compose runtime's default category is "OneConfig".  This is a
        // Kairu-owned control and must be presented as such in Minecraft Controls.
        .name("Open Kairu SMP").category("Kairu SMP")
        .action(pressed -> { if (pressed) KairuComposeClient.openControlCentre(); }).register();

    @Info(title = "Kairu SMP Control Centre", description = "Your Kairu menu is server-authoritative. Player and staff capabilities are supplied by SMPPlatform.")
    public static String introduction = "";

    @Button(title = "World travel", description = "Available worlds are supplied by the server.", category = "Player")
    private void travel() { KairuControlBridge.open("travel"); }
    @Button(title = "Guilds", description = "Create, join, manage ranks and view guild progress.", category = "Player")
    private void guilds() { KairuControlBridge.open("guilds"); }
    @Button(title = "Points & currencies", description = "View Kairu Points, transactions and leaderboards.", category = "Player")
    private void points() { KairuControlBridge.open("points"); }
    @Button(title = "Atrium plots", description = "Available only inside The Atrium.", category = "Player")
    private void plots() { KairuControlBridge.open("plots"); }
    @Button(title = "Player administration", description = "Shown only when the server grants staff permissions.", category = "Administration")
    private void players() { KairuControlBridge.open("players"); }
    @Button(title = "World controls", description = "Maintenance, rules and Multiverse controls when permitted.", category = "Administration")
    private void worlds() { KairuControlBridge.open("worlds"); }

    private KairuControlConfig() { super("kairu_smp_client.json", "Kairu SMP", Category.QOL); }
    public static KairuControlConfig instance() { return INSTANCE == null ? INSTANCE = new KairuControlConfig() : INSTANCE; }
}
