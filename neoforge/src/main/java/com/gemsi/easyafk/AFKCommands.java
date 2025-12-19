package com.gemsi.easyafk;

import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.vehicle.AbstractMinecart;
import net.minecraft.world.entity.vehicle.Boat;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Mod("easyafk")
public class AFKCommands {


    private static final Logger LOGGER = LogManager.getLogger("EasyAFK");

    // In-memory map to track AFK status
    public static final Map<UUID, Boolean> afkStatus = new HashMap<>();

    public AFKCommands() {
        NeoForge.EVENT_BUS.addListener(this::init);
    }

    private void init(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();

        event.getDispatcher().register(
                Commands.literal("afk")
                        .executes(context -> {
                            ServerPlayer player = context.getSource().getPlayerOrException();
                            UUID playerUUID = player.getUUID();
                            boolean playerAfkStatus = !getPlayerAFKStatus(playerUUID);

                            if (playerAfkStatus) {
                                String errorMessage = canEnterAFK(player);
                                if (errorMessage != null) {
                                    sendErrorMessage(player, errorMessage);
                                    return 0;
                                }
                                // Apply AFK
                                AFKPlayer.applyAFK(player);
                                LOGGER.info("{} manually entered AFK mode.", player.getName().getString());
                            } else {
                                // Player wants to leave AFK
                                AFKPlayer.removeAFK(player);
                                LOGGER.info("{} manually left AFK mode.", player.getName().getString());
                            }

                            return 1;
                        })
        );
    }

    public static String canEnterAFK(ServerPlayer player) {
        UUID playerUUID = player.getUUID();

        // Check if falling
        if (player.fallDistance > 1) {
            return Config.msgCannotAfkFalling;
        }

        // Check if jumping or has upward velocity
        if (!player.onGround() && !player.isInWater() && !player.isPassenger()) {
            return Config.msgCannotAfkJumping;
        }

        // Check combat cooldown
        if (AFKPlayer.isInCombat(playerUUID)) {
            long currentTime = System.currentTimeMillis();
            long combatCooldown = AFKListener.combatCooldown.getOrDefault(playerUUID, 0L);
            if (currentTime - combatCooldown < Config.combatCooldown) {
                return Config.msgCannotAfkCombat;
            }
        }

        // Check recent damage
        if (AFKListener.isRecentDamage(playerUUID)) {
            return Config.msgCannotAfkDamage;
        }

        // Check if riding entity (but allow chairs)
        if (player.isPassenger()) {
            Entity vehicle = player.getVehicle();
            if (vehicle != null) {
                if (vehicle instanceof LivingEntity ||
                        vehicle instanceof Boat ||
                        vehicle instanceof AbstractMinecart) {
                    return Config.msgCannotAfkRiding;
                }
            }
        }

        // Check if in dangerous location (lava, fire, etc.)
        if (player.isOnFire() || player.isInLava()) {
            return Config.msgCannotAfkDangerous;
        }

        return null;
    }

    private void sendErrorMessage(ServerPlayer player, String message) {
        Component coloredMessage = ColorParser.parseColors(message);
        player.sendSystemMessage(coloredMessage);
    }

    public static boolean getPlayerAFKStatus(UUID playerUUID) {
        return afkStatus.containsKey(playerUUID);
    }

    public static void addPlayerAFK(UUID playerUUID) {
        afkStatus.put(playerUUID, true);
    }

    public static void removeAFKStatus(UUID playerUUID) {
        afkStatus.remove(playerUUID);
    }
}