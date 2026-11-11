package cn.zgnhit.mcwuwa.client;

import cn.zgnhit.mcwuwa.core.AbilityFeedbackState;
import com.google.gson.JsonObject;
import java.util.*;

/** Presents server-selected conditional actions. Purely local rings never authorize casts. */
final class AbilityFeedbackClient {
    private static final AbilityFeedbackState state=new AbilityFeedbackState();
    private AbilityFeedbackClient() { }
    static void update(JsonObject role,long tick){
        Map<String,AbilityFeedbackState.Signal> signals=new HashMap<>();
        if(role.has("abilities"))for(String id:List.of("basic","skill","liberation","echo")){
            var root=role.getAsJsonObject("abilities");if(!root.has(id))continue;var s=root.getAsJsonObject(id);
            signals.put(id,new AbilityFeedbackState.Signal(text(s,"selectedAction"),text(s,"holdAction"),flag(s,"enhanced"),flag(s,"ready")));
        }
        state.update(text(role,"instance"),tick,signals);
    }
    static void accepted(String action,long tick){state.accepted(action.replace("mc_wuwa:",""),tick);}
    static double strength(String id,long tick){return state.strength(id,tick);}
    static void clear(){state.clear();}
    private static boolean flag(JsonObject o,String k){return o.has(k)&&o.get(k).getAsBoolean();}
    private static String text(JsonObject o,String k){return o.has(k)?o.get(k).getAsString():"";}
}
