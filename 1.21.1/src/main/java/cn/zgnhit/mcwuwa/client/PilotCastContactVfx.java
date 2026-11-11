package cn.zgnhit.mcwuwa.client;
import java.util.*;
import static cn.zgnhit.mcwuwa.client.NewRoleVfxProfiles.*;
/** Contact-trimmed pilot attacks; full cinematic and persistent scenes remain independent. */
final class PilotCastContactVfx {
 private PilotCastContactVfx(){}
 static boolean supports(String a){return PilotCastContactClocks.ALL.containsKey(a);}
 static double delta(String a,double p){var c=PilotCastContactClocks.ALL.get(a);double d=Double.POSITIVE_INFINITY;
  if(c!=null)for(int h:c.hits())if(Math.abs(p*c.duration()-h)<Math.abs(d))d=p*c.duration()-h;return d;}
 // Keep the reviewed anticipation/strike phases inside their authored visible
 // interval while the host independently trims emission to real contact ticks.
 static double geometryProgress(String a,double p){
  if(a.equals("cartethyia/fleurdelys_skill"))return .2+.58*p;
  if(a.equals("cartethyia/fleurdelys_air_01"))return .2+.5*p;
  if(a.equals("cartethyia/fleurdelys_air_03"))return .5+Math.clamp(delta(a,p),-2,3)*.05;
  if(a.startsWith("cartethyia/"))return .2+.55*p;
  if(a.equals("mornye/observation_dodge_counter")||a.equals("lynae/intro"))return p;
  if(a.equals("jinhsi/air")||a.equals("jinhsi/incarnation_heavy"))return .2+.55*p;
  if(a.equals("phrolova/composition_finale"))return .1+.8*p;
  if(Set.of("mornye/observation_basic_03","lynae/air","lynae/cruise_air","lynae/cruise_air_heavy").contains(a))return .2+.55*p;
  // The contact frame stays at the approved midpoint. Its neighbouring frames
  // advance the existing stroke instead of repeatedly drawing one frozen pose.
  return .5+Math.clamp(delta(a,p),-2,3)*.04;
 }
 static List<LightPath> ribbons(String a,double p){
  if(!supports(a)||p<=0||p>=1)return List.of();double d=delta(a,p);if(d<=-2||d>=3)return List.of();
  double pose=geometryProgress(a,p);
  return FiveNormalAttackVfx.contactTrails(NewRoleVfxProfiles.retainedRibbons(a,pose),d);
 }
 static List<LightSurface> surfaces(String a,double p){
  if(!supports(a)||p<=0||p>=1)return List.of();double d=delta(a,p);if(d<=-2||d>=3)return List.of();
  if(a.startsWith("jinhsi/incarnation_basic_")||Set.of("jinhsi/dodge_counter","jinhsi/incarnation_counter","jinhsi/incarnation_heavy").contains(a)){
   // Join only the three existing blade bands; E4 breath, floor rings and
   // A4's extra coiling strands keep their separate authored presentation.
   var paths=ribbons(a,p);int alpha=(int)Math.round(36*Math.clamp(1-Math.max(0,d)/3,0,1));
   return SlashSurfaceGeometry.between(paths.get(0),paths.get(2),alpha<<24|0xffe9ab);
  }
  return NewRoleVfxProfiles.retainedSurfaces(a,geometryProgress(a,p));
 }
 static Sample sample(String a,double p,double u){
  var paths=ribbons(a,p);if(paths.isEmpty())return new Sample(0,0,0,0xffeffff9,.2f,Kind.NONE);
  if(a.equals("cartethyia/skill")&&u<.4)return CartethyiaPilotVfx.sample(a,p,u);
  double at=Math.clamp(u,0,.999999)*paths.size();var path=paths.get((int)at);var v=path.points().get(Math.min(path.points().size()-1,(int)((at-(int)at)*path.points().size())));
  return new Sample(v.side(),v.up(),v.forward(),path.color(),.25f,Kind.DUST);
 }
}
