package com.gemsi.easyafk.commands;

import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.neoforged.fml.common.Mod;
import com.mojang.brigadier.CommandDispatcher;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerPlayer;

import com.gemsi.easyafk.afkplayer.AFKPlayer;

import java.util.HashSet;
import java.util.Set;

@Mod("easyafk")
public class AFKCommands {
    public static final Set<ServerPlayer> afkPlayers = new HashSet<>();

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
                                if (afkPlayers.contains(player)) {
                                    afkPlayers.remove(player);
                                    AFKPlayer.removeInvulnerability(player);
                                    context.getSource().sendSuccess(
                                            () -> Component.literal(player.getName().getString() + ", you are no longer AFK."),
                                            false
                                    );
                                } else {
                                    afkPlayers.add(player);
                                    AFKPlayer.applyInvulnerability(player);
                                    context.getSource().sendSuccess(
                                            () -> Component.literal(player.getName().getString() + ", you are now AFK."),
                                            false
                                    );
                                }
                                return 1;
                            } else {
                                context.getSource().sendFailure(
                                        Component.literal("This command can only be used by players!")
                                );
                                return 0;
                            }
                        })
        );
    }

}
