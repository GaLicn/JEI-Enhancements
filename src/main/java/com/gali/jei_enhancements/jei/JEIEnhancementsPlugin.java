package com.gali.jei_enhancements.jei;

import com.gali.jei_enhancements.JEIEnhancements;
import com.gali.jei_enhancements.bookmark.BookmarkManager;
import com.gali.jei_enhancements.event.BookmarkLayoutClickHandler;
import com.gali.jei_enhancements.event.BookmarkScrollHandler;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.helpers.ICodecHelper;
import mezz.jei.api.helpers.IJeiHelpers;
import mezz.jei.api.runtime.IIngredientManager;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.client.Minecraft;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;

/**
 * JEI插件，用于获取JEI运行时实例和相关组件
 */
@JeiPlugin
public class JEIEnhancementsPlugin implements IModPlugin {

    private static final ResourceLocation UID = ResourceLocation.fromNamespaceAndPath(JEIEnhancements.MODID, "main");

    @Nullable
    private static IJeiRuntime jeiRuntime;
    @Nullable
    private static IIngredientManager ingredientManager;
    @Nullable
    private static ICodecHelper codecHelper;

    @Override
    public ResourceLocation getPluginUid() {
        return UID;
    }

    @Override
    public void onRuntimeAvailable(IJeiRuntime runtime) {
        jeiRuntime = runtime;
        ingredientManager = runtime.getIngredientManager();
        
        IJeiHelpers helpers = runtime.getJeiHelpers();
        codecHelper = helpers.getCodecHelper();
        
        BookmarkScrollHandler.setJeiRuntime(runtime);
        BookmarkLayoutClickHandler.setJeiRuntime(runtime);
        
        // 加载保存的书签数据
        BookmarkManager.getInstance().load();
        
        JEIEnhancements.LOGGER.info("JEI Enhancements: JEI Runtime available");
    }

    @Override
    public void onRuntimeUnavailable() {
        // 保存书签数据
        BookmarkManager.getInstance().save();
        
        jeiRuntime = null;
        ingredientManager = null;
        codecHelper = null;
        
        BookmarkScrollHandler.setJeiRuntime(null);
        BookmarkLayoutClickHandler.setJeiRuntime(null);
        JEIEnhancements.LOGGER.info("JEI Enhancements: JEI Runtime unavailable");
    }

    @Nullable
    public static IJeiRuntime getJeiRuntime() {
        return jeiRuntime;
    }

    @Nullable
    public static IIngredientManager getIngredientManager() {
        return ingredientManager;
    }

    @Nullable
    public static ICodecHelper getCodecHelper() {
        return codecHelper;
    }

    @Nullable
    public static RegistryAccess getRegistryAccess() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level != null) {
            return mc.level.registryAccess();
        }
        return null;
    }
}
