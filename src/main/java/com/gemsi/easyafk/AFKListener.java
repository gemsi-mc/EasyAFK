package com.gemsi.easyafk;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.living.LivingEvent;
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
import net.minecraft.network.chat.Component;
import java.util.Map;

@Mod("easyafk")
public class AFKListener {

    private static final Logger LOGGER = LogUtils.getLogger();

    private static final int AFK_THRESHOLD = 5;
    private static final double MOVEMENT_THRESHOLD = 0.1;

    private static final double FREEZE_CHECK = 500;

    private static final Map<UUID, Integer> playerAFKTime = new HashMap<>(); // Tracks AFK time

    private static final Map<UUID, BlockPos> frozenPlayers = new HashMap<>();



    public static void freezePlayer(UUID playerUUID, BlockPos position) {
        frozenPlayers.put(playerUUID, position);
    }


    public static void unfreezePlayer(UUID playerUUID) {
        frozenPlayers.remove(playerUUID);
    }

    private final Map<ServerPlayer, Long> lastFreezeCheck = new HashMap<>();

    private final Map<ServerPlayer, Long> lastCheckTime = new HashMap<>();
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
            }

            if(!AFKCommands.afkStatus.isEmpty()) {
                serverPlayer.refreshTabListName();
            }

        }
    }

    private void freezePlayer(ServerPlayer serverPlayer) {

        UUID playerUUID = serverPlayer.getUUID();

        long currentTime = System.currentTimeMillis();
        long lastTime = lastFreezeCheck.getOrDefault(serverPlayer, 0L);
        if (currentTime - lastTime >= FREEZE_CHECK) {
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
            lastFreezeCheck.put(serverPlayer, currentTime);
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
        if (event.getPlayer() instanceof ServerPlayer) {

            Player player = event.getPlayer();
            UUID playerUUID = player.getUUID();

            resetAFKTimer(playerUUID);
        }
    }

    @SubscribeEvent
    public void onPlayerBlockPlace(BlockEvent.EntityPlaceEvent event) {
        if (event.getEntity() instanceof ServerPlayer) {

            Player player = (Player) event.getEntity();
            UUID playerUUID = player.getUUID();

            resetAFKTimer(playerUUID);
        }
    }

    @SubscribeEvent
    public void onPlayerBlockToolModification(BlockEvent.BlockToolModificationEvent event) {
        if (event.getPlayer() instanceof ServerPlayer) {

            Player player = event.getPlayer();
            UUID playerUUID = player.getUUID();

            resetAFKTimer(playerUUID);

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
        if (event.getEntity() instanceof ServerPlayer) {
            ServerPlayer player = (ServerPlayer) event.getEntity();
            UUID playerUUID = player.getUUID();
            boolean isPlayerAFK = AFKCommands.getPlayerAFKStatus(playerUUID);

            if(isPlayerAFK) {
                AFKPlayer.removeAFK(player);
            }

        }
    }

    @SubscribeEvent
    public void onTabListDecorate(PlayerEvent.TabListNameFormat event) {
        LOGGER.info("Getting tab list");
        ServerPlayer player = (ServerPlayer) event.getEntity();

        boolean isPlayerAFK = AFKCommands.getPlayerAFKStatus(player.getUUID());
        if (isPlayerAFK) {
            String playerName = player.getName().getString();

            Component tablistName = Component.literal(playerName + " is away.")
                    .setStyle(Style.EMPTY.withColor(TextColor.fromRgb(0x808080)));
            event.setDisplayName(tablistName);
        }
    }

    public static void preventMovement(ServerPlayer serverPlayer) {
        UUID playerUUID = serverPlayer.getUUID();
        if (AFKCommands.afkStatus.getOrDefault(playerUUID, false)) {
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