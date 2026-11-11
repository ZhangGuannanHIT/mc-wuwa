package cn.zgnhit.mcwuwa.core;

import java.util.*;

/** Shared target caps: keep every source/expiry, choose the strongest within each named group. */
public final class EffectCapLedger {
    public enum Lifetime { TIMED, UNTIL_SOURCE_LOGOUT, UNTIL_WEAPON_UNEQUIPPED }
    public record Contribution(UUID instance, String effect, String group, EffectSource source,
                               int additionalLayers, long expiresAt, Lifetime lifetime) {
        public Contribution {
            Objects.requireNonNull(instance); Checks.id(effect); Checks.id(group); Objects.requireNonNull(source);
            if(additionalLayers<1||additionalLayers>1024)throw new IllegalArgumentException("additional layers");
            Checks.tick(expiresAt); Objects.requireNonNull(lifetime);
        }
    }
    private final Map<UUID,Contribution> contributions=new HashMap<>();
    public void pauseOneTick(){contributions.replaceAll((id,c)->new Contribution(c.instance,c.effect,c.group,c.source,c.additionalLayers,CinematicTimeRules.postpone(c.expiresAt,1),c.lifetime));}
    public void put(Contribution contribution) {
        Objects.requireNonNull(contribution);
        var previous=contributions.get(contribution.instance());
        if(previous!=null&&(!previous.effect().equals(contribution.effect())||!previous.group().equals(contribution.group())
            ||!previous.source().weaponInstanceId().equals(contribution.source().weaponInstanceId())
            ||!previous.source().playerId().equals(contribution.source().playerId())))
            throw new IllegalArgumentException("cap contribution identity changed");
        contributions.put(contribution.instance(),contribution);
    }
    public int resolve(String effect,int baseCap,long now) {
        Checks.id(effect); Checks.tick(now); if(baseCap<1||baseCap>1024)throw new IllegalArgumentException("base cap");
        Map<String,Integer> groups=new HashMap<>();
        for(var contribution:contributions.values())if(contribution.effect().equals(effect)&&contribution.expiresAt()>now)
            groups.merge(contribution.group(),contribution.additionalLayers(),Math::max);
        long total=baseCap;for(int value:groups.values())total+=value;
        return (int)Math.min(1024,total);
    }
    public void expire(long now) {Checks.tick(now);contributions.values().removeIf(c->c.expiresAt()<=now);}
    public void sourceLoggedOut(UUID player) {
        Objects.requireNonNull(player);
        contributions.values().removeIf(c->c.source().playerId().equals(player)&&c.lifetime()!=Lifetime.TIMED);
    }
    public void weaponUnequipped(UUID weapon) {
        Objects.requireNonNull(weapon);
        contributions.values().removeIf(c->c.source().weaponInstanceId().equals(weapon)&&c.lifetime()==Lifetime.UNTIL_WEAPON_UNEQUIPPED);
    }
    public List<Contribution> active(long now) {
        Checks.tick(now);return contributions.values().stream().filter(c->c.expiresAt()>now)
            .sorted(Comparator.comparing(c->c.instance().toString())).toList();
    }
    public boolean isEmpty(){return contributions.isEmpty();}
}
