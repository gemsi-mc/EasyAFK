package com.gemsi.easyafk.afkplayer;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.neoforged.bus.api.SubscribeEvent;

import static com.gemsi.easyafk.commands.AFKCommands.afkPlayers;

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

    @SubscribeEvent
    public void onPlayerHurt(LivingEntity event) {
        if (event instanceof ServerPlayer player && !afkPlayers.contains(player)) {
            removeInvulnerability(player);
        }
    }

    // When a player leaves we want to remove them from the array
    public void onPlayerLoggedOut(ServerPlayer player) {
        afkPlayers.remove(player);
    }

    // When a player joins in, if they are not in the array then remove their Invulnerability
    public void onPlayerLogin(ServerPlayer player) {
        if(!afkPlayers.contains(player)) {
            removeInvulnerability(player);
        }
    }
}
