package cn.zgnhit.mcwuwa.core;
import java.util.Set;
/** Shared by visual routing and tracked-entity validation. */
public final class PuppetAnimationRules {
 private PuppetAnimationRules(){}
 private static final Set<String> AUTHORED=Set.of("basic_01","basic_02","basic_03","basic_04",
  "heavy","air","dodge_counter","intro","skill","skill_blast","chameleon","magnetic_blast");
 public static boolean allowed(String model,String clip){return model.equals("yinlin_puppet")&&(AUTHORED.contains(clip)||Set.of("strike","sweep","plunge","burst").contains(clip));}
 public static String forAction(String action){
  if(!action.startsWith("yinlin/")||action.equals("yinlin/liberation"))return "attack";
  String move=action.substring("yinlin/".length());
  if(AUTHORED.contains(move))return move;
  return action.equals("yinlin/air")?"plunge":Set.of("yinlin/skill_blast","yinlin/chameleon","yinlin/magnetic_blast").contains(action)?"burst":action.endsWith("03")||action.endsWith("04")||action.endsWith("heavy")?"sweep":"strike";
 }
}
