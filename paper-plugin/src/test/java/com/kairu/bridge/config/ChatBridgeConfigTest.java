package com.kairu.bridge.config;

import com.kairu.bridge.chat.BridgeEventType;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChatBridgeConfigTest {
    @Test
    void supportsIndependentlyDisabledDirectionsAndEvents() {
        ChatBridgeConfig config = new ChatBridgeConfig(
                false, false, 240, 240, 8_192, 20, 10_000, 20, 100, 600_000,
                List.of("world"), "", false, false, false, false, false, false, false, false);

        assertFalse(config.minecraftToDiscordEnabled());
        assertFalse(config.discordToMinecraftEnabled());
        assertFalse(config.allows(BridgeEventType.CHAT));
        assertFalse(config.allows(BridgeEventType.PLAYER_JOIN));
        assertFalse(config.allows(BridgeEventType.SERVER_STARTED));
        assertTrue(config.worldEnabled("WORLD"));
        assertFalse(config.worldEnabled("nether"));
    }
}
