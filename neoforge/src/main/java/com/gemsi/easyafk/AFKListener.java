package com.gemsi.easyafk;

import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.ICancellableEvent;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.util.TriState;
import net.neoforged.neoforge.event.ServerChatEvent;
import net.neoforged.neoforge.event.entity.EntityMountEvent;
import net.neoforged.neoforge.event.entity.living.LivingDamageEvent;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.entity.player.ItemEntityPickupEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * Wires NeoForge's events onto {@link AFKCore}. All of the AFK rules live there; this
 * class only translates.
 */
public class AFKListener {

    // ========== lifecycle ==========

    @SubscribeEvent
    public void onPlayerLoggedIn(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            AFKCore.onPlayerJoin(player);
        }
    }

    @SubscribeEvent
    public void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            AFKCore.onPlayerDisconnect(player);
        }
    }

    @SubscribeEvent
    public void onPlayerRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            AFKCore.onPlayerRespawn(player);
        }
    }

    @SubscribeEvent
    public void onServerTick(ServerTickEvent.Post event) {
        AFKCore.onServerTick(event.getServer());
    }

    // ========== restrictions ==========

    @SubscribeEvent
    public void onPlayerChat(ServerChatEvent event) {
        AFKCore.onChat(event.getPlayer());
    }

    @SubscribeEvent
    public void onBlockBreak(BlockEvent.BreakEvent event) {
        if (event.getPlayer() instanceof ServerPlayer player && AFKCore.blockIfAFK(player)) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public void onBlockToolModification(BlockEvent.BlockToolModificationEvent event) {
        if (event.getPlayer() instanceof ServerPlayer player && AFKCore.blockIfAFK(player)) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        cancelIfAFK(event);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
        cancelIfAFK(event);
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        cancelIfAFK(event);
    }

    @SubscribeEvent
    public void onAttackEntity(AttackEntityEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && AFKCore.blockIfAFK(player)) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public void onItemPickup(ItemEntityPickupEvent.Pre event) {
        if (event.getPlayer() instanceof ServerPlayer player && AFKCore.blockItemPickup(player)) {
            event.setCanPickup(TriState.FALSE);
        }
    }

    @SubscribeEvent
    public void onMountChange(EntityMountEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        if (event.isMounting()) {
            if (AFKCore.blockIfAFK(player)) {
                event.setCanceled(true);
            }
        } else {
            AFKCore.onDismount(player);
        }
    }

    // ========== damage ==========

    @SubscribeEvent
    public void onLivingDamagePre(LivingDamageEvent.Pre event) {
        if (!AFKCore.allowDamage(event.getEntity(), event.getSource())) {
            event.setNewDamage(0);
        }
    }

    @SubscribeEvent
    public void onLivingDamagePost(LivingDamageEvent.Post event) {
        AFKCore.onDamageTaken(event.getEntity(), event.getSource(), event.getNewDamage());
    }

    private static <T extends PlayerInteractEvent & ICancellableEvent> void cancelIfAFK(T event) {
        if (event.getEntity() instanceof ServerPlayer player && AFKCore.blockIfAFK(player)) {
            event.setCanceled(true);
        }
    }
}
