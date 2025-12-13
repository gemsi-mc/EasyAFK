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
import net.minecraft.world.entity.Entity;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.food.FoodData;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.neoforge.common.util.TriState;
import net.neoforged.neoforge.event.entity.living.LivingEvent;
import net.neoforged.neoforge.event.entity.player.ItemEntityPickupEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;

import java.util.*;

import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.ServerChatEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import static net.minecraft.world.damagesource.DamageTypes.*;

@Mod("easyafk")
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
            player.setInvulnerable(false);
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
            }
        }
    }

    @SubscribeEvent
    public void onPlayerTick(PlayerTickEvent.Post event) {
        if (event.getEntity() instanceof ServerPlayer serverPlayer) {
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
                }

                // Check for auto-kick
                if (Config.autoKickTimeout > 0) {
                    checkAutoKick(serverPlayer);
                }
            }

            // Update tab list if needed
            if (!AFKCommands.afkStatus.isEmpty()) {
                serverPlayer.refreshTabListName();
            }
        }
    }

    private void checkAutoKick(ServerPlayer player) {
        UUID playerUUID = player.getUUID();
        int afkTime = playerAFKTime.getOrDefault(playerUUID, 0);

        // Send warning if enabled and time reached
        if (Config.sendKickWarning &&
                !kickWarningShown.getOrDefault(playerUUID, false) &&
                afkTime >= (Config.autoKickTimeout - Config.kickWarningTime)) {

            Component warning = Component.literal("You will be kicked for being AFK in " +
                            Config.kickWarningTime + " seconds!")
                    .setStyle(Style.EMPTY.withColor(TextColor.fromRgb(0xFF5050)));
            player.sendSystemMessage(warning);
            kickWarningShown.put(playerUUID, true);
        }

        // Kick if time exceeded
        if (afkTime >= Config.autoKickTimeout) {
            player.connection.disconnect(Component.literal("Kicked for being AFK too long"));
            LOGGER.info("{} was kicked for being AFK for {} seconds",
                    player.getName().getString(), afkTime);
        }
    }

    private static void freezePlayer(ServerPlayer serverPlayer) {
        UUID playerUUID = serverPlayer.getUUID();
        double[] frozenPos = frozenPlayers.get(playerUUID);

        if (frozenPos != null && hasPlayerMoved(serverPlayer)) {
            // Teleport the player back to their frozen position
            serverPlayer.connection.teleport(
                    frozenPos[0], // x coordinate
                    frozenPos[1], // y coordinate
                    frozenPos[2], // z coordinate
                    serverPlayer.getYRot(),
                    serverPlayer.getXRot()
            );
            // Reset fall distance to prevent accumulation during teleportation
            serverPlayer.fallDistance = 0;
        }
    }

    private void checkAFKTime(ServerPlayer serverPlayer) {
        UUID playerUUID = serverPlayer.getUUID();
        long currentTime = System.currentTimeMillis();
        long lastTime = lastCheckTime.getOrDefault(serverPlayer, 0L);

        if (currentTime - lastTime >= 1000) {
            boolean moved = hasPlayerMoved(serverPlayer);
            boolean interacted = hasPlayerInteracted(serverPlayer);

            if (moved || interacted) {
                int oldTime = playerAFKTime.getOrDefault(playerUUID, 0);
                resetAFKTimer(playerUUID);
            } else {
                int currentAFKTime = playerAFKTime.getOrDefault(playerUUID, 0) + 1;

                playerAFKTime.put(playerUUID, currentAFKTime);

                if (currentAFKTime >= Config.afkTimeout) {
                    AFKPlayer.applyAFK(serverPlayer);
                }
            }
            lastCheckTime.put(serverPlayer, currentTime);
        }
    }

    private static boolean hasPlayerMoved(ServerPlayer player) {
        double currentX = player.getX();
        double currentY = player.getY();
        double currentZ = player.getZ();

        double lastX = player.getPersistentData().getDouble("lastX");
        double lastY = player.getPersistentData().getDouble("lastY");
        double lastZ = player.getPersistentData().getDouble("lastZ");

        // Initialize if first check
        if (lastX == 0 && lastY == 0 && lastZ == 0) {
            player.getPersistentData().putDouble("lastX", currentX);
            player.getPersistentData().putDouble("lastY", currentY);
            player.getPersistentData().putDouble("lastZ", currentZ);
            return false;
        }

        double deltaX = Math.abs(currentX - lastX);
        double deltaY = Math.abs(currentY - lastY);
        double deltaZ = Math.abs(currentZ - lastZ);

        if (deltaX > Config.movementThreshold || deltaY > Config.movementThreshold || deltaZ > Config.movementThreshold) {
            player.getPersistentData().putDouble("lastX", currentX);
            player.getPersistentData().putDouble("lastY", currentY);
            player.getPersistentData().putDouble("lastZ", currentZ);
            return true;
        }

        return false;
    }

    private static boolean hasPlayerInteracted(ServerPlayer player) {
        Item heldItem = player.getMainHandItem().getItem();

        if (heldItem instanceof BlockItem) {
            return player.isUsingItem();
        }

        return false;
    }

    public static void resetAFKTimer(UUID playerUUID) {
        int oldValue = playerAFKTime.getOrDefault(playerUUID, 0);
        playerAFKTime.put(playerUUID, 0);
        kickWarningShown.remove(playerUUID);
    }

    // ========== EVENT HANDLERS FOR AFK RESTRICTIONS ==========

    @SubscribeEvent
    public void onPlayerChat(ServerChatEvent event) {
        ServerPlayer player = event.getPlayer();
        UUID playerUUID = player.getUUID();

        boolean isPlayerAFK = AFKCommands.getPlayerAFKStatus(playerUUID);
        if (isPlayerAFK) {
            // Remove AFK when they chat
            AFKPlayer.removeAFK(player);
        } else {
            // Reset timer on chat
            resetAFKTimer(playerUUID);
        }
    }

    @SubscribeEvent
    public void onPlayerDestroyItem(BlockEvent.BreakEvent event) {
        if (event.getPlayer() instanceof ServerPlayer sPlayer) {
            UUID playerUUID = event.getPlayer().getUUID();
            boolean isPlayerAFK = AFKCommands.getPlayerAFKStatus(playerUUID);

            if (isPlayerAFK) {
                event.setCanceled(true);
                handleAFKAction(sPlayer);
            } else {
                resetAFKTimer(playerUUID);
            }
        }
    }

    @SubscribeEvent
    public void onPlayerBlockPlace(PlayerInteractEvent.RightClickBlock event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            UUID playerUUID = player.getUUID();
            boolean isPlayerAFK = AFKCommands.getPlayerAFKStatus(playerUUID);

            if (isPlayerAFK) {
                event.setCanceled(true);
                handleAFKAction(player);
            }
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onPlayerRightClickItem(PlayerInteractEvent.RightClickItem event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            UUID playerUUID = player.getUUID();
            boolean isPlayerAFK = AFKCommands.getPlayerAFKStatus(playerUUID);

            if (isPlayerAFK) {
                event.setCanceled(true);
                handleAFKAction(player);
            }
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    private void onPlayerEntityInteract(PlayerInteractEvent.EntityInteract event) {
        if (event.getEntity() instanceof ServerPlayer player) {
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
    public void onPlayerItemPickup(ItemEntityPickupEvent.Pre event) {
        if (event.getPlayer() instanceof ServerPlayer player) {
            UUID playerUUID = player.getUUID();
            boolean isPlayerAFK = AFKCommands.getPlayerAFKStatus(playerUUID);
            if (isPlayerAFK) {
                event.setCanPickup(TriState.FALSE);
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

            if (isPlayerAFK) {
                AFKPlayer.removeAFK(player);
                frozenDataMap.remove(playerUUID);
            } else {
                resetAFKTimer(playerUUID);
            }
        }
    }

    @SubscribeEvent
    public void onPlayerStartRiding(net.neoforged.neoforge.event.entity.EntityMountEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            UUID playerUUID = player.getUUID();
            boolean isPlayerAFK = AFKCommands.getPlayerAFKStatus(playerUUID);

            if (isPlayerAFK && event.isMounting()) {
                event.setCanceled(true);
                AFKPlayer.afkDisallow(player);
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
            event.setDisplayName(tablistName);
        }
    }

    @SubscribeEvent
    public void onPlayerDamagePost(LivingDamageEvent.Post event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            if (!event.getSource().is(FALL) || !Config.preventFallDamage) {
                UUID playerUUID = player.getUUID();
                resetAFKTimer(playerUUID);

                long currentTime = System.currentTimeMillis();
                ResourceKey<DamageType> damageTypeKey = event.getSource().typeHolder().getKey();
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

    @SubscribeEvent
    public void onPlayerDamagePre(LivingDamageEvent.Pre event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            UUID playerUUID = player.getUUID();
            boolean playerAfkStatus = AFKCommands.getPlayerAFKStatus(playerUUID);

            if (playerAfkStatus) {
                // Prevent all damage to AFK players
                event.setNewDamage(0);

                // If it's fall damage and config allows, prevent it
                if (event.getSource().is(FALL) && Config.preventFallDamage) {
                    event.setNewDamage(0);
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