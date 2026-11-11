package cn.zgnhit.mcwuwa;

import com.mojang.logging.LogUtils;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;
import org.slf4j.Logger;
import cn.zgnhit.mcwuwa.content.ContentCatalog;
import cn.zgnhit.mcwuwa.platform.*;
import cn.zgnhit.mcwuwa.platform.entity.ModEntities;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.NeoForge;

/** Common entry point. Client classes are deliberately absent from this class. */
@Mod(McWuwa.MOD_ID)
public final class McWuwa {
    public static final String MOD_ID = "mc_wuwa";
    public static final Logger LOGGER = LogUtils.getLogger();

    private static final class DataHolder { static final ContentCatalog CATALOG = ContentCatalog.loadDefault(); }
    public static ContentCatalog catalog() { return DataHolder.CATALOG; }

    public McWuwa(IEventBus modBus, ModContainer container) {
        LOGGER.info("mc_wuwa: loading fixed 3.7 content for NeoForge 21.1.217");
        catalog();
        cn.zgnhit.mcwuwa.content.EffectDefinitions.installAdaptations();
        ModItems.register(modBus); ModAttachments.register(modBus); ModEntities.register(modBus);
        modBus.addListener(Network::register);
        container.registerConfig(ModConfig.Type.SERVER, CombatConfig.SPEC);
        NeoForge.EVENT_BUS.register(new PlatformRuntime());
    }
}
