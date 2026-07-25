package com.gemsi.easyafk;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundClearTitlesPacket;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.server.ServerLifecycleHooks;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class AFKPlayer {

    private static final Logger LOGGER = LogManager.getLogger("EasyAFK");

    public static boolean isInCombat(UUID playerUUID) {
        return AFKState.TRACKER.inCombat(playerUUID, Config.combatCooldown);
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
        // Entity-level invulnerability short-circuits the damage events, so it has to
        // respect the same switch AFKDamagePolicy does or the config would do nothing.
        if (!Config.invulnerableWhileAFK) {
            return;
        }
        player.setInvulnerable(true);
        player.getAbilities().invulnerable = true;
        player.onUpdateAbilities();
    }

    public static void removeInvulnerability(ServerPlayer player) {
        if (!player.gameMode.isCreative()) {
            player.setInvulnerable(false);
        }
        player.getAbilities().invulnerable = false;
        player.onUpdateAbilities();
    }

    public static void displayAFKTitle(ServerPlayer player) {
        Component title = ColorParser.parseColors(Config.msgTitleAfk);
        Component subtitle = ColorParser.parseColors(Config.msgSubtitleAfk);

        player.connection.send(new ClientboundSetTitlesAnimationPacket(10, 999999, 10));

        // Send the title and subtitle
        player.connection.send(new ClientboundSetTitleTextPacket(title));
        player.connection.send(new ClientboundSetSubtitleTextPacket(subtitle));
    }

    private static void clearAFKTitle(ServerPlayer player) {
        // Clear both title and subtitle
        player.connection.send(new ClientboundClearTitlesPacket(true));
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

        // Check if player is actually in a water block (not just touching water nearby)
        BlockPos playerBlockPos = player.blockPosition();
        boolean inWaterBlock = world.getBlockState(playerBlockPos).getBlock() == Blocks.WATER;

        // Stop all motion first
        player.setDeltaMovement(0, 0, 0);
        player.fallDistance = 0;

        double safeX = x;
        double safeY = y;
        double safeZ = z;

        if (inWaterBlock && Config.floatOnWater) {
            // Player is in water - find the top surface to float them
            BlockPos checkPos = playerBlockPos;

            // Search upward to find the topmost water block
            while (world.getBlockState(checkPos).getBlock() == Blocks.WATER ||
                    world.getBlockState(checkPos.above()).getBlock() == Blocks.WATER) {
                checkPos = checkPos.above();
            }

            // Place player 1 block above the top water block (so they're floating on surface)
            safeY = checkPos.getY(); // + 1.0

            LOGGER.info("Floating {} on water at position: X = {}, Y = {}, Z = {}",
                    player.getName().getString(), safeX, safeY, safeZ);

            // Teleport player to surface
            player.teleportTo(safeX, safeY, safeZ);
            player.setNoGravity(true);

            // Update to actual position after teleport
            safeX = player.getX();
            safeY = player.getY();
            safeZ = player.getZ();
        } else {
            // Not in water - freeze at exact current position after motion stop
            LOGGER.info("Freezing {} at current position: X = {}, Y = {}, Z = {}",
                    player.getName().getString(), safeX, safeY, safeZ);
        }

        // Initialise last position tracking to frozen position to prevent jittering
        player.getPersistentData().putDouble("lastX", safeX);
        player.getPersistentData().putDouble("lastY", safeY);
        player.getPersistentData().putDouble("lastZ", safeZ);

        AFKListener.freezePlayerPosition(playerUUID, safeX, safeY, safeZ);

        // Apply protections
        applyInvulnerability(player);

        // Update AFK status
        AFKCommands.addPlayerAFK(playerUUID);
        AFKListener.removeCombatCooldown(playerUUID);
        AFKListener.removeDamageCooldown(playerUUID);
        AFKListener.freezePlayerState(player);
        player.refreshDisplayName();
        player.refreshTabListName();

        // Display AFK title on player's screen
        displayAFKTitle(player);

        // Broadcast message if enabled
        if (Config.broadcastAFKMessages) {
            String playerName = player.getName().getString();
            String message = Config.msgAfkEnter.replace("{player}", playerName);
            Component serverMessage = ColorParser.parseColors(message);
            assert ServerLifecycleHooks.getCurrentServer() != null;
            ServerLifecycleHooks.getCurrentServer().getPlayerList().broadcastSystemMessage(serverMessage, false);
        }
    }

    public static void removeAFK(ServerPlayer player) {
        UUID playerUUID = player.getUUID();

        // Remove protections
        removeInvulnerability(player);
        player.setNoGravity(false);

        // Reset fall distance to prevent fall damage when exiting AFK
        player.fallDistance = 0;

        // Clear AFK data
        AFKListener.resetAFKTimer(playerUUID);
        AFKCommands.removeAFKStatus(playerUUID);
        AFKListener.unfreezePlayer(playerUUID);
        AFKListener.frozenDataMap.remove(playerUUID);
        player.refreshDisplayName();
        player.refreshTabListName();

        // Clear AFK title from player's screen
        clearAFKTitle(player);

        // Broadcast message if enabled
        if (Config.broadcastAFKMessages) {
            String playerName = player.getName().getString();
            String message = Config.msgAfkExit.replace("{player}", playerName);
            Component serverMessage = ColorParser.parseColors(message);
            assert ServerLifecycleHooks.getCurrentServer() != null;
            ServerLifecycleHooks.getCurrentServer().getPlayerList().broadcastSystemMessage(serverMessage, false);
        }
    }

    public static void afkDisallow(ServerPlayer player) {
        Component coloredMessage = ColorParser.parseColors(Config.msgCannotDoWhileAfk);
        player.sendSystemMessage(coloredMessage);
    }
}