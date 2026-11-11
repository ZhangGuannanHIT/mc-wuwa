package cn.zgnhit.mcwuwa.mixin.client;

import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Read only coordinates; keeps added team controls aligned when the recipe book moves the inventory. */
@Mixin(AbstractContainerScreen.class)
public interface ContainerScreenAccessor {
    @Accessor("leftPos") int mcwuwa$left();
    @Accessor("topPos") int mcwuwa$top();
}
