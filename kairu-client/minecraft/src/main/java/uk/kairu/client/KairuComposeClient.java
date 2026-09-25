/*
 * Kairu SMP Client UI fork.
 *
 * This class is Kairu code.  It is compiled into the Kairu client fork and
 * opens Kairu's Compose UI surface; it does not load a separately-installed
 * OneConfig mod.
 */
package uk.kairu.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import org.polyfrost.oneconfig.api.event.v1.EventManager;
import org.polyfrost.oneconfig.api.event.v1.events.InitializationEvent;
import org.polyfrost.oneconfig.internal.ui.api.ConfigRegistry;
import org.polyfrost.oneconfig.internal.ui.api.ConfigSource;

/** Entry point for the Kairu-owned Compose control centre. */
public final class KairuComposeClient implements ClientModInitializer {
    @Override public void onInitializeClient() {
        ClientReceiveMessageEvents.ALLOW_GAME.register((message, overlay) -> KairuControlBridge.accept(message.getString(), overlay));
        EventManager.register(InitializationEvent.class, () -> {
            KairuControlConfig config = KairuControlConfig.instance();
            if (config.getTree() == null) config.preload();
            ConfigRegistry.INSTANCE.registerTree(config.getTree(), ConfigSource.OC);
        });
    }

    /** Called by Kairu's keybinding hook once the player is connected to SMPPlatform. */
    public static void openControlCentre() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.level == null || minecraft.getConnection() == null || minecraft.hasSingleplayerServer()) {
            if (minecraft.player != null) minecraft.gui.hud.setOverlayMessage(Component.literal("Join the Kairu SMP server to open this menu."), true);
            return;
        }
        // Reopening must restore the last Kairu page, rather than always returning
        // the player to World Travel.
        KairuControlBridge.refreshCurrent();
        org.polyfrost.oneconfig.api.platform.v1.Platform.screen().display(new KairuControlScreen());
    }
}
