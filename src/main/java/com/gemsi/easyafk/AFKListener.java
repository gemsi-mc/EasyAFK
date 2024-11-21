package com.gemsi.easyafk;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.neoforged.fml.common.Mod;
import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

import java.util.UUID;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import java.util.HashMap;
import java.util.Map;

import static com.gemsi.easyafk.AFKCommands.afkStatus;


@Mod("easyafk")
public class AFKListener {

    private static final Logger LOGGER = LogUtils.getLogger();

    private static final int AFK_THRESHOLD = 100;
    private static final double MOVEMENT_THRESHOLD = 0.1;

    private static final Map<UUID, Integer> playerAFKTime = new HashMap<>(); // Tracks AFK time

    private static final Map<UUID, BlockPos> frozenPlayers = new HashMap<>();


    // Toggle AFK status
    public static boolean toggleAfk(ServerPlayer player) {
        UUID playerUUID = player.getUUID();
        boolean isAfk = !afkStatus.getOrDefault(playerUUID, false);
        afkStatus.put(playerUUID, isAfk);
        return isAfk;
    }

    // Freeze the player
    public static void freezePlayer(UUID playerUUID, BlockPos position) {
        frozenPlayers.put(playerUUID, position);
    }

    // Unfreeze the player
    public static void unfreezePlayer(UUID playerUUID) {
        frozenPlayers.remove(playerUUID);
    }

    @SubscribeEvent
    public void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {

        if (event.getEntity() instanceof ServerPlayer) {
            ServerPlayer player = (ServerPlayer) event.getEntity();
            UUID playerUUID = player.getUUID();
            boolean playerAfkStatus = AFKCommands.getPlayerAFKStatus(playerUUID);
            if (playerAfkStatus) {
                AFKPlayer.removeInvulnerability(player);
                AFKCommands.removeAFKStatus(playerUUID);
            }
            ;
        }
    }

    @SubscribeEvent
    public void onPlayerTick(PlayerTickEvent.Pre event) {
        if (event.getEntity() instanceof ServerPlayer) {
            ServerPlayer serverPlayer = (ServerPlayer) event.getEntity();

            checkAFKTime(serverPlayer);

            UUID playerUUID = serverPlayer.getUUID();
            if (afkStatus.getOrDefault(playerUUID, false)) {
                BlockPos frozenPos = frozenPlayers.get(playerUUID);
                if (frozenPos != null) {
                    // Teleport the player back to their frozen position
                    serverPlayer.connection.teleport(
                            frozenPos.getX() + 0.5,
                            frozenPos.getY(),
                            frozenPos.getZ() + 0.5,
                            serverPlayer.getYRot(),
                            serverPlayer.getXRot()
                    );
                }
            }
        }
    }

    private void checkAFKTime(ServerPlayer serverPlayer) {
        UUID playerUUID = serverPlayer.getUUID();
        if (hasPlayerMoved(serverPlayer) || hasPlayerInteracted(serverPlayer)) {
            // Player has moved or interacted, reset their AFK timer
            resetAFKTimer(playerUUID);
        } else {
            int currentTime = playerAFKTime.getOrDefault(playerUUID, 0) + 1;
            playerAFKTime.put(playerUUID, currentTime);
            if (currentTime >=  AFK_THRESHOLD) {
                LOGGER.info(serverPlayer.getName().getString() + " has been AFK for " + (currentTime / 20) + " seconds.");
            }

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
    public void onPlayerDestroyItem(PlayerEvent.BreakSpeed event) {
        if (event.getEntity() instanceof ServerPlayer) {

            Player player = event.getEntity();
            UUID playerUUID = player.getUUID();

            LOGGER.info(player.getName().getString() + " is interacting with block");
            resetAFKTimer(playerUUID);
            // Additional logic here, e.g., marking the player as AFK-inactive or tracking breakage
        }
    }


    public static void preventMovement(ServerPlayer serverPlayer) {
        UUID playerUUID = serverPlayer.getUUID();
        if (afkStatus.getOrDefault(playerUUID, false)) {
            // Record the player's current position when they go AFK
            BlockPos currentPos = serverPlayer.blockPosition();
            frozenPlayers.put(playerUUID, currentPos);

            // Teleport the player back to their current position every tick to freeze them
            serverPlayer.connection.teleport(currentPos.getX() + 0.5, currentPos.getY(), currentPos.getZ() + 0.5, serverPlayer.getYRot(), serverPlayer.getXRot());
        } else {
            // Remove the player from the frozen list if they're no longer AFK
            frozenPlayers.remove(playerUUID);
        }
    }
}