package cn.zgnhit.mcwuwa.content.mechanics;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.List;
import java.util.ArrayList;
import java.util.Optional;
import java.util.Set;
import static cn.zgnhit.mcwuwa.content.mechanics.MechanicsEngine.*;

/** Fixed 3.7.8 per-damage resource slots, independently audited against the 3.7.6 public projection.
 * The platform calls this once per actually successful timeline hit, never once per whole action/target.
 * Multi-target dispatch and Camellya's remainder lifetime are explicit Minecraft adaptations.
 */
public final class NativeResourceYields {
    public record Yield(String resource, double points, double rawUnitsPerPoint) { }
    private record Rule(Yield yield, Set<String> allowedForms, Map<String,Double> timerMultipliers) { }
    private record Profile(Map<String, List<Rule>> damages, Map<String, Set<String>> actions) { }
    private static final Map<String, Profile> PROFILES = load();
    private NativeResourceYields() { }

    private static Map<String, Profile> load() {
        try (var stream = NativeResourceYields.class.getResourceAsStream("/mc_wuwa/combat/native_resources.json")) {
            if (stream == null) throw new IllegalStateException("Missing audited native resource definitions");
            JsonObject root = JsonParser.parseReader(new InputStreamReader(stream, StandardCharsets.UTF_8)).getAsJsonObject();
            if (root.get("schemaVersion").getAsInt() != 1 || !root.get("resourceVersion").getAsString().equals("3.7.8")
                || !root.get("commonProjectionVerified").getAsBoolean() || root.get("comparedUniqueDamageRows").getAsInt() != 2064)
                throw new IllegalStateException("Native resource source needs a new audit");
            Map<String, Profile> profiles = new HashMap<>();
            for (var entry : root.getAsJsonObject("profiles").entrySet()) {
                String slug = entry.getKey(); JsonObject profile = entry.getValue().getAsJsonObject();
                String resource = profile.get("resource").getAsString(); double units = profile.get("rawUnitsPerPoint").getAsDouble();
                double cap = profile.get("displayedCap").getAsDouble();
                if (units < 1 || cap != MechanicsEngine.resourceCaps(slug).getOrDefault(resource, -1d)
                    || profile.get("sourceRawCap").getAsDouble() != cap * units) throw new IllegalStateException("Resource slot/cap disagrees: " + slug);
                Map<String, List<Rule>> damages = new HashMap<>();
                for (var damage : profile.getAsJsonObject("damages").entrySet()) {
                    JsonObject value=damage.getValue().getAsJsonObject();
                    List<Rule> rules=new ArrayList<>();rules.add(readRule(slug,value,resource,units));
                    if(value.has("additionalYields"))for(var extra:value.getAsJsonArray("additionalYields"))
                        rules.add(readRule(slug,extra.getAsJsonObject(),resource,units));
                    damages.put(damage.getKey(),List.copyOf(rules));
                }
                Map<String, Set<String>> actions = new HashMap<>();
                for (var action : profile.getAsJsonObject("actions").entrySet()) {
                    if (!action.getKey().startsWith(slug + "/")) throw new IllegalStateException("Foreign resource action");
                    Set<String> ids = new HashSet<>();
                    for (var id : action.getValue().getAsJsonArray()) {
                        if (!damages.containsKey(id.getAsString())) throw new IllegalStateException("Unknown resource damage reference");
                        ids.add(id.getAsString());
                    }
                    actions.put(action.getKey(), Set.copyOf(ids));
                }
                profiles.put(slug, new Profile(Map.copyOf(damages), Map.copyOf(actions)));
            }
            return Map.copyOf(profiles);
        } catch (Exception failure) { throw new IllegalStateException("Cannot load audited native resource definitions", failure); }
    }
    private static Rule readRule(String slug,JsonObject value,String fallbackResource,double fallbackUnits) {
        double units=value.has("rawUnitsPerPoint")?value.get("rawUnitsPerPoint").getAsDouble():fallbackUnits;
        double points=value.get("points").getAsDouble();
        var rawDifference=value.get("points").getAsBigDecimal().multiply(java.math.BigDecimal.valueOf(units))
            .subtract(value.get("rawChange").getAsBigDecimal()).abs();
        // Integer scales retain exact checks. Fractional scales (War650/100)
        // require decimal rounding tolerance for non-terminating quotient JSON.
        boolean rawMatches=units==Math.rint(units)?rawDifference.signum()==0
            :rawDifference.compareTo(new java.math.BigDecimal("0.000000001"))<=0;
        if(!Double.isFinite(units)||units<1||!Double.isFinite(points)||points==0||!rawMatches
            ||!value.get("originalCondition").getAsString().isEmpty()||value.get("executionTiming").getAsInt()!=0)
            throw new IllegalStateException("Unsupported resource condition or units");
        String resource=value.has("resource")?value.get("resource").getAsString():fallbackResource;
        if(!MechanicsEngine.resourceCaps(slug).containsKey(resource))throw new IllegalStateException("Unknown resource override: "+resource);
        Set<String> forms=new HashSet<>();if(value.has("allowedForms"))for(var form:value.getAsJsonArray("allowedForms"))forms.add(form.getAsString());
        Map<String,Double> multipliers=new HashMap<>();
        if(value.has("timerMultipliers"))for(var factor:value.getAsJsonObject("timerMultipliers").entrySet()) {
            double amount=factor.getValue().getAsDouble();if(!Double.isFinite(amount)||amount<=0)throw new IllegalStateException("Invalid resource multiplier");
            multipliers.put(factor.getKey(),amount);
        }
        return new Rule(new Yield(resource,points,units),Set.copyOf(forms),Map.copyOf(multipliers));
    }
    public static boolean hasRuleForAction(String slug,String action) {
        Profile profile=PROFILES.get(slug);return profile!=null&&profile.actions.containsKey(action);
    }
    /** Compatibility lookup for callers inspecting a single legacy slot. Actual transactions use findAll. */
    public static Optional<Yield> find(String slug,String action,String damage) {
        Profile profile=PROFILES.get(slug);
        if(profile==null||!profile.actions.getOrDefault(action,Set.of()).contains(damage))return Optional.empty();
        List<Rule> rules=profile.damages.get(damage);return rules==null||rules.isEmpty()?Optional.empty():Optional.of(rules.getFirst().yield());
    }
    public static List<Yield> findAll(MechanicsState state,String action,String damage) {
        Profile profile=PROFILES.get(state.characterId());
        if(profile==null||!profile.actions.getOrDefault(action,Set.of()).contains(damage))return List.of();
        List<Yield> result=new ArrayList<>();
        for(Rule rule:profile.damages.getOrDefault(damage,List.of())) {
            if(!rule.allowedForms.isEmpty()&&!rule.allowedForms.contains(state.form()))continue;
            double multiplier=1;for(var factor:rule.timerMultipliers.entrySet())if(state.timer(factor.getKey())>0)multiplier*=factor.getValue();
            Yield yield=rule.yield;result.add(new Yield(yield.resource(),yield.points()*multiplier,yield.rawUnitsPerPoint()));
        }
        return List.copyOf(result);
    }
    static void apply(Transition t, Yield yield) {
        double before = t.get(yield.resource());
        t.add(yield.resource(), yield.points());
        double consumed = Math.max(0, before - t.get(yield.resource()));
        if (t.original.characterId().equals("chisa") && consumed > 0) t.add("saw_consumed", consumed);
        if (t.original.characterId().equals("camellya") && consumed > 0) {
            // Source text gives4 concerto and1 integer bud per10 points. MC uses a
            // per-instance hundredth-point remainder across hits/actions until state
            // cleanup, explicitly not a claim about the unavailable original55/56 executor.
            int units = Math.addExact(t.time("camellya_pistil_remainder"), (int)Math.round(consumed * yield.rawUnitsPerPoint()));
            int threshold = Math.toIntExact(Math.round(10 * yield.rawUnitsPerPoint())), rewards = units / threshold;
            t.timer("camellya_pistil_remainder", units % threshold);
            if (rewards > 0) {
                t.effect(Kind.GAIN_CONCERTO, "camellya_pistils", rewards * 4, 0);
                if (!t.has("budding")) { t.add("buds", rewards); t.timer("buds", 300); }
            }
            // The source disables buds and resonance-energy gain during budding;
            // it does not say to discard the separately specified concerto reward.
            if (t.get("pistils") == 0 && t.has("budding")) {
                t.clear("budding"); t.effect(Kind.BUFF_SELF, "camellya_budding_multiplier", 0, 0);
                t.effect(Kind.BUFF_SELF, "camellya_energy_gain_disabled", 0, 0);
            }
        }
    }
}
