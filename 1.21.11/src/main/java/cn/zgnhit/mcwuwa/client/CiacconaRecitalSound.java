package cn.zgnhit.mcwuwa.client;

import cn.zgnhit.mcwuwa.platform.entity.ConstructEntity;
import java.util.*;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;

/** One original recital per performer; changing to the reserve visual keeps the song playing. */
public final class CiacconaRecitalSound {
    private static final Map<String,Song> songs=new HashMap<>();
    private static net.minecraft.client.multiplayer.ClientLevel previous;
    private CiacconaRecitalSound() { }
    public static void initialize(){NeoForge.EVENT_BUS.addListener((ClientTickEvent.Post e)->tick());}
    private static void tick(){
        var game=Minecraft.getInstance();
        if(game.level!=previous){songs.values().forEach(Song::finish);songs.clear();previous=game.level;}
        if(game.level==null||game.player==null)return;
        Map<String,ConstructEntity> live=new HashMap<>();
        for(var entity:game.level.entitiesForRendering())if(entity instanceof ConstructEntity c&&!c.isRemoved()&&c.distanceToSqr(game.player)<2304
            &&c.animation().equals("attack")&&(c.modelId().equals("ciaccona_microphone")||c.modelId().equals("ciaccona_singer_ensemble")))live.put(c.ownerUuid(),c);
        // Spawn/discard packets for the microphone -> reserve singer replacement
        // can arrive on adjacent ticks. Keep the same streaming song through that handoff.
        songs.entrySet().removeIf(entry->{if(live.containsKey(entry.getKey())){entry.getValue().missingTicks=0;return false;}
            if(++entry.getValue().missingTicks<=5)return false;entry.getValue().finish();return true;});
        live.forEach((owner,visual)->{
            var song=songs.get(owner);
            // The performance clock is held during its opening; start the full
            // stem when the singing pose begins instead of spending its first bars in R.
            if(song==null&&openingPending(ownerAction(visual),ownerActionExpired(visual)))return;
            if(song==null||startsNewRecital(song.visual.startTick()+song.visual.durationTicks(),visual.startTick()+visual.durationTicks())){
                if(song!=null)song.finish();song=new Song(visual);songs.put(owner,song);game.getSoundManager().play(song);
            }
            else song.visual=visual;
        });
    }
    private static String ownerAction(ConstructEntity visual){
        String owner=visual.ownerUuid();if(owner.isEmpty())return "";
        return cn.zgnhit.mcwuwa.client.presentation.PresentationClient.state(UUID.fromString(owner)).map(p->p.actionId()).orElse("");
    }
    private static boolean ownerActionExpired(ConstructEntity visual){
        String owner=visual.ownerUuid();if(owner.isEmpty())return true;
        return cn.zgnhit.mcwuwa.client.presentation.PresentationClient.state(UUID.fromString(owner))
            .map(p->p.expired(cn.zgnhit.mcwuwa.client.presentation.PresentationClient.now())).orElse(true);
    }
    static boolean openingPending(String action,boolean expired){
        return !expired&&(action.equals("ciaccona/liberation")||action.equals("mc_wuwa:ciaccona/liberation"));
    }
    static boolean playbackPaused(String action,boolean expired,boolean gamePaused){
        return gamePaused||!expired&&cn.zgnhit.mcwuwa.core.CinematicTimeRules.cinematic(action);
    }
    /** Called on SoundEngine.tick; execute keeps all OpenAL calls on its audio thread. */
    public static void syncPlayback(net.minecraft.client.resources.sounds.SoundInstance instance,
                                    net.minecraft.client.sounds.ChannelAccess.ChannelHandle channel,boolean gamePaused){
        if(!(instance instanceof Song song))return;
        boolean paused=playbackPaused(ownerAction(song.visual),ownerActionExpired(song.visual),gamePaused);
        channel.execute(source->{if(paused)source.pause();else source.unpause();});
    }
    static boolean startsNewRecital(long previousDeadline,long nextDeadline){
        // Microphone and reserve singer share the server field's exact expiry,
        // even though switching replaces the entity and its individual start tick.
        return previousDeadline!=nextDeadline;
    }
    private static final class Song extends AbstractTickableSoundInstance {
        ConstructEntity visual;
        int missingTicks;
        Song(ConstructEntity visual){super(SoundEvent.createVariableRangeEvent(Identifier.fromNamespaceAndPath("mc_wuwa","ciaccona.recital")),SoundSource.PLAYERS,RandomSource.create());this.visual=visual;looping=false;volume=.8f;pitch=1;tick();}
        void finish(){stop();}
        @Override public void tick(){x=visual.getX();y=visual.getY()+1;z=visual.getZ();}
    }
}
