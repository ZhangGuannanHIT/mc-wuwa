package cn.zgnhit.mcwuwa.core;

/** User-authorized fixed MC adaptation after the three requested wikis failed to provide executors.
 * Raw curve integers are retained; interpreting them /10000 and the execution schedule is an adaptation.
 * See docs/data-audit/global-executors-20261013.md and docs/implementation/global-systems.md.
 */
public final class GlobalCombatRules {
    public static final String EVIDENCE="minecraft_adaptation_20261013_after_three_wiki_audit";
    public static final double LEVEL_90_BASE=3674;
    private static final int[] GLACIO={1225,2221,3217,4213,5209,6205,7201,8196,9192,10188,13584,16981,20377,23773,27169,30565};
    private static final int[] FUSION={8400,15229,22058,28888,35717,42546,49375,56204,63034,69863,93150,116438,139726,163013,186301,209588};
    private static final int[] ELECTRO={5000,9065,13130,17195,21260,25325,29390,33455,37520,41585,55447,69308,83170,97032,110893,124755};
    private static final int[] AERO={4500,11250,22500,33750,45000,56250,67500,78750,90000,101250,112500,123750,135000,146250,157500};
    private static final int[] SPECTRO={3000,5439,7878,10317,12756,15195,17634,20073,22512,24951,33268,41585,49902,58219,66536,74853};
    private static final double[] SLOW={.03,.04,.05,.07,.10,.125,.15,.175,.20,.20};
    private GlobalCombatRules(){}
    public static double curve(ElementalEffects.Kind kind,int layers) {
        if(layers<1||layers>1024)throw new IllegalArgumentException("effect layers");
        int[] values=switch(kind){case GLACIO_CHAFE,GLACIO_FROST->GLACIO;case FUSION_BURST->FUSION;case ELECTRO_FLARE->ELECTRO;case AERO_EROSION->AERO;case SPECTRO_FRAZZLE->SPECTRO;case HAVOC_BANE->throw new IllegalArgumentException("no havoc damage");};
        // Extension beyond the frozen rows is explicitly MC-authored; it is not a recovered game formula.
        double raw=layers<=values.length?values[layers-1]:values[values.length-1]+(double)(layers-values.length)*(values[values.length-1]-values[values.length-2]);
        return raw/10000;
    }
    public static double effectDamage(ElementalEffects.Kind kind,int layers,int rage) {
        double multiplier=curve(kind,layers);
        if(kind==ElementalEffects.Kind.ELECTRO_FLARE&&rage>0)multiplier+=curve(kind,rage);
        return LEVEL_90_BASE*multiplier;
    }
    public static double slow(int layers){return layers<=0?0:SLOW[Math.min(layers,SLOW.length)-1];}
    public static int freezeTicks(int cap){if(cap<1||cap>1024)throw new IllegalArgumentException("cap");return 80+Math.max(0,cap-10)*10;}
    public static double tuneDamage(double amplificationPoints) {
        Checks.nonnegative("tune amplification",amplificationPoints);
        return LEVEL_90_BASE*(1+amplificationPoints/100);
    }
    public static ElementalEffects.Rules effect(String id,ElementalEffects.Kind kind) {
        int cap=kind==ElementalEffects.Kind.AERO_EROSION||kind==ElementalEffects.Kind.HAVOC_BANE?3:10;
        long duration=switch(kind){case SPECTRO_FRAZZLE->60;case AERO_EROSION->296;case HAVOC_BANE->500;default->300;};
        long period=switch(kind){case SPECTRO_FRAZZLE,AERO_EROSION->60;case ELECTRO_FLARE->100;default->0;};
        var decay=switch(kind){case SPECTRO_FRAZZLE->ElementalEffects.Decay.MINUS_ONE;case ELECTRO_FLARE->ElementalEffects.Decay.FLOOR_HALF;default->ElementalEffects.Decay.NONE;};
        var capPolicy=kind==ElementalEffects.Kind.AERO_EROSION?ElementalEffects.CapChange.KEEP_UNTIL_NEXT_APPLICATION:ElementalEffects.CapChange.CLAMP;
        return new ElementalEffects.Rules(id,EVIDENCE,kind,cap,duration,period,decay,kind==ElementalEffects.Kind.ELECTRO_FLARE?10:0,capPolicy,
            kind==ElementalEffects.Kind.HAVOC_BANE?null:(source,layers,currentCap,rage,trigger)->effectDamage(kind,kind==ElementalEffects.Kind.GLACIO_FROST?currentCap:layers,rage),
            new ElementalEffects.Timing(kind==ElementalEffects.Kind.AERO_EROSION,true,kind==ElementalEffects.Kind.AERO_EROSION?40:0,true));
    }
}
