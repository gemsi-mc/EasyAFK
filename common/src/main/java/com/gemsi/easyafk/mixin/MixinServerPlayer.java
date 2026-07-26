package com.gemsi.easyafk.mixin;

import com.gemsi.easyafk.AFKCommands;
import com.gemsi.easyafk.Config;
import com.gemsi.easyafk.TabList;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Injects the AFK prefix and duration into the tab list.
 *
 * <p>Forge and NeoForge have a {@code TabListNameFormat} event for this and Fabric does
 * not, but both of those cache the name they hand out and only recompute it inside a
 * method that broadcasts a packet of its own. Overriding the getter instead is the one
 * approach that works the same on all three, and it lets the per-second refresh be a
 * single batched packet (see {@link TabList#pushAll}).
 */
@Mixin(ServerPlayer.class)
public abstract class MixinServerPlayer {

    @Inject(method = "getTabListDisplayName", at = @At("HEAD"), cancellable = true)
    private void easyafk$afkTabListName(CallbackInfoReturnable<Component> cir) {
        ServerPlayer self = (ServerPlayer) (Object) this;
        if (Config.showAFKInTab && AFKCommands.getPlayerAFKStatus(self.getUUID())) {
            cir.setReturnValue(TabList.nameFor(self));
        }
    }
}
