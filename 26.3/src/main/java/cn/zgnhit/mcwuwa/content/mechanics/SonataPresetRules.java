package cn.zgnhit.mcwuwa.content.mechanics;

/** Shared fixed-preset identity for the server and cultivation screen. */
public final class SonataPresetRules {
    private SonataPresetRules() { }
    private static boolean deniaStrain(String character,String mode){
        String canonical=MechanicsState.canonicalMode(character,mode);
        return character.equals("denia")&&(canonical.equals("strain")||canonical.equals("tune"));
    }
    public static int sonataId(String character,String mode,int defaultId){
        if(character.equals("lucilla")&&MechanicsState.canonicalMode(character,mode).equals("echo"))return 8;
        return defaultId==28&&deniaStrain(character,mode)?31:defaultId;
    }
    public static int echoId(String character,String mode,int defaultId){
        if(character.equals("lucilla")&&MechanicsState.canonicalMode(character,mode).equals("echo"))return 6000052;
        return deniaStrain(character,mode)?6000198:defaultId;
    }
}
