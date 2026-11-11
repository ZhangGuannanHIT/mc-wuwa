package cn.zgnhit.mcwuwa.mixin;

import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Creative cursor revisions use their own response, not vanilla's unversioned
 * cursor packet (which creative ignores but our details screen would accept). */
@Mixin(AbstractContainerMenu.class)
public interface MenuCursorSyncAccessor {
    @Accessor("remoteCarried") void mcwuwa$setRemoteCarried(ItemStack stack);
}
