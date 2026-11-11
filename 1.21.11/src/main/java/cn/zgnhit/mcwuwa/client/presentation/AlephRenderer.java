package cn.zgnhit.mcwuwa.client.presentation;

import cn.zgnhit.mcwuwa.platform.entity.AlephEntity;
import cn.zgnhit.mcwuwa.platform.entity.ModEntities;
import software.bernie.geckolib.animatable.GeoReplacedEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animatable.manager.AnimatableManager;
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.constant.DataTickets;
import software.bernie.geckolib.constant.dataticket.DataTicket;
import software.bernie.geckolib.model.GeoModel;
import software.bernie.geckolib.renderer.GeoReplacedEntityRenderer;
import software.bernie.geckolib.renderer.base.GeoRenderState;
import software.bernie.geckolib.util.GeckoLibUtil;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

/** Client-only replacement renderer: the server entity remains a normal Zombie subclass. */
public final class AlephRenderer<R extends LivingEntityRenderState & GeoRenderState> extends GeoReplacedEntityRenderer<AlephRenderer.Visual, AlephEntity, R> {
    private static final DataTicket<Boolean> WINDING = DataTicket.create("mc_wuwa_aleph_winding", Boolean.class);
    private static final DataTicket<Boolean> STAGGERED = DataTicket.create("mc_wuwa_aleph_staggered", Boolean.class);
    private static final Identifier MODEL = Identifier.fromNamespaceAndPath("mc_wuwa", "aleph_one");
    private static final Identifier TEXTURE = Identifier.fromNamespaceAndPath("mc_wuwa", "textures/entity/aleph_one.png");

    public AlephRenderer(EntityRendererProvider.Context context) {
        super(context, new AlephModel(), new Visual());
        this.shadowRadius = .75f;
    }

    public static void register(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(ModEntities.ALEPH.get(), AlephRenderer::new);
    }

    @Override public void addRenderData(Visual visual, AlephEntity entity, R state, float partialTick) {
        // fillRenderState calls this before extracting controller states. Adding
        // these after super.extractRenderState would make predicates see no data.
        ((GeoRenderState)state).addGeckolibData(WINDING, entity.isWindingUp());
        ((GeoRenderState)state).addGeckolibData(STAGGERED, entity.isStaggered());
    }

    public static final class Visual implements GeoReplacedEntity {
        @Override public net.minecraft.world.entity.EntityType<?> getReplacingEntityType() { return ModEntities.ALEPH.get(); }
        private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
        private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("animation.aleph_one.idle");
        private static final RawAnimation ATTACK = RawAnimation.begin().thenPlay("animation.aleph_one.attack");
        private static final RawAnimation HURT = RawAnimation.begin().thenPlay("animation.aleph_one.hurt");

        @Override public void registerControllers(AnimatableManager.ControllerRegistrar registrar) {
            registrar.add(new AnimationController<Visual>("body", 2, test ->
                test.setAndContinue(test.getDataOrDefault(STAGGERED,false) ? HURT : test.getDataOrDefault(WINDING,false) ? ATTACK : IDLE)));
        }
        @Override public AnimatableInstanceCache getAnimatableInstanceCache() { return cache; }
    }

    private static final class AlephModel extends GeoModel<Visual> {
        // GeckoLib5 maps these logical IDs to geckolib/models and animations,
        // stripping the directories and .geo/.animation suffix during loading.
        @Override public Identifier getModelResource(GeoRenderState state) { return MODEL; }
        @Override public Identifier getTextureResource(GeoRenderState state) { return TEXTURE; }
        @Override public Identifier getAnimationResource(Visual animatable) { return MODEL; }
    }
}
