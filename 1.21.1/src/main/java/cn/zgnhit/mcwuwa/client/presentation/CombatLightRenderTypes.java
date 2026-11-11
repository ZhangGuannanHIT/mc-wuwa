package cn.zgnhit.mcwuwa.client.presentation;

import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;

public final class CombatLightRenderTypes {
    private CombatLightRenderTypes() {}

    static RenderType of(ResourceLocation texture) {
        return RenderType.entityTranslucentEmissive(texture, false);
    }
}
