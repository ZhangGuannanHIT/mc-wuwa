package cn.zgnhit.mcwuwa.platform;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;

/** Read legacy item and entity fields while preserving the original missing-value defaults. */
public final class NbtValues {
    private NbtValues() {}
    public static String getString(CompoundTag tag, String key, String fallback) {
        return tag.contains(key, Tag.TAG_STRING) ? tag.getString(key) : fallback;
    }
    public static int getInt(CompoundTag tag, String key, int fallback) {
        return tag.contains(key, Tag.TAG_ANY_NUMERIC) ? tag.getInt(key) : fallback;
    }
    public static double getDouble(CompoundTag tag, String key, double fallback) {
        return tag.contains(key, Tag.TAG_ANY_NUMERIC) ? tag.getDouble(key) : fallback;
    }
    public static float getFloat(CompoundTag tag, String key, float fallback) {
        return tag.contains(key, Tag.TAG_ANY_NUMERIC) ? tag.getFloat(key) : fallback;
    }
}
