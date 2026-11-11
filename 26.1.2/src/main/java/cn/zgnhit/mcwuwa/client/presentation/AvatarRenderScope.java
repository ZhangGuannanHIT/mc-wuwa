package cn.zgnhit.mcwuwa.client.presentation;

/** A world body replacement must not replace inventory portraits or minimap head/icon passes. */
public final class AvatarRenderScope {
    private static final ThreadLocal<Integer> WORLD_DEPTH=ThreadLocal.withInitial(()->0);
    private AvatarRenderScope() { }
    public static void enterWorld(){WORLD_DEPTH.set(WORLD_DEPTH.get()+1);}
    public static void leaveWorld(){int depth=WORLD_DEPTH.get();if(depth<=1)WORLD_DEPTH.remove();else WORLD_DEPTH.set(depth-1);}
    public static boolean worldPass(){return WORLD_DEPTH.get()>0;}
}
