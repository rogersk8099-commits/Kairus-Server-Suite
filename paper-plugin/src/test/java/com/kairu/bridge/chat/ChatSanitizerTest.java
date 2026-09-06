package com.kairu.bridge.chat;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChatSanitizerTest {
    @Test
    void removesMinecraftFormattingControlsAndCollapsesWhitespace() {
        String value = ChatSanitizer.sanitize("  §aHello\n\u0000  world\t", 100);
        assertEquals("Hello world", value);
    }

    @Test
    void truncatesByUnicodeCodePointWithoutSplittingEmoji() {
        String value = ChatSanitizer.sanitize("A😀BC", 3);
        assertEquals("A😀B", value);
        assertEquals(3, value.codePointCount(0, value.length()));
    }

    @Test
    void truncatesUtf8AtCodePointBoundary() {
        String value = ChatSanitizer.truncateUtf8("a😀b", 5);
        assertEquals("a😀", value);
        assertTrue(ChatSanitizer.utf8Length(value) <= 5);
        assertFalse(value.contains("\uFFFD"));
    }
}
