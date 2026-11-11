package cn.zgnhit.mcwuwa.mixin.client;

import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** IPN 2.3.5 initializes its recursion flag only on the class-loading thread. */
@Pseudo
@Mixin(targets="org.anti_ad.mc.ipnext.item.ItemTypeExtensionsObject",remap=false)
public abstract class IpnOrderThreadMixin {
    @Shadow @Final private static ThreadLocal<Boolean> local;

    @Inject(method="defaultOrderListChanged",at=@At("HEAD"),require=0)
    private void mcWuwa$initializeOrderCallbackThread(CallbackInfo callback){
        // TRUE is IPN's own initial state: allow normal rebuilding. Preserve FALSE
        // during its recursive setValue callback rather than suppressing sorting.
        if(local.get()==null)local.set(Boolean.TRUE);
    }
}
