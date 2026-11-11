package cn.zgnhit.mcwuwa.platform;

import java.util.HashSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.AABB;
import net.neoforged.neoforge.common.CommonHooks;

/** Butterfly collection uses native drops and pickup, including protection and full inventories. */
final class ShorekeeperHarvest {
    private static final TagKey<Block> COLLECTIBLES=TagKey.create(Registries.BLOCK,
        ResourceLocation.fromNamespaceAndPath("mc_wuwa","shorekeeper_collectibles"));
    private ShorekeeperHarvest() { }
    static boolean collectible(ServerPlayer player,BlockPos pos){return player.level().hasChunkAt(pos)&&player.level().getBlockState(pos).is(COLLECTIBLES);}

    static void collect(ServerPlayer player) {
        if(player.isSpectator()||!player.getAbilities().mayBuild)return;
        for(var pos:BlockPos.betweenClosed(player.blockPosition().offset(-2,-1,-2),player.blockPosition().offset(2,1,2)))
            collectAt(player,pos.immutable());
    }

    static boolean collectAt(ServerPlayer player,BlockPos pos) {
        var level=player.level();var state=level.getBlockState(pos);
        if(player.isSpectator()||!player.getAbilities().mayBuild||!state.is(COLLECTIBLES)
            ||!level.mayInteract(player,pos)
            ||CommonHooks.fireBlockBreak(level,player.gameMode.getGameModeForPlayer(),player,pos,state).isCanceled())return false;
        // Native creative pickup deliberately deletes an uninsertable stack. Leave the
        // plant intact instead; this utility must not turn a full creative bag into a void.
        var inventory=player.getInventory();
        if(player.hasInfiniteMaterials()&&inventory.getFreeSlot()<0
            &&inventory.getSlotWithRemainingSpace(new net.minecraft.world.item.ItemStack(state.getBlock()))<0)return false;
        // Only collect this operation's new drops. Existing stacks, owned drops and unrelated
        // inventory items must not be picked up or deleted as a side effect of butterfly travel.
        var area=new AABB(pos).inflate(1);
        var existing=new HashSet<java.util.UUID>();
        level.getEntitiesOfClass(ItemEntity.class,area).forEach(item->existing.add(item.getUUID()));
        if(!level.destroyBlock(pos,true,player))return false;
        for(var item:level.getEntitiesOfClass(ItemEntity.class,area))if(!existing.contains(item.getUUID())){
            pickupHarvest(player,item);
        }
        return true;
    }
    /** Creative native pickup discards overflow even after a partial insertion.
     * Verify the actual generated drop, including modded loot counts, first. */
    static boolean pickupHarvest(ServerPlayer player,ItemEntity item){
        if(player.hasInfiniteMaterials()){
            var stack=item.getItem();int remaining=stack.getCount();var inventory=player.getInventory();
            // Slot zero is a virtual weapon when a team is equipped, never storage.
            int firstSlot=ModAttachments.team(player).hasTeam()?1:0;
            for(int slot=firstSlot;slot<inventory.items.size()&&remaining>0;slot++){
                var current=inventory.items.get(slot);int cap=Math.min(stack.getMaxStackSize(),inventory.getMaxStackSize());
                if(current.isEmpty())remaining-=cap;
                else if(net.minecraft.world.item.ItemStack.isSameItemSameComponents(current,stack))remaining-=Math.max(0,cap-current.getCount());
            }
            if(remaining>0)return false;
        }
        int before=item.getItem().getCount();
        item.setNoPickUpDelay();item.playerTouch(player);
        return item.isRemoved()||item.getItem().getCount()<before;
    }
}
