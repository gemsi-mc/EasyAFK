package com.gemsi.easyafk;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EffectFreezePolicyTest {

    private static final int FROZEN = 600;

    @Test
    @DisplayName("an effect that has not drifted is left alone")
    void withinSlack() {
        assertFalse(EffectFreezePolicy.hasDecayed(FROZEN, FROZEN, false));
        assertFalse(EffectFreezePolicy.hasDecayed(FROZEN - 1, FROZEN, false));
        assertFalse(EffectFreezePolicy.hasDecayed(FROZEN - EffectFreezePolicy.SLACK_TICKS + 1, FROZEN, false));
    }

    @Test
    @DisplayName("an effect is topped up once it has lost a full slack window")
    void pastSlack() {
        assertTrue(EffectFreezePolicy.hasDecayed(FROZEN - EffectFreezePolicy.SLACK_TICKS, FROZEN, false));
        assertTrue(EffectFreezePolicy.hasDecayed(0, FROZEN, false));
    }

    @Test
    @DisplayName("an infinite effect never needs topping up")
    void infiniteNeverDecays() {
        assertFalse(EffectFreezePolicy.hasDecayed(0, -1, true));
        assertFalse(EffectFreezePolicy.hasDecayed(Integer.MAX_VALUE, -1, true));
    }

    @Test
    @DisplayName("an effect shorter than the slack window is never topped up mid-flight")
    void shorterThanSlack() {
        // A 10 tick effect can never lose 20, so it is left to run out and is only put back
        // once it has actually gone, which the caller handles by seeing no effect at all.
        assertFalse(EffectFreezePolicy.hasDecayed(10, 10, false));
        assertFalse(EffectFreezePolicy.hasDecayed(1, 10, false));
    }
}
