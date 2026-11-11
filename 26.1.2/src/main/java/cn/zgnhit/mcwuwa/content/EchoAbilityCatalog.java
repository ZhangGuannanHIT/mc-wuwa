package cn.zgnhit.mcwuwa.content;

import com.google.gson.JsonObject;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Audited max-rarity active echo hit groups; frame timing and geometry are MC authoring. */
public final class EchoAbilityCatalog {
    private static final Map<String, CharacterDefinition.DamageDefinition> DAMAGE_CACHE = new java.util.concurrent.ConcurrentHashMap<>();
    public record Hit(String damageId, int tick) { }
    public record Ability(int id, int cooldownTicks, int charges, boolean transformation,
                          int durationTicks, double rangeBlocks, double coneDot, List<Hit> hits,
                          boolean authoredApproximation, String timingEvidence) {
        public Ability { hits = List.copyOf(hits); }
    }
    private static final Map<Integer, Ability> ABILITIES = Map.ofEntries(
        Map.entry(6000121,ability(6000121,160,2,true,24,7,-1,repeated("28003101",4,6,4))),
        Map.entry(6000195,ability(6000195,400,1,false,30,7,-1,List.of(new Hit("20013101",16)))),
        Map.entry(6000224,ability(6000224,400,1,false,28,12,.5,repeated("20015401",3,8,6))),
        Map.entry(6000104,ability(6000104,400,1,false,32,7,-1,
            hits(new String[]{"20008801","20008802","20008802","20008802","20008802"},new int[]{12,16,20,24,28}))),
        Map.entry(6000200,ability(6000200,400,1,false,28,8,-1,List.of(new Hit("28004001",12)))),
        Map.entry(6000198,ability(6000198,500,1,true,30,8,-1,List.of(new Hit("20013401",12)))),
        Map.entry(6000160,ability(6000160,400,1,false,58,8,-1,hits(new String[]{"28003201","28003201","28003201","28003201","28003201","28003201","28003201","28003201","28003201","28003201","28003202"},new int[]{4,8,12,16,20,24,28,32,36,40,48}))),
        Map.entry(6000091,ability(6000091,500,1,true,32,7,0,List.of(new Hit("28002301",18)))),
        Map.entry(6000221,ability(6000221,500,1,true,32,8,-1,List.of(new Hit("28004401",16)))),
        Map.entry(6000114,ability(6000114,400,1,false,56,8,-1,hits(new String[]{"28002801","28002802"},new int[]{10,40}))),
        Map.entry(6000083,ability(6000083,500,1,true,32,7,0,List.of(new Hit("28001401",16)))),
        Map.entry(6000092,ability(6000092,400,1,false,30,8,-1,List.of(new Hit("28002401",12)))),
        Map.entry(6000082,ability(6000082,500,1,true,36,8,-1,List.of(new Hit("28001701",18)))),
        Map.entry(6000060,ability(6000060,400,1,false,24,5,-1,List.of(new Hit("280013010",6)))),
        Map.entry(6000192,ability(6000192,400,1,false,28,14,-1,List.of(new Hit("28003801",8)))),
        Map.entry(6000116,ability(6000116,400,1,false,30,7,-1,List.of(new Hit("28003001",12)))),
        Map.entry(6000052, ability(6000052,400,1,true,32,6,.0, List.of(new Hit("28001002",20)))),
        Map.entry(6000059, ability(6000059,400,1,false,52,10,-.5, hits(new String[]{"280012002","280012003","280012003","280012003","280012003","280012003","280012004","280012004"},new int[]{6,12,16,20,24,28,38,46}))),
        Map.entry(6000090, ability(6000090,240,3,true,24,6,.0, List.of(new Hit("28002201",12)))),
        Map.entry(6000106, ability(6000106,400,1,false,48,12,.5, hits(new String[]{"28002601","28002601","28002601","28002601","28002601","28002601","28002601","28002601","28002602"},new int[]{6,10,14,18,22,26,30,34,42}))),
        Map.entry(6000115, ability(6000115,500,1,true,40,7,-1, hits(new String[]{"28002901","28002901","28002901"},new int[]{16,24,32}))),
        Map.entry(6000167, ability(6000167,500,1,false,28,8,-1, hits(new String[]{"28003301","28003301"},new int[]{8,20}))),
        Map.entry(6000189, ability(6000189,400,1,false,48,16,.8, repeated("28003601",10,6,4))),
        Map.entry(6000190, ability(6000190,400,1,true,36,6,.0, List.of(new Hit("28003401",24)))),
        Map.entry(6000191, ability(6000191,400,1,false,32,10,.0, hits(new String[]{"28003701","28003702"},new int[]{10,24}))),
        Map.entry(6000199, ability(6000199,400,1,false,36,12,.2, hits(new String[]{"28003901","28003901","28003901","28003901","28003901","28003902"},new int[]{6,10,14,18,22,30}))),
        Map.entry(6000218, ability(6000218,400,1,false,24,8,-1, List.of(new Hit("28004303",10)))),
        // The project's fixed user of 6000225 is Hsin: its six-hit Hsin override applies.
        Map.entry(6000225, ability(6000225,400,1,false,40,10,-.4, hits(new String[]{"280045010","280045010","280045010","280045010","280045010","280045011"},new int[]{6,10,14,18,22,32})))
    );
    private EchoAbilityCatalog() { }
    public static Ability require(int id) {
        Ability ability = ABILITIES.get(id); if (ability == null) throw new IllegalArgumentException("Unmapped echo ability " + id); return ability;
    }
    public static java.util.Collection<Ability> abilities() { return ABILITIES.values(); }
    private static Ability ability(int id,int cooldown,int charges,boolean transform,int duration,double range,double cone,List<Hit> hits) {
        return new Ability(id,cooldown,charges,transform,duration,range,cone,hits,true,"minecraft_authored_approximation; original timing and collision are unverified");
    }
    private static List<Hit> hits(String[] ids,int[] times) { List<Hit> result=new ArrayList<>();for(int i=0;i<ids.length;i++)result.add(new Hit(ids[i],times[i]));return result; }
    private static List<Hit> repeated(String id,int count,int first,int step) { List<Hit> result=new ArrayList<>();for(int i=0;i<count;i++)result.add(new Hit(id,first+i*step));return result; }
    public static CharacterDefinition.DamageDefinition damage(ContentCatalog catalog,int echoId,String damageId) {
        return DAMAGE_CACHE.computeIfAbsent(echoId+"/"+damageId, key -> loadDamage(catalog,echoId,damageId));
    }
    private static CharacterDefinition.DamageDefinition loadDamage(ContentCatalog catalog,int echoId,String damageId) {
        JsonObject raw=catalog.echo(echoId).getAsJsonObject("raw").getAsJsonObject("skill").getAsJsonObject("damage").getAsJsonObject(damageId);
        if(raw==null)throw new IllegalArgumentException("Unknown echo source damage "+echoId+"/"+damageId);
        String element=switch(raw.get("element").getAsInt()){case 1->"glacio";case 2->"fusion";case 3->"electro";case 4->"aero";case 5->"spectro";case 6->"havoc";default->throw new IllegalStateException("unmapped echo element");};
        int type=raw.get("type").getAsInt();String kind=switch(type){case 4->"skill";case 5->"echo";default->throw new IllegalStateException("unmapped echo damage kind");};
        var rates=raw.getAsJsonArray("rate_lv");
        return new CharacterDefinition.DamageDefinition(damageId,type,kind,element,raw.has("related_property")&&raw.get("related_property").getAsString().equals("生命")?"health":"attack",5,rates.get(rates.size()-1).getAsDouble()/10000,
                raw.get("tough_lv").getAsDouble()/5943,raw.get("weakness_lvl").getAsDouble()/3960,raw,true,
                List.of("Echo timing/geometry are authored; displayed rounding may differ from exact raw fixed-point rate."));
    }
    /** Used only when the source description supplies a real damage coefficient but omits its damage row. */
    public static CharacterDefinition.DamageDefinition describedProc(ContentCatalog catalog,int echoId,int parameter,String element,String kind) {
        var source=catalog.echo(echoId);String value=source.getAsJsonArray("parameters").get(parameter).getAsString();
        if(!value.matches("[0-9]+(?:\\.[0-9]+)?%"))throw new IllegalStateException("not a percentage parameter");
        JsonObject evidence=new JsonObject();evidence.addProperty("echoId",echoId);evidence.addProperty("sourceParameter",parameter);evidence.addProperty("sourceFormula",value);
        return new CharacterDefinition.DamageDefinition("echo_"+echoId+"_parameter_"+parameter,kind.equals("skill")?4:5,kind,element,"attack",5,
                Double.parseDouble(value.substring(0,value.length()-1))/100,0,0,evidence,true,List.of("Source damage row not published; toughness/off-tune unknown and not credited."));
    }
}
