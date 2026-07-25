package com.gemsi.easyafk;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AFKTrackerTest {

    private static final UUID PLAYER = UUID.fromString("00000000-0000-0000-0000-000000000001");
    private static final UUID OTHER = UUID.fromString("00000000-0000-0000-0000-000000000002");

    /** Test clock in milliseconds, advanced explicitly so nothing depends on wall time. */
    private long now;
    private AFKTracker tracker;

    @BeforeEach
    void setUp() {
        now = 1_000_000L;
        tracker = new AFKTracker(() -> now);
    }

    private void advanceSeconds(long seconds) {
        now += seconds * 1000L;
    }

    @Test
    void playerIsNotAfkUntilMarked() {
        assertFalse(tracker.isAFK(PLAYER));
    }

    @Test
    void idleSecondsGrowWithTime() {
        tracker.recordActivity(PLAYER);
        advanceSeconds(30);
        assertEquals(30, tracker.idleSeconds(PLAYER));
    }

    @Test
    void activityResetsIdleTime() {
        tracker.recordActivity(PLAYER);
        advanceSeconds(30);
        tracker.recordActivity(PLAYER);
        assertEquals(0, tracker.idleSeconds(PLAYER));
    }

    @Test
    void unseenPlayerIsNotIdle() {
        assertEquals(0, tracker.idleSeconds(PLAYER));
    }

    @Test
    void afkDurationCountsFromWhenMarked() {
        tracker.markAFK(PLAYER);
        advanceSeconds(90);
        assertEquals(90, tracker.afkDurationSeconds(PLAYER));
    }

    @Test
    void afkDurationIsZeroWhenNotAfk() {
        assertEquals(0, tracker.afkDurationSeconds(PLAYER));
    }

    @Test
    void clearingAfkAlsoClearsDuration() {
        tracker.markAFK(PLAYER);
        advanceSeconds(90);
        tracker.clearAFK(PLAYER);

        assertFalse(tracker.isAFK(PLAYER));
        assertEquals(0, tracker.afkDurationSeconds(PLAYER));
    }

    @Test
    void clearingAfkResetsTheIdleTimerSoTheyAreNotInstantlyAfkAgain() {
        tracker.recordActivity(PLAYER);
        advanceSeconds(300);
        tracker.markAFK(PLAYER);
        advanceSeconds(300);
        tracker.clearAFK(PLAYER);

        assertEquals(0, tracker.idleSeconds(PLAYER));
    }

    @Test
    void combatExpiresAfterCooldown() {
        tracker.recordCombat(PLAYER);
        assertTrue(tracker.inCombat(PLAYER, 15_000));

        advanceSeconds(16);
        assertFalse(tracker.inCombat(PLAYER, 15_000));
    }

    @Test
    void damageExpiresAfterCooldown() {
        tracker.recordDamage(PLAYER);
        assertTrue(tracker.recentlyDamaged(PLAYER, 15_000));

        advanceSeconds(16);
        assertFalse(tracker.recentlyDamaged(PLAYER, 15_000));
    }

    /**
     * Entering AFK clears these, so that a player who was recently hit is not blocked
     * from leaving AFK again by their own stale cooldown.
     */
    @Test
    void clearingCooldownsEndsCombatAndDamageImmediately() {
        tracker.recordCombat(PLAYER);
        tracker.recordDamage(PLAYER);

        tracker.clearCooldowns(PLAYER);

        assertFalse(tracker.inCombat(PLAYER, 15_000));
        assertFalse(tracker.recentlyDamaged(PLAYER, 15_000));
    }

    @Test
    void clearingCooldownsLeavesOtherPlayersAlone() {
        tracker.recordCombat(OTHER);

        tracker.clearCooldowns(PLAYER);

        assertTrue(tracker.inCombat(OTHER, 15_000));
    }

    @Test
    void playerWithNoCombatIsNotInCombat() {
        assertFalse(tracker.inCombat(PLAYER, 15_000));
    }

    @Test
    void kickWarningIsOnlyReportedAsNewOnce() {
        assertTrue(tracker.markKickWarningShown(PLAYER));
        assertFalse(tracker.markKickWarningShown(PLAYER));
    }

    @Test
    void clearingAfkAllowsTheKickWarningToBeShownAgain() {
        tracker.markAFK(PLAYER);
        tracker.markKickWarningShown(PLAYER);
        tracker.clearAFK(PLAYER);
        tracker.markAFK(PLAYER);

        assertTrue(tracker.markKickWarningShown(PLAYER));
    }

    /**
     * The leak regression: every per-player map must be emptied on disconnect, or a
     * long-running server accumulates state for every player who has ever joined.
     */
    @Test
    void forgettingAPlayerReleasesEveryPieceOfTheirState() {
        tracker.recordActivity(PLAYER);
        tracker.markAFK(PLAYER);
        tracker.recordCombat(PLAYER);
        tracker.recordDamage(PLAYER);
        tracker.markKickWarningShown(PLAYER);

        tracker.forget(PLAYER);

        assertEquals(0, tracker.trackedPlayerCount(),
                "state left behind for a disconnected player: " + tracker.describeTrackedState());
    }

    @Test
    void forgettingOnePlayerLeavesOthersAlone() {
        tracker.markAFK(PLAYER);
        tracker.markAFK(OTHER);

        tracker.forget(PLAYER);

        assertFalse(tracker.isAFK(PLAYER));
        assertTrue(tracker.isAFK(OTHER));
    }

    @Test
    void afkPlayersAreListed() {
        tracker.markAFK(PLAYER);
        tracker.markAFK(OTHER);
        tracker.clearAFK(OTHER);

        assertEquals(java.util.Set.of(PLAYER), tracker.afkPlayers());
    }
}
