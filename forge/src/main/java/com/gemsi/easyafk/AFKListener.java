package com.gemsi.easyafk;

import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.ServerChatEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityMountEvent;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.event.entity.player.EntityItemPickupEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/**
 * Wires Forge's events onto {@link AFKCore}. All of the AFK rules live there; this class
 * only translates.
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
    public void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase == TickEvent.Phase.END) {
            AFKCore.onServerTick(event.getServer());
        }
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
    public void onItemPickup(EntityItemPickupEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && AFKCore.blockItemPickup(player)) {
            event.setCanceled(true);
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

    /**
     * Forge has a single damage event, so the cancel and the bookkeeping have to share one
     * handler: recording damage that was cancelled would wake the player for a hit they
     * never took.
     */
    @SubscribeEvent
    public void onLivingDamage(LivingDamageEvent event) {
        if (!AFKCore.allowDamage(event.getEntity(), event.getSource())) {
            event.setAmount(0);
            event.setCanceled(true);
            return;
        }
        AFKCore.onDamageTaken(event.getEntity(), event.getSource(), event.getAmount());
    }

    private static void cancelIfAFK(PlayerInteractEvent event) {
        if (event.getEntity() instanceof ServerPlayer player && AFKCore.blockIfAFK(player)) {
            event.setCanceled(true);
        }
    }
}
