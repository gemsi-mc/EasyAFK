package com.gemsi.easyafk;

import net.minecraft.core.BlockPos;
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

                            UUID playerUUID = player.getUUID();

                            boolean playerAfkStatus = !getPlayerAFKStatus(playerUUID);

                            if (playerAfkStatus) {

                                // Add player to the map with true status (AFK)
                                AFKPlayer.applyAFK(player);
                                LOGGER.info(player.getName().getString() + " is now in AFK mode.");
                            } else {
                                // Remove player from the map if they are no longer AFK
                                afkStatus.remove(playerUUID);
                                AFKPlayer.removeAFK(player);
                                LOGGER.info(player.getName().getString() + " is no longer in AFK mode.");
                            }

                            return 1;
                        })
        );
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
