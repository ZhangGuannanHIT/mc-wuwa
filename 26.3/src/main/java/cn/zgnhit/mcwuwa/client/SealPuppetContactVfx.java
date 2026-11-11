package cn.zgnhit.mcwuwa.client;
import java.util.*;
import static cn.zgnhit.mcwuwa.client.NewRoleVfxProfiles.*;
/** Reuses reviewed seals, phoenix feathers and puppet strings, timed per actual hit. */
final class SealPuppetContactVfx {
 private SealPuppetContactVfx(){}
 static boolean supports(String a){return SealPuppetContactClocks.ALL.containsKey(a);}
 static double delta(String a,double p){var c=SealPuppetContactClocks.ALL.get(a);double d=Double.POSITIVE_INFINITY;
  if(c!=null)for(int h:c.hits())if(Math.abs(p*c.duration()-h)<Math.abs(d))d=p*c.duration()-h;return d;}
 static List<LightPath> ribbons(String a,double p){
  if(!supports(a)||p<=0||p>=1)return List.of();double d=delta(a,p);if(d<=-2||d>=3)return List.of();
  // Descending constructs must advance through their vertical travel. A fixed
  // midpoint repeats the same suspended column at every damage contact.
  double phase=a.equals("hsin/illuminated_descend")||a.equals("hsin/illuminated_suppress")?p:.5;
  var source=HsinAerialConstructVfx.enhanced(a)?HsinAerialConstructVfx.mechanismEdges(a,p)
   :a.startsWith("hsin/")?SupplementalActionVfx.ribbons(a,phase):BlazePuppetAttackVfx.ribbons(a,a.equals("changli/true_sight_conquest")?p:.5);
  return FiveNormalAttackVfx.contactTrails(source,d);
 }
 static List<LightSurface> surfaces(String a,double p){
  if(HsinAerialConstructVfx.supports(a))return HsinAerialConstructVfx.surfaces(a,p);
  if(!supports(a)||p<=0||p>=1)return List.of();double d=delta(a,p);if(d<=-2||d>=3)return List.of();
  return a.startsWith("hsin/")?List.of():BlazePuppetAttackVfx.surfaces(a,a.equals("changli/true_sight_conquest")?p:.5);
 }
 static Sample sample(String a,double p,double u){
  var paths=ribbons(a,p);if(paths.isEmpty())return new Sample(0,0,0,0xffeffff9,.2f,Kind.NONE);
  double at=Math.clamp(u,0,.999999)*paths.size();var path=paths.get((int)at);var v=path.points().get(Math.min(24,(int)((at-(int)at)*25)));
  return new Sample(v.side(),v.up(),v.forward(),path.color(),.25f,Kind.DUST);
 }
}
