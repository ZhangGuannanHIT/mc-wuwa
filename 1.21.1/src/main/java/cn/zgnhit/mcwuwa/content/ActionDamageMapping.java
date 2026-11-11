package cn.zgnhit.mcwuwa.content;

import java.util.List;

/** Source-verified damage grouping. Missing time and geometry are never given invented defaults. */
public record ActionDamageMapping(String id, String name, String characterId, String weaponId,
        int sourceNode, String sourceParameter, String sourceFormula, String requiredForm,
        boolean damageGroupingVerified, boolean executable, List<HitGroup> hitGroups,
        double totalMultiplier, int totalHitCount, Integer durationTicks, List<Integer> hitTimesTicks,
        boolean authoredApproximation, boolean nativeTimingVerified, Geometry geometry, String timingEvidence,
        List<String> unresolved) {
    public ActionDamageMapping {
        hitGroups = List.copyOf(hitGroups);
        hitTimesTicks = hitTimesTicks == null ? null : List.copyOf(hitTimesTicks);
        unresolved = List.copyOf(unresolved);
    }
    public record HitGroup(String damageId, int count) {
        public HitGroup { if (count <= 0) throw new IllegalArgumentException("Hit count must be positive"); }
    }
    public record Geometry(String shape, double rangeBlocks, double widthBlocks, String delivery,
                           boolean requiresLineOfSight, String source) { }
}
