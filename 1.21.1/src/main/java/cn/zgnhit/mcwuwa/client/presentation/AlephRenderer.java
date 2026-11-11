package cn.zgnhit.mcwuwa.client.presentation;

import cn.zgnhit.mcwuwa.platform.entity.AlephEntity;
import cn.zgnhit.mcwuwa.platform.entity.ModEntities;
import software.bernie.geckolib.animatable.GeoReplacedEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.constant.DataTickets;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.renderer.GeoReplacedEntityRenderer;
import software.bernie.geckolib.util.GeckoLibUtil;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

/** Client-only replacement renderer: the server entity remains a normal Zombie subclass. */
public final class AlephRenderer extends GeoReplacedEntityRenderer<AlephEntity, AlephRenderer.Visual> {
    private static final ResourceLocation MODEL = ResourceLocation.fromNamespaceAndPath("mc_wuwa", "geo/aleph_one.geo.json");
    private static final ResourceLocation TEXTURE = ResourceLocation.fromNamespaceAndPath("mc_wuwa", "textures/entity/aleph_one.png");

    public AlephRenderer(EntityRendererProvider.Context context) {
        super(context, new AlephModel(), new Visual());
        this.shadowRadius = .75f;
    }

    public static void register(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(ModEntities.ALEPH.get(), AlephRenderer::new);
    }

    public static final class Visual implements GeoReplacedEntity {
        private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
        private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("animation.aleph_one.idle");
        private static final RawAnimation ATTACK = RawAnimation.begin().thenPlay("animation.aleph_one.attack");
        private static final RawAnimation HURT = RawAnimation.begin().thenPlay("animation.aleph_one.hurt");

        @Override public net.minecraft.world.entity.EntityType<?> getReplacingEntityType() { return ModEntities.ALEPH.get(); }

        @Override public void registerControllers(AnimatableManager.ControllerRegistrar registrar) {
            registrar.add(new AnimationController<>(this, "body", 2, state -> {
                var entity = state.getData(DataTickets.ENTITY);
                if (entity instanceof AlephEntity aleph) {
                    if (aleph.isStaggered()) return state.setAndContinue(HURT);
                    if (aleph.isWindingUp()) return state.setAndContinue(ATTACK);
                }
                return state.setAndContinue(IDLE);
            }));
        }
        @Override public AnimatableInstanceCache getAnimatableInstanceCache() { return cache; }
    }

    private static final class AlephModel extends GeoModel<Visual> {
        @Override public ResourceLocation getModelResource(Visual animatable) { return MODEL; }
        @Override public ResourceLocation getTextureResource(Visual animatable) { return TEXTURE; }
        @Override public ResourceLocation getAnimationResource(Visual animatable) { return ResourceLocation.fromNamespaceAndPath("mc_wuwa", "animations/aleph_one.animation.json"); }
    }
}
