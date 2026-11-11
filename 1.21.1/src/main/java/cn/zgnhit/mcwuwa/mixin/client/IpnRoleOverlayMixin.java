package cn.zgnhit.mcwuwa.mixin.client;

import cn.zgnhit.mcwuwa.client.IntegrationVisualClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Optional public IPN rendering seam. Refill behavior, user preferences and other icons remain intact. */
@Pseudo
@Mixin(targets="org.anti_ad.mc.ipnext.event.autorefill.AutoRefillHandler",remap=false)
public abstract class IpnRoleOverlayMixin {
    @Inject(method={"drawForeground","postRenderHud"},at=@At("HEAD"),cancellable=true,require=0)
    private void mcWuwa$quietRoleIndicators(CallbackInfo callback){
        if(IntegrationVisualClient.suppressRefillIndicators())callback.cancel();
    }
}
