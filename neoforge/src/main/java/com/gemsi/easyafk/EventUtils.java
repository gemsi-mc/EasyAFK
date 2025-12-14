package com.gemsi.easyafk;

import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.Event;
import net.neoforged.bus.api.ICancellableEvent;

import java.util.UUID;

public class EventUtils {
    /**
     * Checks if the event involves an AFK player and cancels it if true.
     *
     * @param event        The event being processed.
     * @param entityGetter A functional interface to extract the player from the event.
     */
    public static <T extends Event & ICancellableEvent> void shouldCancelForAFK(T event, EntityExtractor<T> entityGetter) {
        Object entity = entityGetter.getEntity(event);
        if (entity instanceof ServerPlayer player) {
            UUID playerUUID = player.getUUID();
            boolean isPlayerAFK = AFKCommands.getPlayerAFKStatus(playerUUID);

            if (isPlayerAFK) {
                AFKPlayer.afkDisallow(player);
                event.setCanceled(true); // This will work because T extends Cancellable
            }
        }
    }

    @FunctionalInterface
    public interface EntityExtractor<T> {
        Object getEntity(T event);
    }
}