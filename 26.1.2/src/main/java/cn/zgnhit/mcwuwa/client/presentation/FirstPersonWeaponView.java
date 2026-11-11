package cn.zgnhit.mcwuwa.client.presentation;

import cn.zgnhit.mcwuwa.McWuwa;
import com.google.gson.*;
import java.util.*;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.world.entity.HumanoidArm;
import org.joml.Matrix4f;

/** Grip-preserving correction in the custom FP arm view.
 * Native idle item rendering and custom action arms use different coordinate bases. */
final class FirstPersonWeaponView {
    private static volatile Map<String,Matrix4f> bindings=Map.of();
    private static volatile Map<String,Set<String>> exclusions=Map.of();
    private record GripFrame(Matrix4f pivot,Matrix4f inverse,boolean aroundY){}
    private record Transition(float nativeUntil,float correctedFrom){}
    private static volatile Map<String,GripFrame> gripFrames=Map.of();
    private static volatile Map<String,Transition> transitions=Map.of();
    private FirstPersonWeaponView(){}
    static void reload(ResourceManager resources){
        bindings=Map.of();exclusions=Map.of();gripFrames=Map.of();transitions=Map.of();
        resources.getResource(Identifier.fromNamespaceAndPath("mc_wuwa","presentation/first_person_weapon_grips.json")).ifPresent(resource->{
            try(var reader=resource.openAsReader()){install(JsonParser.parseReader(reader).getAsJsonObject());}
            catch(Exception error){McWuwa.LOGGER.error("Cannot load custom first-person weapon grip corrections",error);}
        });
    }
    static void install(JsonObject root){
        var loaded=new HashMap<String,Matrix4f>();
        var frames=new HashMap<String,GripFrame>();var stages=new HashMap<String,Transition>();
        var excluded=new HashMap<String,Set<String>>();
        for(var entry:root.getAsJsonArray("weapons")){
            var row=entry.getAsJsonObject();String weapon=row.get("weapon").getAsString();var grip=row.getAsJsonArray("gripPixels");
            var actions=new HashSet<String>();if(row.has("excludedActions"))for(var action:row.getAsJsonArray("excludedActions"))actions.add(action.getAsString());
            excluded.put(weapon,Set.copyOf(actions));
            if(row.has("stagedActions"))for(var stage:row.getAsJsonArray("stagedActions")){
                var value=stage.getAsJsonObject();
                stages.put(weapon+"/"+value.get("action").getAsString(),new Transition(value.get("nativeUntil").getAsFloat(),value.get("correctedFrom").getAsFloat()));
            }
            for(var arm:HumanoidArm.values()){
                boolean left=arm==HumanoidArm.LEFT;float sign=left?-1:1;
                var display=row.getAsJsonObject(left?"leftDisplay":"rightDisplay");
                var r=display.getAsJsonArray("rotation");var t=display.getAsJsonArray("translation");var s=display.getAsJsonArray("scale");
                var basis=new Matrix4f().translate(sign*t.get(0).getAsFloat()/16,t.get(1).getAsFloat()/16,t.get(2).getAsFloat()/16)
                    .rotateXYZ((float)Math.toRadians(r.get(0).getAsDouble()),sign*(float)Math.toRadians(r.get(1).getAsDouble()),sign*(float)Math.toRadians(r.get(2).getAsDouble()))
                    .scale(s.get(0).getAsFloat(),s.get(1).getAsFloat(),s.get(2).getAsFloat());
                float x=(grip.get(0).getAsFloat()-8)/16,y=(grip.get(1).getAsFloat()-8)/16,z=(grip.get(2).getAsFloat()-8)/16;
                // Conjugate the local half-turn through the display basis so the grip stays fixed.
                // Blades retain their cutting-edge X axis; the reviewed pistol retains its up axis.
                var correction=new Matrix4f(basis).translate(x,y,z);
                if(row.has("flipAxis")&&row.get("flipAxis").getAsString().equals("y"))correction.rotateY((float)Math.PI);
                else correction.rotateX((float)Math.PI);
                correction.translate(-x,-y,-z).mul(new Matrix4f(basis).invert());
                loaded.put(weapon+"/"+arm.name(),correction);
                var pivot=new Matrix4f(basis).translate(x,y,z);
                frames.put(weapon+"/"+arm.name(),new GripFrame(pivot,new Matrix4f(pivot).invert(),row.has("flipAxis")&&row.get("flipAxis").getAsString().equals("y")));
            }
        }
        exclusions=Map.copyOf(excluded);gripFrames=Map.copyOf(frames);transitions=Map.copyOf(stages);bindings=Map.copyOf(loaded);
    }
    static Optional<Matrix4f> basis(String weapon,HumanoidArm arm){
        var matrix=bindings.get(weapon+"/"+arm.name());return matrix==null?Optional.empty():Optional.of(new Matrix4f(matrix));
    }
    static Optional<Matrix4f> basis(String weapon,HumanoidArm arm,String action){
        return basis(weapon,arm,action,1);
    }
    static Optional<Matrix4f> basis(String weapon,HumanoidArm arm,String action,float progress){
        String id=action.startsWith("mc_wuwa:")?action.substring(8):action;
        if(exclusions.getOrDefault(weapon,Set.of()).contains(id))return Optional.empty();
        var stage=transitions.get(weapon+"/"+id);
        if(stage==null||progress>=stage.correctedFrom())return basis(weapon,arm);
        if(progress<=stage.nativeUntil())return Optional.empty();
        var grip=gripFrames.get(weapon+"/"+arm.name());if(grip==null)return Optional.empty();
        float amount=(progress-stage.nativeUntil())/(stage.correctedFrom()-stage.nativeUntil());
        amount=amount*amount*(3-2*amount);
        var correction=new Matrix4f(grip.pivot());
        if(grip.aroundY())correction.rotateY((float)Math.PI*amount);else correction.rotateX((float)Math.PI*amount);
        return Optional.of(correction.mul(grip.inverse()));
    }
}
