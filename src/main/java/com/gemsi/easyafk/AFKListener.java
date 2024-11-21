package com.gemsi.easyafk;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.neoforged.fml.common.Mod;
import com.mojang.logging.LogUtils;
import org.slf4j.Logger;
import net.neoforged.neoforge.event.level.BlockEvent;
import java.util.UUID;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import java.util.HashMap;
import java.util.Map;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
@Mod("easyafk")
public class AFKListener {

    private static final Logger LOGGER = LogUtils.getLogger();

    private static final int AFK_THRESHOLD = 5;
    private static final double MOVEMENT_THRESHOLD = 0.1;

    private static final Map<UUID, Integer> playerAFKTime = new HashMap<>(); // Tracks AFK time

    private final Map<ServerPlayer, Long> lastCheckTime = new HashMap<>();



    @SubscribeEvent
    public void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {

        if (event.getEntity() instanceof ServerPlayer) {
            ServerPlayer player = (ServerPlayer) event.getEntity();
            UUID playerUUID = player.getUUID();
            boolean playerAfkStatus = AFKCommands.getPlayerAFKStatus(playerUUID);
            resetAFKTimer(playerUUID);
            if (playerAfkStatus) {
                AFKPlayer.removeInvulnerability(player);
                AFKCommands.removeAFKStatus(playerUUID);

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
                    LOGGER.info(serverPlayer.getName().getString() + " has been AFK for " + (currentAFKTime) + " seconds.");
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

    private void resetAFKTimer(UUID playerUUID) {
        playerAFKTime.put(playerUUID, 0);
    }

    @SubscribeEvent
    public void onPlayerDestroyItem(BlockEvent.BreakEvent event) {
        if (event.getPlayer() instanceof ServerPlayer) {

            Player player = event.getPlayer();
            UUID playerUUID = player.getUUID();

            LOGGER.info(player.getName().getString() + " is interacting with block");
            resetAFKTimer(playerUUID);
            // Additional logic here, e.g., marking the player as AFK-inactive or tracking breakage
        }
    }

    @SubscribeEvent
    public void onPlayerBlockPlace(BlockEvent.EntityPlaceEvent event) {
        if (event.getEntity() instanceof ServerPlayer) {

            Player player = (Player) event.getEntity();
            UUID playerUUID = player.getUUID();

            LOGGER.info(player.getName().getString() + " is place a block");
            resetAFKTimer(playerUUID);
            // Additional logic here, e.g., marking the player as AFK-inactive or tracking breakage
        }
    }

    @SubscribeEvent
    public void onPlayerBlockToolModification(BlockEvent.BlockToolModificationEvent event) {
        if (event.getPlayer() instanceof ServerPlayer) {

            Player player = event.getPlayer();
            UUID playerUUID = player.getUUID();

            LOGGER.info(player.getName().getString() + " is modifying");
            resetAFKTimer(playerUUID);
            // Additional logic here, e.g., marking the player as AFK-inactive or tracking breakage
        }
    }

    @SubscribeEvent
    public void onPlayerItemCraft(PlayerEvent.ItemCraftedEvent event) {
        if (event.getEntity() instanceof ServerPlayer) {

            Player player = event.getEntity();
            UUID playerUUID = player.getUUID();

            LOGGER.info(player.getName().getString() + " is crafting");
            resetAFKTimer(playerUUID);
            // Additional logic here, e.g., marking the player as AFK-inactive or tracking breakage
        }
    }



}