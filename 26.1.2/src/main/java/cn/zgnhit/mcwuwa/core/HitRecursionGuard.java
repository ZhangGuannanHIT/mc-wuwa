package cn.zgnhit.mcwuwa.core;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
/** One synchronous damage chain; sequential legitimate segments start independent chains. */
public final class HitRecursionGuard {
    public record Key(UUID weapon, UUID target, String action) {
        public Key { java.util.Objects.requireNonNull(weapon);java.util.Objects.requireNonNull(target);Checks.id(action); }
    }
    private final Set<Key> active=new HashSet<>();
    private final int depthLimit;
    public HitRecursionGuard(int depthLimit) {if(depthLimit<1)throw new IllegalArgumentException("depth limit");this.depthLimit=depthLimit;}
    public boolean enter(Key key) {return active.size()<depthLimit&&active.add(java.util.Objects.requireNonNull(key));}
    public void exit(Key key) {if(!active.remove(key))throw new IllegalStateException("damage chain scope mismatch");}
    public int depth(){return active.size();}
}
