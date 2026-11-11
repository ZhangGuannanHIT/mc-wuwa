package cn.zgnhit.mcwuwa.content.mechanics;

import java.util.Set;

/** Frozen 1311 forte: named attacks, not every skill-damage attack, apply Flare.
 * Five limited palaces-basic applications are owned by CharacterBranches. */
public final class HsinTargetRules {
    private static final Set<String> FIVE=Set.of("hsin/palaces_formation","hsin/illuminated_suppress");
    private static final Set<String> ONE=Set.of("hsin/intro","hsin/illuminated_intro","hsin/worldly_suppress");
    private static final Set<String> SKILLS=Set.of("hsin/skill","hsin/illuminated_skill","hsin/palaces_formation");
    private HsinTargetRules(){}
    public static int electroLayers(String action){return FIVE.contains(action)?5:ONE.contains(action)?1:0;}
    public static boolean spendsRealm(String action){return SKILLS.contains(action);}
    public static boolean stallsInIlluminatedForm(String action){return action.equals("hsin/illuminated_skill")||action.equals("hsin/palaces_formation");}
}
