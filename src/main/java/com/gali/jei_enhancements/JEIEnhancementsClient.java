package com.gali.jei_enhancements;

import com.gali.jei_enhancements.event.BookmarkScrollHandler;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;

@Mod(value = JEIEnhancements.MODID, dist = Dist.CLIENT)
public class JEIEnhancementsClient {
    public JEIEnhancementsClient(ModContainer container) {
        // 注册事件处理器
        NeoForge.EVENT_BUS.register(new BookmarkScrollHandler());
        JEIEnhancements.LOGGER.info("JEI Enhancements Client initialized");
    }
}
