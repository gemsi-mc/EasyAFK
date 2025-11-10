package com.gemsi.easyafk;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import org.slf4j.Logger;
import net.minecraft.commands.Commands;
import net.neoforged.fml.common.Mod;
import com.mojang.brigadier.CommandDispatcher;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerPlayer;
import com.mojang.logging.LogUtils;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Mod("easyafk")
public class AFKCommands {

    private static final Logger LOGGER = LogUtils.getLogger();

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
                                // Player wants to go AFK

                                // Check if falling
                                if (player.fallDistance > 1) {
                                    sendErrorMessage(player, "You cannot go into AFK whilst falling!");
                                    return 0;
                                }

                                // Check combat cooldown
                                if (AFKPlayer.isInCombat(playerUUID)) {
                                    long currentTime = System.currentTimeMillis();
                                    long combatCooldown = AFKListener.combatCooldown.getOrDefault(playerUUID, 0L);
                                    if (currentTime - combatCooldown < Config.combatCooldown) {
                                        sendErrorMessage(player, "You cannot go into AFK whilst you are in combat!");
                                        return 0;
                                    }
                                }

                                // Check recent damage
                                if (AFKListener.isRecentDamage(playerUUID)) {
                                    sendErrorMessage(player, "You can't go AFK! Stay alert, danger is everywhere!");
                                    return 0;
                                }

                                // Check if riding entity
                                if (player.isPassenger()) {
                                    sendErrorMessage(player, "You cannot go AFK while riding an entity!");
                                    return 0;
                                }

                                // Check if in dangerous location (lava, fire, etc.)
                                if (player.isOnFire() || player.isInLava()) {
                                    sendErrorMessage(player, "You cannot go AFK in a dangerous location!");
                                    return 0;
                                }

                                // Apply AFK
                                AFKPlayer.applyAFK(player);
                                LOGGER.info("{} manually entered AFK mode.", player.getName().getString());

                            } else {
                                // Player wants to leave AFK
                                AFKPlayer.removeAFK(player);
                                player.refreshTabListName();
                                LOGGER.info("{} manually left AFK mode.", player.getName().getString());
                            }

                            return 1;
                        })
        );
    }

    private void sendErrorMessage(ServerPlayer player, String message) {
        Component coloredMessage = Component.literal(message)
                .setStyle(Style.EMPTY.withColor(TextColor.fromRgb(0xFF5050)));
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
