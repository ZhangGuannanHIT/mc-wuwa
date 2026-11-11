package cn.zgnhit.mcwuwa.content.mechanics;

import cn.zgnhit.mcwuwa.content.ActionDamageMapping;

/** Source qualification and explicit MC dimensions; never inferred from visual particle size. */
public final class ActionGeometryRules {
    private ActionGeometryRules() { }
    public static ActionDamageMapping.Geometry forChain(ActionDamageMapping action,int chain) {
        if(chain<0||chain>6)throw new IllegalArgumentException("Chain level");
        var base=action.geometry();
        // Hiyuki C1/2 specifies larger third-basic reach but no world-unit distance.
        // 1.5x radius AND width are authored MC adaptation, not a source-measured ratio.
        if(chain>=1&&action.id().equals("hiyuki/future_basic_03"))
            return new ActionDamageMapping.Geometry(base.shape(),base.rangeBlocks()*1.5,
                base.widthBlocks()*1.5,base.delivery(),base.requiresLineOfSight(),"minecraft_authored_hiyuki_c1_expansion");
        return base;
    }
}
