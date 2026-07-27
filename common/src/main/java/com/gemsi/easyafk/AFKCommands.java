package com.gemsi.easyafk;

import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.vehicle.AbstractMinecart;
import net.minecraft.world.entity.vehicle.Boat;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.UUID;

/**
 * The {@code /afk} command and the checks that decide whether AFK may be entered at all.
 * Shared by every loader; each one only has to hand over its command dispatcher.
 */
public class AFKCommands {

    private static final Logger LOGGER = LogManager.getLogger("EasyAFK");

    /**
     * @return null if the player may go AFK, otherwise the message explaining why not
     */
    public static String canEnterAFK(ServerPlayer player) {
        UUID playerUUID = player.getUUID();

        if (player.fallDistance > 1) {
            return Config.msgCannotAfkFalling;
        }

        // Mid-air, but sitting in something or swimming is fine.
        if (!player.onGround() && !player.isInWater() && !player.isPassenger()) {
            return Config.msgCannotAfkJumping;
        }

        if (AFKPlayer.isInCombat(playerUUID)) {
            return Config.msgCannotAfkCombat;
        }

        if (AFKCore.isRecentDamage(playerUUID)) {
            return Config.msgCannotAfkDamage;
        }

        // Chairs and other static seats are allowed; anything that can carry you off is not.
        if (player.isPassenger()) {
            Entity vehicle = player.getVehicle();
            if (vehicle instanceof LivingEntity || vehicle instanceof Boat || vehicle instanceof AbstractMinecart) {
                return Config.msgCannotAfkRiding;
            }
        }

        if (player.isOnFire() || player.isInLava()) {
            return Config.msgCannotAfkDangerous;
        }

        return null;
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
                Commands.literal("afk")
                        .executes(context -> {
                            ServerPlayer player = context.getSource().getPlayerOrException();

                            if (getPlayerAFKStatus(player.getUUID())) {
                                AFKPlayer.removeAFK(player);
                                LOGGER.info("{} manually left AFK mode.", player.getName().getString());
                                return 1;
                            }

                            String errorMessage = canEnterAFK(player);
                            if (errorMessage != null) {
                                player.sendSystemMessage(ColorParser.parseColors(errorMessage));
                                return 0;
                            }

                            AFKPlayer.applyAFK(player);
                            LOGGER.info("{} manually entered AFK mode.", player.getName().getString());
                            return 1;
                        })
        );
    }

    public static boolean getPlayerAFKStatus(UUID playerUUID) {
        return AFKState.TRACKER.isAFK(playerUUID);
    }

    public static void addPlayerAFK(UUID playerUUID) {
        AFKState.TRACKER.markAFK(playerUUID);
    }

    public static void removeAFKStatus(UUID playerUUID) {
        AFKState.TRACKER.clearAFK(playerUUID);
    }

    /**
     * @return how many whole seconds the player has been AFK, or 0 if not AFK.
     */
    public static long getAFKDurationSeconds(UUID playerUUID) {
        return AFKState.TRACKER.afkDurationSeconds(playerUUID);
    }
}
