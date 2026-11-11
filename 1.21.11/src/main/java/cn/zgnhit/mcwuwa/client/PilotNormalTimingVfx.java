package cn.zgnhit.mcwuwa.client;
import java.util.*;
import static cn.zgnhit.mcwuwa.client.NewRoleVfxProfiles.*;
/** Retain the existing source-observed pilot silhouettes; fit each report to the accepted hit clock. */
final class PilotNormalTimingVfx {
 record Clock(int duration,int[] hits){}
 private static final Map<String,Clock> CLOCKS=Map.ofEntries(
Map.entry("aemeath/basic_01",new Clock(12,new int[]{3})),
Map.entry("aemeath/basic_02",new Clock(16,new int[]{4,8,12})),
Map.entry("aemeath/basic_03",new Clock(22,new int[]{5,8,12,15,18})),
Map.entry("aemeath/basic_04",new Clock(28,new int[]{7,10,14,17,21,24})),
Map.entry("jinhsi/basic_01",new Clock(14,new int[]{3})),
Map.entry("jinhsi/basic_02",new Clock(18,new int[]{4,7,11,14})),
Map.entry("jinhsi/basic_03",new Clock(24,new int[]{6,8,10,12,14,16,18,20})),
Map.entry("jinhsi/basic_04",new Clock(22,new int[]{5,18})),
Map.entry("lynae/basic_01",new Clock(10,new int[]{3})),
Map.entry("lynae/basic_02",new Clock(14,new int[]{3,6,10})),
Map.entry("lynae/basic_03",new Clock(16,new int[]{4})),
Map.entry("mornye/basic_01",new Clock(16,new int[]{4,8,12})),
Map.entry("mornye/basic_02",new Clock(20,new int[]{5,7,9,12,14,16})),
Map.entry("mornye/basic_03",new Clock(24,new int[]{6,8,11,13,15,18,20})),
Map.entry("mornye/basic_04",new Clock(26,new int[]{6})),
Map.entry("cartethyia/basic_01",new Clock(12,new int[]{3})),
Map.entry("cartethyia/basic_02",new Clock(18,new int[]{4,9,14})),
Map.entry("cartethyia/basic_03",new Clock(20,new int[]{5,9,12,16})),
Map.entry("cartethyia/basic_04",new Clock(22,new int[]{5,9,14,18})),
Map.entry("phrolova/basic_01",new Clock(16,new int[]{4,12})),
Map.entry("phrolova/basic_02",new Clock(18,new int[]{4})),
Map.entry("phrolova/basic_03",new Clock(26,new int[]{6,9,12,16,19,22})),
Map.entry("hsin/basic_01",new Clock(14,new int[]{3,10})),
Map.entry("hsin/basic_02",new Clock(20,new int[]{5,9,12,16})),
Map.entry("hsin/basic_03",new Clock(24,new int[]{6,10,13,16,20})),
Map.entry("hsin/basic_04",new Clock(26,new int[]{6,14,22})),
Map.entry("yinlin/basic_01",new Clock(12,new int[]{3})),
Map.entry("yinlin/basic_02",new Clock(16,new int[]{4,12})),
Map.entry("yinlin/basic_03",new Clock(26,new int[]{6,9,11,14,17,19,22})),
Map.entry("yinlin/basic_04",new Clock(20,new int[]{5})));
 private PilotNormalTimingVfx(){}
 static boolean supports(String a){return CLOCKS.containsKey(a);}
 static double delta(String a,double p){var c=CLOCKS.get(a);if(c==null)return Double.POSITIVE_INFINITY;double d=Double.POSITIVE_INFINITY;for(int h:c.hits())if(Math.abs(p*c.duration()-h)<Math.abs(d))d=p*c.duration()-h;return d;}
 static List<LightPath> ribbons(String a,double p){double d=delta(a,p);if(d<=-2||d>=3)return List.of();
  double phase=.42+Math.clamp(d,-2,3)*.06;
  // Dispatch directly to the original authored geometry, never recurse through the global profile.
  var paths=a.startsWith("yinlin/")?BlazePuppetAttackVfx.ribbons(a,phase):SupplementalActionVfx.ribbons(a,phase);
  return FiveNormalAttackVfx.contactTrails(paths,d);
 }
 static List<LightSurface> surfaces(String a,double p){double d=delta(a,p);if(Math.abs(d)>1.5)return List.of();
  // Surfaces and their bright outlines must advance on the same contact phase.
  double phase=.42+Math.clamp(d,-2,3)*.06;
  if(a.startsWith("jinhsi/basic_")||a.equals("cartethyia/basic_01")||a.equals("cartethyia/basic_02")){
   // Retain the reviewed inner/outer blade edges. The later Cartethyia
   // aperture rays and volumetric sword seal are deliberately excluded.
   var paths=ribbons(a,p);int alpha=(int)Math.round(36*Math.clamp(1-Math.max(0,d)/3,0,1));
   return SlashSurfaceGeometry.between(paths.getFirst(),paths.get(a.startsWith("jinhsi/")?2:1),
       alpha<<24|(a.startsWith("jinhsi/")?0xffe9ab:0x8feaff));
  }
  if(a.startsWith("yinlin/")){
   // Fill only the puppet's attack strokes. The last three paths are taut
   // hand-control strings and must remain thin, never broad opaque sheets.
   var paths=ribbons(a,p);var out=new ArrayList<LightSurface>();
   int alpha=(int)Math.round(40*Math.clamp(1-Math.max(0,d)/3,0,1));
   if(a.equals("yinlin/basic_03"))return SlashSurfaceGeometry.between(paths.get(0),paths.get(1),alpha<<24|0xff70c8);
   for(int i=0;i<paths.size()-3;i++)out.addAll(SlashSurfaceGeometry.verticalWidth(paths.get(i),.045,
       alpha<<24|(paths.get(i).color()&0xffffff)));
   return List.copyOf(out);
  }
  if(a.startsWith("phrolova/"))return PhrolovaPilotVfx.surfaces(a,phase);
  if(a.startsWith("mornye/"))return MornyePilotVfx.surfaces(a,phase);
  if(a.startsWith("aemeath/"))return AemeathPilotVfx.surfaces(a,phase);
  if(a.startsWith("lynae/"))return LynaePilotVfx.surfaces(a,phase);
  return List.of();
 }
 static Sample sample(String a,double p,double u){var paths=ribbons(a,p);if(paths.isEmpty())return new Sample(0,0,0,0xffffffff,.2f,Kind.NONE);double at=Math.clamp(u,0,.999999)*paths.size();var path=paths.get((int)at);var v=path.points().get(Math.min(path.points().size()-1,(int)((at-(int)at)*path.points().size())));return new Sample(v.side(),v.up(),v.forward(),path.color(),.25f,Kind.DUST);}
}
