package com.gemsi.easyafk;

import net.minecraft.core.Holder;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.network.protocol.game.ClientboundContainerSetSlotPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodData;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.util.TriState;
import net.neoforged.neoforge.event.entity.living.LivingEvent;
import net.neoforged.neoforge.event.entity.player.ItemEntityPickupEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.neoforged.fml.common.Mod;
import com.mojang.logging.LogUtils;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import org.slf4j.Logger;
import net.neoforged.neoforge.event.level.BlockEvent;
import java.util.UUID;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import java.util.HashMap;
import net.minecraft.network.chat.Component;
import java.util.Map;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;

import static net.minecraft.world.damagesource.DamageTypes.FALL;


@Mod("easyafk")
public class AFKListener {

    private static final Logger LOGGER = LogUtils.getLogger();

    private static final int AFK_THRESHOLD = 120;
    private static final double MOVEMENT_THRESHOLD = 0.1;

    private static final double FREEZE_CHECK = 200;

    private static final Map<UUID, Integer> playerAFKTime = new HashMap<>(); // Tracks AFK time

    private static final Map<UUID, double[]> frozenPlayers = new HashMap<>();

    static final Map<UUID, PlayerData> frozenDataMap = new HashMap<>();


    public static final Map<UUID, Long> combatCooldown = new HashMap<>();



    public static void freezePlayerPosition(UUID playerUUID, double x, double y, double z) {
        double[] coords = new double[]{x, y, z};
        frozenPlayers.put(playerUUID, coords);
    }


    public static void unfreezePlayer(UUID playerUUID) {
        frozenPlayers.remove(playerUUID);
    }

    private static final Map<ServerPlayer, Long> lastFreezeCheck = new HashMap<>();

    private final Map<ServerPlayer, Long> lastCheckTime = new HashMap<>();

    public static void removeCombatCooldown(UUID playerUUID) {
        combatCooldown.remove(playerUUID);
    }

    private static class PlayerData {
        int hunger;
        float saturation;
        float health;
        Map<MobEffect, MobEffectInstance> potionEffects;
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

        }
    }

    @SubscribeEvent
    public void onPlayerTick(PlayerTickEvent.Post event) {

        if (event.getEntity() instanceof ServerPlayer serverPlayer) {
            UUID playerUUID = serverPlayer.getUUID();

            boolean playerAfkStatus = AFKCommands.getPlayerAFKStatus(playerUUID);

            if (!playerAfkStatus) {
                checkAFKTime(serverPlayer);
            }
            else {
                freezePlayer(serverPlayer);
                AFKListener.maintainFrozenState(serverPlayer);
            }

            if(!AFKCommands.afkStatus.isEmpty()) {
                serverPlayer.refreshTabListName();
            }

        }
    }

    private static void freezePlayer(ServerPlayer serverPlayer) {

        UUID playerUUID = serverPlayer.getUUID();

            double[] frozenPos = frozenPlayers.get(playerUUID);

            if (hasPlayerMoved(serverPlayer)) {
                // Teleport the player back to their frozen position
                serverPlayer.connection.teleport(
                        frozenPos[0], // x coordinate
                        frozenPos[1], // y coordinate
                        frozenPos[2], // z coordinate
                        serverPlayer.getYRot(),  // Player's Y rotation
                        serverPlayer.getXRot()   // Player's X rotation
                );
            }




    }

    private void checkAFKTime(ServerPlayer serverPlayer) {

        UUID playerUUID = serverPlayer.getUUID();

        long currentTime = System.currentTimeMillis();

        // Get the last time we checked for this player, default to 0 if not found
        long lastTime = lastCheckTime.getOrDefault(serverPlayer, 0L);

        if (currentTime - lastTime >= 1000) {
            if (hasPlayerMoved(serverPlayer) || hasPlayerInteracted(serverPlayer)) {
                // Player has moved or interacted, reset their AFK timer
                resetAFKTimer(playerUUID);
            } else {
                int currentAFKTime = playerAFKTime.getOrDefault(playerUUID, 0) + 1;
                playerAFKTime.put(playerUUID, currentAFKTime);

                if (currentAFKTime >= AFK_THRESHOLD) {
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


        if (Math.abs(currentX - lastX) > MOVEMENT_THRESHOLD || Math.abs(currentY - lastY) > MOVEMENT_THRESHOLD || Math.abs(currentZ - lastZ) > MOVEMENT_THRESHOLD) {
            // Update the player's last known position
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
        playerAFKTime.put(playerUUID, 0);
    }

    @SubscribeEvent
    public void onPlayerDestroyItem(BlockEvent.BreakEvent event) {
        if (event.getPlayer() instanceof ServerPlayer sPlayer) {

            Player player = event.getPlayer();
            UUID playerUUID = player.getUUID();

            boolean isPlayerAFK = AFKCommands.getPlayerAFKStatus(playerUUID);
            if(isPlayerAFK) {
                AFKPlayer.afkDisallow(sPlayer);
                event.setCanceled(true);
            }else {
                event.setCanceled(false);
            }
        }
    }

    @SubscribeEvent
    public void onPlayerBlockPlace(PlayerInteractEvent.RightClickBlock event) {

        if (event.getEntity() instanceof ServerPlayer player) {
            UUID playerUUID = player.getUUID();
            boolean isPlayerAFK = AFKCommands.getPlayerAFKStatus(playerUUID);

            InteractionHand hand = event.getHand();


            if(isPlayerAFK) {
                AFKPlayer.afkDisallow(player);
                // Cancel the block placement
                event.setCanceled(true);

                // Restore the item in hand
                ItemStack handStack = player.getItemInHand(hand);
                player.setItemInHand(hand, handStack);

                updateClientInventory(player,hand);

            }else {
                resetAFKTimer(playerUUID);
                event.setCanceled(false);
            }
        }
    }

    @SubscribeEvent
    private void onPlayerEntityInteract(PlayerInteractEvent.EntityInteract event) {
        EventUtils.shouldCancelForAFK(event, PlayerInteractEvent.EntityInteract::getEntity);
    }

    private void updateClientInventory(ServerPlayer player, InteractionHand hand) {
        player.connection.send(new ClientboundContainerSetSlotPacket(-2, 0, player.getInventory().selected, player.getItemInHand(hand)));
    }

    @SubscribeEvent
    public void onPlayerBlockToolModification(BlockEvent.BlockToolModificationEvent event) {
        if (event.getPlayer() instanceof ServerPlayer) {

            Player player = event.getPlayer();
            UUID playerUUID = player.getUUID();

            boolean isPlayerAFK = AFKCommands.getPlayerAFKStatus(playerUUID);
            if(!isPlayerAFK) {
                resetAFKTimer(playerUUID);
            }
            else {
                EventUtils.shouldCancelForAFK(event, BlockEvent.BlockToolModificationEvent::getPlayer);
            }
        }

    }

    @SubscribeEvent
    public void onPlayerAttackEntity(AttackEntityEvent event) {
        EventUtils.shouldCancelForAFK(event, AttackEntityEvent::getEntity);
    }

    @SubscribeEvent
    public void onPlayerItemPickup(ItemEntityPickupEvent.Pre event) {
        if (event.getPlayer() instanceof ServerPlayer player) {
            UUID playerUUID = player.getUUID();
            boolean isPlayerAFK = AFKCommands.getPlayerAFKStatus(playerUUID);
            if (isPlayerAFK) event.setCanPickup(TriState.FALSE);
        }
    }

    @SubscribeEvent
    public void onPlayerItemCraft(PlayerEvent.ItemCraftedEvent event) {
        if (event.getEntity() instanceof ServerPlayer) {
            Player player = event.getEntity();
            UUID playerUUID = player.getUUID();
            resetAFKTimer(playerUUID);

        }
    }

    @SubscribeEvent
    public void onPlayerJump(LivingEvent.LivingJumpEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            UUID playerUUID = player.getUUID();
            boolean isPlayerAFK = AFKCommands.getPlayerAFKStatus(playerUUID);

            UUID playerId = event.getEntity().getUUID();

            if(isPlayerAFK) {
                AFKPlayer.removeAFK(player);
                frozenDataMap.remove(playerId);
            }

        }
    }

    @SubscribeEvent
    public void onTabListDecorate(PlayerEvent.TabListNameFormat event) {
        ServerPlayer player = (ServerPlayer) event.getEntity();

        boolean isPlayerAFK = AFKCommands.getPlayerAFKStatus(player.getUUID());
        if (isPlayerAFK) {
            String playerName = player.getName().getString();

            Component afkPrefix = Component.literal("[AFK] ")
                    .setStyle(Style.EMPTY.withBold(false).withColor(TextColor.fromRgb(0x808080)));

            Component playerNameComponent = Component.literal(playerName)
                    .setStyle(Style.EMPTY.withBold(false).withColor(TextColor.fromRgb(0xFFFFFF)));

            Component tablistName = afkPrefix.copy().append(playerNameComponent);

            event.setDisplayName(tablistName);
        }
    }

    @SubscribeEvent
    public void onPlayerDamage(LivingDamageEvent.Post event) {
        if(event.getEntity() instanceof ServerPlayer player) {
            if (!event.getSource().is(FALL)) {
                UUID playerUUID = player.getUUID();

                resetAFKTimer(playerUUID);
                long currentTime = System.currentTimeMillis();
                combatCooldown.put(playerUUID, currentTime);
            }

        }
    }

    @SubscribeEvent
    public void onPlayerDamage(LivingDamageEvent.Pre event) {
        if(event.getEntity() instanceof ServerPlayer player) {
            UUID playerUUID = player.getUUID();
            boolean playerAfkStatus = AFKCommands.getPlayerAFKStatus(playerUUID);

            if(playerAfkStatus) {
                event.setNewDamage(0);
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

        for (MobEffectInstance effectInstance : player.getActiveEffects()) {
            Holder<MobEffect> holder = effectInstance.getEffect();
            if (holder.isBound()) {
                MobEffect mobEffect = holder.value();
                data.potionEffects.put(mobEffect, effectInstance);
            }
        }

        frozenDataMap.put(playerId, data);
    }



    private static void maintainFrozenState(ServerPlayer player) {
        UUID playerId = player.getUUID();
        if (!frozenDataMap.containsKey(playerId)) return;

        PlayerData data = frozenDataMap.get(playerId);
        FoodData foodData = player.getFoodData();

        foodData.setFoodLevel(data.hunger);
        foodData.setSaturation(data.saturation);

        player.setHealth(data.health);

        player.removeAllEffects();
        for (Map.Entry<MobEffect, MobEffectInstance> entry : data.potionEffects.entrySet()) {
            MobEffectInstance effectInstance = entry.getValue();
            player.addEffect(new MobEffectInstance(effectInstance));
        }
    }

//    @SubscribeEvent
//    public void onNameFormat(PlayerEvent.NameFormat event) {
//        ServerPlayer player = (ServerPlayer) event.getEntity();
//        String playerName = player.getName().getString();  // Get the player's original name
//        UUID playerUUID = player.getUUID();
//
//        // Check if the player is AFK (replace this with your AFK detection logic)
//        boolean isAFK = AFKCommands.getPlayerAFKStatus(playerUUID);
//
//        // If the player is AFK, prepend "[AFK]" to their name
//        if (isAFK) {
//            event.setDisplayname(Component.literal("[AFK] " + playerName));
//        } else {
//            // Otherwise, just use the player's original name
//            event.setDisplayname(Component.literal(playerName));
//        }
//    }

}