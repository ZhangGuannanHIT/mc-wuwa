package cn.zgnhit.mcwuwa.mixin.client;

import cn.zgnhit.mcwuwa.client.CombatClient;
import cn.zgnhit.mcwuwa.client.IntegrationVisualClient;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.KeyMapping;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;

/** Iris is optional. Preserve all other keybinds and the user's saved mappings.
 * A shared R must not rebuild a shader pipeline each time liberation is pressed. */
@Pseudo
@Mixin(targets="net.irisshaders.iris.Iris",remap=false)
public abstract class IrisCombatKeyMixin {
    @WrapOperation(method="handleKeybinds",at=@At(value="INVOKE",target="Lnet/minecraft/client/KeyMapping;consumeClick()Z"),require=0)
    private static boolean mcWuwa$claimCombatReload(KeyMapping binding,Operation<Boolean> original) {
        boolean pressed=original.call(binding);
        // Public mapping identity avoids a hard dependency on Iris's private field name.
        if(!pressed||!"iris.keybind.reload".equals(binding.getName()))return pressed;
        boolean claim=CombatClient.claimsLiberationKey(binding);IntegrationVisualClient.irisReload(claim);
        return !claim;
    }
}
