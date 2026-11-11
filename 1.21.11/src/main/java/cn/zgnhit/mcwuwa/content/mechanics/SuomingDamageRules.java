package cn.zgnhit.mcwuwa.content.mechanics;
import java.util.Set;
/** Permanent source1312 chain values; time-limited contract effects use named buffs. */
public final class SuomingDamageRules {
    private SuomingDamageRules(){}
    public static double coefficient(int chain,String action){
        double value=0;
        if(chain>=1&&Set.of("suoming/intro","suoming/intro_deep","suoming/intro_unison","suoming/intro_deep_unison").contains(action))value+=.60;
        if(chain>=5&&action.equals("suoming/liberation"))value+=.40;
        if(chain>=6&&Set.of("suoming/heart","suoming/heart_hold").contains(action))value+=.50;
        return value;
    }
}
