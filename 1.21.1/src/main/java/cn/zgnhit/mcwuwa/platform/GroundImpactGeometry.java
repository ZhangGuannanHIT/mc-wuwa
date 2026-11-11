package cn.zgnhit.mcwuwa.platform;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/** Server collision projection for ground bursts. The sixteen-block probe is an MC distance,
 * not an original-game measurement. Never accepts a client-provided landing point. */
public final class GroundImpactGeometry {
    private GroundImpactGeometry() { }
    public static Vec3 floor(ServerPlayer player) {
        Vec3 from=player.position().add(0,.25,0),to=from.add(0,-16,0);
        var hit=player.level().clip(new ClipContext(from,to,ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,player));
        return hit.getType()==HitResult.Type.BLOCK?hit.getLocation().add(0,.05,0):player.position();
    }
    public static boolean lynaeImpact(String action) {
        return action.equals("lynae/rainbow_splash")||action.equals("lynae/visual_impact");
    }
    /** Source Hiyuki normal_attack: frost heavy returns to ground before attacking.
     * MC adaptation: resolve one downward collision move, retaining camera angles.
     * No floor means no cast or resource transaction, rather than a void teleport. */
    public static boolean returnToGround(ServerPlayer player) {
        if(TraversalMotion.solidSupport(player))return true;
        if(player.getY()<=player.level().getMinBuildHeight())return false;
        Vec3 from=player.position().add(0,.05,0);
        var hit=player.level().clip(new ClipContext(from,new Vec3(from.x,player.level().getMinBuildHeight(),from.z),ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,player));
        if(hit.getType()!=HitResult.Type.BLOCK)return false;
        player.move(net.minecraft.world.entity.MoverType.SELF,new Vec3(0,Math.min(-.05,hit.getLocation().y-player.getY()-.01),0));
        if(!TraversalMotion.solidSupport(player))return false;
        player.setOnGround(true);player.setDeltaMovement(player.getDeltaMovement().multiply(1,0,1));player.fallDistance=0;
        player.teleportTo(player.getX(),player.getY(),player.getZ());player.hurtMarked=true;
        return true;
    }
}
