package cn.zgnhit.mcwuwa.platform;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/** MC photography collection:16-block,24-degree lens, loaded plant whitelist.
 * Reuses the real protected harvest/pickup transaction, so full inventories do
 * not delete items and the camera cannot break terrain or containers. */
final class LucillaPhotography {
    private LucillaPhotography(){}
    static List<BlockPos> visiblePlants(ServerPlayer player){
        var found=new ArrayList<BlockPos>();Vec3 eye=player.getEyePosition(),look=player.getLookAngle();
        for(var mutable:BlockPos.betweenClosed(player.blockPosition().offset(-16,-8,-16),player.blockPosition().offset(16,8,16))){
            if(!ShorekeeperHarvest.collectible(player,mutable))continue;
            Vec3 center=Vec3.atCenterOf(mutable),delta=center.subtract(eye);
            if(delta.lengthSqr()>256||delta.normalize().dot(look)<Math.cos(Math.toRadians(24)))continue;
            var hit=player.level().clip(new ClipContext(eye,center,ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,player));
            if(hit.getType()!=HitResult.Type.MISS&&!hit.getBlockPos().equals(mutable))continue;
            found.add(mutable.immutable());
        }
        return List.copyOf(found);
    }
    static void mark(ServerPlayer player){
        for(var pos:visiblePlants(player))player.serverLevel().sendParticles(player,ParticleTypes.END_ROD,false,
            pos.getX()+.5,pos.getY()+.8,pos.getZ()+.5,1,.04,.04,.04,0);
        for(var item:visibleEchoes(player))player.serverLevel().sendParticles(player,ParticleTypes.END_ROD,false,
            item.getX(),item.getY()+.45,item.getZ(),2,.06,.08,.06,0);
    }
    static List<ItemEntity> visibleEchoes(ServerPlayer player){
        Vec3 eye=player.getEyePosition(),look=player.getLookAngle();
        return player.level().getEntitiesOfClass(ItemEntity.class,player.getBoundingBox().inflate(16),item->{
            if(item.isRemoved()||!EchoData.valid(item.getItem())||item.getTarget()!=null&&!item.getTarget().equals(player.getUUID()))return false;
            Vec3 center=item.position().add(0,.15,0),delta=center.subtract(eye);
            return delta.lengthSqr()<=256&&delta.normalize().dot(look)>=Math.cos(Math.toRadians(24))
                &&player.level().clip(new ClipContext(eye,center,ClipContext.Block.COLLIDER,ClipContext.Fluid.NONE,player)).getType()==HitResult.Type.MISS;
        });
    }
    static void shutter(ServerPlayer player){
        int count=0,echoes=0;
        for(var pos:visiblePlants(player))if(ShorekeeperHarvest.collectAt(player,pos))count++;
        for(var item:visibleEchoes(player))if(ShorekeeperHarvest.pickupHarvest(player,item))echoes++;
        player.displayClientMessage(net.minecraft.network.chat.Component.literal("拍摄完成 · 采集 "+count+" 处植物、"+echoes+" 份声骸"), true);
    }
}
