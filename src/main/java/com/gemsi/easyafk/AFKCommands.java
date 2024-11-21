package com.gemsi.easyafk;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import org.slf4j.Logger;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.neoforged.fml.common.Mod;
import com.mojang.brigadier.CommandDispatcher;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerPlayer;
import com.mojang.logging.LogUtils;

import com.gemsi.easyafk.AFKListener;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

@Mod("easyafk")
public class AFKCommands {

    private static final Logger LOGGER = LogUtils.getLogger();

    // In-memory map to track AFK status
    public static final Map<UUID, Boolean> afkStatus = new HashMap<>();

    // Custom tag for persistent storage
    private static final String AFK_TAG = "easyafk:is_afk";


    public AFKCommands() {
        NeoForge.EVENT_BUS.addListener(this::init);
    }

    private void init(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();

        event.getDispatcher().register(
                Commands.literal("afk")
                        .executes(context -> {
                            ServerPlayer player = context.getSource().getPlayerOrException();

                            if (player != null) {
                                // Send a message with the player's name
                                UUID playerUUID = player.getUUID();

                                boolean playerAfkStatus = !getPlayerAFKStatus(playerUUID);

                                if (playerAfkStatus) {
                                    BlockPos currentPos = player.blockPosition();
                                    // Add player to the map with true status (AFK)
                                    AFKListener.freezePlayer(playerUUID, currentPos);
                                    afkStatus.put(playerUUID, true);
                                    AFKPlayer.applyInvulnerability(player);
                                    LOGGER.info(player.getName().getString() + " is now in AFK mode.");

                                } else {
                                    AFKListener.unfreezePlayer(playerUUID);
                                    // Remove player from the map if they are no longer AFK
                                    afkStatus.remove(playerUUID);
                                    AFKPlayer.removeInvulnerability(player);
                                    LOGGER.info(player.getName().getString() + " is no longer in AFK mode.");

                                }

                                // Save to persistent data (optional)
                                saveAfkStatus(player, playerAfkStatus);

                                String playerName = player.getName().getString();

                                String message = playerAfkStatus
                                        ? "You are now in AFK mode."
                                        : "You are no longer in AFK mode.";

                                // Add color to the message based on AFK status
                                Component coloredMessage = Component.literal(message)
                                        .setStyle(Style.EMPTY.withColor(playerAfkStatus ? TextColor.fromRgb(0x00FF00) : TextColor.fromRgb(0xFF0000))); // Green for AFK, Red for not AFK

                                context.getSource().sendSuccess(
                                        () -> coloredMessage,
                                        false
                                );

                                return 1;
                            } else {
                                context.getSource().sendFailure(
                                        Component.literal("This command can only be used by players!")
                                                .setStyle(Style.EMPTY.withColor(TextColor.fromRgb(0xFF0000))) // Red color for the error message
                                );
                                return 0;
                            }
                        })
        );
    }


    public static boolean getPlayerAFKStatus(UUID playerUUID) {
        return afkStatus.containsKey(playerUUID);
    }

    /**
     * Save AFK status to persistent data.
     */
    private void saveAfkStatus(ServerPlayer player, boolean isAfk) {
        CompoundTag playerData = player.getPersistentData();
        CompoundTag afkData = playerData.getCompound(player.getUUID().toString());

        afkData.putBoolean(AFK_TAG.toString(), isAfk);
        playerData.put(player.getUUID().toString(), afkData);
    }

    /**
     * Load AFK status from persistent data (if needed).
     */
    public static boolean loadAfkStatus(ServerPlayer player) {
        CompoundTag playerData = player.getPersistentData();
        CompoundTag afkData = playerData.getCompound(player.getUUID().toString());
        return afkData.getBoolean(AFK_TAG.toString());
    }

    public static void removeAFKStatus(UUID playerUUID) {
        afkStatus.remove(playerUUID);
    }
    
}
