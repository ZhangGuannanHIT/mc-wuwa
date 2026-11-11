package cn.zgnhit.mcwuwa.client;
import java.util.*;
import static cn.zgnhit.mcwuwa.client.NewRoleVfxProfiles.*;
/** Source-reviewed caster silhouettes, revealed per hit rather than throughout recovery. */
final class TideCasterContactVfx {
 private TideCasterContactVfx(){}
 static boolean supports(String a){return TideCasterContactClocks.ALL.containsKey(a);}
 static double delta(String a,double p){var c=TideCasterContactClocks.ALL.get(a);double d=Double.POSITIVE_INFINITY;
  if(c!=null)for(int h:c.hits())if(Math.abs(p*c.duration()-h)<Math.abs(d))d=p*c.duration()-h;return d;}
 static double geometryProgress(String a,double p){return .5+Math.clamp(delta(a,p),-2,3)*.04;}
 static List<LightPath> ribbons(String a,double p){
  // Entry has one damage contact but a continuing outward sweep. Keeping
  // its authored animation behind that contact gate erased the entire tail.
  if(a.equals("phoebe/intro"))return PhoebePrismVfx.ribbons(a,p);
  if(!supports(a)||p<=0||p>=1)return List.of();double d=delta(a,p);if(d<=-2||d>=3)return List.of();
  List<LightPath> source;
  double phase=geometryProgress(a,p);
  if(PhoebePrismVfx.supports(a))source=PhoebePrismVfx.ribbons(a,phase);
  else if(CantarellaTideVfx.supports(a))source=CantarellaTideVfx.ribbons(a,p);
  else if(FanInfernalVfx.supports(a))source=FanInfernalVfx.ribbons(a,phase);
  else {int slash=a.indexOf('/');source=TideRoleVfx.ribbons(a.substring(0,slash),a.substring(slash+1),phase);}
  return FiveNormalAttackVfx.contactTrails(source,d);
 }
 static List<LightSurface> surfaces(String a,double p){
  if(a.equals("phoebe/intro"))return PhoebePrismVfx.surfaces(a,p);
  if(!supports(a)||p<=0||p>=1)return List.of();double d=delta(a,p);if(d<=-2||d>=3)return List.of();
  double phase=geometryProgress(a,p);
  if(PhoebePrismVfx.supports(a))return PhoebePrismVfx.surfaces(a,phase);
  if(CantarellaTideVfx.supports(a))return CantarellaTideVfx.surfaces(a,p);
  return FanInfernalVfx.supports(a)?FanInfernalVfx.surfaces(a,phase):List.of();
 }
 static Sample sample(String a,double p,double u){
  var paths=ribbons(a,p);if(paths.isEmpty())return new Sample(0,0,0,0xffeffff9,.2f,Kind.NONE);
  double at=Math.clamp(u,0,.999999)*paths.size();var path=paths.get((int)at);var v=path.points().get(Math.min(24,(int)((at-(int)at)*25)));
  return new Sample(v.side(),v.up(),v.forward(),path.color(),.25f,Kind.DUST);
 }
}
