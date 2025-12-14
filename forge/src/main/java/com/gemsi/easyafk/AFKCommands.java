package com.gemsi.easyafk;

import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Mod.EventBusSubscriber(modid = "easyafk")
public class AFKCommands {

    private static final Logger LOGGER = LogManager.getLogger("EasyAFK");

    // In-memory map to track AFK status
    public static final Map<UUID, Boolean> afkStatus = new HashMap<>();

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();

        dispatcher.register(
                Commands.literal("afk")
                        .executes(context -> {
                            ServerPlayer player = context.getSource().getPlayerOrException();
                            UUID playerUUID = player.getUUID();
                            boolean playerAfkStatus = !getPlayerAFKStatus(playerUUID);

                            if (playerAfkStatus) {
                                // Player wants to go AFK

                                // Check if falling
                                if (player.fallDistance > 1) {
                                    sendErrorMessage(player, Config.msgCannotAfkFalling);
                                    return 0;
                                }

                                // Check if jumping or has upward velocity
                                if (!player.onGround() && !player.isInWater()) {
                                    sendErrorMessage(player, Config.msgCannotAfkJumping);
                                    return 0;
                                }

                                // Check combat cooldown
                                if (AFKPlayer.isInCombat(playerUUID)) {
                                    long currentTime = System.currentTimeMillis();
                                    long combatCooldown = AFKListener.combatCooldown.getOrDefault(playerUUID, 0L);
                                    if (currentTime - combatCooldown < Config.combatCooldown) {
                                        sendErrorMessage(player, Config.msgCannotAfkCombat);
                                        return 0;
                                    }
                                }

                                // Check recent damage
                                if (AFKListener.isRecentDamage(playerUUID)) {
                                    sendErrorMessage(player, Config.msgCannotAfkDamage);
                                    return 0;
                                }

                                // Check if riding entity
                                if (player.isPassenger()) {
                                    sendErrorMessage(player, Config.msgCannotAfkRiding);
                                    return 0;
                                }

                                // Check if in dangerous location (lava, fire, etc.)
                                if (player.isOnFire() || player.isInLava()) {
                                    sendErrorMessage(player, Config.msgCannotAfkDangerous);
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

    private static void sendErrorMessage(ServerPlayer player, String message) {
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
