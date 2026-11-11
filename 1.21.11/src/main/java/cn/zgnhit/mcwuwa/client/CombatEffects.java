package cn.zgnhit.mcwuwa.client;

import cn.zgnhit.mcwuwa.core.Element;
import cn.zgnhit.mcwuwa.core.TickBudget;
import cn.zgnhit.mcwuwa.platform.ClientVisualConfig;
import cn.zgnhit.mcwuwa.platform.Network;
import net.minecraft.client.Minecraft;
import net.minecraft.core.particles.DustParticleOptions;
import java.util.*;

/** Decorative hit feedback only. Necessary models, indicators and all gameplay stay independent. */
public final class CombatEffects {
    private static TickBudget budget=new TickBudget(20);
    private record Seen(long sequence,long tick,int submitted,int style){}
    private static final Map<UUID,Seen> seen=new LinkedHashMap<>() {
        @Override protected boolean removeEldestEntry(Map.Entry<UUID,Seen> eldest){return size()>256;}
    };
    private record ActionSeen(String action,long receivedTick,long serverStartTick,long sequence,int submitted) { }
    private static final Map<UUID,ActionSeen> actionSeen=new LinkedHashMap<>() {
        @Override protected boolean removeEldestEntry(Map.Entry<UUID,ActionSeen> eldest){return size()>256;}
    };
    private record Timeline(String action,int entityId,long start,int duration,long sequence,
                            net.minecraft.world.phys.Vec3 origin,net.minecraft.world.phys.Vec3 forward) { }
    private static final Map<UUID,Timeline> timelines=new HashMap<>();
    private static final Map<UUID,ActionSeen> timedSeen=new LinkedHashMap<>() {
        @Override protected boolean removeEldestEntry(Map.Entry<UUID,ActionSeen> eldest){return size()>256;}
    };
    private static long effectsTick=Long.MIN_VALUE;
    private record ScarSeen(long start,double forwardDistance,int groundParticles,long groundTick) { }
    private static final Map<UUID,ScarSeen> scarSeen=new LinkedHashMap<>() {
        @Override protected boolean removeEldestEntry(Map.Entry<UUID,ScarSeen> eldest){return size()>256;}
    };
    private CombatEffects(){}
    /** Head-anchored sustained breath shares the normal decorative particle budget. */
    public static void heronBreath(net.minecraft.world.entity.player.Player actor,net.minecraft.world.phys.Vec3 mouth){
        var game=Minecraft.getInstance();if(game.level==null||game.player==null)return;
        double intensity=actor==game.player?ClientVisualConfig.OWN_INTENSITY.get():ClientVisualConfig.OTHER_INTENSITY.get();
        int count=budget.grant(game.level.getGameTime(),(int)Math.round(12*intensity),ClientVisualConfig.PARTICLE_BUDGET.get());
        var look=actor.getLookAngle();var direction=new net.minecraft.world.phys.Vec3(look.x,Math.min(-.35,look.y),look.z).normalize();
        var end=mouth.add(direction.scale(8));
        var wall=game.level.clip(new net.minecraft.world.level.ClipContext(mouth,end,net.minecraft.world.level.ClipContext.Block.COLLIDER,net.minecraft.world.level.ClipContext.Fluid.NONE,actor));
        if(wall.getType()==net.minecraft.world.phys.HitResult.Type.BLOCK)end=wall.getLocation();
        var delta=end.subtract(mouth);
        for(int i=0;i<count;i++){
            var at=mouth.add(delta.scale(i/(double)Math.max(1,count-1)));
            game.level.addParticle(new DustParticleOptions(i%3==0?0xe1a6f5:0x713fa4,.8f),at.x,at.y,at.z,0,0,0);
        }
    }
    /** Feedback exists even when an optional animation cannot load. Only authenticated S2C starts call this.
     * Never authorizes damage, moves the camera or turns a missed slash into a hit. */
    public static void acceptAction(Network.Visual visual,net.minecraft.world.entity.Entity actor) {
        var game=Minecraft.getInstance();if(game.player==null||game.level==null||visual.actionId().equals("@form"))return;
        if(visual.durationTicks()<=0||visual.actionId().isEmpty()) {
            var old=timelines.get(actor.getUUID());if(old!=null&&visual.sequence()>=old.sequence())timelines.remove(actor.getUUID());return;
        }
        String id=visual.actionId().replace("mc_wuwa:","");int slash=id.indexOf('/');if(slash<1)return;
        long now=game.level.getGameTime();if(now-visual.startTick()>80||visual.startTick()>now+20)return;
        var old=actionSeen.get(actor.getUUID());if(old!=null&&visual.sequence()<=old.sequence())return;
        cn.zgnhit.mcwuwa.content.CharacterDefinition definition;
        try{definition=cn.zgnhit.mcwuwa.McWuwa.catalog().requireByCharacter(id.substring(0,slash));}catch(java.util.NoSuchElementException ignored){return;}
        boolean own=actor.getUUID().equals(game.player.getUUID());double strength=own?ClientVisualConfig.OWN_INTENSITY.get():ClientVisualConfig.OTHER_INTENSITY.get();
        int wanted=(int)Math.round((id.endsWith("/charge")?4: id.contains("heavy")?18:8)*strength);
        wanted=switch(game.options.particles().get()){case ALL->wanted;case DECREASED->wanted/2;case MINIMAL->Math.min(wanted,2);};
        int count=budget.grant(now,wanted,ClientVisualConfig.PARTICLE_BUDGET.get());
        int emitted=0;
        var direction=net.minecraft.world.phys.Vec3.directionFromRotation(0,actor.getYRot());
        timelines.put(actor.getUUID(),new Timeline(id,actor.getId(),visual.startTick(),visual.durationTicks(),visual.sequence(),actor.position(),direction));
        int color=actionColor(definition.characterId(),definition.element());
        var dust=new DustParticleOptions(color,.8f);var look=actor.getLookAngle();var side=new net.minecraft.world.phys.Vec3(-direction.z,0,direction.x);
        var center=actor.position().add(look.scale(1.3)).add(0,actor.getBbHeight()*.65,0);
        for(int i=0;i<count;i++){double u=count<2?.5:i/(double)(count-1),angle=-1.2+u*2.4;
            var profile=stateColor(actor.getUUID(),id,actionSample(id,.03,u,0,color));
            if(profile!=null){var pos=actor.position().add(0,actor.getBbHeight()*.65,0).add(direction.scale(profile.forward())).add(side.scale(profile.side())).add(0,profile.up(),0);if(emitProfile(pos,profile,u))emitted++;}
            else {var pos=center.add(side.scale(Math.sin(angle)*.7)).add(0,Math.cos(angle)*.35-.12,0);game.level.addParticle(dust,pos.x,pos.y,pos.z,look.x*.02,.01,look.z*.02);emitted++;}
        }
        actionSeen.put(actor.getUUID(),new ActionSeen(id,now,visual.startTick(),visual.sequence(),emitted));
        if(!cn.zgnhit.mcwuwa.client.presentation.ActionPresentationRegistry.available(id)&&actor instanceof net.minecraft.world.entity.player.Player player&&!id.endsWith("/charge"))player.swing(net.minecraft.world.InteractionHand.MAIN_HAND);
    }
    /** Every role uses its actual S2C timeline. These particles have no hit callbacks. */
    public static void tick() {
        var game=Minecraft.getInstance();if(game.level==null||game.player==null)return;
        long now=game.level.getGameTime();if(now==effectsTick)return;effectsTick=now;
        for(var entry:List.copyOf(timelines.entrySet())) {
            var value=entry.getValue();var actor=game.level.getEntity(value.entityId());long elapsed=now-value.start();
            if(actor==null||!actor.getUUID().equals(entry.getKey())||!actor.isAlive()||elapsed>=value.duration()) {timelines.remove(entry.getKey());continue;}
            if(elapsed<0||elapsed%2!=0||actor.distanceToSqr(game.player)>4096)continue;
            String slug=value.action().split("/",2)[0];var definition=cn.zgnhit.mcwuwa.McWuwa.catalog().requireByCharacter(slug);
            double intensity=entry.getKey().equals(game.player.getUUID())?ClientVisualConfig.OWN_INTENSITY.get():ClientVisualConfig.OTHER_INTENSITY.get();
            boolean powerful=value.action().contains("liberation")||value.action().contains("epiphany")||value.action().contains("heavy")||value.action().contains("impact");
            boolean longScar=value.action().equals("cartethyia/liberation_finale");
            double progress=elapsed/(double)value.duration();
            // Leave the shared128/s budget available for the actual ground stroke. Spending24
            // every two ticks during anticipation used to starve the landed scar and its hits.
            int desired=(int)Math.round((value.action().endsWith("/charge")?2:longScar?(progress<.47?4:12):cn.zgnhit.mcwuwa.core.ActionLightRules.supports(value.action())?powerful?8:4:powerful?16:6)*intensity);
            desired=switch(game.options.particles().get()){case ALL->desired;case DECREASED->desired/2;case MINIMAL->Math.min(desired,2);};
            int count=budget.grant(now,desired,ClientVisualConfig.PARTICLE_BUDGET.get());if(count==0)continue;
            int emitted=0;
            int color=actionColor(definition.characterId(),definition.element());
            var look=actor.getLookAngle();var horizontal=net.minecraft.world.phys.Vec3.directionFromRotation(0,actor.getYRot());
            var side=new net.minecraft.world.phys.Vec3(-horizontal.z,0,horizontal.x);
            var origin=actor.position().add(0,actor.getBbHeight()*.65,0);
            net.minecraft.world.phys.Vec3 mouth=null;
            if(value.action().startsWith("jinhsi/epiphany_"))for(var entity:game.level.entitiesForRendering())
                if(entity instanceof cn.zgnhit.mcwuwa.platform.entity.ConstructEntity construct&&construct.modelId().equals("jue_dragon")&&construct.animation().equals("breath")&&construct.ownerUuid().equals(entry.getKey().toString())) {
                    var mesh=cn.zgnhit.mcwuwa.client.presentation.ActionPresentationRegistry.mesh("jue_dragon");if(mesh.isEmpty())continue;
                    var prepared=cn.zgnhit.mcwuwa.client.presentation.SegmentedMeshRenderer.prepare(mesh.get(),cn.zgnhit.mcwuwa.client.presentation.ActionPresentationRegistry.modelPose("jue_dragon","breath",(float)progress),null,false,false);
                    var point=prepared.bonePoint("jaw",0,33,-34);if(point.isPresent()) {
                        var p=point.get().mul(construct.visualScale()).rotateY((float)Math.toRadians(180-construct.getYRot()));
                        mouth=construct.position().add(p.x,p.y,p.z);break;
                    }
                }
            var ground=origin;
            if(longScar||slug.equals("lynae")&&value.action().matches("lynae/(visual_impact|rainbow_splash)")) {
                var anchor=longScar?value.origin():actor.position();
                ground=anchor;
                var floor=game.level.clip(new net.minecraft.world.level.ClipContext(anchor.add(0,.25,0),anchor.add(0,-16,0),net.minecraft.world.level.ClipContext.Block.COLLIDER,net.minecraft.world.level.ClipContext.Fluid.NONE,actor));
                if(floor.getType()==net.minecraft.world.phys.HitResult.Type.BLOCK)ground=floor.getLocation().add(0,.1,0);
            }
            for(int i=0;i<count;i++) {
                double u=(i+.5)/count,angle=u*Math.PI*2+progress*5;net.minecraft.world.phys.Vec3 point;
                int shade=color;
                var profile=stateColor(entry.getKey(),value.action(),actionSample(value.action(),progress,u,elapsed,color));
                profile=MornyePilotVfx.aimedSample(value.action(),progress,u,actor.getXRot(),profile);
                profile=IunoMoonBowVfx.aimedSample(value.action(),progress,u,actor.getXRot(),profile);
                if(longScar) {
                    // MC-authored26-block light scar follows the visible sword line. It is decorative;
                    // actual hit geometry is still the separately audited server action mapping.
                    var forward=value.forward();var strokeSide=new net.minecraft.world.phys.Vec3(-forward.z,0,forward.x);
                    // Four interleaved samples fill the previous fixed-position gaps using
                    // the same particle count/budget. Most particles describe the main stroke.
                    u=(i+((elapsed/2)%4+.5)/4)/count;
                    double distance=1.2+u*24.8,edge=i%6==1?(i%12<6?1:-1)*Math.sin(u*Math.PI)*.4:0;
                    point=ground.add(forward.scale(distance)).add(strokeSide.scale(-.9+edge)).add(0,.14+Math.max(0,1-progress/.47)*(1-u)*4.2,0);
                    if(progress>=.47) {
                        var surface=game.level.clip(new net.minecraft.world.level.ClipContext(point.add(0,3,0),point.add(0,-16,0),net.minecraft.world.level.ClipContext.Block.COLLIDER,net.minecraft.world.level.ClipContext.Fluid.NONE,actor));
                        if(surface.getType()!=net.minecraft.world.phys.HitResult.Type.BLOCK)continue;
                        point=surface.getLocation().add(0,.14,0);
                    }
                    shade=i%4==0?0xFFF3FFF8:0xFF72EDBB;
                }
                else if(mouth!=null)point=mouth.lerp(AttackVisualAim.endpoint(actor,1,12),u).add(side.scale(Math.sin(angle)*.09));
                else if(profile!=null) {
                    var forward=value.forward();var localSide=new net.minecraft.world.phys.Vec3(-forward.z,0,forward.x);
                    point=origin.add(forward.scale(profile.forward())).add(localSide.scale(profile.side())).add(0,profile.up(),0);
                    shade=profile.color();
                }
                else if(slug.equals("lynae")&&value.action().matches("lynae/(visual_impact|rainbow_splash)")) {
                    double radius=1.2+Math.sin(progress*Math.PI)*3.5;point=ground.add(Math.cos(angle)*radius,.03,Math.sin(angle)*radius);
                    shade=switch(i%3){case 0->0xFF5DE2EF;case 1->0xFFFF67B2;default->0xFFF8EE67;};
                } else if(value.action().equals("cartethyia/liberation_finale")||value.action().equals("aemeath/liberation_finale"))
                    point=actor.position().add(look.multiply(1,0,1).scale(2+u*5)).add(side.scale((i%2==0?1:-1)*.15)).add(0,Math.max(.08,(1-progress*3)*(1-u)*4),0);
                else if(slug.equals("mornye")||slug.equals("phrolova")||slug.equals("hsin"))
                    point=origin.add(Math.cos(angle)*(powerful?1.8:1),Math.sin(angle*2)*.35,Math.sin(angle)*(powerful?1.8:1));
                else if(slug.equals("yinlin"))point=origin.add(look.scale(u*(powerful?4:2))).add(side.scale(Math.sin(u*18+elapsed)*.16)).add(0,Math.cos(u*14)*.12,0);
                else if(slug.equals("yangyang_xuanling"))point=origin.add(look.scale(1+progress)).add(side.scale(Math.sin(angle)*1.2)).add(0,u*.8-.2,0);
                else if(slug.equals("aero_rover")||slug.equals("camellya"))point=origin.add(Math.cos(angle)*(1+u),u*.8-.2,Math.sin(angle)*(1+u));
                else point=origin.add(look.scale(1.1)).add(side.scale(Math.sin(-1.4+u*2.8)*(powerful?1.7:1))).add(0,Math.cos(-1.4+u*2.8)*.5-.3,0);
                net.minecraft.core.particles.ParticleOptions particle;
                if(profile!=null){if(emitProfile(point,profile,u))emitted++;continue;}
                if(longScar&&progress>=.47&&i==0&&elapsed%6==0)particle=net.minecraft.core.particles.ParticleTypes.SWEEP_ATTACK;
                else if(mouth!=null&&i%4==0||longScar&&i%4==0)particle=net.minecraft.core.particles.ParticleTypes.END_ROD;
                else particle=new DustParticleOptions(shade,longScar?3.2f:powerful?.85f:.55f);
                if(slug.equals("ciaccona")&&i%6==0)
                    game.level.addParticle(net.minecraft.core.particles.ParticleTypes.NOTE,point.x,point.y,point.z,(i%12)/24.0,0,0);
                else game.level.addParticle(particle,point.x,point.y,point.z,0,.005,0);
                emitted++;
                if(longScar) {double distance=1.2+u*24.8;var previous=scarSeen.get(entry.getKey());boolean same=previous!=null&&previous.start()==value.start();
                    int landed=(same?previous.groundParticles():0)+(progress>=.32?1:0);
                    long landedAt=progress>=.32?now:same?previous.groundTick():Long.MIN_VALUE;
                    scarSeen.put(entry.getKey(),new ScarSeen(value.start(),same?Math.max(distance,previous.forwardDistance()):distance,landed,landedAt));}
            }
            if(emitted>0)timedSeen.put(entry.getKey(),new ActionSeen(value.action(),now,value.start(),value.sequence(),emitted));
        }
    }
    /** Authored action palettes follow observed skill light, independently of damage element. */
    private static int actionColor(String role,String element){
        return switch(role){
            case "mornye","cartethyia","yangyang_xuanling"->0xff8cdcff;
            case "aemeath"->0xffffb6f2;
            case "lynae"->0xff86fff0;
            case "hsin","phrolova","camellya"->0xffff526f;
            case "hiyuki"->0xffffdce7;
            default->switch(element){case "aero"->0xff72edbb;case "havoc"->0xffff6c9e;case "spectro"->0xffffe997;case "fusion"->0xffffaa55;case "electro"->0xffcb94ff;default->0xff74dfff;};
        };
    }
    private static NewRoleVfxProfiles.Sample actionSample(String action,double progress,double u,long elapsed,int color){
        var profile=NewRoleVfxProfiles.sample(action,progress,u,elapsed);
        return profile==null&&action.endsWith("/charge")?NewRoleVfxProfiles.charge(color,u,elapsed):profile;
    }
    private static boolean emitProfile(net.minecraft.world.phys.Vec3 point,NewRoleVfxProfiles.Sample sample,double u){
        var game=Minecraft.getInstance();if(game.level==null||sample.kind()==NewRoleVfxProfiles.Kind.NONE)return false;
        net.minecraft.core.particles.ParticleOptions particle=switch(sample.kind()){
            case STAR->net.minecraft.core.particles.ParticleTypes.END_ROD;
            case NOTE->net.minecraft.core.particles.ParticleTypes.NOTE;
            case SLASH->net.minecraft.core.particles.ParticleTypes.SWEEP_ATTACK;
            case DUST->new DustParticleOptions(sample.color(),sample.size());
            case STONE->new net.minecraft.core.particles.BlockParticleOption(net.minecraft.core.particles.ParticleTypes.BLOCK,net.minecraft.world.level.block.Blocks.STONE.defaultBlockState());
            case NONE->throw new IllegalStateException("quiet sample must not be emitted");
        };
        game.level.addParticle(particle,point.x,point.y,point.z,sample.kind()==NewRoleVfxProfiles.Kind.NOTE?u*.7:0,sample.kind()==NewRoleVfxProfiles.Kind.STONE?.025:0,0);
        return true;
    }
    private static NewRoleVfxProfiles.Sample stateColor(UUID actor,String action,NewRoleVfxProfiles.Sample sample){
        var game=Minecraft.getInstance();
        if(sample==null||!action.equals("ciaccona/liberation_note")||game.player==null||!actor.equals(game.player.getUUID()))return sample;
        for(int slot=0;slot<3;slot++){
            var role=CombatClient.roleView(slot);
            if(role.has("weapon")&&role.get("weapon").getAsString().equals("woodland_aria")&&role.has("rhythmColor"))
                return new NewRoleVfxProfiles.Sample(sample.side(),sample.up(),sample.forward(),role.get("rhythmColor").getAsInt()==2?0xffffdf83:0xff82f3d0,sample.size(),NewRoleVfxProfiles.Kind.DUST);
        }
        return sample;
    }
    public static void accept(Network.Impact impact) {
        var game=Minecraft.getInstance();if(game.player==null||game.level==null)return;
        if(!game.level.dimension().identifier().toString().equals(impact.dimension()))return;
        long now=game.level.getGameTime();if(impact.tick()>now+20||now-impact.tick()>80)return;
        var previous=seen.get(impact.owner());if(previous!=null&&impact.sequence()<=previous.sequence())return;
        if(game.player.distanceToSqr(impact.x(),impact.y(),impact.z())>4096)return;
        double intensity=impact.owner().equals(game.player.getUUID())?ClientVisualConfig.OWN_INTENSITY.get():ClientVisualConfig.OTHER_INTENSITY.get();
        int desired=(int)Math.round((impact.style()>=2?24:impact.style()==1?8:14)*intensity);
        desired=switch(game.options.particles().get()){case ALL->desired;case DECREASED->desired/2;case MINIMAL->Math.min(desired,1);};
        int count=budget.grant(now,desired,ClientVisualConfig.PARTICLE_BUDGET.get());
        seen.put(impact.owner(),new Seen(impact.sequence(),impact.tick(),count,impact.style()));if(count==0)return;
        int color=switch(Element.values()[impact.element()]) {
            case GLACIO->0xFF74DFFF;case FUSION->0xFFFFAA55;case ELECTRO->0xFFCB94FF;case AERO->0xFF72EDBB;
            case SPECTRO->0xFFFFE997;case HAVOC->0xFFFF6C9E;case PHYSICAL->0xFFCDD8E0;
        };
        var dust=new DustParticleOptions(color,(float)(.55+.35*intensity));
        var core=new DustParticleOptions(0xFFF4FBFF,(float)(.35+.2*intensity));
        if(impact.style()==5) {
            // Candidate from BV1FMFRziEjY 238.3s / 262.0s: white expanding
            // perimeter and violet vertical filaments. Attribution is inferred
            // by comparison, not claimed frame-exact (see response research).
            var actor=game.level.getPlayerByUUID(impact.owner());
            double fx=actor==null?0:impact.x()-actor.getX(),fz=actor==null?1:impact.z()-actor.getZ();
            double length=Math.hypot(fx,fz);if(length<.001){fx=0;fz=1;length=1;}
            double rx=fz/length,rz=-fx/length;
            for(int i=0;i<count;i++) {
                double side,up,velocitySide=0,velocityUp=0;int shade;float size;
                if(i%4==0) {
                    // Two slender vertical strands, not a solid wall hiding the enemy.
                    double u=(i/4+.5)/Math.max(1,Math.ceil(count/4d));
                    side=(i%8==0?-.12:.12);up=-1.15+u*2.8;
                    shade=0xFFD7B8FF;size=1.15f;velocityUp=.025;
                } else {
                    double angle=Math.TAU*i/count,radius=i%2==0?1.05:1.28;
                    side=Math.cos(angle)*radius;up=Math.sin(angle)*radius;
                    shade=i%4==1?0xFFFFD7EF:0xFFFFF7EE;size=1.4f;
                    velocitySide=side*.075;velocityUp=up*.075;
                }
                game.level.addParticle(new DustParticleOptions(shade,(float)(size*Math.min(1.5,intensity))),
                    impact.x()+rx*side-fx/length*.65,impact.y()+up,impact.z()+rz*side-fz/length*.65,
                    rx*velocitySide,velocityUp,rz*velocitySide);
            }
            return;
        }
        if(impact.style()==4) {
            // Spectral Analysis is crossed paint splashes, distinct from the persistent rainbow.
            // The plane faces the attacker, not the local camera, so observers share its orientation.
            var actor=game.level.getPlayerByUUID(impact.owner());
            double fx=actor==null?0:impact.x()-actor.getX(),fz=actor==null?1:impact.z()-actor.getZ();
            double length=Math.hypot(fx,fz);if(length<.001){fx=0;fz=1;length=1;}
            double rx=fz/length,rz=-fx/length;
            int[] palette={0xFF39E6F2,0xFFEE68CF,0xFFFFDF59};
            for(int i=0;i<count;i++) {
                int arm=i%4,layer=(i/4)%3;
                double reach=.12+1.15*((i/4+.5)/Math.max(1,Math.ceil(count/4.0)));
                double side=(arm<2?1:-1)*reach,up=(arm%2==0?1:-1)*reach*.62;
                double offset=(layer-1)*.065;
                var paint=new DustParticleOptions(palette[layer],(float)((2.4-reach*.6)*Math.min(1.5,intensity)));
                // Move the decorative plane toward the source, outside a normal target's torso.
                // A center-of-body particle cloud is hidden by the entity in both camera modes.
                game.level.addParticle(paint,impact.x()+rx*side-fx/length*.65,impact.y()+up+offset,impact.z()+rz*side-fz/length*.65,
                    rx*side*.09,up*.09,rz*side*.09);
            }
            return;
        }
        // Mornye response: observed white-hot core, orange jet and dark outer plume.
        // Counts, size and particle lifetime are MC adaptation, not video freeze duration.
        if(impact.style()==3) {
            var ember=new DustParticleOptions(0xFFFF893E,(float)(1.4+.4*intensity));
            var smoke=new DustParticleOptions(0xFF45352F,(float)(1.6+.4*intensity));
            var flash=new DustParticleOptions(0xFFFFF2DC,(float)(1.5+.4*intensity));
            var actor=game.level.getPlayerByUUID(impact.owner());
            double fx=actor==null?0:impact.x()-actor.getX(),fz=actor==null?1:impact.z()-actor.getZ();
            double length=Math.hypot(fx,fz);if(length<.001){fx=0;fz=1;length=1;}
            double rx=fz/length,rz=-fx/length;
            for(int i=0;i<count;i++) {
                double angle=Math.PI*2*i/count,dx=Math.cos(angle),dz=Math.sin(angle);
                boolean hot=i%4==0,outer=i%4==3;
                double radius=hot?.18:outer?.85:.52,speed=outer?.035:.11;
                game.level.addParticle(hot?flash:outer?smoke:ember,
                    impact.x()+rx*dx*radius-fx/length*.65,impact.y()+dz*radius,impact.z()+rz*dx*radius-fz/length*.65,
                    rx*dx*speed,dz*speed,rz*dx*speed);
            }
            return;
        }
        for(int i=0;i<count;i++) {
            double angle=Math.PI*2*(i/(double)count+(impact.sequence()%17)/17.0),radius=impact.style()==2?.55:.25;
            double dx=Math.cos(angle),dz=Math.sin(angle);
            // Alternate a bright contact core, colored perimeter and outward sparks.
            // Every layer still consumes one existing particle-budget allowance.
            boolean center=i%4==0,spark=i%4==1;
            double reach=center?.12:radius,speed=spark?(impact.style()==2?.13:.09):.025;
            game.level.addParticle(center?core:dust,impact.x()+dx*reach,impact.y()+(i%3-1)*.10,impact.z()+dz*reach,
                dx*speed,spark?.055+(i%3)*.025:.015,dz*speed);
        }
    }
    /** Native acceptance inspects actual S2C submission; this is not a fabricated gameplay event. */
    static boolean submittedSince(UUID owner,long tick){var value=seen.get(owner);return value!=null&&value.tick()>=tick&&value.submitted()>0;}
    static boolean submittedStyleSince(UUID owner,long tick,int style){var value=seen.get(owner);return value!=null&&value.tick()>=tick&&value.submitted()>0&&value.style()==style;}
    // Match starts in the server clock. Reception may precede that start in the client clock,
    // within the same skew tolerated above; receivedTick remains the real arrival time.
    static boolean actionSubmittedSince(UUID owner,String action,long tick){var value=actionSeen.get(owner);return value!=null&&value.serverStartTick()>=tick&&value.action().equals(action)&&value.submitted()>0;}
    /** Read-only native-test evidence keeps reception time distinct from the authoritative start. */
    static String actionSubmissionEvidence(UUID owner){return "initial="+actionSeen.get(owner)+"; timed="+timedSeen.get(owner);}
    static boolean timedSubmitted(UUID owner,String action,long start){var value=timedSeen.get(owner);return value!=null&&value.action().equals(action)&&value.serverStartTick()==start&&value.submitted()>0;}
    static double scarDistance(UUID owner,long start){var value=scarSeen.get(owner);return value!=null&&value.start()==start?value.forwardDistance():0;}
    static int scarGroundParticles(UUID owner,long start){var value=scarSeen.get(owner);return value!=null&&value.start()==start?value.groundParticles():0;}
    static long scarGroundTick(UUID owner,long start){var value=scarSeen.get(owner);return value!=null&&value.start()==start?value.groundTick():Long.MIN_VALUE;}
    public static void clear(){seen.clear();actionSeen.clear();timedSeen.clear();scarSeen.clear();timelines.clear();effectsTick=Long.MIN_VALUE;budget=new TickBudget(20);}
}
