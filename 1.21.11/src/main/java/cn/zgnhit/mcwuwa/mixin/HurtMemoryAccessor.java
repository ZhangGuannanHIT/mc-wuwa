package cn.zgnhit.mcwuwa.mixin;

import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Native damage has independent hit cadence; preserve vanilla hurt-cooldown comparison memory. */
@Mixin(value=LivingEntity.class,remap=false)
public interface HurtMemoryAccessor {
    @Accessor(value="lastHurt",remap=false) float mcwuwa$getLastHurt();
    @Accessor(value="lastHurt",remap=false) void mcwuwa$setLastHurt(float value);
}
