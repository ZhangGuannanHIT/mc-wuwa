package cn.zgnhit.mcwuwa.platform;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.ai.attributes.Attributes;

/** Vanilla attackable props use their own damage, destruction and drop rules. */
public final class NativeAttackTargets {
    private NativeAttackTargets() { }
    /** A dragon's hitboxes belong to one combat recipient, not independent breakable props. */
    public static LivingEntity livingTarget(Entity target){
        if(target instanceof LivingEntity living)return living;
        if(target instanceof net.neoforged.neoforge.entity.PartEntity<?> part&&part.getParent() instanceof LivingEntity living)return living;
        return null;
    }
    public static boolean accepts(ServerPlayer player,Entity target){
        return target!=player && target.level()==player.level() && player.isAlive() && target.isAlive()
            && !target.isSpectator() && target.isPickable() && target.isAttackable()
            && (livingTarget(target)==null||target instanceof ArmorStand)
            && !player.isAlliedTo(target);
    }
    public static boolean hit(ServerPlayer player,Entity target){
        if(!accepts(player,target)||target.skipAttackInteraction(player))return false;
        return target.hurt(player.damageSources().playerAttack(player),
            (float)Math.max(1,player.getAttributeValue(Attributes.ATTACK_DAMAGE)));
    }
}
