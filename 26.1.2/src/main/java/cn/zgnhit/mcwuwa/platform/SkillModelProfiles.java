package cn.zgnhit.mcwuwa.platform;

import cn.zgnhit.mcwuwa.core.CharacterInstance;
import cn.zgnhit.mcwuwa.platform.entity.ConstructEntity;
import java.util.*;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;

/** Short action-bound visuals reuse the shipped meshes. Placement/scale are MC art parameters;
 * damage and persistent independent summons are owned by their existing executors. */
public final class SkillModelProfiles {
    private record Profile(String model,double right,double up,double forward,float scale,String animation) { }
    private static final Map<String,Profile> PROFILES=Map.ofEntries(
        Map.entry("lynae/intro",new Profile("survey_motorcycle",0,.05,0,1f,"attack")),
        // Keep the wide cloak outside the owner's first-person sight line.
        // This visual placement does not move the authoritative hit geometry.
        Map.entry("phrolova/intro",new Profile("hecate",2.8,0,3,1f,"intro")),
        Map.entry("phrolova/intro_eternal",new Profile("hecate",2.8,0,3,1f,"intro_eternal")),
        Map.entry("sigrika/skill_sun",new Profile("sigrika_sun_sprite",.8,.2,1.2,1f,"attack")),
        Map.entry("sigrika/rune_sun",new Profile("sigrika_sun_sprite",.8,.2,1.2,1f,"attack")),
        Map.entry("sigrika/heavy_origin_sun",new Profile("sigrika_sun_sprite",.8,.2,1.2,1f,"attack")),
        Map.entry("iuno/liberation",new Profile("iuno_new_moon_platform",0,.75,0,1.3f,"attack")),
        Map.entry("lupa/liberation",new Profile("lupa_flame_wolf_heads",0,.15,1.3,1.0f,"attack")),
        Map.entry("lupa/skill_break_enemy",new Profile("lupa_flame_wolf_heads",0,.15,1.3,1.0f,"attack")),
        Map.entry("qingxiao/liberation",new Profile("qingxiao_sword_zither",0,.8,1.1,.6f,"attack")),
        Map.entry("qingxiao/heavy_banishing",new Profile("qingxiao_sword_zither",0,.8,1.1,.6f,"attack")),
        Map.entry("qingxiao/heavy_banishing_enhanced",new Profile("qingxiao_sword_zither",0,.8,1.1,.6f,"attack")),
        Map.entry("phoebe/air_glide",new Profile("skill_phoebe_staff",0,.05,0,.85f,"glide")),
        Map.entry("phoebe/air",new Profile("skill_phoebe_staff",0,.05,0,.85f,"glide")),
        Map.entry("cantarella/skill_drain",new Profile("skill_cantarella_jellyfish",0,1.6,1,1f,"attack")),
        Map.entry("jinhsi/epiphany_rays",new Profile("jue_dragon",.7,.55,-1.3,.70f,"breath")),
        Map.entry("jinhsi/epiphany_final",new Profile("jue_dragon",.7,.55,-1.3,.70f,"breath")),
        // The mesh already rises .85--4.20 local blocks at release. With .45
        // scale this puts the body 1.88--3.39 blocks above the caster, behind
        // them; a second three-block lift hid the entire dragon above camera.
        Map.entry("jinhsi/liberation",new Profile("jue_dragon",.7,.55,-1.8,.70f,"breath")),
        Map.entry("jinhsi/skill_overflowing",new Profile("jue_dragon",.7,1.5,-1.8,.38f,"overflow")),
        Map.entry("jinhsi/overflowing_radiance",new Profile("jue_dragon",.7,1.5,-1.8,.38f,"overflow")),
        Map.entry("aemeath/liberation_finale",new Profile("aemeath_mecha",1.5,.35,4.5,.78f,"plunge")),
        // These casts switch the actor form before presentation; project the departing form.
        Map.entry("aemeath/skill",new Profile("aemeath_assist",-1.2,.05,1.1,1f,"cooperative")),
        Map.entry("aemeath/skill_dawn",new Profile("aemeath_mecha",1.4,.1,1.5,.58f,"skill")),
        Map.entry("aemeath/wing_ensemble_descend",new Profile("aemeath_assist",-1.3,.3,1.1,1f,"ensemble")),
        Map.entry("aemeath/wing_ensemble_stage",new Profile("aemeath_mecha",1.4,.4,1.6,.58f,"wing_ensemble_descend")),
        Map.entry("camellya/intro",new Profile("camellya_blossom",0,.1,1,1.05f,"attack")),
        Map.entry("camellya/bloom_dark_heart",new Profile("camellya_blossom",0,0,1,1.5f,"attack")),
        Map.entry("camellya/liberation",new Profile("camellya_blossom",0,0,1,1.7f,"attack")),
        Map.entry("yinlin/magnetic_blast",new Profile("yinlin_puppet",1,0,1,.9f,"attack")),
        Map.entry("yinlin/skill",new Profile("yinlin_puppet",1,0,1,.9f,"attack")),
        Map.entry("yinlin/intro",new Profile("yinlin_puppet",1,0,1,.9f,"attack")),
        Map.entry("yinlin/skill_blast",new Profile("yinlin_puppet",1,0,1,.9f,"attack")),
        // Official223--226s: the looming puppet is behind the performer.
        Map.entry("yinlin/liberation",new Profile("yinlin_puppet",1,.8,-1.8,1.4f,"attack")),
        Map.entry("hsin/illuminated_skill",new Profile("hsin_construct",0,-1.3,3,2.4f,"attack")),
        Map.entry("hsin/illuminated_descend",new Profile("hsin_construct",0,1.1,2.7,1.8f,"attack")),
        Map.entry("hsin/illuminated_suppress",new Profile("hsin_construct",0,1.1,2.7,2.6f,"attack")),
        Map.entry("hsin/liberation_palaces",new Profile("hsin_palace_city",0,0,0,1f,"attack")),
        Map.entry("hsin/palaces_formation",new Profile("hsin_construct",0,0,2.6,.9f,"attack")));
    private SkillModelProfiles() { }
    /** Only the current cinematic's own newly spawned prop advances with its shot. */
    public static boolean cinematicModel(String action,String model){
        String id=action.startsWith("mc_wuwa:")?action.substring(8):action;
        if(!cn.zgnhit.mcwuwa.core.CinematicTimeRules.cinematic(id))return false;
        var profile=PROFILES.get(id);
        return profile!=null&&profile.model.equals(model)
            ||id.equals("cartethyia/liberation_finale")&&model.equals("skill_cart_scar");
    }
    private static boolean hasLight(String action){return cn.zgnhit.mcwuwa.core.ActionLightRules.supports(action);}
    public static ConstructEntity light(ServerPlayer player,CharacterInstance role,String action,Vec3 origin,int ticks,boolean follow){
        if(!hasLight(action))throw new IllegalArgumentException("unsupported action light");
        return ConstructEntity.spawn(player.level(),player,role.instanceId(),"skill_light_"+action.replace("/","__"),origin,Math.max(1,ticks),1,follow,"attack");
    }
    public static void departure(ServerPlayer player,CharacterInstance role){
        String action=cn.zgnhit.mcwuwa.McWuwa.catalog().requireByWeapon(role.character().weaponPath()).characterId()+"/outro";
        if(hasLight(action))light(player,role,action,player.position(),12,false);
    }
    public static List<ConstructEntity> start(ServerPlayer player,CharacterInstance role,String action,int ticks,int instrument) {
        var visuals=new ArrayList<ConstructEntity>();
        // Hecate owns its own synchronized action and renderer anchor, including off-field casts.
        if(action.startsWith("phrolova/hecate_"))return List.of();
        if(hasLight(action))visuals.add(light(player,role,action,player.position(),ticks,!action.equals("lynae/intro")));
        var profile=PROFILES.get(action);
        if(action.equals("cartethyia/liberation_finale"))visuals.add(ConstructEntity.spawn(player.level(),player,role.instanceId(),"skill_cart_scar",player.position(),ticks,1,false,"cleave"));
        boolean heldInstrument=action.equals("ciaccona/basic_04")||action.equals("ciaccona/skill")||action.equals("ciaccona/heavy_quartet");
        if(heldInstrument){
            String kind=action.equals("ciaccona/basic_04")?(instrument==2?"drum":instrument==3?"guitar":"microphone"):"guitar";
            profile=new Profile("ciaccona_"+kind,0,0,0,1,"idle");
        }
        // Full Azure/Feather flow panels show luminous slashes, not a rigid pair
        // of grey wings in front of the caster. EnhancedAttackVfx owns these cues.
        if(profile==null&&action.startsWith("yinlin/")&&(action.startsWith("yinlin/basic_")
            ||Set.of("yinlin/heavy","yinlin/air","yinlin/dodge_counter","yinlin/chameleon").contains(action)))
            profile=new Profile("yinlin_puppet",1,0,1,.9f,"attack");
        // Bloom petals are translucent light surfaces, not an additional opaque
        // construct around the camera. The attack and resources remain server-owned.
        if(profile==null&&action.equals("hiyuki/future_heavy_frost"))profile=new Profile("hiyuki_frost_blade",0,.2,1.2,1.1f,"attack");
        if(profile==null&&action.startsWith("aero_rover/unbound_wind_"))profile=new Profile("rover_wind_blade",0,.3,1,1.2f,"attack");
        if(profile!=null&&action.startsWith("yinlin/")&&!action.equals("yinlin/liberation")){
            String clip=cn.zgnhit.mcwuwa.core.PuppetAnimationRules.forAction(action);
            profile=new Profile(profile.model,profile.right,profile.up,profile.forward,profile.scale,clip);
        }
        if(profile==null)return List.copyOf(visuals);
        Vec3 forward=Vec3.directionFromRotation(0,player.getYRot()),right=new Vec3(forward.z,0,-forward.x);
        Vec3 origin=player.position().add(right.scale(profile.right)).add(0,profile.up,0).add(forward.scale(profile.forward));
        String animation=cn.zgnhit.mcwuwa.core.ActionPropAnimationRules.forAction(action,profile.animation);
        // The ridden staff travels with the actor throughout the air action;
        // stationary summons keep their independent cast anchors.
        boolean followsActor=heldInstrument||action.equals("phoebe/air_glide")||action.equals("phoebe/air");
        visuals.add(ConstructEntity.spawn(player.level(),player,role.instanceId(),profile.model,origin,ticks,profile.scale,followsActor,animation));
        return List.copyOf(visuals);
    }
    public static void end(List<ConstructEntity> visuals) { for(var entity:visuals)entity.discard(); }
}
