package cn.zgnhit.mcwuwa.content.mechanics;

/** Server chooses held attacks from its real stance/resource state. A wire hold never grants resources.
 * A fresh hold becomes a Xuanling heavy only with two feathers; listening always continues its combo. */
public final class HeldAttackRules {
    public enum Mode { HEAVY, COMBO, CAMELLYA_LOOP, QIUYUAN_INK_LOOP, GALBRENA_HEAVY_LOOP, MORNYE_OBSERVATION_LOOP, SINGLE_BASIC }
    private HeldAttackRules() { }
    /** A physical hold is already recognized by the client. These source
     * continuations fire while airborne, rather than waiting for release. */
    public static boolean immediateAirHeavy(MechanicsState state,String current,boolean airborne){
        if(state==null||!airborne)return false;
        if(current.isEmpty()&&(java.util.Set.of("aero_rover","lupa","denia").contains(state.characterId())
            ||state.characterId().equals("cartethyia")&&state.form().equals("fleurdelys")
            ||state.characterId().equals("hiyuki")&&state.form().equals("future")&&state.resource("chill")<3))return true;
        return state.characterId().equals("aero_rover")
                &&java.util.Set.of("aero_rover/wind_air_01","aero_rover/wind_air_02").contains(current)
            ||state.characterId().equals("chisa")&&!state.form().equals("saw")&&state.timer("chisa_shattered_ready")>0
                &&(current.isEmpty()||current.equals("chisa/heavy"));
    }
    public static Mode mode(MechanicsState state, boolean airborne) {
        if(state.characterId().equals("ciaccona")&&state.timer("ciaccona_aim")>0
            ||state.characterId().equals("lucilla")&&state.timer("lucilla_camera")>0)return Mode.SINGLE_BASIC;
        if(state.characterId().equals("mornye")&&state.form().equals("observation"))return Mode.MORNYE_OBSERVATION_LOOP;
        if(state.characterId().equals("suoming"))return airborne?Mode.SINGLE_BASIC:Mode.COMBO;
        if(state.characterId().equals("augusta")&&state.form().equals("realm"))return airborne?Mode.SINGLE_BASIC:Mode.COMBO;
        if(state.characterId().equals("lucilla"))return airborne?Mode.SINGLE_BASIC:Mode.COMBO;
        if(state.characterId().equals("qingxiao")) {
            boolean ready=state.form().equals("immortal")?state.resource("sword_intent")>=100:
                state.resource("qin_heart")>=100&&state.resource("sword_rhyme")>=100;
            return ready?Mode.HEAVY:Mode.COMBO;
        }
        if(state.characterId().equals("carlotta")&&state.form().equals("gunflower"))return Mode.SINGLE_BASIC;
        if(state.characterId().equals("suisui")&&state.form().equals("normal")&&!airborne)return Mode.COMBO;
        if(state.characterId().equals("galbrena")&&!airborne)return Mode.GALBRENA_HEAVY_LOOP;
        if(state.characterId().equals("hiyuki")&&state.form().equals("present")&&state.timer("hiyuki_basic3_window")>0)return Mode.SINGLE_BASIC;
        // Source1108/node1 explicitly supports holding the normal three-hit
        // chain. Below300 Mind this gesture is not an unavailable arrow charge.
        if(state.characterId().equals("hiyuki")&&state.form().equals("present")&&state.resource("mind")<300)
            return airborne?Mode.SINGLE_BASIC:Mode.COMBO;
        if(state.characterId().equals("qiuyuan")&&state.form().equals("drunken"))return Mode.QIUYUAN_INK_LOOP;
        if(state.characterId().equals("lynae")&&state.form().equals("sampling")&&!airborne&&state.resource("overflow")<120)return Mode.COMBO;
        if (state.characterId().equals("yangyang_xuanling"))
            return state.form().equals("listening") || state.resource("feathers") < 2 ? Mode.COMBO : Mode.HEAVY;
        if (state.characterId().equals("camellya")
            && (state.form().equals("bloom") || !airborne && state.nextBasic() >= 4)) return Mode.CAMELLYA_LOOP;
        return Mode.HEAVY;
    }
    public static MechanicsEngine.Input next(Mode mode) {
        return mode == Mode.CAMELLYA_LOOP || mode == Mode.QIUYUAN_INK_LOOP ||mode==Mode.GALBRENA_HEAVY_LOOP ||mode==Mode.MORNYE_OBSERVATION_LOOP ? MechanicsEngine.Input.BASIC_HOLD : MechanicsEngine.Input.BASIC;
    }
    /** Hold starts with the existing chain; spinning is offered only after basic3.
     * Each completed spin still has its authored automatic final basic. */
    public static MechanicsEngine.Input next(Mode mode, MechanicsState state, String completedAction) {
        if(state.characterId().equals("suoming")&&mode==Mode.COMBO)return MechanicsEngine.Input.BASIC_HOLD;
        if(state.characterId().equals("lucilla")&&mode==Mode.COMBO)return MechanicsEngine.Input.BASIC_HOLD;
        if(state.characterId().equals("qingxiao")&&mode==Mode.COMBO)return MechanicsEngine.Input.BASIC_HOLD;
        if(mode!=Mode.CAMELLYA_LOOP)return next(mode);
        if(automaticFinisher(state,completedAction))return MechanicsEngine.Input.BASIC;
        return state.nextBasic()==4?MechanicsEngine.Input.BASIC_HOLD:MechanicsEngine.Input.BASIC;
    }
    public static boolean automaticFinisher(MechanicsState state,String completedAction) {
        return state.characterId().equals("camellya")&&(completedAction.equals("camellya/basic_04")&&state.form().equals("normal")
            ||completedAction.equals("camellya/bloom_spinning")&&state.form().equals("bloom"));
    }
    public static MechanicsState afterCompletion(MechanicsState state,String completedAction) {
        if(!automaticFinisher(state,completedAction))return state;
        int next=completedAction.equals("camellya/basic_04")?5:4;
        // These long actions can outlive the ordinary combo window. Their automatic
        // finisher follows completion, rather than a stale/reset cursor.
        return new MechanicsState(state.characterId(),state.form(),state.mode(),state.chain(),next,state.resources(),state.timers());
    }
}
