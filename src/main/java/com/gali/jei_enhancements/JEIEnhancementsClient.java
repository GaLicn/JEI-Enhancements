package com.gali.jei_enhancements;

import com.gali.jei_enhancements.bookmark.BookmarkLayoutManager;
import com.gali.jei_enhancements.event.BookmarkLayoutClickHandler;
import com.gali.jei_enhancements.event.BookmarkScrollHandler;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;

@Mod(value = JEIEnhancements.MODID, dist = Dist.CLIENT)
public class JEIEnhancementsClient {
    public JEIEnhancementsClient(ModContainer container) {
        // 加载布局设置
        BookmarkLayoutManager.getInstance().load();
        
        // 注册事件处理器
        NeoForge.EVENT_BUS.register(new BookmarkScrollHandler());
        NeoForge.EVENT_BUS.register(new BookmarkLayoutClickHandler());
        
        JEIEnhancements.LOGGER.info("JEI Enhancements Client initialized");
    }
}
