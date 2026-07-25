package com.gemsi.easyafk;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ConfigCoercionTest {

    @AfterEach
    void restoreDefaults() {
        ConfigSchema.applyDefaults();
    }

    private static ConfigSchema.Entry entry(String field) {
        return ConfigSchema.entries().stream()
                .filter(e -> e.field().equals(field))
                .findFirst()
                .orElseThrow(() -> new AssertionError("no schema entry: " + field));
    }

    @Test
    void keepsIntegersInsideTheirRange() {
        // afkTimeout is bounded 10..3600
        assertEquals(300, ConfigSchema.coerce(entry("afkTimeout"), 300));
    }

    @Test
    void clampsIntegersAboveMaximum() {
        assertEquals(3600, ConfigSchema.coerce(entry("afkTimeout"), 99999));
    }

    @Test
    void clampsIntegersBelowMinimum() {
        assertEquals(10, ConfigSchema.coerce(entry("afkTimeout"), -5));
    }

    @Test
    void clampsDoublesToRange() {
        // movementThreshold is bounded 0.01..5.0
        assertEquals(5.0, ConfigSchema.coerce(entry("movementThreshold"), 100.0));
        assertEquals(0.01, ConfigSchema.coerce(entry("movementThreshold"), 0.0));
    }

    @Test
    void passesBooleansThrough() {
        assertEquals(false, ConfigSchema.coerce(entry("freezeHunger"), false));
    }

    @Test
    void passesStringsThrough() {
        assertEquals("&aHi", ConfigSchema.coerce(entry("msgAfkEnter"), "&aHi"));
    }

    @Test
    void convertsStringListElementsToStrings() {
        assertEquals(List.of("a", "b"), ConfigSchema.coerce(entry("exemptPlayers"), List.of("a", "b")));
    }

    @Test
    void rejectsValueOfWrongType() {
        // A server owner typing a word where a number belongs should be reported,
        // not silently coerced into something meaningless.
        assertThrows(IllegalArgumentException.class,
                () -> ConfigSchema.coerce(entry("afkTimeout"), "three hundred"));
    }

    @Test
    void rejectsNonBooleanForBooleanSetting() {
        assertThrows(IllegalArgumentException.class,
                () -> ConfigSchema.coerce(entry("freezeHunger"), "yes"));
    }

    @Test
    void rejectsNullValue() {
        assertThrows(IllegalArgumentException.class,
                () -> ConfigSchema.coerce(entry("msgAfkEnter"), null));
    }
}
