package com.gemsi.easyafk;

import net.fabricmc.fabric.api.entity.event.v1.ServerLivingEntityEvents;
import net.fabricmc.fabric.api.entity.event.v1.ServerPlayerEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.event.player.AttackEntityCallback;
import net.fabricmc.fabric.api.event.player.PlayerBlockBreakEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.fabricmc.fabric.api.message.v1.ServerMessageEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;

/**
 * Wires Fabric's events onto {@link AFKCore}. All of the AFK rules live there; this class
 * only translates.
 */
public final class AFKListener {

    private AFKListener() {
    }

    public static void register() {
        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) ->
                AFKCore.onPlayerJoin(handler.getPlayer()));

        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) ->
                AFKCore.onPlayerDisconnect(handler.getPlayer()));

        ServerPlayerEvents.AFTER_RESPAWN.register((oldPlayer, newPlayer, alive) ->
                AFKCore.onPlayerRespawn(newPlayer));

        ServerTickEvents.END_SERVER_TICK.register(AFKCore::onServerTick);

        ServerMessageEvents.CHAT_MESSAGE.register((message, sender, params) -> AFKCore.onChat(sender));

        PlayerBlockBreakEvents.BEFORE.register((world, player, pos, state, blockEntity) ->
                !(player instanceof ServerPlayer serverPlayer) || !AFKCore.blockIfAFK(serverPlayer));

        AttackEntityCallback.EVENT.register((player, world, hand, entity, hitResult) -> interact(player));

        UseBlockCallback.EVENT.register((player, world, hand, hitResult) -> interact(player));

        // Also what stops an AFK player mounting anything.
        UseEntityCallback.EVENT.register((player, world, hand, entity, hitResult) -> interact(player));

        ServerLivingEntityEvents.ALLOW_DAMAGE.register((entity, source, amount) ->
                AFKCore.allowDamage(entity, source));

        ServerLivingEntityEvents.AFTER_DAMAGE.register((entity, source, baseDamage, takenDamage, blocked) ->
                AFKCore.onDamageTaken(entity, source, takenDamage));
    }

    private static InteractionResult interact(Player player) {
        if (player instanceof ServerPlayer serverPlayer && AFKCore.blockIfAFK(serverPlayer)) {
            return InteractionResult.FAIL;
        }
        return InteractionResult.PASS;
    }
}
