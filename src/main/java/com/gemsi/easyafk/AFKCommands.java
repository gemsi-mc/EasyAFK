package com.gemsi.easyafk.commands;

import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.RegisterClientCommandsEvent;
import net.neoforged.neoforge.common.NeoForge;

@Mod("easyafk")
public class AFKCommands {
    public AFKCommands() {
        NeoForge.EVENT_BUS.addListener(this::init);
    }

    private void init(RegisterClientCommandsEvent event) {
        event.getDispatcher().register(
                Commands.literal("afk")
                        .executes(context -> {
                            context.getSource().sendSuccess(() -> Component.literal("Successfully put you in AFK Mode"), false);
                            return 1;
                        }));

    }
    
}