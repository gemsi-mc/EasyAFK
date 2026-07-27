package com.gemsi.easyafk;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ClientboundClearTitlesPacket;
import net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket;
import net.minecraft.network.protocol.game.ClientboundSetTitlesAnimationPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.UUID;

/**
 * Entering and leaving AFK: the protections applied, the position pinned and the
 * messages sent. Shared by every loader.
 */
public class AFKPlayer {

    private static final Logger LOGGER = LogManager.getLogger("EasyAFK");

    /** Upper bound on the water surface search, so a bad world can't hang the tick. */
    private static final int MAX_WATER_SEARCH = 256;

    public static boolean isInCombat(UUID playerUUID) {
        return AFKState.TRACKER.inCombat(playerUUID, Config.combatCooldown);
    }

    public static boolean isExemptFromAutoAFK(ServerPlayer player) {
        if (Config.exemptPlayers.contains(player.getStringUUID())) {
            return true;
        }
        return Config.minPermissionLevel > 0 && player.hasPermissions(Config.minPermissionLevel);
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

    /**
     * Puts the damage-related abilities back the way the player's game mode wants them.
     *
     * <p>Clearing {@code abilities.invulnerable} outright used to leave creative players
     * damageable after they left AFK, because creative is invulnerable by that very flag.
     * Only that flag is touched, so flight or build permissions granted by other mods
     * survive.
     */
    public static void restoreVanillaAbilities(ServerPlayer player) {
        GameType mode = player.gameMode.getGameModeForPlayer();
        player.setInvulnerable(false);
        player.getAbilities().invulnerable = mode == GameType.CREATIVE || mode == GameType.SPECTATOR;
        player.onUpdateAbilities();
    }

    public static void displayAFKTitle(ServerPlayer player) {
        player.connection.send(new ClientboundSetTitlesAnimationPacket(10, 999999, 10));
        player.connection.send(new ClientboundSetTitleTextPacket(ColorParser.parseColors(Config.msgTitleAfk)));
        player.connection.send(new ClientboundSetSubtitleTextPacket(ColorParser.parseColors(Config.msgSubtitleAfk)));
    }

    private static void clearAFKTitle(ServerPlayer player) {
        player.connection.send(new ClientboundClearTitlesPacket(true));
    }

    public static void applyAFK(ServerPlayer player) {
        UUID playerUUID = player.getUUID();

        double x = player.getX();
        double y = player.getY();
        double z = player.getZ();

        var world = player.getCommandSenderWorld();
        BlockPos feet = BlockPos.containing(x, y, z);
        boolean inWater = player.isInWater()
                || world.getBlockState(player.blockPosition()).getBlock() == Blocks.WATER
                || world.getBlockState(feet).getBlock() == Blocks.WATER
                || world.getBlockState(feet.above()).getBlock() == Blocks.WATER;

        player.setDeltaMovement(0, 0, 0);
        player.fallDistance = 0;

        if (inWater && Config.floatOnWater) {
            BlockPos surface = feet;
            int searched = 0;
            while (searched < MAX_WATER_SEARCH
                    && (world.getBlockState(surface).getBlock() == Blocks.WATER
                    || world.getBlockState(surface.above()).getBlock() == Blocks.WATER)) {
                surface = surface.above();
                searched++;
            }

            LOGGER.info("Floating {} on water at position: X = {}, Y = {}, Z = {} (searched {} blocks up)",
                    player.getName().getString(), x, surface.getY(), z, searched);

            player.teleportTo(x, surface.getY(), z);
            player.setNoGravity(true);

            x = player.getX();
            y = player.getY();
            z = player.getZ();
        } else {
            LOGGER.info("Freezing {} at current position: X = {}, Y = {}, Z = {}",
                    player.getName().getString(), x, y, z);
        }

        AFKCore.freezePosition(playerUUID, x, y, z);
        applyInvulnerability(player);

        AFKCommands.addPlayerAFK(playerUUID);
        AFKCore.clearCooldowns(playerUUID);
        AFKCore.snapshotState(player);

        TabList.push(player);
        displayAFKTitle(player);

        broadcast(player, Config.msgAfkEnter);
    }

    public static void removeAFK(ServerPlayer player) {
        UUID playerUUID = player.getUUID();

        restoreVanillaAbilities(player);
        player.setNoGravity(false);
        // Otherwise the fall they were frozen mid-way through lands on them.
        player.fallDistance = 0;

        AFKCore.resetAFKTimer(playerUUID);
        AFKCommands.removeAFKStatus(playerUUID);
        AFKCore.clearAFKSession(playerUUID);

        TabList.push(player);
        clearAFKTitle(player);

        broadcast(player, Config.msgAfkExit);
    }

    public static void afkDisallow(ServerPlayer player) {
        player.sendSystemMessage(ColorParser.parseColors(Config.msgCannotDoWhileAfk));
    }

    private static void broadcast(ServerPlayer player, String template) {
        if (!Config.broadcastAFKMessages) {
            return;
        }
        MinecraftServer server = player.getServer();
        if (server == null) {
            return;
        }
        Component message = ColorParser.parseColors(template.replace("{player}", player.getName().getString()));
        server.getPlayerList().broadcastSystemMessage(message, false);
    }
}
