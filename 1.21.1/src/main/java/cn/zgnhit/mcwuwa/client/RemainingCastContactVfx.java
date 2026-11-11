package cn.zgnhit.mcwuwa.client;
import java.util.*;
import static cn.zgnhit.mcwuwa.client.NewRoleVfxProfiles.*;
/** Preserve source-reviewed branch geometry; trim only its anticipation/recovery emission. */
final class RemainingCastContactVfx {
 private RemainingCastContactVfx(){}
 static boolean supports(String a){return RemainingCastContactClocks.ALL.containsKey(a);}
 static double delta(String a,double p){var c=RemainingCastContactClocks.ALL.get(a);double d=Double.POSITIVE_INFINITY;
  if(c!=null)for(int h:c.hits())if(Math.abs(p*c.duration()-h)<Math.abs(d))d=p*c.duration()-h;return d;}
 // The sword-zither release has a gathering phase and a final giant blade.
 // Sampling every contact at .5 would permanently hide that finishing phase.
 // Aero intro likewise has a rising entry before its low second-contact sweep.
 static double geometryProgress(String a,double p){return a.startsWith("qingxiao/heavy_banishing")||a.startsWith("yangyang_xuanling/flow_")||a.equals("aero_rover/intro")?p:.5+Math.clamp(delta(a,p),-2,3)*.04;}
 static List<LightPath> ribbons(String a,double p){
  if(!supports(a)||p<=0||p>=1)return List.of();double d=delta(a,p);if(d<=-2||d>=3)return List.of();
  return FiveNormalAttackVfx.contactTrails(NewRoleVfxProfiles.retainedRibbons(a,geometryProgress(a,p)),d);
 }
 static List<LightSurface> surfaces(String a,double p){
  if(!supports(a)||p<=0||p>=1)return List.of();double d=delta(a,p);if(d<=-2||d>=3)return List.of();
  if(a.equals("aero_rover/intro")){
   var edges=NewRoleVfxProfiles.retainedRibbons(a,geometryProgress(a,p));
   return SlashSurfaceGeometry.between(edges.getFirst(),edges.getLast(),0x288cdcff);
  }
  var faces=NewRoleVfxProfiles.retainedSurfaces(a,geometryProgress(a,p));
  if(a.equals("camellya/ephemeral"))return faces.stream().map(f->new LightSurface(f.vertices(),(Math.min(f.color()>>>24,32)<<24)|(f.color()&0xffffff),f.texture())).toList();
  return faces;
 }
 static Sample sample(String a,double p,double u){
  var paths=ribbons(a,p);if(paths.isEmpty())return new Sample(0,0,0,0xffeffff9,.2f,Kind.NONE);
  double at=Math.clamp(u,0,.999999)*paths.size();var path=paths.get((int)at);var v=path.points().get(Math.min(path.points().size()-1,(int)((at-(int)at)*path.points().size())));
  return new Sample(v.side(),v.up(),v.forward(),path.color(),.25f,Kind.DUST);
 }
}
