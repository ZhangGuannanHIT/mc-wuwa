package cn.zgnhit.mcwuwa.platform;
import net.minecraft.world.entity.player.Player;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.attachment.*;
import net.neoforged.neoforge.registries.*;
public final class ModAttachments {
    private static final DeferredRegister<AttachmentType<?>> TYPES = DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, "mc_wuwa");
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<TeamAttachment>> TEAM = TYPES.register("team",
        () -> AttachmentType.<TeamAttachment>builder(holder -> new TeamAttachment((Player) holder))
            .serialize(new IAttachmentSerializer<CompoundTag, TeamAttachment>() {
                @Override public TeamAttachment read(IAttachmentHolder holder, CompoundTag input, HolderLookup.Provider provider) {
                    // Player constructs InventoryMenu before login/death-copy decoding. Its native slots
                    // already capture this container; replacing the attachment leaves those slots orphaned.
                    var existing = holder.getData(TEAM);
                    existing.deserializeNBT(provider, input);
                    return existing;
                }
                @Override public CompoundTag write(TeamAttachment attachment, HolderLookup.Provider provider) {
                    return attachment.serializeNBT(provider);
                }
            }).copyOnDeath().build());
    public static TeamAttachment team(Player player) { return ((IAttachmentHolder) player).getData(TEAM); }
    public static void register(IEventBus bus) { TYPES.register(bus); }
    private ModAttachments() { }
}
