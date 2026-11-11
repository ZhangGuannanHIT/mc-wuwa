package cn.zgnhit.mcwuwa.client;
import cn.zgnhit.mcwuwa.core.CinematicHazardPauseState;
import cn.zgnhit.mcwuwa.platform.Network;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.world.entity.Entity;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;
import java.util.function.Consumer;
import java.util.*;
import net.neoforged.neoforge.client.event.RenderFrameEvent;

/** Server-selected enemies and their attacks stop predicting motion during a cinematic. */
final class EnemyHazardPauseClient {
    private static final CinematicHazardPauseState STATE=new CinematicHazardPauseState();
    private static ClientLevel world;
    private record Anchors(long start,long end,Map<Integer,Network.HazardAnchor> positions){}
    private static final Map<UUID,Anchors> ANCHORS=new LinkedHashMap<>();
    static Consumer<Entity> observer;
    /** Diagnostic identity of the active shot, not a scene-wide entity lifetime. */
    static String observationWindow(Entity entity){
        if(world==null)return "";long now=world.getGameTime();
        for(var entry:ANCHORS.entrySet()){
            var frame=entry.getValue();
            if(now>=frame.start()&&now<frame.end()&&frame.positions().containsKey(entity.getId()))
                return entry.getKey()+"/"+frame.start()+"/"+frame.end();
        }
        return "";
    }
    private EnemyHazardPauseClient(){}
    static void initialize(){NeoForge.EVENT_BUS.addListener(EnemyHazardPauseClient::entityTick);NeoForge.EVENT_BUS.addListener(EnemyHazardPauseClient::tick);NeoForge.EVENT_BUS.addListener(EnemyHazardPauseClient::frame);}
    private static void currentWorld(){var next=Minecraft.getInstance().level;if(world!=next){STATE.clear();ANCHORS.clear();world=next;}}
    static void accept(Network.HazardPause packet){
        currentWorld();if(world==null)return;
        STATE.accept(packet.owner(),packet.start(),packet.end(),packet.entities().stream().map(Network.HazardAnchor::entity).toList());
        if(packet.end()<=packet.start()||packet.entities().isEmpty())ANCHORS.remove(packet.owner());
        else {var positions=new LinkedHashMap<Integer,Network.HazardAnchor>();for(var anchor:packet.entities())positions.put(anchor.entity(),anchor);ANCHORS.put(packet.owner(),new Anchors(packet.start(),packet.end(),positions));}
        applyAnchors();
    }
    private static void tick(ClientTickEvent.Post event){currentWorld();if(world!=null){STATE.expire(world.getGameTime());ANCHORS.values().removeIf(a->a.end()<=world.getGameTime());applyAnchors();}}
    private static void frame(RenderFrameEvent.Pre event){currentWorld();applyAnchors();}
    private static void applyAnchors(){
        if(world==null)return;long now=world.getGameTime();
        for(var frame:ANCHORS.values())if(now>=frame.start()&&now<frame.end())for(var anchor:frame.positions().values()){
            var entity=world.getEntity(anchor.entity());if(entity!=null)applyAnchor(entity,anchor);
        }
    }
    private static void applyAnchor(Entity entity,Network.HazardAnchor anchor){
        entity.setPos(anchor.x(),anchor.y(),anchor.z());
        // The authoritative stopped position supersedes a queued network lerp.
        // Keeping that old target can pull the body backwards when ticking resumes.
        if(entity.isInterpolating())entity.getInterpolation().cancel();
        entity.setOldPosAndRot();
    }
    private static void entityTick(EntityTickEvent.Pre event){
        currentWorld();var entity=event.getEntity();
        if(world==null||entity.level()!=world||!STATE.frozen(entity.getId(),world.getGameTime()))return;
        long now=world.getGameTime();
        for(var frame:ANCHORS.values())if(now>=frame.start()&&now<frame.end()){
            var anchor=frame.positions().get(entity.getId());
            if(anchor!=null){applyAnchor(entity,anchor);break;}
        }
        entity.tickCount=Math.max(0,entity.tickCount-1);event.setCanceled(true);
        if(observer!=null)observer.accept(entity);
    }
}
