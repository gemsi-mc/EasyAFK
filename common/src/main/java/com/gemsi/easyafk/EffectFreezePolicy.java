package com.gemsi.easyafk;

/**
 * Decides when a frozen potion effect has to be pushed back onto an AFK player.
 *
 * <p>Re-applying an effect sends a packet, so doing it every tick for every effect (which
 * is what a blanket {@code removeAllEffects()} plus re-add amounted to) is 20 packets a
 * second per effect for no visible gain. Letting an effect drift by up to
 * {@link #SLACK_TICKS} before topping it up costs one packet a second instead, and the
 * client never renders the difference because it only shows whole seconds.
 *
 * <p>Kept free of Minecraft types so it stays unit testable.
 */
public final class EffectFreezePolicy {

    /** How much duration a frozen effect may lose before it is topped back up. */
    public static final int SLACK_TICKS = 20;

    private EffectFreezePolicy() {
    }

    /**
     * @param currentDuration  ticks left on the effect the player actually has
     * @param frozenDuration   ticks the snapshot holds
     * @param frozenIsInfinite whether the snapshot's effect never expires
     * @return true if the snapshot should be re-applied
     */
    public static boolean hasDecayed(int currentDuration, int frozenDuration, boolean frozenIsInfinite) {
        // An infinite effect cannot decay, so touching it would only ever be noise.
        return !frozenIsInfinite && currentDuration <= frozenDuration - SLACK_TICKS;
    }
}
