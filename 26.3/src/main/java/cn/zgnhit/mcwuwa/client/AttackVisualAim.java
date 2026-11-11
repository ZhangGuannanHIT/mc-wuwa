package cn.zgnhit.mcwuwa.client;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.*;
import net.minecraft.world.level.ClipContext;

/** Read-only visual intersection along the existing view, never target selection or camera input. */
public final class AttackVisualAim {
    private AttackVisualAim(){}
    public static Vec3 endpoint(Entity actor,float partial,double reach){
        Vec3 eye=actor.getEyePosition(partial),end=eye.add(actor.getViewVector(partial).scale(reach));
        var block=actor.level().clip(new ClipContext(eye,end,ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,actor));
        if(block.getType()==HitResult.Type.BLOCK)end=block.getLocation();
        double nearest=eye.distanceToSqr(end);
        for(var entity:actor.level().getEntities(actor,new AABB(eye,end).inflate(.3),e->e.isAlive()&&e.isPickable()&&e.isAttackable()&&!e.isSpectator()&&!actor.isAlliedTo(e))){
            var intersection=entity.getBoundingBox().inflate(.1).clip(eye,end);
            if(intersection.isPresent()&&eye.distanceToSqr(intersection.get())<nearest){end=intersection.get();nearest=eye.distanceToSqr(end);}
        }
        return end;
    }
}
