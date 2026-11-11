package cn.zgnhit.mcwuwa.client.presentation;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import java.util.*;

/** Validated visual mixing policy. Contact markers limit blending, never trigger hits. */
public record PlayerPosePolicy(Set<String> boneMask, double blendInTicks, double returnTicks,
                               double contactProgress) {
    public static final Set<String> BONES=Set.of("body","pelvis","torso","head","right_arm","left_arm",
        "right_forearm","left_forearm","right_leg","left_leg","right_shin","left_shin","right_item","left_item");
    public PlayerPosePolicy {
        boneMask=Set.copyOf(boneMask);
        if(boneMask.isEmpty()||!BONES.containsAll(boneMask))throw new IllegalArgumentException("Invalid animation bone mask");
        if(!Double.isFinite(blendInTicks)||blendInTicks<0||blendInTicks>6
            ||!Double.isFinite(returnTicks)||returnTicks<0||returnTicks>6
            ||!Double.isFinite(contactProgress)||contactProgress<0||contactProgress>1)
            throw new IllegalArgumentException("Invalid animation transition policy");
    }
    public static PlayerPosePolicy defaults(){return new PlayerPosePolicy(BONES,2,3,0);}
    public static PlayerPosePolicy parse(JsonObject action,ActionPresentationRegistry.Clip tp,ActionPresentationRegistry.Clip fp) {
        Set<String> mask=new HashSet<>();
        if(!action.has("boneMask"))throw new IllegalArgumentException("Missing boneMask");
        for(JsonElement bone:action.getAsJsonArray("boneMask"))
            if(!mask.add(bone.getAsString()))throw new IllegalArgumentException("Duplicate boneMask member");
        String id=action.get("actionId").getAsString();
        // Deliberate MC adaptation defaults; the resource may provide an explicit shorter window.
        boolean heavy=id.contains("heavy")||id.contains("liberation");
        double in=heavy?2.4:id.contains("dodge")?1:2, out=heavy?4:3;
        if(action.has("transitions")) {
            JsonObject transitions=action.getAsJsonObject("transitions");
            in=transitions.get("blendInTicks").getAsDouble();out=transitions.get("returnTicks").getAsDouble();
        }
        double contact=1;
        if(action.has("requiredMarkers"))for(JsonElement entry:action.getAsJsonArray("requiredMarkers")) {
            double seconds=entry.getAsJsonObject().get("seconds").getAsDouble();
            if(!Double.isFinite(seconds)||seconds<0||seconds>tp.length()||seconds>fp.length())
                throw new IllegalArgumentException("Contact outside animation");
            contact=Math.min(contact,Math.min(seconds/tp.length(),seconds/fp.length()));
        }
        return new PlayerPosePolicy(mask,in,out,contact);
    }
}
