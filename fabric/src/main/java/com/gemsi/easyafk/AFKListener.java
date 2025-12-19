package com.gemsi.easyafk;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.player.*;
import net.fabricmc.fabric.api.message.v1.ServerMessageEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.food.FoodData;
import net.minecraft.world.item.ItemStack;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import static net.minecraft.world.damagesource.DamageTypes.FALL;
import static net.minecraft.world.damagesource.DamageTypes.PLAYER_ATTACK;

public class AFKListener {

    private static final Logger LOGGER = LogManager.getLogger("EasyAFK");

    private static final Map<UUID, Integer> playerAFKTime = new HashMap<>();
    private static final Map<UUID, double[]> frozenPlayers = new HashMap<>();
    static final Map<UUID, PlayerData> frozenDataMap = new HashMap<>();
    public static final Map<UUID, Long> combatCooldown = new HashMap<>();
    public static final Map<UUID, Long> damageTimestamps = new HashMap<>();
    private static final Map<ServerPlayer, Long> lastCheckTime = new HashMap<>();
    private static final Map<UUID, Boolean> kickWarningShown = new HashMap<>();
    private static final Map<UUID, java.util.concurrent.atomic.AtomicBoolean> recentlyShowedMessage = new java.util.concurrent.ConcurrentHashMap<>();
    private static final Map<UUID, Boolean> wasPassengerWhenAfk = new HashMap<>();


    // Store last positions since Fabric doesn't have getPersistentData()
    private static final Map<UUID, double[]> lastPositions = new HashMap<>();

    public static boolean isRecentDamage(UUID playerUUID) {
        if (damageTimestamps.containsKey(playerUUID)) {
            long lastDamageTime = damageTimestamps.get(playerUUID);
            long currentTime = System.currentTimeMillis();
            return currentTime - lastDamageTime <= Config.damageCooldown;
        }
        return false;
    }

    public static void freezePlayerPosition(UUID playerUUID, double x, double y, double z) {
        double[] coords = new double[]{x, y, z};
        frozenPlayers.put(playerUUID, coords);
    }

    public static void unfreezePlayer(UUID playerUUID) {
        frozenPlayers.remove(playerUUID);
    }

    public static void removeCombatCooldown(UUID playerUUID) {
        combatCooldown.remove(playerUUID);
    }

    public static void removeDamageCooldown(UUID playerUUID) {
        damageTimestamps.remove(playerUUID);
    }

    public static void clearKickWarning(UUID playerUUID) {
        kickWarningShown.remove(playerUUID);
    }

    private static class PlayerData {
        int hunger;
        float saturation;
        float health;
        Map<MobEffect, MobEffectInstance> potionEffects;
    }

    private static void handleAFKAction(ServerPlayer player) {
        UUID playerUUID = player.getUUID();

        // Get or create AtomicBoolean for this player
        java.util.concurrent.atomic.AtomicBoolean flag = recentlyShowedMessage.computeIfAbsent(
                playerUUID,
                k -> new java.util.concurrent.atomic.AtomicBoolean(false)
        );

        // Only show message if we can successfully change false to true (atomic operation)
        if (flag.compareAndSet(false, true)) {
            AFKPlayer.afkDisallow(player);

            // Schedule flag reset after 1 tick (50ms)
            Objects.requireNonNull(player.getServer()).tell(new net.minecraft.server.TickTask(
                    player.getServer().getTickCount() + 1,
                    () -> flag.set(false)
            ));
        }

        // Always sync inventory to prevent items from disappearing
        Objects.requireNonNull(player.getServer()).execute(() -> {
            player.inventoryMenu.sendAllDataToRemote();

            ItemStack mainHandStack = player.getItemInHand(InteractionHand.MAIN_HAND);
            ItemStack offHandStack = player.getItemInHand(InteractionHand.OFF_HAND);

            player.connection.send(new ClientboundContainerSetSlotPacket(-2, 0, player.getInventory().selected, mainHandStack));
            player.connection.send(new ClientboundContainerSetSlotPacket(-2, 0, 40, offHandStack));
        });
    }

    public static void register() {
        // Player disconnect
        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            ServerPlayer player = handler.getPlayer();
            UUID playerUUID = player.getUUID();
            boolean playerAfkStatus = AFKCommands.getPlayerAFKStatus(playerUUID);
            resetAFKTimer(playerUUID);

            if (playerAfkStatus) {
                AFKPlayer.removeAFK(player);
            }

            // Clean up all data
            lastCheckTime.remove(player);
            kickWarningShown.remove(playerUUID);
            lastPositions.remove(playerUUID);
            wasPassengerWhenAfk.remove(playerUUID);
            frozenPlayers.remove(playerUUID);
            frozenDataMap.remove(playerUUID);
            recentlyShowedMessage.remove(playerUUID);
        });

        // Player join
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            ServerPlayer player = handler.getPlayer();
            // Ensure player state is completely reset on login
            player.setNoGravity(false);
            if (!player.gameMode.isCreative()) {
                player.setInvulnerable(false);
            }
            player.getAbilities().invulnerable = false;
            player.onUpdateAbilities();
            player.fallDistance = 0;
            player.setDeltaMovement(0, player.getDeltaMovement().y, 0);

            // Clear any lingering AFK titles
            player.connection.send(new net.minecraft.network.protocol.game.ClientboundClearTitlesPacket(true));

            LOGGER.info("Reset player state for {} on login", player.getName().getString());
        });

        // Player respawn
        ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) -> {
            UUID playerUUID = newPlayer.getUUID();
            boolean playerAfkStatus = AFKCommands.getPlayerAFKStatus(playerUUID);

            if (playerAfkStatus) {
                AFKPlayer.removeAFK(newPlayer);
            }
        });

        // Player tick
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            for (ServerPlayer serverPlayer : server.getPlayerList().getPlayers()) {
                UUID playerUUID = serverPlayer.getUUID();
                boolean playerAfkStatus = AFKCommands.getPlayerAFKStatus(playerUUID);

                if (!playerAfkStatus) {
                    boolean isExempt = AFKPlayer.isExemptFromAutoAFK(serverPlayer);
                    if (!isExempt) {
                        checkAFKTime(serverPlayer);
                    }
                } else {
                    // Check if player is trying to jump BEFORE freezing them
                    boolean tryingToJump = serverPlayer.getDeltaMovement().y > 0.1;

                    if (tryingToJump) {
                        // Player is jumping - remove AFK immediately
                        AFKPlayer.removeAFK(serverPlayer);
                        frozenDataMap.remove(playerUUID);
                        wasPassengerWhenAfk.remove(playerUUID);
                        LOGGER.info("{} removed from AFK due to jumping", serverPlayer.getName().getString());
                        continue; // Skip the rest - don't freeze this tick
                    }

                    // Track if they were sitting when AFK started
                    if (!wasPassengerWhenAfk.containsKey(playerUUID)) {
                        wasPassengerWhenAfk.put(playerUUID, serverPlayer.isPassenger());
                    }

                    // Check if player is sneaking while on a chair (trying to dismount)
                    boolean wasPassenger = wasPassengerWhenAfk.getOrDefault(playerUUID, false);
                    if (wasPassenger && serverPlayer.isPassenger() && serverPlayer.isShiftKeyDown()) {
                        // They're sneaking while on a chair - remove AFK so they can dismount
                        AFKPlayer.removeAFK(serverPlayer);
                        frozenDataMap.remove(playerUUID);
                        wasPassengerWhenAfk.remove(playerUUID);
                        LOGGER.info("{} removed from AFK due to sneaking on chair", serverPlayer.getName().getString());
                        continue;
                    }

                    // Check if player dismounted from a chair (backup check)
                    if (wasPassenger && !serverPlayer.isPassenger()) {
                        // They were sitting and now they're not - they dismounted
                        AFKPlayer.removeAFK(serverPlayer);
                        frozenDataMap.remove(playerUUID);
                        wasPassengerWhenAfk.remove(playerUUID);
                        LOGGER.info("{} removed from AFK due to dismounting", serverPlayer.getName().getString());
                        continue;
                    }

                    // Player is AFK - maintain frozen state
                    // Only freeze position if they're NOT on a chair (passengers)
                    if (!serverPlayer.isPassenger()) {
                        freezePlayer(serverPlayer);
                    }
                    maintainFrozenState(serverPlayer);

                    // Continue incrementing AFK timer for kick check
                    long currentTime = System.currentTimeMillis();
                    long lastTime = lastCheckTime.getOrDefault(serverPlayer, 0L);

                    if (currentTime - lastTime >= 1000) {
                        int currentAFKTime = playerAFKTime.getOrDefault(playerUUID, 0) + 1;
                        playerAFKTime.put(playerUUID, currentAFKTime);
                        lastCheckTime.put(serverPlayer, currentTime);
                    }

                    // Check for auto-kick
                    if (Config.autoKickTimeout > 0) {
                        checkAutoKick(serverPlayer);
                    }
                }
            }
        });

        // Chat messages
        ServerMessageEvents.CHAT_MESSAGE.register((message, sender, params) -> {
            UUID playerUUID = sender.getUUID();
            boolean isPlayerAFK = AFKCommands.getPlayerAFKStatus(playerUUID);

            if (!isPlayerAFK) {
                resetAFKTimer(playerUUID);
            } else {
                // Remove AFK when player sends a chat message
                AFKPlayer.removeAFK(sender);
                frozenDataMap.remove(playerUUID);
            }
        });

        // Block break
        PlayerBlockBreakEvents.BEFORE.register((world, player, pos, state, blockEntity) -> {
            if (player instanceof ServerPlayer serverPlayer) {
                UUID playerUUID = serverPlayer.getUUID();
                boolean isPlayerAFK = AFKCommands.getPlayerAFKStatus(playerUUID);

                if (isPlayerAFK) {
                    handleAFKAction(serverPlayer);
                    return false;
                }
            }
            return true;
        });

        // Attack entity
        AttackEntityCallback.EVENT.register((player, world, hand, entity, hitResult) -> {
            if (player instanceof ServerPlayer serverPlayer) {
                UUID playerUUID = serverPlayer.getUUID();
                boolean isPlayerAFK = AFKCommands.getPlayerAFKStatus(playerUUID);

                if (isPlayerAFK) {
                    handleAFKAction(serverPlayer);
                    return InteractionResult.FAIL;
                } else {
                    resetAFKTimer(playerUUID);
                }
            }
            return InteractionResult.PASS;
        });

        // Use block
        UseBlockCallback.EVENT.register((player, world, hand, hitResult) -> {
            if (player instanceof ServerPlayer serverPlayer) {
                UUID playerUUID = serverPlayer.getUUID();
                boolean isPlayerAFK = AFKCommands.getPlayerAFKStatus(playerUUID);

                if (!isPlayerAFK) {
                    resetAFKTimer(playerUUID);
                } else {
                    handleAFKAction(serverPlayer);
                    return InteractionResult.FAIL;
                }
            }
            return InteractionResult.PASS;
        });

        // Damage events
        ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) -> {
            if (entity instanceof ServerPlayer player) {
                UUID playerUUID = player.getUUID();
                boolean playerAfkStatus = AFKCommands.getPlayerAFKStatus(playerUUID);

                if (playerAfkStatus) {
                    // Prevent all damage to AFK players
                    if (source.is(FALL) && Config.preventFallDamage) {
                        return false;
                    }
                    return false;
                }
            }
            return true;
        });

        ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, baseDamage, takenDamage, blocked) -> {
            if (entity instanceof ServerPlayer player && takenDamage > 0) {
                if (!source.is(FALL) || !Config.preventFallDamage) {
                    UUID playerUUID = player.getUUID();
                    resetAFKTimer(playerUUID);

                    long currentTime = System.currentTimeMillis();
                    ResourceKey<DamageType> damageTypeKey = source.typeHolder().unwrapKey().orElse(null);

                    if (damageTypeKey != null) {
                        boolean isCombatDamage = DamageManager.isCombatDamage(damageTypeKey);

                        if (isCombatDamage) {
                            if (source.is(PLAYER_ATTACK)) {
                                Entity attackerPlayer = source.getEntity();
                                if (attackerPlayer != null) {
                                    UUID attackerUUID = attackerPlayer.getUUID();
                                    combatCooldown.put(attackerUUID, currentTime);
                                }
                            }
                            combatCooldown.put(playerUUID, currentTime);
                        } else {
                            damageTimestamps.put(playerUUID, currentTime);
                        }
                    }
                }
            }
        });

        // Prevent mounting entities while AFK
        UseEntityCallback.EVENT.register((player, world, hand, entity, hitResult) -> {
            if (player instanceof ServerPlayer serverPlayer) {
                UUID playerUUID = serverPlayer.getUUID();
                boolean isPlayerAFK = AFKCommands.getPlayerAFKStatus(playerUUID);

                if (isPlayerAFK) {
                    // Block mounting any entity while AFK
                    handleAFKAction(serverPlayer);
                    return InteractionResult.FAIL;
                } else {
                    resetAFKTimer(playerUUID);
                }
            }
            return InteractionResult.PASS;
        });
    }

    static void resetAFKTimer(UUID playerUUID) {
        playerAFKTime.put(playerUUID, 0);
    }

    private static void checkAFKTime(ServerPlayer player) {
        UUID playerUUID = player.getUUID();

        // Calculate time-based AFK check
        long currentTime = System.currentTimeMillis();
        long lastTime = lastCheckTime.getOrDefault(player, 0L);

        if (currentTime - lastTime >= 1000) {
            int currentAFKTime = playerAFKTime.getOrDefault(playerUUID, 0) + 1;
            playerAFKTime.put(playerUUID, currentAFKTime);
            lastCheckTime.put(player, currentTime);
        }

        // Calculate distance-based movement check
        double currentX = player.getX();
        double currentY = player.getY();
        double currentZ = player.getZ();

        double[] lastPos = lastPositions.get(playerUUID);
        double lastX = lastPos != null ? lastPos[0] : currentX;
        double lastY = lastPos != null ? lastPos[1] : currentY;
        double lastZ = lastPos != null ? lastPos[2] : currentZ;

        double distance = Math.sqrt(
                Math.pow(currentX - lastX, 2) +
                        Math.pow(currentY - lastY, 2) +
                        Math.pow(currentZ - lastZ, 2)
        );

        if (distance > Config.movementThreshold) {
            resetAFKTimer(playerUUID);
            lastPositions.put(playerUUID, new double[]{currentX, currentY, currentZ});
        } else if (lastPos == null) {
            // Initialise position tracking
            lastPositions.put(playerUUID, new double[]{currentX, currentY, currentZ});
        }

        // Auto-AFK if timeout exceeded
        int afkTime = playerAFKTime.getOrDefault(playerUUID, 0);
        if (afkTime >= Config.afkTimeout) {
            // Validate if player can enter AFK before applying it
            String errorMessage = AFKCommands.canEnterAFK(player);
            if (errorMessage != null) {
                resetAFKTimer(playerUUID);
                return;
            }

            AFKPlayer.applyAFK(player);
            LOGGER.info("{} has been automatically marked as AFK ({}s inactive).", player.getName().getString(), afkTime);
        }
    }

    private static void checkAutoKick(ServerPlayer player) {
        UUID playerUUID = player.getUUID();
        int afkTime = playerAFKTime.getOrDefault(playerUUID, 0);

        // Check if warning should be sent
        if (Config.sendKickWarning) {
            int timeUntilKick = Config.autoKickTimeout - afkTime;
            if (timeUntilKick <= Config.kickWarningTime && timeUntilKick > 0) {
                boolean hasShownWarning = kickWarningShown.getOrDefault(playerUUID, false);
                if (!hasShownWarning) {
                    String warningMessage = "&eYou will be kicked for being AFK in " + timeUntilKick + " seconds!";
                    Component coloredWarning = ColorParser.parseColors(warningMessage);
                    player.sendSystemMessage(coloredWarning);
                    kickWarningShown.put(playerUUID, true);
                    LOGGER.info("Sent AFK kick warning to {} ({}s until kick)", player.getName().getString(), timeUntilKick);
                }
            }
        }

        // Kick if timeout exceeded
        if (afkTime >= Config.autoKickTimeout) {
            String kickMessage = "&cYou have been kicked for being AFK too long.";
            Component coloredKickMessage = ColorParser.parseColors(kickMessage);
            player.connection.disconnect(coloredKickMessage);
            LOGGER.info("Kicked {} for being AFK too long ({}s)", player.getName().getString(), afkTime);
        }
    }

    private static void freezePlayer(ServerPlayer player) {
        UUID playerUUID = player.getUUID();

        if (!frozenPlayers.containsKey(playerUUID)) {
            return;
        }

        double[] frozenCoords = frozenPlayers.get(playerUUID);
        double frozenX = frozenCoords[0];
        double frozenY = frozenCoords[1];
        double frozenZ = frozenCoords[2];

        // Teleport to frozen position if they've moved
        if (player.getX() != frozenX || player.getY() != frozenY || player.getZ() != frozenZ) {
            player.teleportTo(frozenX, frozenY, frozenZ);
        }

        // Stop all movement
        player.fallDistance = 0;
    }

    static void freezePlayerState(ServerPlayer player) {
        UUID playerId = player.getUUID();
        FoodData foodData = player.getFoodData();

        PlayerData data = new PlayerData();
        data.hunger = foodData.getFoodLevel();
        data.saturation = foodData.getSaturationLevel();
        data.health = player.getHealth();
        data.potionEffects = new HashMap<>();

        if (Config.freezePotionEffects) {
            for (MobEffectInstance effectInstance : player.getActiveEffects()) {
                Holder<MobEffect> holder = effectInstance.getEffect();
                if (holder.isBound()) {
                    MobEffect mobEffect = holder.value();
                    data.potionEffects.put(mobEffect, effectInstance);
                }
            }
        }

        frozenDataMap.put(playerId, data);
    }

    private static void maintainFrozenState(ServerPlayer player) {
        UUID playerId = player.getUUID();
        if (!frozenDataMap.containsKey(playerId)) return;

        PlayerData data = frozenDataMap.get(playerId);
        FoodData foodData = player.getFoodData();

        if (Config.freezeHunger) {
            foodData.setFoodLevel(data.hunger);
            foodData.setSaturation(data.saturation);
        }

        if (Config.freezeHealth) {
            player.setHealth(data.health);
        }

        if (Config.freezePotionEffects) {
            player.removeAllEffects();
            for (Map.Entry<MobEffect, MobEffectInstance> entry : data.potionEffects.entrySet()) {
                MobEffectInstance effectInstance = entry.getValue();
                player.addEffect(new MobEffectInstance(effectInstance));
            }
        }
    }
}