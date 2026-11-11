package cn.zgnhit.mcwuwa.content.mechanics;

/** Source node7 explicitly exempts these sequences from ordinary combo resets.
 * This preserves only progression; cancelled hit bodies and held input still end.
 */
public final class ComboContinuationRules {
    private ComboContinuationRules(){ }
    public static boolean retainsCursor(String character,String form,int listeningTicks){
        return character.equals("cantarella")&&form.equals("mirage")
            ||character.equals("jinhsi")&&form.equals("incarnation")
            ||character.equals("yangyang_xuanling")&&form.equals("listening")&&listeningTicks>0;
    }
    public static int afterCancellation(MechanicsState state){
        // C3 explicitly preserves the bow cursor when an E follows a recent bow attack.
        if(state.characterId().equals("iuno")&&state.chain()>=3&&state.form().equals("new_moon")&&state.timer("iuno_bow_continuation")>0)
            return Math.clamp(state.nextBasic(),1,3);
        if(state.characterId().equals("galbrena")&&state.timer("gal_hellwalk_continuation")>0)return Math.clamp(state.nextBasic(),1,state.form().equals("demon")?5:4);
        if(!retainsCursor(state.characterId(),state.form(),state.timer("listening")))return 1;
        return Math.clamp(state.nextBasic(),1,state.characterId().equals("jinhsi")?4:3);
    }
}
