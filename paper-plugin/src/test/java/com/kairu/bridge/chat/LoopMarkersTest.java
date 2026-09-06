package com.kairu.bridge.chat;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LoopMarkersTest {
    @Test
    void marksAndRecognizesDiscordOriginWithoutChangingVisibleTextWhenStripped() {
        String marked = LoopMarkers.markDiscordOrigin("[Discord] Ada: hello", "discord_123");
        assertTrue(LoopMarkers.containsBridgeMarker(marked));
        assertEquals("[Discord] Ada: hello", LoopMarkers.removeBridgeMarkers(marked));
    }

    @Test
    void rejectsUnsafeQueueIds() {
        assertThrows(IllegalArgumentException.class, () -> LoopMarkers.markDiscordOrigin("hello", "bad/id"));
        assertFalse(LoopMarkers.containsBridgeMarker("ordinary player message"));
    }
}
