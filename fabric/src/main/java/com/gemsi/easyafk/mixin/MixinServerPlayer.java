package com.gemsi.easyafk.mixin;

import com.gemsi.easyafk.AFKCommands;
import com.gemsi.easyafk.AFKPlayer;
import com.gemsi.easyafk.Config;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Fabric has no {@code TabListNameFormat} event like Forge/NeoForge, so we override
 * the player's tab list display name directly to inject the AFK prefix and duration.
 */
@Mixin(ServerPlayer.class)
public abstract class MixinServerPlayer {

    @Inject(method = "getTabListDisplayName", at = @At("HEAD"), cancellable = true)
    private void easyafk$afkTabListName(CallbackInfoReturnable<Component> cir) {
        ServerPlayer self = (ServerPlayer) (Object) this;
        if (Config.showAFKInTab && AFKCommands.getPlayerAFKStatus(self.getUUID())) {
            cir.setReturnValue(AFKPlayer.buildTabListName(self));
        }
    }
}
