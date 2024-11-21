package com.gemsi.easyafk;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.OutgoingChatMessage;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.server.level.ServerPlayer;

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

        String message = "You are now in AFK mode.";

        Component coloredMessage = Component.literal(message)
                .setStyle(Style.EMPTY.withColor(TextColor.fromRgb(0x00FF00))); // Green for AFK, Red for not AFK


        player.sendSystemMessage(coloredMessage);

    }

    public static void removeAFK(ServerPlayer player) {
        UUID playerUUID = player.getUUID();
        removeInvulnerability(player);

        AFKCommands.removeAFKStatus(playerUUID);

        String message = "You are no longer in AFK mode.";
        AFKListener.unfreezePlayer(playerUUID);
        Component coloredMessage = Component.literal(message)
                .setStyle(Style.EMPTY.withColor(TextColor.fromRgb(0xFF0000))); // Green for AFK, Red for not AFK


        player.sendSystemMessage(coloredMessage);

    }

}
