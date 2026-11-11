package cn.zgnhit.mcwuwa.content;

import cn.zgnhit.mcwuwa.core.Element;
import cn.zgnhit.mcwuwa.core.ElementalEffects;
import cn.zgnhit.mcwuwa.core.GlobalCombatRules;
import java.util.*;

/** Source-backed terminology, separated from unverified hidden numeric tables. */
public final class EffectDefinitions {
    public record Definition(String id, String chineseName, Element element,
                             ElementalEffects.Kind kind, OptionalInt baseCap,
                             String evidenceId, Set<String> missingNumericFields) {
        public Definition { missingNumericFields = Set.copyOf(missingNumericFields); }
    }
    /** Installation requires an actual source id; no guessed default is supplied. */
    public record VerifiedExecution(ElementalEffects.Rules rules, OptionalInt freezeTicks,
                                    OptionalDouble slowPerLayer, OptionalDouble burstRadiusBlocks, boolean sourceVerified) {
        public VerifiedExecution(ElementalEffects.Rules rules,OptionalInt freeze,OptionalDouble slow,OptionalDouble radius) {
            this(rules,freeze,slow,radius,true);
        }
        public VerifiedExecution {
            Objects.requireNonNull(rules); Objects.requireNonNull(freezeTicks);
            Objects.requireNonNull(slowPerLayer); Objects.requireNonNull(burstRadiusBlocks);
            if (freezeTicks.isPresent() && freezeTicks.getAsInt() < 1) throw new IllegalArgumentException("freeze ticks");
            if (slowPerLayer.isPresent() && (slowPerLayer.getAsDouble() < 0 || slowPerLayer.getAsDouble() > 1)) throw new IllegalArgumentException("slow");
            if (burstRadiusBlocks.isPresent() && (!Double.isFinite(burstRadiusBlocks.getAsDouble()) || burstRadiusBlocks.getAsDouble() <= 0)) throw new IllegalArgumentException("radius");
            if (rules.kind() == ElementalEffects.Kind.FUSION_BURST && burstRadiusBlocks.isEmpty()) throw new IllegalArgumentException("Fusion execution needs sourced/authored radius evidence");
        }
    }
    private static final Map<String, Definition> DEFINITIONS = Map.ofEntries(
        entry("spectro_frazzle", "光噪效应", Element.SPECTRO, ElementalEffects.Kind.SPECTRO_FRAZZLE, 0, "850008", "base_cap", "duration", "period", "level_damage_curve"),
        entry("aero_erosion", "风蚀效应", Element.AERO, ElementalEffects.Kind.AERO_EROSION, 0, "850009", "base_cap", "duration", "period", "layer_decay_period", "level_damage_curve"),
        entry("glacio_chafe", "霜渐效应", Element.GLACIO, ElementalEffects.Kind.GLACIO_CHAFE, 10, "850010", "duration", "slow_per_layer", "freeze_duration_curve", "level_damage_curve"),
        entry("fusion_burst", "聚爆效应", Element.FUSION, ElementalEffects.Kind.FUSION_BURST, 10, "850011", "duration", "burst_radius", "level_damage_curve"),
        entry("havoc_bane", "虚湮效应", Element.HAVOC, ElementalEffects.Kind.HAVOC_BANE, 3, "850012", "duration"),
        entry("electro_flare", "电磁效应", Element.ELECTRO, ElementalEffects.Kind.ELECTRO_FLARE, 10, "850013", "duration", "period", "level_damage_curve", "rage_damage_curve")
    );
    private static final Map<String, VerifiedExecution> EXECUTIONS = new HashMap<>();
    private static final Definition FROST=new Definition("glacio_frost","霜冻效应",Element.GLACIO,ElementalEffects.Kind.GLACIO_FROST,OptionalInt.of(10),
        "nanoka_3_7_character_1108_forte",Set.of("native_formula_1007","duration","freeze_duration_curve","slow_per_layer"));
    private EffectDefinitions() {}
    private static Map.Entry<String, Definition> entry(String id, String name, Element element,
            ElementalEffects.Kind kind, int cap, String source, String... missing) {
        return Map.entry(id, new Definition(id, name, element, kind, cap == 0 ? OptionalInt.empty() : OptionalInt.of(cap),
            "nanoka_3_7_hyperlink_" + source, Set.of(missing)));
    }
    public static Optional<Definition> find(String id) { return Optional.ofNullable(id.equals(FROST.id())?FROST:DEFINITIONS.get(id)); }
    public static Collection<Definition> all() { return DEFINITIONS.values(); }
    public static Optional<VerifiedExecution> execution(String id) { return Optional.ofNullable(EXECUTIONS.get(id)); }
    /** Register during common initialization, before any live world has effect rounds. */
    public static void registerVerified(VerifiedExecution execution) {
        if(!execution.sourceVerified())throw new IllegalArgumentException("MC adaptations cannot be registered as verified source executors");
        Definition definition = find(execution.rules().definitionId()).orElseThrow();
        if (definition.kind() != execution.rules().kind()) throw new IllegalArgumentException("effect kind mismatch");
        if (EXECUTIONS.putIfAbsent(definition.id(), execution) != null) throw new IllegalStateException("effect execution already frozen");
    }
    /** Fixed and explicitly labelled user-authorized fallback; never rewrites the frozen source manifest. */
    public static void installAdaptations() {
        for(var definition:DEFINITIONS.values()) {
            if(EXECUTIONS.containsKey(definition.id()))continue;
            var kind=definition.kind();
            EXECUTIONS.put(definition.id(),new VerifiedExecution(GlobalCombatRules.effect(definition.id(),kind),
                kind==ElementalEffects.Kind.GLACIO_CHAFE?OptionalInt.of(80):OptionalInt.empty(),
                kind==ElementalEffects.Kind.GLACIO_CHAFE?OptionalDouble.of(.02):OptionalDouble.empty(),
                kind==ElementalEffects.Kind.FUSION_BURST?OptionalDouble.of(3):OptionalDouble.empty(),false));
        }
        EXECUTIONS.putIfAbsent(FROST.id(),new VerifiedExecution(GlobalCombatRules.effect(FROST.id(),FROST.kind()),OptionalInt.of(80),OptionalDouble.of(.02),OptionalDouble.empty(),false));
    }
    public static final double HAVOC_DEFENSE_REDUCTION_PER_LAYER = .02;
}
