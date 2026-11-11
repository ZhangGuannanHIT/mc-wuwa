package cn.zgnhit.mcwuwa.mixin.client;

import cn.zgnhit.mcwuwa.client.CiacconaRecitalSound;
import java.util.Map;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.ChannelAccess;
import net.minecraft.client.sounds.SoundEngine;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Keeps only our recital stream on the same pause clock as its performer's state. */
@Mixin(SoundEngine.class)
public abstract class RecitalSoundClockMixin {
    @Shadow @Final private Map<SoundInstance,ChannelAccess.ChannelHandle> instanceToChannel;

    @Inject(method="tick",at=@At("RETURN"))
    private void mcWuwa$recitalClock(boolean gamePaused,CallbackInfo callback){
        instanceToChannel.forEach((sound,channel)->CiacconaRecitalSound.syncPlayback(sound,channel,gamePaused));
    }
}
