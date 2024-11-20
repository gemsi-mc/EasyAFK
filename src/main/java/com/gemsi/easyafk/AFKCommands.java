package com.gemsi.easyafk.commands;

import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.neoforged.fml.common.Mod;
import com.mojang.brigadier.CommandDispatcher;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerPlayer;

@Mod("easyafk")
public class AFKCommands {
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
                                String playerName = player.getName().getString();
                                context.getSource().sendSuccess(
                                        () -> Component.literal(playerName + ", you are now in AFK mode."),
                                        false
                                );
                                return 1; // Return success code
                            } else {
                                // Handle case where no player is associated (e.g., console execution)
                                context.getSource().sendFailure(
                                        Component.literal("This command can only be used by players!")
                                );
                                return 0; // Return failure code
                            }
                        }));

    }
    
}