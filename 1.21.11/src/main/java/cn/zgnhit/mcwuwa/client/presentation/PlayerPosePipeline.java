package cn.zgnhit.mcwuwa.client.presentation;

import org.joml.Quaternionf;
import org.joml.Vector3f;
import java.util.*;

/** Bounded per-view visual state. Owns no entity, controller, gameplay clock or combat event. */
public final class PlayerPosePipeline {
    public enum Phase { VANILLA, ACTION, RETURNING }
    private record Key(UUID actor,boolean firstPerson) {}
    private record ActionKey(UUID instance,String action,long start,long revision) {
        static ActionKey of(ActorPresentationState action){return new ActionKey(action.instanceId(),action.actionId(),action.startTick(),action.revision());}
    }
    private static final class State {
        String rig;int resourceRevision;ActionKey action;PlayerPosePolicy policy;
        Map<String,ActionPresentationRegistry.Pose> from=Map.of(),last=Map.of();
        double start,end,lastTime,returnStart=Double.NaN;float weight=1;
        Phase phase=Phase.ACTION;
        Phase renderedPhase=Phase.VANILLA;float lastReturnProgress;
    }
    public record Result(Phase phase,Map<String,ActionPresentationRegistry.Pose> pose,float blendWeight,float returnProgress) {}
    public record Diagnostic(Phase phase,Phase renderedPhase,double sampleTime,float blendWeight,float returnProgress) {}
    private final Map<Key,State> states=new HashMap<>();
    public void clear(){states.clear();}
    public void discard(UUID actor){states.keySet().removeIf(key->key.actor().equals(actor));}
    public void discardView(UUID actor,boolean firstPerson){states.remove(new Key(actor,firstPerson));}
    /** Ending never advances or retains the old animation player: only a private final pose survives. */
    public void end(UUID actor,double time) {
        var iterator=states.entrySet().iterator();
        while(iterator.hasNext()) {
            var entry=iterator.next();if(!entry.getKey().actor().equals(actor))continue;
            State state=entry.getValue();
            // An offscreen view may still hold a mid-action pose from before F5.
            // Never resurrect that stale pose during a later cancellation.
            if(time-state.lastTime>3){iterator.remove();continue;}
            if(state.phase==Phase.ACTION) {
                state.phase=Phase.RETURNING;state.from=transitionSource(state.last);state.returnStart=Math.max(time,state.lastTime);
                state.end=state.returnStart+state.policy.returnTicks();state.action=null;
            }
        }
    }
    public Phase phase(UUID actor,boolean firstPerson){State state=states.get(new Key(actor,firstPerson));return state==null?Phase.VANILLA:state.phase;}
    /** Copy of the pose actually submitted for this view, for native capture diagnostics. */
    public Map<String,ActionPresentationRegistry.Pose> lastPose(UUID actor,boolean firstPerson){
        State state=states.get(new Key(actor,firstPerson));return state==null?Map.of():copy(state.last);
    }
    public Optional<Diagnostic> diagnostic(UUID actor,boolean firstPerson){State state=states.get(new Key(actor,firstPerson));
        return state==null?Optional.empty():Optional.of(new Diagnostic(state.phase,state.renderedPhase,state.lastTime,state.weight,state.lastReturnProgress));}
    public void prune(Set<UUID> tracked,double time){states.entrySet().removeIf(entry->!tracked.contains(entry.getKey().actor())
        ||entry.getValue().phase==Phase.RETURNING&&time>=entry.getValue().end);}

    /** Both target and base are sampled at time by the caller; this never shifts that time. */
    public Result sample(UUID actor,boolean firstPerson,String rig,int revision,ActorPresentationState action,
                         PlayerPosePolicy policy,double time,double firstContactTick,
                         Map<String,ActionPresentationRegistry.Pose> base,Map<String,ActionPresentationRegistry.Pose> target) {
        Key key=new Key(actor,firstPerson);State state=states.get(key);
        if(state!=null&&(!state.rig.equals(rig)||state.resourceRevision!=revision||time<state.lastTime)) {
            states.remove(key);state=null;
        }
        if(action==null) {
            if(state==null||state.phase!=Phase.RETURNING||time>=state.end) {states.remove(key);return vanilla();}
            float amount=ease(fraction(time,state.returnStart,state.end));
            state.last=mix(state.from,base,amount);
            passBase(state.last,base,state.policy.boneMask());state.lastTime=time;
            state.renderedPhase=Phase.RETURNING;state.lastReturnProgress=amount;
            return new Result(Phase.RETURNING,copy(state.last),1,amount);
        }
        ActionKey actionKey=ActionKey.of(action);
        if(state==null||!actionKey.equals(state.action)) {
            Map<String,ActionPresentationRegistry.Pose> previous=state!=null&&time-state.lastTime<=3?state.last:base;
            state=new State();state.rig=rig;state.resourceRevision=revision;state.action=actionKey;state.policy=policy;
            state.from=transitionSource(previous);state.start=time;
            // A late packet beyond contact skips the blend. No animation is restarted or delayed.
            state.end=Math.min(time+policy.blendInTicks(),firstContactTick);
            states.put(key,state);
        }
        Map<String,ActionPresentationRegistry.Pose> masked=copy(base);
        for(String bone:policy.boneMask())masked.put(bone,copy(target.getOrDefault(bone,ActionPresentationRegistry.Pose.identity())));
        state.weight=ease(fraction(time,state.start,state.end));
        state.last=mix(state.from,masked,state.weight);
        passBase(state.last,base,policy.boneMask());state.lastTime=time;state.phase=Phase.ACTION;
        state.renderedPhase=Phase.ACTION;state.lastReturnProgress=0;
        return new Result(Phase.ACTION,copy(state.last),state.weight,0);
    }
    private static Result vanilla(){return new Result(Phase.VANILLA,Map.of(),1,1);}
    /** A completed whole-body turn has no joint twist. Do not unwind it during recovery
     * or when chaining another attack; authored winding inside the clip remains untouched. */
    private static Map<String,ActionPresentationRegistry.Pose> transitionSource(Map<String,ActionPresentationRegistry.Pose> source){
        var result=copy(source);var body=result.get("body");
        if(body!=null){var r=body.rotation();r.x-=360*Math.round(r.x/360);r.y-=360*Math.round(r.y/360);r.z-=360*Math.round(r.z/360);}
        return result;
    }
    private static void passBase(Map<String,ActionPresentationRegistry.Pose> result,Map<String,ActionPresentationRegistry.Pose> base,Set<String> mask) {
        for(String bone:List.copyOf(result.keySet()))if(!mask.contains(bone)) {
            if(base.containsKey(bone))result.put(bone,copy(base.get(bone)));else result.remove(bone);
        }
    }
    private static float fraction(double time,double from,double to){return to<=from?1:(float)Math.clamp((time-from)/(to-from),0,1);}
    private static float ease(float t){return t*t*(3-2*t);}
    static Map<String,ActionPresentationRegistry.Pose> copy(Map<String,ActionPresentationRegistry.Pose> source) {
        Map<String,ActionPresentationRegistry.Pose> result=new HashMap<>();source.forEach((name,pose)->result.put(name,copy(pose)));return result;
    }
    private static ActionPresentationRegistry.Pose copy(ActionPresentationRegistry.Pose pose){return new ActionPresentationRegistry.Pose(new Vector3f(pose.position()),new Vector3f(pose.rotation()),new Vector3f(pose.scale()));}
    static Map<String,ActionPresentationRegistry.Pose> mix(Map<String,ActionPresentationRegistry.Pose> from,Map<String,ActionPresentationRegistry.Pose> to,float weight) {
        if(weight<=0)return copy(from);if(weight>=1)return copy(to);
        Set<String> bones=new HashSet<>(from.keySet());bones.addAll(to.keySet());Map<String,ActionPresentationRegistry.Pose> result=new HashMap<>();
        for(String bone:bones) {
            var a=from.getOrDefault(bone,ActionPresentationRegistry.Pose.identity());var b=to.getOrDefault(bone,ActionPresentationRegistry.Pose.identity());
            result.put(bone,new ActionPresentationRegistry.Pose(new Vector3f(a.position()).lerp(b.position(),weight),
                rotation(a.rotation(),b.rotation(),weight),new Vector3f(a.scale()).lerp(b.scale(),weight)));
        }
        return result;
    }
    private static Vector3f rotation(Vector3f a,Vector3f b,float t) {
        Vector3f reference=new Vector3f(a).lerp(b,t);
        // Weighted joint skin interprets authored Euler turns, not only rigid orientation.
        // Preserve explicit winding when either endpoint lies outside the principal range.
        if(Math.max(Math.max(Math.abs(a.x),Math.abs(a.y)),Math.abs(a.z))>=170
            ||Math.max(Math.max(Math.abs(b.x),Math.abs(b.y)),Math.abs(b.z))>=170)return reference;
        float r=(float)(Math.PI/180);
        Quaternionf qa=new Quaternionf().rotationZYX(a.z*r,a.y*r,a.x*r);
        Quaternionf qb=new Quaternionf().rotationZYX(b.z*r,b.y*r,b.x*r);
        Vector3f e=qa.slerp(qb,t).normalize().getEulerAnglesZYX(new Vector3f()).div(r);
        Vector3f alternate=new Vector3f(e.x+180,180-e.y,e.z+180);
        unwrap(e,reference);unwrap(alternate,reference);
        if(alternate.distanceSquared(reference)<e.distanceSquared(reference))e=alternate;
        // A branch far from the author channels could produce an internal skin twist.
        return e.distanceSquared(reference)>90*90?reference:e;
    }
    private static void unwrap(Vector3f value,Vector3f ref){value.x+=360*Math.round((ref.x-value.x)/360);value.y+=360*Math.round((ref.y-value.y)/360);value.z+=360*Math.round((ref.z-value.z)/360);}
}
