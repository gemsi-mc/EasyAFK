package com.gemsi.easyafk;

import net.minecraft.core.Holder;
import net.minecraft.network.protocol.game.ClientboundClearTitlesPacket;
import net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.TickTask;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.food.FoodData;
import net.minecraft.world.item.ItemStack;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Every AFK rule EasyAFK has, in one place.
 *
 * <p>The loaders differ only in how events reach us, so each loader module keeps a small
 * {@code AFKListener} that translates its own events into calls on this class. Nothing in
 * here may touch a loader API: {@code common} is compiled against vanilla Minecraft and
 * then recompiled into each loader jar.
 *
 * <p>Every entry point here is called from the server thread, so the state maps are plain
 * {@link HashMap}s. {@link #actionMessageGuard} is the exception: it is the one map a
 * loader could reach from an event fired off-thread, so it stays concurrent.
 */
public final class AFKCore {

    private static final Logger LOGGER = LogManager.getLogger("EasyAFK");

    /** Upward velocity that counts as a jump rather than as being nudged by the world. */
    private static final double JUMP_VELOCITY = 0.1;

    /** How far a frozen player may drift before we snap them back, squared. */
    private static final double POSITION_TOLERANCE_SQR = 1.0E-6;

    private static final Map<UUID, double[]> frozenPositions = new HashMap<>();
    private static final Map<UUID, FrozenState> frozenStates = new HashMap<>();
    private static final Map<UUID, double[]> lastPositions = new HashMap<>();
    private static final Map<UUID, Boolean> wasPassengerWhenAFK = new HashMap<>();
    private static final Map<UUID, AtomicBoolean> actionMessageGuard = new ConcurrentHashMap<>();

    private AFKCore() {
    }

    // ========== lifecycle ==========

    /** Clears anything AFK left on a player, so a fresh login never inherits it. */
    public static void onPlayerJoin(ServerPlayer player) {
        AFKPlayer.restoreVanillaAbilities(player);
        player.setNoGravity(false);
        player.fallDistance = 0;
        player.setDeltaMovement(0, player.getDeltaMovement().y, 0); // keep Y velocity for landing
        player.connection.send(new ClientboundClearTitlesPacket(true));

        LOGGER.info("Reset player state for {} on login", player.getName().getString());
    }

    public static void onPlayerDisconnect(ServerPlayer player) {
        UUID id = player.getUUID();
        boolean wasAFK = AFKState.TRACKER.isAFK(id);
        resetAFKTimer(id);

        if (wasAFK) {
            AFKPlayer.removeAFK(player);
        }

        AFKState.TRACKER.forget(id);
        clearAFKSession(id);
        lastPositions.remove(id);
        actionMessageGuard.remove(id);
    }

    public static void onPlayerRespawn(ServerPlayer player) {
        if (AFKState.TRACKER.isAFK(player.getUUID())) {
            AFKPlayer.removeAFK(player);
        }
    }

    // ========== tick ==========

    /**
     * The whole AFK loop, driven once per server tick on every loader.
     *
     * <p>Iterating the player list here rather than reacting to a per-player tick event is
     * what keeps the once-a-second work (tab list, idle sampling) on one shared clock.
     */
    public static void onServerTick(MinecraftServer server) {
        boolean secondBoundary = server.getTickCount() % 20 == 0;
        List<ServerPlayer> tabRefresh = null;

        // Copied because auto-kick can drop a player out of the live list.
        for (ServerPlayer player : List.copyOf(server.getPlayerList().getPlayers())) {
            if (!AFKState.TRACKER.isAFK(player.getUUID())) {
                if (secondBoundary && !AFKPlayer.isExemptFromAutoAFK(player)) {
                    checkIdle(player);
                }
                continue;
            }

            if (leftAFK(player)) {
                continue;
            }

            // Passengers keep their seat's position, so only free players are pinned.
            if (!player.isPassenger()) {
                holdPosition(player);
            }
            maintainFrozenState(player);

            // Both the tab list duration and the kick timeout are in whole seconds, so
            // there is nothing to gain from checking them 20 times a second.
            if (secondBoundary) {
                if (Config.showAFKInTab && Config.showAFKDurationInTab) {
                    if (tabRefresh == null) {
                        tabRefresh = new ArrayList<>();
                    }
                    tabRefresh.add(player);
                }

                if (Config.autoKickTimeout > 0) {
                    checkAutoKick(player);
                }
            }
        }

        if (tabRefresh != null) {
            TabList.pushAll(server, tabRefresh);
        }
    }

    /**
     * Checks the ways a player can end their own AFK by moving.
     *
     * @return true if the player is no longer AFK and should be skipped this tick
     */
    private static boolean leftAFK(ServerPlayer player) {
        UUID id = player.getUUID();

        if (Config.exitAFKOnJump && player.getDeltaMovement().y > JUMP_VELOCITY) {
            return exitAFK(player, "jumping");
        }

        // Sitting players are not pinned, so leaving the seat is the signal instead. Sneaking
        // is caught separately because the client will not let them dismount while frozen.
        boolean seated = wasPassengerWhenAFK.computeIfAbsent(id, key -> player.isPassenger());
        if (seated) {
            if (!player.isPassenger()) {
                return exitAFK(player, "dismounting");
            }
            if (player.isShiftKeyDown()) {
                return exitAFK(player, "sneaking to dismount");
            }
        }

        return false;
    }

    private static boolean exitAFK(ServerPlayer player, String reason) {
        AFKPlayer.removeAFK(player);
        LOGGER.info("{} removed from AFK due to {}", player.getName().getString(), reason);
        return true;
    }

    private static void checkIdle(ServerPlayer player) {
        UUID id = player.getUUID();

        if (hasMoved(player) || player.isUsingItem()) {
            resetAFKTimer(id);
            return;
        }

        long idleSeconds = AFKState.TRACKER.idleSeconds(id);
        if (idleSeconds < Config.afkTimeout) {
            return;
        }

        // Falling, in combat, on fire and so on: stay out of AFK and start the clock again.
        if (AFKCommands.canEnterAFK(player) != null) {
            resetAFKTimer(id);
            return;
        }

        AFKPlayer.applyAFK(player);
        LOGGER.info("{} has been automatically marked as AFK ({}s inactive).",
                player.getName().getString(), idleSeconds);
    }

    private static boolean hasMoved(ServerPlayer player) {
        UUID id = player.getUUID();
        double[] current = {player.getX(), player.getY(), player.getZ()};
        double[] last = lastPositions.get(id);

        if (last == null) {
            lastPositions.put(id, current);
            return false;
        }

        double dx = current[0] - last[0];
        double dy = current[1] - last[1];
        double dz = current[2] - last[2];
        if (Math.sqrt(dx * dx + dy * dy + dz * dz) <= Config.movementThreshold) {
            return false;
        }

        lastPositions.put(id, current);
        return true;
    }

    private static void checkAutoKick(ServerPlayer player) {
        UUID id = player.getUUID();
        long afkSeconds = AFKState.TRACKER.idleSeconds(id);

        if (Config.sendKickWarning) {
            long untilKick = Config.autoKickTimeout - afkSeconds;
            if (untilKick <= Config.kickWarningTime && untilKick > 0
                    && AFKState.TRACKER.markKickWarningShown(id)) {
                player.sendSystemMessage(ColorParser.parseColors(
                        Config.msgKickWarning.replace("{seconds}", String.valueOf(untilKick))));
                LOGGER.info("Sent AFK kick warning to {} ({}s until kick)",
                        player.getName().getString(), untilKick);
            }
        }

        if (afkSeconds >= Config.autoKickTimeout) {
            player.connection.disconnect(ColorParser.parseColors(Config.msgKicked));
            LOGGER.info("Kicked {} for being AFK too long ({}s)", player.getName().getString(), afkSeconds);
        }
    }

    // ========== restrictions ==========

    /**
     * The single answer to "may this player do this?" behind every cancellable event.
     *
     * @return true if the caller should cancel the action
     */
    public static boolean blockIfAFK(ServerPlayer player) {
        if (!AFKState.TRACKER.isAFK(player.getUUID())) {
            resetAFKTimer(player.getUUID());
            return false;
        }
        refuseAction(player);
        return true;
    }

    /** Item pickups are dropped silently: no message, since the player did not ask for it. */
    public static boolean blockItemPickup(ServerPlayer player) {
        return AFKState.TRACKER.isAFK(player.getUUID());
    }

    public static void onChat(ServerPlayer player) {
        if (AFKState.TRACKER.isAFK(player.getUUID())) {
            AFKPlayer.removeAFK(player);
            LOGGER.info("{} removed from AFK due to chatting", player.getName().getString());
        } else {
            resetAFKTimer(player.getUUID());
        }
    }

    /** Leaving a vehicle by any route the loader can see ends AFK. */
    public static void onDismount(ServerPlayer player) {
        if (AFKState.TRACKER.isAFK(player.getUUID())) {
            exitAFK(player, "dismounting");
        }
    }

    /**
     * Tells the player off and re-syncs their inventory.
     *
     * <p>Cancelling a block break or a right click leaves the client's predicted inventory
     * out of step with the server's, which looks like items vanishing, so the held stacks
     * are pushed back. The message is guarded to one per tick because a single click can
     * raise several cancellable events.
     */
    private static void refuseAction(ServerPlayer player) {
        MinecraftServer server = player.getServer();
        if (server == null) {
            return;
        }

        AtomicBoolean shown = actionMessageGuard.computeIfAbsent(player.getUUID(), key -> new AtomicBoolean(false));
        if (shown.compareAndSet(false, true)) {
            AFKPlayer.afkDisallow(player);
            server.tell(new TickTask(server.getTickCount() + 1, () -> shown.set(false)));
        }

        server.execute(() -> {
            player.inventoryMenu.sendAllDataToRemote();

            ItemStack mainHand = player.getItemInHand(InteractionHand.MAIN_HAND);
            ItemStack offHand = player.getItemInHand(InteractionHand.OFF_HAND);

            player.connection.send(new ClientboundContainerSetSlotPacket(-2, 0, player.getInventory().selected, mainHand));
            player.connection.send(new ClientboundContainerSetSlotPacket(-2, 0, 40, offHand));
        });
    }

    // ========== damage ==========

    /**
     * @param entity the entity about to be hurt; non-players are always allowed
     * @return true if the damage should be applied
     */
    public static boolean allowDamage(Entity entity, DamageSource source) {
        if (!(entity instanceof ServerPlayer player)) {
            return true;
        }
        boolean afk = AFKState.TRACKER.isAFK(player.getUUID());
        return !AFKDamagePolicy.fromConfig().shouldCancel(afk, source.is(DamageTypes.FALL));
    }

    /**
     * Records damage that actually landed, which both wakes the player and starts the
     * combat cooldown that stops them going straight back to AFK.
     */
    public static void onDamageTaken(Entity entity, DamageSource source, float taken) {
        if (taken <= 0 || !(entity instanceof ServerPlayer player)) {
            return;
        }
        if (source.is(DamageTypes.FALL) && Config.preventFallDamage) {
            return;
        }

        UUID id = player.getUUID();
        resetAFKTimer(id);

        ResourceKey<DamageType> damageType = source.typeHolder().unwrapKey().orElse(null);
        if (damageType == null) {
            return;
        }

        if (!DamageManager.isCombatDamage(damageType)) {
            AFKState.TRACKER.recordDamage(id);
            return;
        }

        if (source.is(DamageTypes.PLAYER_ATTACK)) {
            Entity attacker = source.getEntity();
            if (attacker != null) {
                AFKState.TRACKER.recordCombat(attacker.getUUID());
            }
        }
        AFKState.TRACKER.recordCombat(id);
    }

    public static boolean isRecentDamage(UUID playerUUID) {
        return AFKState.TRACKER.recentlyDamaged(playerUUID, Config.damageCooldown);
    }

    public static void resetAFKTimer(UUID playerUUID) {
        AFKState.TRACKER.recordActivity(playerUUID);
    }

    // ========== frozen state ==========

    static void freezePosition(UUID playerUUID, double x, double y, double z) {
        frozenPositions.put(playerUUID, new double[]{x, y, z});
        lastPositions.put(playerUUID, new double[]{x, y, z});
    }

    /** Drops everything that only makes sense while a player is AFK. */
    static void clearAFKSession(UUID playerUUID) {
        frozenPositions.remove(playerUUID);
        frozenStates.remove(playerUUID);
        wasPassengerWhenAFK.remove(playerUUID);
    }

    static void clearCooldowns(UUID playerUUID) {
        AFKState.TRACKER.clearCooldowns(playerUUID);
    }

    private static void holdPosition(ServerPlayer player) {
        double[] frozen = frozenPositions.get(player.getUUID());
        if (frozen == null) {
            return;
        }

        if (player.distanceToSqr(frozen[0], frozen[1], frozen[2]) > POSITION_TOLERANCE_SQR) {
            // Goes through the connection so the client is told to snap back too.
            player.connection.teleport(frozen[0], frozen[1], frozen[2], player.getYRot(), player.getXRot());
        }
        player.fallDistance = 0;
    }

    /** Snapshots the vitals that {@code freeze*} config options hold still. */
    static void snapshotState(ServerPlayer player) {
        FoodData food = player.getFoodData();

        FrozenState state = new FrozenState();
        state.hunger = food.getFoodLevel();
        state.saturation = food.getSaturationLevel();
        state.health = player.getHealth();

        if (Config.freezePotionEffects) {
            // Copied, not referenced: the player's own instances keep ticking down, and a
            // snapshot that decays with them would freeze nothing.
            for (Map.Entry<Holder<MobEffect>, MobEffectInstance> effect : player.getActiveEffectsMap().entrySet()) {
                state.effects.put(effect.getKey(), new MobEffectInstance(effect.getValue()));
            }
        }

        frozenStates.put(player.getUUID(), state);
    }

    private static void maintainFrozenState(ServerPlayer player) {
        FrozenState state = frozenStates.get(player.getUUID());
        if (state == null) {
            return;
        }

        if (Config.freezeHunger) {
            FoodData food = player.getFoodData();
            food.setFoodLevel(state.hunger);
            food.setSaturation(state.saturation);
        }

        if (Config.freezeHealth) {
            player.setHealth(state.health);
        }

        if (Config.freezePotionEffects) {
            restoreEffects(player, state);
        }
    }

    /**
     * Brings the player's effects back in line with the snapshot.
     *
     * <p>This used to be {@code removeAllEffects()} plus a re-add of everything, every tick,
     * which sent a remove and an add packet per effect 20 times a second. Now nothing is
     * touched until it actually diverges: an effect is re-applied only once
     * {@link EffectFreezePolicy} says it has decayed, and an effect the snapshot does not
     * have is removed the tick it appears.
     */
    private static void restoreEffects(ServerPlayer player, FrozenState state) {
        for (Holder<MobEffect> active : List.copyOf(player.getActiveEffectsMap().keySet())) {
            if (!state.effects.containsKey(active)) {
                player.removeEffect(active);
            }
        }

        for (Map.Entry<Holder<MobEffect>, MobEffectInstance> entry : state.effects.entrySet()) {
            MobEffectInstance frozen = entry.getValue();
            MobEffectInstance current = player.getEffect(entry.getKey());

            // addEffect() will not weaken a stronger instance, so a level gained while AFK
            // has to be taken away before the snapshot can go back on.
            if (current != null && current.getAmplifier() != frozen.getAmplifier()) {
                player.removeEffect(entry.getKey());
                current = null;
            }

            if (current == null || EffectFreezePolicy.hasDecayed(
                    current.getDuration(), frozen.getDuration(), frozen.isInfiniteDuration())) {
                player.addEffect(new MobEffectInstance(frozen));
            }
        }
    }

    private static final class FrozenState {
        private int hunger;
        private float saturation;
        private float health;
        private final Map<Holder<MobEffect>, MobEffectInstance> effects = new HashMap<>();
    }
}
