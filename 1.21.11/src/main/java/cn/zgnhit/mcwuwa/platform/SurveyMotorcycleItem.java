package cn.zgnhit.mcwuwa.platform;

import cn.zgnhit.mcwuwa.platform.entity.ModEntities;
import cn.zgnhit.mcwuwa.core.SurveyMotorcycleRules;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;

/** Placement consumes one real item on the server only. */
public final class SurveyMotorcycleItem extends Item {
    public SurveyMotorcycleItem(Properties properties){super(properties);}
    @Override public InteractionResult useOn(UseOnContext context){
        var player=context.getPlayer();if(player==null)return InteractionResult.PASS;
        var level=context.getLevel();var position=context.getClickedPos().relative(context.getClickedFace());
        var bike=ModEntities.SURVEY_MOTORCYCLE.get().create(level,EntitySpawnReason.SPAWN_ITEM_USE);
        if(bike==null)return InteractionResult.FAIL;
        bike.restoreKinetic(context.getItemInHand().getOrDefault(net.minecraft.core.component.DataComponents.CUSTOM_DATA,net.minecraft.world.item.component.CustomData.EMPTY).copyTag().getFloatOr("mc_wuwa_kinetic",1000));
        var stored=context.getItemInHand().getOrDefault(net.minecraft.core.component.DataComponents.CUSTOM_DATA,net.minecraft.world.item.component.CustomData.EMPTY).copyTag();
        bike.restoreAerial(stored.getIntOr("mc_wuwa_leaps",SurveyMotorcycleRules.LEAP_CAPACITY),stored.getIntOr("mc_wuwa_air_energy",SurveyMotorcycleRules.GLIDE_CAPACITY));
        bike.setPos(position.getX()+.5,position.getY()+.05,position.getZ()+.5);bike.setYRot(player.getYRot());
        if(!level.noCollision(bike,bike.getBoundingBox()))return InteractionResult.FAIL;
        if(!level.isClientSide()){
            if(!level.addFreshEntity(bike))return InteractionResult.FAIL;
            context.getItemInHand().consume(1,player);
        }
        return InteractionResult.SUCCESS;
    }
}
