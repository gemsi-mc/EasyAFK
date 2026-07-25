package com.gemsi.easyafk;

/**
 * Decides whether incoming damage to a player should be cancelled.
 *
 * <p>{@code invulnerableWhileAFK} is the blanket switch: with it on, an AFK player takes
 * no damage at all. With it off, {@code preventFallDamage} still shields the player from
 * the fall they would otherwise take when AFK freezing drops them, but everything else
 * (mobs, lava, drowning) lands normally.
 */
public record AFKDamagePolicy(boolean invulnerableWhileAFK, boolean preventFallDamage) {

    public static AFKDamagePolicy fromConfig() {
        return new AFKDamagePolicy(Config.invulnerableWhileAFK, Config.preventFallDamage);
    }

    public boolean shouldCancel(boolean afk, boolean fallDamage) {
        if (!afk) {
            return false;
        }
        if (invulnerableWhileAFK) {
            return true;
        }
        return fallDamage && preventFallDamage;
    }
}
