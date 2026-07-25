package com.gemsi.easyafk;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AFKDamagePolicyTest {

    private static final AFKDamagePolicy INVULNERABLE =
            new AFKDamagePolicy(true, true);
    private static final AFKDamagePolicy FALL_ONLY =
            new AFKDamagePolicy(false, true);
    private static final AFKDamagePolicy NO_PROTECTION =
            new AFKDamagePolicy(false, false);

    @Test
    void allowsDamageToPlayersWhoAreNotAfk() {
        assertFalse(INVULNERABLE.shouldCancel(false, true));
        assertFalse(INVULNERABLE.shouldCancel(false, false));
    }

    @Test
    void invulnerableModeCancelsFallDamage() {
        assertTrue(INVULNERABLE.shouldCancel(true, true));
    }

    @Test
    void invulnerableModeCancelsNonFallDamage() {
        assertTrue(INVULNERABLE.shouldCancel(true, false));
    }

    @Test
    void fallOnlyModeCancelsFallDamage() {
        assertTrue(FALL_ONLY.shouldCancel(true, true));
    }

    @Test
    void fallOnlyModeAllowsNonFallDamage() {
        assertFalse(FALL_ONLY.shouldCancel(true, false));
    }

    @Test
    void noProtectionAllowsFallDamage() {
        assertFalse(NO_PROTECTION.shouldCancel(true, true));
    }

    @Test
    void noProtectionAllowsNonFallDamage() {
        assertFalse(NO_PROTECTION.shouldCancel(true, false));
    }
}
