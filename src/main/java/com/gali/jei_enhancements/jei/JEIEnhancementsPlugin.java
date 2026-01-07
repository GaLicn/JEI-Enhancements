package com.gali.jei_enhancements.jei;

import com.gali.jei_enhancements.JEIEnhancements;
import com.gali.jei_enhancements.event.BookmarkScrollHandler;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.resources.ResourceLocation;

/**
 * JEI插件，用于获取JEI运行时实例
 */
@JeiPlugin
public class JEIEnhancementsPlugin implements IModPlugin {

    private static final ResourceLocation UID = ResourceLocation.fromNamespaceAndPath(JEIEnhancements.MODID, "main");

    @Override
    public ResourceLocation getPluginUid() {
        return UID;
    }

    @Override
    public void onRuntimeAvailable(IJeiRuntime jeiRuntime) {
        BookmarkScrollHandler.setJeiRuntime(jeiRuntime);
        JEIEnhancements.LOGGER.info("JEI Enhancements: JEI Runtime available");
    }

    @Override
    public void onRuntimeUnavailable() {
        BookmarkScrollHandler.setJeiRuntime(null);
        JEIEnhancements.LOGGER.info("JEI Enhancements: JEI Runtime unavailable");
    }
}
