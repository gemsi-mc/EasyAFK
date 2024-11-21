package com.gemsi.easyafk;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.OutgoingChatMessage;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

import java.awt.*;
import java.util.UUID;

public class AFKPlayer {

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

    public static void applyAFK(ServerPlayer player) {

        UUID playerUUID = player.getUUID();

        BlockPos currentPos = player.blockPosition();
        applyInvulnerability(player);

        AFKCommands.addPlayerAFK(playerUUID);
        AFKListener.freezePlayer(playerUUID, currentPos);

        AFKListener.preventMovement(player);

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
