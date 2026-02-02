package com.gali.jei_enhancements;

import com.gali.jei_enhancements.bookmark.BookmarkLayoutManager;
import com.gali.jei_enhancements.event.BookmarkLayoutClickHandler;
import com.gali.jei_enhancements.event.BookmarkScrollHandler;
import net.minecraftforge.common.MinecraftForge;

public class JEIEnhancementsClient {
    public static void init() {
        // 加载布局设置
        BookmarkLayoutManager.getInstance().load();

        // 注册事件处理器
        MinecraftForge.EVENT_BUS.register(new BookmarkScrollHandler());
        MinecraftForge.EVENT_BUS.register(new BookmarkLayoutClickHandler());

        JEIEnhancements.LOGGER.info("JEI Enhancements Client initialized");
    }
}
