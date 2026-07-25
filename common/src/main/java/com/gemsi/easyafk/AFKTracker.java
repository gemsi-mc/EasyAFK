package com.gemsi.easyafk;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;

/**
 * All per-player AFK bookkeeping, keyed by UUID and driven by an injected clock.
 *
 * <p>Idle time is derived from a single "last activity" timestamp rather than a counter
 * incremented each tick, so there is no per-tick state to keep in sync and nothing keyed
 * by {@code ServerPlayer} (whose instance is replaced on respawn and dimension change).
 *
 * <p>{@link #forget(UUID)} must release every map here; {@code AFKTrackerTest} asserts it.
 */
public final class AFKTracker {

    private final LongSupplier clock;

    private final Map<UUID, Long> lastActivityMillis = new ConcurrentHashMap<>();
    private final Map<UUID, Long> afkSinceMillis = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lastCombatMillis = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lastDamageMillis = new ConcurrentHashMap<>();
    private final Set<UUID> kickWarned = ConcurrentHashMap.newKeySet();

    public AFKTracker(LongSupplier clock) {
        this.clock = clock;
    }

    public AFKTracker() {
        this(System::currentTimeMillis);
    }

    public void recordActivity(UUID player) {
        lastActivityMillis.put(player, clock.getAsLong());
    }

    public long idleSeconds(UUID player) {
        return elapsedSeconds(lastActivityMillis.get(player));
    }

    public void markAFK(UUID player) {
        afkSinceMillis.put(player, clock.getAsLong());
    }

    /**
     * Also resets the idle timer, otherwise the player would still look idle from before
     * they went AFK and would be re-marked on the very next check.
     */
    public void clearAFK(UUID player) {
        afkSinceMillis.remove(player);
        kickWarned.remove(player);
        recordActivity(player);
    }

    public boolean isAFK(UUID player) {
        return afkSinceMillis.containsKey(player);
    }

    public long afkDurationSeconds(UUID player) {
        return elapsedSeconds(afkSinceMillis.get(player));
    }

    public Set<UUID> afkPlayers() {
        return Set.copyOf(afkSinceMillis.keySet());
    }

    public void recordCombat(UUID player) {
        lastCombatMillis.put(player, clock.getAsLong());
    }

    public boolean inCombat(UUID player, long cooldownMillis) {
        return withinCooldown(lastCombatMillis.get(player), cooldownMillis);
    }

    public void recordDamage(UUID player) {
        lastDamageMillis.put(player, clock.getAsLong());
    }

    public boolean recentlyDamaged(UUID player, long cooldownMillis) {
        return withinCooldown(lastDamageMillis.get(player), cooldownMillis);
    }

    /** Ends the combat and damage cooldowns for a player straight away. */
    public void clearCooldowns(UUID player) {
        lastCombatMillis.remove(player);
        lastDamageMillis.remove(player);
    }

    /** @return true the first time it is called for this AFK session, false after. */
    public boolean markKickWarningShown(UUID player) {
        return kickWarned.add(player);
    }

    /** Releases every piece of state held for a player. Call on disconnect. */
    public void forget(UUID player) {
        lastActivityMillis.remove(player);
        afkSinceMillis.remove(player);
        lastCombatMillis.remove(player);
        lastDamageMillis.remove(player);
        kickWarned.remove(player);
    }

    public int trackedPlayerCount() {
        return trackedPlayers().size();
    }

    public String describeTrackedState() {
        return "lastActivity=" + lastActivityMillis.keySet()
                + " afkSince=" + afkSinceMillis.keySet()
                + " lastCombat=" + lastCombatMillis.keySet()
                + " lastDamage=" + lastDamageMillis.keySet()
                + " kickWarned=" + kickWarned;
    }

    private Set<UUID> trackedPlayers() {
        Set<UUID> all = new HashSet<>();
        all.addAll(lastActivityMillis.keySet());
        all.addAll(afkSinceMillis.keySet());
        all.addAll(lastCombatMillis.keySet());
        all.addAll(lastDamageMillis.keySet());
        all.addAll(kickWarned);
        return all;
    }

    private long elapsedSeconds(Long since) {
        if (since == null) {
            return 0;
        }
        return Math.max(0, (clock.getAsLong() - since) / 1000L);
    }

    private boolean withinCooldown(Long since, long cooldownMillis) {
        return since != null && clock.getAsLong() - since <= cooldownMillis;
    }
}
