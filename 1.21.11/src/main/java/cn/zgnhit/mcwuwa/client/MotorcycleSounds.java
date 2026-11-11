package cn.zgnhit.mcwuwa.client;

import cn.zgnhit.mcwuwa.platform.entity.SurveyMotorcycleEntity;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.client.event.ClientTickEvent;

/** Original archived vehicle SFX, positional and client-only. Gameplay never depends on sound. */
public final class MotorcycleSounds {
    private static final Map<UUID,State> states=new HashMap<>();
    private static net.minecraft.client.multiplayer.ClientLevel previousLevel;
    private static final class State { Loop engine,nos;String band="";boolean occupied,boost; }
    private MotorcycleSounds() { }
    public static void initialize(){NeoForge.EVENT_BUS.addListener((ClientTickEvent.Post event)->tick());}
    private static SoundEvent sound(String suffix){return SoundEvent.createVariableRangeEvent(Identifier.fromNamespaceAndPath("mc_wuwa","motorcycle."+suffix));}
    private static void once(SurveyMotorcycleEntity bike,String suffix){bike.level().playLocalSound(bike.getX(),bike.getY(),bike.getZ(),sound(suffix),SoundSource.PLAYERS,.45f,1,false);}
    private static void tick(){
        var game=Minecraft.getInstance();if(game.level!=previousLevel){for(var state:states.values())stop(state);states.clear();previousLevel=game.level;}
        if(game.level==null||game.player==null)return;var live=new HashSet<UUID>();
        if(game.options.getSoundSourceVolume(SoundSource.MASTER)<=0||game.options.getSoundSourceVolume(SoundSource.PLAYERS)<=0){for(var state:states.values())stop(state);states.clear();return;}
        for(var entity:game.level.entitiesForRendering())if(entity instanceof SurveyMotorcycleEntity bike&&bike.distanceToSqr(game.player)<4096){
            live.add(bike.getUUID());var state=states.computeIfAbsent(bike.getUUID(),key->new State());boolean occupied=bike.isVehicle(),boost=occupied&&bike.boosting();
            if(occupied&&!state.occupied)once(bike,"mount");else if(!occupied&&state.occupied)once(bike,"dismount");
            if(boost&&!state.boost)once(bike,"boost_start");else if(!boost&&state.boost)once(bike,"boost_end");
            double speed=bike.getDeltaMovement().horizontalDistance();String band=!occupied?"":speed<.02?"idle":speed<.18?"low":boost?"boost":"cruise";
            if(!band.equals(state.band)){if(state.engine!=null)state.engine.finish();state.engine=band.isEmpty()?null:new Loop(bike,band,.35f);if(state.engine!=null)game.getSoundManager().play(state.engine);state.band=band;}
            if(bike.tickCount%20==0&&state.engine!=null&&!game.getSoundManager().isActive(state.engine)&&!state.engine.isStopped())game.getSoundManager().play(state.engine);
            if(boost&&state.nos==null){state.nos=new Loop(bike,"boost_loop",.22f);game.getSoundManager().play(state.nos);}
            if(!boost&&state.nos!=null){state.nos.finish();state.nos=null;}
            if(bike.tickCount%20==0&&state.nos!=null&&!game.getSoundManager().isActive(state.nos)&&!state.nos.isStopped())game.getSoundManager().play(state.nos);
            state.occupied=occupied;state.boost=boost;
        }
        states.entrySet().removeIf(entry->{if(live.contains(entry.getKey()))return false;stop(entry.getValue());return true;});
    }
    private static void stop(State state){if(state.engine!=null)state.engine.finish();if(state.nos!=null)state.nos.finish();}
    private static final class Loop extends AbstractTickableSoundInstance {
        private final SurveyMotorcycleEntity bike;
        Loop(SurveyMotorcycleEntity bike,String suffix,float strength){super(sound(suffix),SoundSource.PLAYERS,RandomSource.create());this.bike=bike;looping=true;delay=0;volume=strength;pitch=1;tick();}
        void finish(){stop();}
        @Override public void tick(){if(bike.isRemoved()||!bike.isVehicle()){stop();return;}x=bike.getX();y=bike.getY();z=bike.getZ();}
    }
}
