package cn.zgnhit.mcwuwa.platform;

import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;

/** 1.21.1 only sends continuous vanilla directional input while riding. */
public record MovementKeys(boolean forward, boolean backward, boolean left, boolean right,
                           boolean jump, boolean shift, boolean sprint) implements CustomPacketPayload {
    public static final MovementKeys EMPTY = new MovementKeys(false,false,false,false,false,false,false);
    private static final Map<ServerPlayer, MovementKeys> HELD = new WeakHashMap<>();
    public static final Type<MovementKeys> TYPE = new Type<>(ResourceLocation.fromNamespaceAndPath("mc_wuwa","movement_keys"));
    public static final StreamCodec<RegistryFriendlyByteBuf, MovementKeys> CODEC = StreamCodec.of(
        (buf, keys) -> buf.writeByte(keys.mask()), buf -> fromMask(buf.readUnsignedByte()));

    private int mask() {
        return (forward ? 1 : 0) | (backward ? 2 : 0) | (left ? 4 : 0) | (right ? 8 : 0)
            | (jump ? 16 : 0) | (shift ? 32 : 0) | (sprint ? 64 : 0);
    }
    private static MovementKeys fromMask(int mask) {
        return new MovementKeys((mask&1)!=0,(mask&2)!=0,(mask&4)!=0,(mask&8)!=0,
            (mask&16)!=0,(mask&32)!=0,(mask&64)!=0);
    }
    public static MovementKeys get(ServerPlayer player) { return HELD.getOrDefault(player, EMPTY); }
    public static void set(ServerPlayer player, MovementKeys keys) { HELD.put(player, keys); }
    @Override public Type<MovementKeys> type() { return TYPE; }
}
