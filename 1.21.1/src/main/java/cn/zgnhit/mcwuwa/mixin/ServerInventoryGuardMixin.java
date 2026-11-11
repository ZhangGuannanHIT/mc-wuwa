package cn.zgnhit.mcwuwa.mixin;

import cn.zgnhit.mcwuwa.platform.ModAttachments;
import cn.zgnhit.mcwuwa.platform.WeaponData;
import cn.zgnhit.mcwuwa.platform.WeaponItem;
import cn.zgnhit.mcwuwa.platform.EchoData;
import cn.zgnhit.mcwuwa.platform.ModItems;
import cn.zgnhit.mcwuwa.platform.PlatformRuntime;
import cn.zgnhit.mcwuwa.platform.Network;
import net.minecraft.world.item.ItemStack;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.network.protocol.game.ServerboundSetCreativeModeSlotPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Packet guards run after vanilla has transferred processing onto the server game thread. */
@Mixin(ServerGamePacketListenerImpl.class)
public abstract class ServerInventoryGuardMixin {
    @Shadow public ServerPlayer player;
    @Shadow private int ackBlockChangesUpTo;
    private static final String ON_SERVER = "Lnet/minecraft/network/protocol/PacketUtils;ensureRunningOnSameThread(Lnet/minecraft/network/protocol/Packet;Lnet/minecraft/network/PacketListener;Lnet/minecraft/server/level/ServerLevel;)V";
    @Inject(method = "handlePlayerAction", at = @At(value = "INVOKE", target = ON_SERVER, shift = At.Shift.AFTER), cancellable = true)
    private void wuwa$referenceAction(ServerboundPlayerActionPacket packet, CallbackInfo ci) {
        if (player.getInventory().selected == 0 && ModAttachments.team(player).hasTeam()) {
            switch (packet.getAction()) {
                case RELEASE_USE_ITEM -> { return; } // Native release is harmless and must terminate item use.
                case START_DESTROY_BLOCK, ABORT_DESTROY_BLOCK, STOP_DESTROY_BLOCK ->
                    ackBlockChangesUpTo=Math.max(ackBlockChangesUpTo,packet.getSequence());
                default -> { }
            }
            // Local and server Inventory/Slot guards already protect the virtual reference.
            // A blanket full-state packet here overwrites creative's client-owned cursor,
            // including when a delayed combat/mining packet arrives after opening a screen.
            ci.cancel();
        }
    }
    @Inject(method = "handleSetCreativeModeSlot", at = @At(value = "INVOKE", target = ON_SERVER, shift = At.Shift.AFTER), cancellable = true)
    private void wuwa$creativeReference(ServerboundSetCreativeModeSlotPacket packet, CallbackInfo ci) {
        var team = ModAttachments.team(player);
        int index = packet.slotNum();
        var cursor=player.inventoryMenu.getCarried();var incoming=packet.itemStack();
        if(EchoData.templateSpecies(incoming).isPresent()) {
            if(!PlatformRuntime.resolveCreativeEchoTemplate(player,incoming)){Network.correctCreativeSlot(player,index);ci.cancel();return;}
        }
        // A custom editor gives the physical outgoing item to the server cursor. Vanilla
        // creative placement sends only the destination stack and never consumes that cursor.
        // Transfer it once, rather than rejecting its own UUID or returning a second copy on close.
        boolean managed=cursor.getItem() instanceof WeaponItem||EchoData.valid(cursor);
        if(player.getAbilities().instabuild&&player.containerMenu==player.inventoryMenu&&managed
            &&cursor.getCount()==1&&incoming.getCount()==1&&ItemStack.isSameItemSameComponents(cursor,incoming)){
            if(index>=0&&index<player.inventoryMenu.slots.size()){
                var destination=player.inventoryMenu.slots.get(index);
                boolean physical=destination.container==player.getInventory()&&destination.getContainerSlot()>=0&&destination.getContainerSlot()<36
                    &&(!team.hasTeam()||destination.getContainerSlot()!=0)&&destination.mayPlace(incoming);
                if(physical){
                    String identity=cursor.getItem() instanceof WeaponItem?WeaponData.tag(cursor).getString("instance"):EchoData.data(cursor).getString("instance");
                    boolean duplicate=false;for(var slot:player.inventoryMenu.slots){if(slot.container==player.getInventory()&&slot.getContainerSlot()==0&&team.hasTeam())continue;
                        var item=slot.getItem();if(!identity.isEmpty()&&((item.getItem() instanceof WeaponItem&&WeaponData.tag(item).getString("instance").equals(identity))||(EchoData.valid(item)&&EchoData.data(item).getString("instance").equals(identity))))duplicate=true;
                    }
                    if(!duplicate){var outgoing=destination.getItem().copy();destination.set(cursor.copy());player.inventoryMenu.setCarried(outgoing);Network.rememberCreativeCursor(player);}
                    Network.correctCreativeSlot(player,index);ci.cancel();return;
                }
            }else if(index==-1){player.inventoryMenu.setCarried(ItemStack.EMPTY);Network.rememberCreativeCursor(player);}
        }
        boolean forbidden = team.hasTeam() && index >= 0 && index < player.inventoryMenu.slots.size()
            && (player.inventoryMenu.slots.get(index).container == team.inventory()
                || player.inventoryMenu.slots.get(index).container == player.getInventory()
                    && player.inventoryMenu.slots.get(index).getContainerSlot() == 0);
        // This packet can bypass client menu clicks. Compare every owned physical weapon,
        // even with an empty team, while permitting replacement of the same destination.
        if (packet.itemStack().getItem() instanceof WeaponItem) {
            String text = WeaponData.tag(packet.itemStack()).getString("instance");
            if (!text.isEmpty()) {
                try {
                    var instance = java.util.UUID.fromString(text);
                    var destination = index >= 0 && index < player.inventoryMenu.slots.size()
                        ? player.inventoryMenu.slots.get(index).getItem() : net.minecraft.world.item.ItemStack.EMPTY;
                    for (var slot : player.inventoryMenu.slots)
                        if (slot.getItem() != destination && wuwa$sameWeapon(instance, slot.getItem())) forbidden = true;
                    if (wuwa$sameWeapon(instance, player.inventoryMenu.getCarried())) forbidden = true;
                } catch (IllegalArgumentException malformedUuid) { forbidden = true; }
            }
        }else if(incoming.is(ModItems.ECHO.get())&&!incoming.isEmpty()){
            if(!EchoData.valid(incoming))forbidden=true;
            else{
                String identity=EchoData.data(incoming).getString("instance");
                var destination=index>=0&&index<player.inventoryMenu.slots.size()?player.inventoryMenu.slots.get(index):null;
                for(var slot:player.inventoryMenu.slots)if(slot!=destination&&EchoData.valid(slot.getItem())&&EchoData.data(slot.getItem()).getString("instance").equals(identity))forbidden=true;
                if(EchoData.valid(cursor)&&EchoData.data(cursor).getString("instance").equals(identity))forbidden=true;
                for(int role=0;role<3;role++)for(int echo=0;echo<5;echo++)if(WeaponData.tag(team.inventory().getItem(role)).getCompound("echo_"+echo).getString("instance").equals(identity))forbidden=true;
            }
        }
        if (forbidden) { Network.correctCreativeSlot(player,index); ci.cancel(); }
    }
    @Inject(method="handleSetCreativeModeSlot",at=@At("RETURN"))
    private void wuwa$syncRolledEcho(ServerboundSetCreativeModeSlotPacket packet,CallbackInfo ci){
        if(EchoData.valid(packet.itemStack()))Network.correctCreativeSlot(player,packet.slotNum());
    }
    @Unique
    private static boolean wuwa$sameWeapon(java.util.UUID instance, net.minecraft.world.item.ItemStack stack) {
        if (!(stack.getItem() instanceof WeaponItem)) return false;
        String text = WeaponData.tag(stack).getString("instance");
        try { return !text.isEmpty() && java.util.UUID.fromString(text).equals(instance); }
        catch (IllegalArgumentException invalidExistingItem) { return false; }
    }
}
