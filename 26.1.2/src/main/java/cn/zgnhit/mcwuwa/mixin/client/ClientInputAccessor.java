package cn.zgnhit.mcwuwa.mixin.client;

import net.minecraft.client.player.ClientInput;
import net.minecraft.world.phys.Vec2;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Exact movement-vector seam; does not consume jump or combat key states. */
@Mixin(ClientInput.class)
public interface ClientInputAccessor {
    @Accessor("moveVector") void mcwuwa$setMoveVector(Vec2 value);
}
