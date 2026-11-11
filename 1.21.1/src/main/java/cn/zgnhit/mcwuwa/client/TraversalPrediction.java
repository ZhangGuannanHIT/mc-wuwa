package cn.zgnhit.mcwuwa.client;

import cn.zgnhit.mcwuwa.platform.TraversalMotion;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;

/** Local movement only after a fresh server-owned form/movement approval.
 * Never grants stamina, edits a role, decides attack windows or predicts damage. */
final class TraversalPrediction {
    private static LocalPlayer owner;
    private static boolean ownsGravity,originalNoGravity;
    private TraversalPrediction() { }
    static void initialize(){NeoForge.EVENT_BUS.addListener(net.neoforged.bus.api.EventPriority.LOWEST,TraversalPrediction::rootedInput);NeoForge.EVENT_BUS.addListener(TraversalPrediction::tick);NeoForge.EVENT_BUS.addListener(net.neoforged.bus.api.EventPriority.LOWEST,TraversalPrediction::driftTick);}
    private static void rootedInput(net.neoforged.neoforge.client.event.MovementInputUpdateEvent event){
        var game=Minecraft.getInstance();var role=CombatClient.roleView(CombatClient.activeSlot());
        if(game.player==null||game.level==null||game.screen!=null||game.getOverlay()!=null||event.getEntity()!=game.player||!CombatClient.wuwaMode()
            ||game.level.getGameTime()-(long)CombatHud.number(CombatClient.view(),"tick")>12
            ||!role.has("weapon")||!role.has("form"))return;
        boolean vine=role.get("weapon").getAsString().equals("red_spring")&&role.get("form").getAsString().equals("bloom");
        boolean exhaustedObservation=role.get("weapon").getAsString().equals("starfield_calibrator")&&role.get("form").getAsString().equals("observation")
            &&role.has("movement")&&role.getAsJsonObject("movement").has("mode")
            &&role.getAsJsonObject("movement").get("mode").getAsString().startsWith("mornye_exhausted");
        boolean heart=role.get("weapon").getAsString().equals("unspoken_rue")&&role.get("form").getAsString().equals("calamity");
        if(!vine&&!exhaustedObservation&&!heart)return;
        if(heart){
            var input=event.getInput();
            input.up=input.down=input.left=input.right=input.jumping=false;
        }
        // Vanilla air steering uses its own acceleration, independent of movement
        // speed attributes. Stop translation at the vector seam; only Heart also forbids jump.
        event.getInput().forwardImpulse=0; event.getInput().leftImpulse=0;
    }
    private static void driftTick(ClientTickEvent.Pre event){
        var game=Minecraft.getInstance();var player=game.player;var role=CombatClient.roleView(CombatClient.activeSlot());
        if(player==null||game.level==null||game.screen!=null||game.getOverlay()!=null||!player.isAlive()||!CombatClient.wuwaMode()||!role.has("movement")||player.isPassenger()
            ||game.level.getGameTime()-(long)CombatHud.number(CombatClient.view(),"tick")>12)return;
        if(cinematicPaused())return;
        var movement=role.getAsJsonObject("movement");long until=(long)CombatHud.number(movement,"driftUntil");
        if(game.level.getGameTime()<(long)CombatHud.number(movement,"driftStart")||until<=game.level.getGameTime()||until-game.level.getGameTime()>20)return;
        double x=CombatHud.number(movement,"driftX"),z=CombatHud.number(movement,"driftZ");
        if(!Double.isFinite(x)||!Double.isFinite(z)||x*x+z*z>.36)return;
        var velocity=player.getDeltaMovement();player.setDeltaMovement(x,velocity.y,z);
    }
    private static void tick(ClientTickEvent.Pre event) {
        var game=Minecraft.getInstance();var player=game.player;var role=CombatClient.roleView(CombatClient.activeSlot());
        if(player!=owner){clear();owner=player;}
        // A GUI must not restore the pre-flight gravity over the server's
        // current cinematic air hold. Disconnect/death/stale views still clean up.
        if(player!=null&&game.level!=null&&player.isAlive()&&CombatClient.wuwaMode()&&cinematicPaused()
            &&game.level.getGameTime()-(long)CombatHud.number(CombatClient.view(),"tick")<=12)return;
        if(player==null||game.level==null||game.screen!=null||game.getOverlay()!=null||!player.isAlive()||!CombatClient.wuwaMode()||!role.has("movement")
            ||!role.has("weapon")||!role.has("form")
            ||game.level.getGameTime()-(long)CombatHud.number(CombatClient.view(),"tick")>12
            ||player.isFallFlying()||player.isPassenger()||player.onClimbable()||player.getAbilities().flying) {releaseGravity();return;}
        var movement=role.getAsJsonObject("movement");String mode=movement.has("mode")?movement.get("mode").getAsString():"";
        if(mode.equals("suoming_heart")&&role.get("weapon").getAsString().equals("unspoken_rue")&&role.get("form").getAsString().equals("calamity")){
            releaseGravity();var velocity=player.getDeltaMovement();player.setDeltaMovement(0,Math.min(0,velocity.y),0);player.setSprinting(false);return;
        }
        boolean swordFlight=mode.equals("qingxiao_sword_flight")&&role.get("weapon").getAsString().equals("glint_of_clouds");
        boolean moonCruise=mode.equals("iuno_phase_cruise")&&role.get("weapon").getAsString().equals("moongazers_sigil")&&!role.get("form").getAsString().equals("normal");
        if(swordFlight||moonCruise) {
            if(player.isInWater()||TraversalMotion.solidSupport(player)){releaseGravity();return;}
            acquireGravity(player,movement);
            TraversalMotion.apply(player,mode,0,(game.options.keyLeft.isDown()?1:0)-(game.options.keyRight.isDown()?1:0),
                (game.options.keyUp.isDown()?1:0)-(game.options.keyDown.isDown()?1:0),false,false,game.options.keySprint.isDown());return;
        }
        if((mode.equals("phoebe_staff_glide")||mode.equals("phoebe_staff_dive"))&&role.get("weapon").getAsString().equals("luminous_hymn")
            ||(mode.equals("cantarella_surf")||mode.equals("cantarella_swim"))&&role.get("weapon").getAsString().equals("whispers_of_sirens")){
            // Stop locally on the same terrain boundary as the server. The last
            // approved snapshot may otherwise keep propelling a landed caster.
            if(mode.startsWith("phoebe_staff_")&&(TraversalMotion.solidSupport(player)||player.isInWater())
                ||mode.equals("cantarella_swim")&&!player.isInWater()){releaseGravity();return;}
            if(mode.equals("cantarella_swim")&&!player.getAbilities().instabuild&&CombatHud.number(CombatClient.view(),"stamina")<TraversalMotion.CANTARELLA_SWIM_STAMINA_COST){releaseGravity();return;}
            if(mode.equals("cantarella_swim"))releaseGravity();else acquireGravity(player,movement);
            TraversalMotion.apply(player,mode,0,(game.options.keyLeft.isDown()?1:0)-(game.options.keyRight.isDown()?1:0),(game.options.keyUp.isDown()?1:0)-(game.options.keyDown.isDown()?1:0),game.options.keyJump.isDown(),game.options.keyShift.isDown(),game.options.keySprint.isDown());return;
        }
        if(mode.equals("shorekeeper_butterfly")&&role.get("weapon").getAsString().equals("stellar_symphony")){
            releaseGravity();TraversalMotion.apply(player,mode,0,(game.options.keyLeft.isDown()?1:0)-(game.options.keyRight.isDown()?1:0),1);return;
        }
        if(mode.equals("combat_hover")||mode.equals("combat_leap")||mode.equals("lynae_leap")) {
            acquireGravity(player,movement);
            boolean rooted=role.get("weapon").getAsString().equals("red_spring")&&role.get("form").getAsString().equals("bloom");
            TraversalMotion.apply(player,mode,0,rooted?0:(game.options.keyLeft.isDown()?1:0)-(game.options.keyRight.isDown()?1:0),
                rooted?0:(game.options.keyUp.isDown()?1:0)-(game.options.keyDown.isDown()?1:0));return;
        }
        if(mode.equals("camellya_bloom")&&role.get("weapon").getAsString().equals("red_spring")&&role.get("form").getAsString().equals("bloom")) {
            if(TraversalMotion.solidSupport(player))releaseGravity();else acquireGravity(player,movement);
            TraversalMotion.apply(player,mode,0,(game.options.keyLeft.isDown()?1:0)-(game.options.keyRight.isDown()?1:0),
                (game.options.keyUp.isDown()?1:0)-(game.options.keyDown.isDown()?1:0));return;
        }
        if(mode.equals("cantarella_mirage")&&role.get("weapon").getAsString().equals("whispers_of_sirens")&&role.get("form").getAsString().equals("mirage")){
            var floor=player.level().clip(new net.minecraft.world.level.ClipContext(player.position().add(0,1,0),player.position().add(0,-3,0),
                net.minecraft.world.level.ClipContext.Block.COLLIDER,net.minecraft.world.level.ClipContext.Fluid.ANY,player));
            if(floor.getType()==net.minecraft.world.phys.HitResult.Type.MISS){releaseGravity();return;}
            acquireGravity(player,movement);
            TraversalMotion.apply(player,mode,0,(game.options.keyLeft.isDown()?1:0)-(game.options.keyRight.isDown()?1:0),
                (game.options.keyUp.isDown()?1:0)-(game.options.keyDown.isDown()?1:0));
            var velocity=player.getDeltaMovement();player.setDeltaMovement(velocity.x,Math.clamp(floor.getLocation().y+.35-player.getY(),-.12,.12),velocity.z);return;
        }
        if(mode.equals("ciaccona_recital")&&role.get("weapon").getAsString().equals("woodland_aria")&&role.get("form").getAsString().equals("performing")){releaseGravity();TraversalMotion.apply(player,mode,0,0,0);return;}
        boolean chisa=role.get("weapon").getAsString().equals("kumokiri")&&!role.get("form").getAsString().equals("saw")
            &&(mode.equals("chisa_glide")||mode.equals("chisa_glide_dash"));
        boolean hsin=role.get("weapon").getAsString().equals("blooming_jadehaven")&&role.get("form").getAsString().equals("illuminated")
            &&(mode.equals("hsin_water")||mode.equals("hsin_air"));
        boolean fleur=role.get("weapon").getAsString().equals("defiers_thorn")&&role.get("form").getAsString().equals("fleurdelys")
            &&(mode.equals("fleurdelys_water")||mode.equals("fleurdelys_air"));
        boolean realm=role.get("weapon").getAsString().equals("thunderflare_dominion")&&role.get("form").getAsString().equals("realm")&&mode.equals("augusta_water");
        boolean observation=role.get("weapon").getAsString().equals("starfield_calibrator")&&role.get("form").getAsString().equals("observation")
            &&(mode.equals("mornye_hover")||mode.equals("mornye_sprint")||mode.equals("mornye_descent")||mode.equals("mornye_exhausted")||mode.equals("mornye_exhausted_descent"));
        boolean qiuyuan=role.get("weapon").getAsString().equals("emerald_sentence")&&mode.equals("qiuyuan_dash");
        boolean flight=role.get("weapon").getAsString().equals("everbright_polestar")&&role.get("form").getAsString().equals("mech")&&mode.equals("aemeath_flight");
        boolean wandering=role.get("weapon").getAsString().equals("blooming_jadehaven")&&role.get("form").getAsString().equals("worldly")&&mode.equals("hsin_wander");
        if(!chisa&&!hsin&&!fleur&&!realm&&!observation&&!flight&&!wandering&&!qiuyuan){releaseGravity();return;}
        if(!observation&&!flight&&!wandering&&!qiuyuan&&(game.options.keyJump.isDown()||game.options.keyShift.isDown())){releaseGravity();return;}
        if(observation||flight||wandering||qiuyuan) {
            if(!flight&&!observation&&CombatHud.number(CombatClient.view(),"stamina")<.25){releaseGravity();return;}
            if(observation)mode=TraversalMotion.observationMode(mode.endsWith("descent"),mode.equals("mornye_sprint"),CombatHud.number(CombatClient.view(),"stamina"));
            acquireGravity(player,movement);
            TraversalMotion.apply(player,mode,0,(game.options.keyLeft.isDown()?1:0)-(game.options.keyRight.isDown()?1:0),
                (game.options.keyUp.isDown()?1:0)-(game.options.keyDown.isDown()?1:0),flight&&game.options.keyJump.isDown(),flight&&game.options.keyShift.isDown(),flight&&game.options.keySprint.isDown());return;
        }
        if(chisa) {
            if(mode.equals("chisa_glide")&&!game.options.keySprint.isDown()||player.isInWater()){releaseGravity();return;}
            acquireGravity(player,movement);
            TraversalMotion.apply(player,mode,0,(game.options.keyLeft.isDown()?1:0)-(game.options.keyRight.isDown()?1:0),
                (game.options.keyUp.isDown()?1:0)-(game.options.keyDown.isDown()?1:0));return;
        }
        double y=CombatHud.number(movement,"supportY");
        if(!Double.isFinite(y)||Math.abs(y-player.getY())>.6||!realm&&CombatHud.number(CombatClient.view(),"stamina")<.25){releaseGravity();return;}
        if(mode.endsWith("_air")&&game.options.keySprint.isDown()){releaseGravity();return;}
        acquireGravity(player,movement);
        TraversalMotion.apply(player,mode,y,(game.options.keyLeft.isDown()?1:0)-(game.options.keyRight.isDown()?1:0),
            (game.options.keyUp.isDown()?1:0)-(game.options.keyDown.isDown()?1:0));
    }
    private static boolean cinematicPaused(){
        var view=CombatClient.view();return view.has("cinematicPaused")&&view.get("cinematicPaused").getAsBoolean();
    }
    static void clear(){releaseGravity();owner=null;}
    static boolean supportedOnWater() {
        var game=Minecraft.getInstance();var role=CombatClient.roleView(CombatClient.activeSlot());
        return game.player!=null&&game.level!=null&&CombatClient.wuwaMode()&&role.has("movement")
            &&(role.getAsJsonObject("movement").get("mode").getAsString().equals("hsin_water")
                ||role.getAsJsonObject("movement").get("mode").getAsString().equals("fleurdelys_water"))
            &&game.level.getGameTime()-(long)CombatHud.number(CombatClient.view(),"tick")<=12
            &&TraversalMotion.waterSurface(game.player).isPresent();
    }
    /** The current flag may already be the server's replicated hover grant. */
    static boolean approvedBaseline(com.google.gson.JsonObject movement,boolean currentNoGravity){
        return movement.has("gravityOwned")&&movement.get("gravityOwned").getAsBoolean()&&movement.has("baseNoGravity")
            ?movement.get("baseNoGravity").getAsBoolean():currentNoGravity;
    }
    private static void acquireGravity(LocalPlayer player,com.google.gson.JsonObject movement){
        if(!ownsGravity){originalNoGravity=approvedBaseline(movement,player.isNoGravity());ownsGravity=true;}
        else if(movement.has("gravityOwned")&&movement.get("gravityOwned").getAsBoolean()&&movement.has("baseNoGravity"))
            originalNoGravity=movement.get("baseNoGravity").getAsBoolean();
        player.setNoGravity(true);
    }
    private static void releaseGravity(){if(ownsGravity&&owner!=null)owner.setNoGravity(originalNoGravity);ownsGravity=false;}
}
