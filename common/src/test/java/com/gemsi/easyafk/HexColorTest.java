package com.gemsi.easyafk;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class HexColorTest {

    @Test
    void parsesHexWithLeadingHash() {
        assertEquals(0xFF5050, HexColor.parse("#FF5050"));
    }

    @Test
    void parsesHexWithoutLeadingHash() {
        assertEquals(0xFF5050, HexColor.parse("FF5050"));
    }

    @Test
    void parsesLowercaseHex() {
        assertEquals(0xabcdef, HexColor.parse("#abcdef"));
    }

    @Test
    void fallsBackToWhiteOnGarbage() {
        assertEquals(0xFFFFFF, HexColor.parse("not-a-color"));
    }

    @Test
    void fallsBackToWhiteOnNull() {
        assertEquals(0xFFFFFF, HexColor.parse(null));
    }

    @Test
    void formatsBackToHexString() {
        assertEquals("#FF5050", HexColor.format(0xFF5050));
    }

    @Test
    void formatsPaddingShortValues() {
        assertEquals("#0000FF", HexColor.format(0x0000FF));
    }
}
