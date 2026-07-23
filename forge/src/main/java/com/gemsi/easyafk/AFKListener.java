package com.gemsi.easyafk;

import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Pose;
import net.minecraft.world.food.FoodData;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.ServerChatEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityMountEvent;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.event.entity.player.*;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import static net.minecraft.world.damagesource.DamageTypes.FALL;
import static net.minecraft.world.damagesource.DamageTypes.PLAYER_ATTACK;

@Mod.EventBusSubscriber(modid = "easyafk")
public class AFKListener {

    private static final Logger LOGGER = LogManager.getLogger("EasyAFK");

    private static final Map<UUID, Integer> playerAFKTime = new HashMap<>();
    private static final Map<UUID, double[]> frozenPlayers = new HashMap<>();
    static final Map<UUID, PlayerData> frozenDataMap = new HashMap<>();
    public static final Map<UUID, Long> combatCooldown = new HashMap<>();
    public static final Map<UUID, Long> damageTimestamps = new HashMap<>();
    private final Map<ServerPlayer, Long> lastCheckTime = new HashMap<>();
    private static final Map<UUID, Boolean> kickWarningShown = new HashMap<>();
    private static final Map<UUID, java.util.concurrent.atomic.AtomicBoolean> recentlyShowedMessage = new java.util.concurrent.ConcurrentHashMap<>();

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

    private void handleAFKAction(ServerPlayer player) {
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

    @SubscribeEvent
    public void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            UUID playerUUID = player.getUUID();
            boolean playerAfkStatus = AFKCommands.getPlayerAFKStatus(playerUUID);
            resetAFKTimer(playerUUID);

            if (playerAfkStatus) {
                AFKPlayer.removeAFK(player);
                frozenDataMap.remove(playerUUID);
            }

            // Clean up all data
            lastCheckTime.remove(player);
            kickWarningShown.remove(playerUUID);
        }
    }

    @SubscribeEvent
    public void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            // Ensure player state is completely reset on login
            // This fixes issues where AFK flags persist after logout/login
            player.setNoGravity(false);
            if (!player.gameMode.isCreative()) {
                player.setInvulnerable(false);
            }
            player.getAbilities().invulnerable = false;
            player.onUpdateAbilities();
            player.fallDistance = 0;
            player.setDeltaMovement(0, player.getDeltaMovement().y, 0); // Keep Y velocity for landing

            // Clear any lingering AFK titles
            player.connection.send(new net.minecraft.network.protocol.game.ClientboundClearTitlesPacket(true));

            LOGGER.info("Reset player state for {} on login", player.getName().getString());
        }
    }

    @SubscribeEvent
    public void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            UUID playerUUID = player.getUUID();
            boolean playerAfkStatus = AFKCommands.getPlayerAFKStatus(playerUUID);

            // Remove AFK on respawn
            if (playerAfkStatus) {
                AFKPlayer.removeAFK(player);
                frozenDataMap.remove(playerUUID);
            }
        }
    }

    @SubscribeEvent
    public void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase == TickEvent.Phase.END && event.player instanceof ServerPlayer serverPlayer) {
            UUID playerUUID = serverPlayer.getUUID();
            boolean playerAfkStatus = AFKCommands.getPlayerAFKStatus(playerUUID);

            if (!playerAfkStatus) {
                // Check if player should be auto-AFK'd
                boolean isExempt = AFKPlayer.isExemptFromAutoAFK(serverPlayer);

                if (!isExempt) {
                    checkAFKTime(serverPlayer);
                }
            } else {
                // Player is AFK - maintain frozen state
                freezePlayer(serverPlayer);
                maintainFrozenState(serverPlayer);

                // Continue incrementing AFK timer for kick check
                long currentTime = System.currentTimeMillis();
                long lastTime = lastCheckTime.getOrDefault(serverPlayer, 0L);

                if (currentTime - lastTime >= 1000) {
                    int currentAFKTime = playerAFKTime.getOrDefault(playerUUID, 0) + 1;
                    playerAFKTime.put(playerUUID, currentAFKTime);
                    lastCheckTime.put(serverPlayer, currentTime);

                    // Refresh the tab list once per second so the AFK duration stays current
                    if (Config.showAFKInTab && Config.showAFKDurationInTab) {
                        serverPlayer.refreshTabListName();
                    }
                }

                // Check for auto-kick
                if (Config.autoKickTimeout > 0) {
                    checkAutoKick(serverPlayer);
                }
            }
        }
    }

    static void resetAFKTimer(UUID playerUUID) {
        playerAFKTime.put(playerUUID, 0);
    }

    private void checkAFKTime(ServerPlayer player) {
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

        double lastX = player.getPersistentData().getDouble("lastX");
        double lastY = player.getPersistentData().getDouble("lastY");
        double lastZ = player.getPersistentData().getDouble("lastZ");

        double distance = Math.sqrt(
                Math.pow(currentX - lastX, 2) +
                        Math.pow(currentY - lastY, 2) +
                        Math.pow(currentZ - lastZ, 2)
        );

        if (distance > Config.movementThreshold) {
            resetAFKTimer(playerUUID);
            player.getPersistentData().putDouble("lastX", currentX);
            player.getPersistentData().putDouble("lastY", currentY);
            player.getPersistentData().putDouble("lastZ", currentZ);
        } else if (!player.getPersistentData().contains("lastX")) {
            // Initialize position tracking
            player.getPersistentData().putDouble("lastX", currentX);
            player.getPersistentData().putDouble("lastY", currentY);
            player.getPersistentData().putDouble("lastZ", currentZ);
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

    private void checkAutoKick(ServerPlayer player) {
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

    private void freezePlayer(ServerPlayer player) {
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

    @SubscribeEvent
    public void onPlayerChat(ServerChatEvent event) {
        ServerPlayer player = event.getPlayer();
        UUID playerUUID = player.getUUID();
        boolean isPlayerAFK = AFKCommands.getPlayerAFKStatus(playerUUID);

        if (!isPlayerAFK) {
            resetAFKTimer(playerUUID);
        } else {
            AFKPlayer.removeAFK(player);
            frozenDataMap.remove(playerUUID);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            UUID playerUUID = player.getUUID();
            boolean isPlayerAFK = AFKCommands.getPlayerAFKStatus(playerUUID);

            if (!isPlayerAFK) {
                resetAFKTimer(playerUUID);
            } else {
                Item item = event.getItemStack().getItem();
                if (item instanceof BlockItem) {
                    event.setCanceled(true);
                    handleAFKAction(player);
                }
            }
        }
    }

    @SubscribeEvent
    public void onPlayerBreakBlock(BlockEvent.BreakEvent event) {
        if (event.getPlayer() instanceof ServerPlayer player) {
            UUID playerUUID = player.getUUID();
            boolean isPlayerAFK = AFKCommands.getPlayerAFKStatus(playerUUID);

            if (isPlayerAFK) {
                event.setCanceled(true);
                handleAFKAction(player);
            }
        }
    }

    @SubscribeEvent
    public void onPlayerBlockToolModification(BlockEvent.BlockToolModificationEvent event) {
        if (event.getPlayer() instanceof ServerPlayer player) {
            UUID playerUUID = player.getUUID();
            boolean isPlayerAFK = AFKCommands.getPlayerAFKStatus(playerUUID);

            if (!isPlayerAFK) {
                resetAFKTimer(playerUUID);
            } else {
                event.setCanceled(true);
                handleAFKAction(player);
            }
        }
    }

    @SubscribeEvent
    public void onPlayerAttackEntity(AttackEntityEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            UUID playerUUID = player.getUUID();
            boolean isPlayerAFK = AFKCommands.getPlayerAFKStatus(playerUUID);

            if (isPlayerAFK) {
                AFKPlayer.afkDisallow(player);
                event.setCanceled(true);
            } else {
                resetAFKTimer(playerUUID);
            }
        }
    }

    @SubscribeEvent
    public void onPlayerItemPickup(EntityItemPickupEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            UUID playerUUID = player.getUUID();
            boolean isPlayerAFK = AFKCommands.getPlayerAFKStatus(playerUUID);
            if (isPlayerAFK) {
                event.setCanceled(true);
            }
        }
    }

    @SubscribeEvent
    public void onPlayerItemCraft(PlayerEvent.ItemCraftedEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            UUID playerUUID = player.getUUID();
            boolean isPlayerAFK = AFKCommands.getPlayerAFKStatus(playerUUID);

            if (!isPlayerAFK) {
                resetAFKTimer(playerUUID);
            }
        }
    }

    @SubscribeEvent
    public void onPlayerJump(LivingEvent.LivingJumpEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            UUID playerUUID = player.getUUID();
            boolean isPlayerAFK = AFKCommands.getPlayerAFKStatus(playerUUID);

            // Ignore jump events if player is sitting or riding something
            if (player.isPassenger()) {
                return;
            }

            // Ignore if player isn't in standing pose
            if (player.getPose() != Pose.STANDING) {
                return;
            }

            if (isPlayerAFK) {
                AFKPlayer.removeAFK(player);
                frozenDataMap.remove(playerUUID);
            } else {
                resetAFKTimer(playerUUID);
            }
        }
    }

    @SubscribeEvent
    public void onPlayerStartRiding(EntityMountEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            UUID playerUUID = player.getUUID();
            boolean isPlayerAFK = AFKCommands.getPlayerAFKStatus(playerUUID);

            if (isPlayerAFK) {
                if (event.isMounting()) {
                    event.setCanceled(true);
                    AFKPlayer.afkDisallow(player);
                } else {
                    AFKPlayer.removeAFK(player);
                    frozenDataMap.remove(playerUUID);
                    LOGGER.info("{} removed from AFK due to dismounting", player.getName().getString());
                }
            }
        }
    }

    @SubscribeEvent
    public void onTabListDecorate(PlayerEvent.TabListNameFormat event) {
        if (!Config.showAFKInTab) return;

        ServerPlayer player = (ServerPlayer) event.getEntity();
        boolean isPlayerAFK = AFKCommands.getPlayerAFKStatus(player.getUUID());

        if (isPlayerAFK) {
            String playerName = player.getName().getString();

            // Parse the AFK prefix with color codes
            Component afkPrefix = ColorParser.parseColors(Config.afkPrefix);

            // Create player name component (white by default, can be customized)
            Component playerNameComponent = Component.literal(playerName)
                    .setStyle(Style.EMPTY.withBold(false).withColor(TextColor.fromRgb(Config.colorAfkPlayerName)));

            Component tablistName = afkPrefix.copy().append(playerNameComponent);

            // Append the live AFK duration, e.g. "[AFK] Steve (5m 12s)"
            if (Config.showAFKDurationInTab) {
                long seconds = AFKCommands.getAFKDurationSeconds(player.getUUID());
                String durationText = Config.afkDurationFormat.replace("{time}", AFKDuration.format(seconds));
                tablistName = tablistName.copy().append(ColorParser.parseColors(durationText));
            }

            event.setDisplayName(tablistName);
        }
    }

    @SubscribeEvent
    public void onPlayerDamagePost(LivingDamageEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && event.getAmount() > 0) {
            if (!event.getSource().is(FALL) || !Config.preventFallDamage) {
                UUID playerUUID = player.getUUID();
                resetAFKTimer(playerUUID);

                long currentTime = System.currentTimeMillis();
                Holder<DamageType> damageTypeHolder = event.getSource().typeHolder();
                ResourceKey<DamageType> damageTypeKey = damageTypeHolder.unwrapKey().orElse(null);

                if (damageTypeKey != null) {
                    boolean isCombatDamage = DamageManager.isCombatDamage(damageTypeKey);

                    if (isCombatDamage) {
                        if (event.getSource().is(PLAYER_ATTACK)) {
                            Entity attackerPlayer = event.getSource().getEntity();
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
    }

    @SubscribeEvent
    public void onPlayerDamagePre(LivingDamageEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            UUID playerUUID = player.getUUID();
            boolean playerAfkStatus = AFKCommands.getPlayerAFKStatus(playerUUID);

            if (playerAfkStatus) {
                // Prevent all damage to AFK players
                event.setAmount(0);
                event.setCanceled(true);

                // If it's fall damage and config allows, prevent it
                if (event.getSource().is(FALL) && Config.preventFallDamage) {
                    event.setAmount(0);
                    event.setCanceled(true);
                }
            }
        }
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