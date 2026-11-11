package cn.zgnhit.mcwuwa.mixin;

import cn.zgnhit.mcwuwa.platform.entity.SurveyMotorcycleEntity;
import net.minecraft.world.entity.vehicle.Boat;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Retain vanilla input, collision and networking; replace only this vehicle's boat buoyancy. */
@Mixin(Boat.class)
public abstract class MotorcycleLandMotionMixin {
    @Shadow private float deltaRotation;
    @Inject(method="floatBoat",at=@At("HEAD"),cancellable=true)
    private void mcWuwa$landMotion(CallbackInfo callback){
        if((Object)this instanceof SurveyMotorcycleEntity bike){bike.applyLandMotion();deltaRotation*=.9f;callback.cancel();}
    }
    @Inject(method="controlBoat",at=@At("TAIL"))
    private void mcWuwa$limitBeforeMoving(CallbackInfo callback){if((Object)this instanceof SurveyMotorcycleEntity bike)bike.limitDriveSpeed();}
}
