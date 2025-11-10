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

    public static boolean isInCombat(UUID playerUUID) {
        if (AFKListener.combatCooldown.containsKey(playerUUID)) {
            long lastDamageTime = AFKListener.combatCooldown.get(playerUUID);
            long currentTime = System.currentTimeMillis();
            return currentTime - lastDamageTime <= Config.combatCooldown;
        }
        return false;
    }

    public static boolean isExemptFromAutoAFK(ServerPlayer player) {
        // Check if player is in exempt list
        if (Config.exemptPlayers.contains(player.getStringUUID())) {
            return true;
        }

        // Check permission level
        if (Config.minPermissionLevel > 0 && player.hasPermissions(Config.minPermissionLevel)) {
            return true;
        }

        return false;
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
        // Blocks that players can pass through
        unsafeBlocks.add(Blocks.AIR);
        unsafeBlocks.add(Blocks.SHORT_GRASS);
        unsafeBlocks.add(Blocks.TALL_GRASS);
        unsafeBlocks.add(Blocks.CAVE_AIR);
        unsafeBlocks.add(Blocks.FERN);
        unsafeBlocks.add(Blocks.LARGE_FERN);
        unsafeBlocks.add(Blocks.SEAGRASS);
        unsafeBlocks.add(Blocks.TALL_SEAGRASS);
        unsafeBlocks.add(Blocks.KELP);
        unsafeBlocks.add(Blocks.KELP_PLANT);
        unsafeBlocks.add(Blocks.VINE);
        unsafeBlocks.add(Blocks.DEAD_BUSH);

        // Liquid blocks
        liquidBlocks.add(Blocks.WATER);
        liquidBlocks.add(Blocks.LAVA);
    }

    public static void applyAFK(ServerPlayer player) {
        UUID playerUUID = player.getUUID();

        double x = player.getX();
        double z = player.getZ();
        double y = player.getY();

        var world = player.getCommandSenderWorld();

        // Find safe position below player
        for (double yOffset = y; yOffset > world.getMinBuildHeight(); yOffset--) {
            BlockPos checkPos = new BlockPos((int) x, (int) yOffset, (int) z);
            BlockState blockState = world.getBlockState(checkPos);

            if (!unsafeBlocks.contains(blockState.getBlock())) {
                // Handle water blocks specifically
                if (blockState.getBlock() == Blocks.WATER && Config.floatOnWater) {
                    // Find the top-most water block
                    while (world.getBlockState(checkPos.above()).getBlock() == Blocks.WATER) {
                        checkPos = checkPos.above();
                        yOffset++;
                    }
                }

                // Calculate safe coordinates above the block
                double safeY = yOffset + 1.0;
                double safeX = checkPos.getX() + (x - checkPos.getX());
                double safeZ = checkPos.getZ() + (z - checkPos.getZ());

                // Round the coordinates
                double roundedX = Math.round(safeX * 1000.0) / 1000.0;
                double roundedY = Math.round(safeY * 1000.0) / 1000.0;
                int roundedYInt = (int) roundedY;
                double roundedZ = Math.round(safeZ * 1000.0) / 1000.0;

                LOGGER.info("Safe Position for {}: X = {}, Y = {}, Z = {}",
                        player.getName().getString(), roundedX, roundedYInt, roundedZ);
                AFKListener.freezePlayerPosition(playerUUID, roundedX, roundedYInt, roundedZ);
                break;
            }
        }

        // Apply protections
        applyInvulnerability(player);

        // Float on water if enabled
        if (Config.floatOnWater && player.getBlockStateOn().is(Blocks.WATER)) {
            player.setNoGravity(true);
        }

        // Update AFK status
        AFKCommands.addPlayerAFK(playerUUID);
        AFKListener.removeCombatCooldown(playerUUID);
        AFKListener.removeDamageCooldown(playerUUID);
        AFKListener.freezePlayerState(player);
        player.refreshDisplayName();
        player.refreshTabListName();

        // Broadcast message if enabled
        if (Config.broadcastAFKMessages) {
            String playerName = player.getName().getString();
            Component serverMessage = Component.literal(playerName + " is now AFK.")
                    .setStyle(Style.EMPTY.withColor(TextColor.fromRgb(0xFF5050)));
            assert ServerLifecycleHooks.getCurrentServer() != null;
            ServerLifecycleHooks.getCurrentServer().getPlayerList().broadcastSystemMessage(serverMessage, false);
        }
    }

    public static void removeAFK(ServerPlayer player) {
        UUID playerUUID = player.getUUID();

        // Remove protections
        removeInvulnerability(player);
        player.setNoGravity(false);

        // Clear AFK data
        AFKListener.resetAFKTimer(playerUUID);
        AFKCommands.removeAFKStatus(playerUUID);
        AFKListener.unfreezePlayer(playerUUID);
        AFKListener.frozenDataMap.remove(playerUUID);
        AFKListener.clearKickWarning(playerUUID);
        player.refreshDisplayName();
        player.refreshTabListName();

        // Broadcast message if enabled
        if (Config.broadcastAFKMessages) {
            String playerName = player.getName().getString();
            Component serverMessage = Component.literal(playerName + " is no longer AFK.")
                    .setStyle(Style.EMPTY.withColor(TextColor.fromRgb(0x50FF50)));
            assert ServerLifecycleHooks.getCurrentServer() != null;
            ServerLifecycleHooks.getCurrentServer().getPlayerList().broadcastSystemMessage(serverMessage, false);
        }
    }

    public static void afkDisallow(ServerPlayer player) {
        String message = "You cannot do this while AFK!";
        Component coloredMessage = Component.literal(message)
                .setStyle(Style.EMPTY.withColor(TextColor.fromRgb(0xFF5050)));
        player.sendSystemMessage(coloredMessage);
    }
}
