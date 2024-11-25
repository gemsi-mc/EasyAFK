package com.gemsi.easyafk;

import com.mojang.logging.LogUtils;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.server.ServerLifecycleHooks;
import org.slf4j.Logger;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class AFKPlayer {

    private static final Logger LOGGER = LogUtils.getLogger();
    public static final int COMBAT_THRESHOLD = 15000;

    public static boolean isInCombat(UUID playerUUID) {

        // Check if the player is in combat (last damage time was within the threshold)
        if (AFKListener.combatCooldown.containsKey(playerUUID)) {
            long lastDamageTime = AFKListener.combatCooldown.get(playerUUID);
            long currentTime = System.currentTimeMillis();

            return currentTime - lastDamageTime <= COMBAT_THRESHOLD;
        }

        return false;  // Return false if the player doesn't exist in the map
    }


    public static void applyInvulnerability(ServerPlayer player) {
        player.setInvulnerable(true);
        player.getAbilities().invulnerable = true;
        player.onUpdateAbilities();
    }

    public static void removeInvulnerability(ServerPlayer player) {
        player.setInvulnerable(false);
        player.getAbilities().invulnerable = false;
        player.onUpdateAbilities();
    }

    private static final Set<Block> unsafeBlocks = new HashSet<>();
    private static final Set<Block> liquidBlocks = new HashSet<>();
    static {
        // Add blocks to the unsafe blocks set
        unsafeBlocks.add(Blocks.AIR);
        unsafeBlocks.add(Blocks.SHORT_GRASS);
        unsafeBlocks.add(Blocks.TALL_GRASS);
        unsafeBlocks.add(Blocks.CAVE_AIR);

        // Liquid blocks
        liquidBlocks.add(Blocks.WATER);

    }

    public static void applyAFK(ServerPlayer player) {

        UUID playerUUID = player.getUUID();


        double x = player.getX();
        double z = player.getZ();
        double y = player.getY();

        // Get the world (level) the player is in
        var world = player.getCommandSenderWorld();

        // Start from the player's current Y position and move downward
        for (double yOffset = y; yOffset > 0; yOffset--) {
            BlockPos checkPos = new BlockPos((int) x, (int) yOffset, (int) z);  // Cast yOffset to int for BlockPos
            BlockState blockState = world.getBlockState(checkPos);

            if (!unsafeBlocks.contains(blockState.getBlock())) {
                // Handle water blocks specifically
                if (blockState.getBlock() == Blocks.WATER) {
                    // Start at this Y and move upwards to find the top-most water block
                    while (world.getBlockState(checkPos.above()).getBlock() == Blocks.WATER) {
                        checkPos = checkPos.above();  // Move up
                        yOffset++;
                    }
                }

                // Calculate safe coordinates above the block
                double safeY = yOffset + 1.0;
                double safeX = checkPos.getX() + (x - checkPos.getX());  // Adjust X relative to the block position
                double safeZ = checkPos.getZ() + (z - checkPos.getZ());  // Adjust Z relative to the block position

                // Round the coordinates
                double roundedX = Math.round(safeX * 1000.0) / 1000.0;
                double roundedY = Math.round(safeY * 1000.0) / 1000.0;
                int roundedYInt = (int) roundedY;
                double roundedZ = Math.round(safeZ * 1000.0) / 1000.0;

                // Log and save the position
                LOGGER.info("Safe Position: X = {}, Y = {}, Z = {}", roundedX, roundedYInt, roundedZ);
                AFKListener.freezePlayerPosition(playerUUID, roundedX, roundedYInt, roundedZ);
                break;
            }
        }


        applyInvulnerability(player);

        if (player.getBlockStateOn().is(Blocks.WATER)) {
            player.setNoGravity(true);
        }

        AFKCommands.addPlayerAFK(playerUUID);
        AFKListener.removeCombatCooldown(playerUUID);
        AFKListener.freezePlayerState(player);
        player.refreshDisplayName();
        //AFKListener.preventMovement(player);

        String playerName = player.getName().getString();
        Component serverMessage = Component.literal(playerName + " is now AFK.")
                .setStyle(Style.EMPTY.withColor(TextColor.fromRgb(0xFF5050)));
        assert ServerLifecycleHooks.getCurrentServer() != null;
        ServerLifecycleHooks.getCurrentServer().getPlayerList().broadcastSystemMessage(serverMessage, false);

    }

    public static void removeAFK(ServerPlayer player) {
        UUID playerUUID = player.getUUID();
        removeInvulnerability(player);
        AFKListener.resetAFKTimer(playerUUID);
        AFKCommands.removeAFKStatus(playerUUID);
        AFKListener.unfreezePlayer(playerUUID);
        AFKListener.frozenDataMap.remove(playerUUID);
        player.refreshDisplayName();

        player.setNoGravity(false);
        String playerName = player.getName().getString();
        Component serverMessage = Component.literal(playerName + " is no longer AFK.")
                .setStyle(Style.EMPTY.withColor(TextColor.fromRgb(0x50FF50)));

        assert ServerLifecycleHooks.getCurrentServer() != null;
        ServerLifecycleHooks.getCurrentServer().getPlayerList().broadcastSystemMessage(serverMessage, false);

    }


    public static void afkDisallow(ServerPlayer player) {
            String message = "You cannot do this while AFK!";
            Component coloredMessage = Component.literal(message)
                    .setStyle(Style.EMPTY.withColor(TextColor.fromRgb(0xFF5050)));
            player.sendSystemMessage(coloredMessage);
    }

}
