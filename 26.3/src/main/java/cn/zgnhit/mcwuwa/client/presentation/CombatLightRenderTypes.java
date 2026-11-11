package cn.zgnhit.mcwuwa.client.presentation;

import com.mojang.renderpearl.api.pipeline.BlendFunction;
import com.mojang.renderpearl.api.pipeline.ColorTargetState;
import com.mojang.renderpearl.api.pipeline.DepthStencilState;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import com.mojang.renderpearl.api.pipeline.CompareOp;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Util;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RegisterRenderPipelinesEvent;

/** Decorative light tests against world depth but never becomes an occluder.
 * Uses vanilla entity shaders; no external shader pack or custom shader is required. */
@EventBusSubscriber(value=Dist.CLIENT,modid="mc_wuwa")
public final class CombatLightRenderTypes {
    private static final RenderPipeline LIGHT=RenderPipeline.builder(RenderPipelines.ENTITY_SNIPPET)
        .withLocation(Identifier.fromNamespaceAndPath("mc_wuwa","pipeline/combat_light"))
        .withShaderDefine("ALPHA_CUTOUT",.001f)
        .withBindGroupLayout(net.minecraft.client.renderer.BindGroupLayouts.SAMPLER1)
        
        .withColorTargetState(new ColorTargetState(BlendFunction.TRANSLUCENT))
        .withDepthStencilState(new DepthStencilState(CompareOp.LESS_THAN_OR_EQUAL,false))
        .withCull(false).build();
    private static final java.util.function.Function<Identifier,RenderType> TYPES=Util.memoize(texture->
        RenderType.create("mc_wuwa_combat_light",RenderSetup.builder(LIGHT)
            .withTexture("Sampler0",texture).useLightmap().useOverlay().sortOnUpload().createRenderSetup()));
    private CombatLightRenderTypes(){}
    @SubscribeEvent
    static void register(RegisterRenderPipelinesEvent event){event.registerPipeline(LIGHT);}
    static RenderType of(Identifier texture){return TYPES.apply(texture);}
}
