package com.gali.jei_enhancements.mixin;

import com.gali.jei_enhancements.jei.JEIEnhancementsPlugin;
import com.gali.jei_enhancements.recipe.RecipeBookmarkHelper;
import mezz.jei.api.gui.IRecipeLayoutDrawable;
import mezz.jei.api.helpers.ICodecHelper;
import mezz.jei.api.runtime.IIngredientManager;
import mezz.jei.gui.bookmarks.BookmarkList;
import mezz.jei.gui.recipes.IRecipeLayoutWithButtons;
import mezz.jei.gui.recipes.RecipeGuiLayouts;
import mezz.jei.gui.recipes.RecipesGui;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.RegistryAccess;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Optional;

/**
 * 拦截配方界面的按键事件
 */
@Mixin(value = RecipesGui.class, remap = false)
public abstract class RecipesGuiMixin {

    @Shadow @Final private RecipeGuiLayouts layouts;
    @Shadow @Final private BookmarkList bookmarks;

    /**
     * 拦截按键事件，处理Shift+A和Ctrl+Shift+A
     */
    @Inject(method = "keyPressed", at = @At("HEAD"), cancellable = true)
    private void onKeyPressed(int keyCode, int scanCode, int modifiers, CallbackInfoReturnable<Boolean> cir) {
        // A的keyCode是65
        if (keyCode != 65) {
            return;
        }
        
        // 获取必要的组件
        IIngredientManager ingredientManager = JEIEnhancementsPlugin.getIngredientManager();
        ICodecHelper codecHelper = JEIEnhancementsPlugin.getCodecHelper();
        RegistryAccess registryAccess = JEIEnhancementsPlugin.getRegistryAccess();
        
        if (ingredientManager == null || codecHelper == null || registryAccess == null) {
            return;
        }
        
        // 检查是否是Ctrl+Shift+A (添加配方组，带数量)
        if (Screen.hasControlDown() && Screen.hasShiftDown()) {
            Optional<IRecipeLayoutDrawable<?>> hoveredLayout = getHoveredRecipeLayout();
            
            if (hoveredLayout.isPresent()) {
                boolean added = RecipeBookmarkHelper.addRecipeToBookmarks(
                        hoveredLayout.get(),
                        bookmarks,
                        codecHelper,
                        registryAccess,
                        ingredientManager,
                        true // saveCount = true for Ctrl+Shift+A
                );
                
                if (added) {
                    cir.setReturnValue(true);
                }
            }
        }
        // 检查是否是Shift+A (添加配方组，不带数量)
        else if (Screen.hasShiftDown() && !Screen.hasControlDown()) {
            Optional<IRecipeLayoutDrawable<?>> hoveredLayout = getHoveredRecipeLayout();
            
            if (hoveredLayout.isPresent()) {
                boolean added = RecipeBookmarkHelper.addRecipeToBookmarks(
                        hoveredLayout.get(),
                        bookmarks,
                        codecHelper,
                        registryAccess,
                        ingredientManager,
                        false // saveCount = false for Shift+A
                );
                
                if (added) {
                    cir.setReturnValue(true);
                }
            }
        }
    }
    
    /**
     * 获取当前鼠标悬停的配方布局
     */
    @SuppressWarnings("unchecked")
    private Optional<IRecipeLayoutDrawable<?>> getHoveredRecipeLayout() {
        net.minecraft.client.Minecraft mc = net.minecraft.client.Minecraft.getInstance();
        double mouseX = mc.mouseHandler.xpos() * mc.getWindow().getGuiScaledWidth() / mc.getWindow().getScreenWidth();
        double mouseY = mc.mouseHandler.ypos() * mc.getWindow().getGuiScaledHeight() / mc.getWindow().getScreenHeight();
        
        try {
            // 通过反射获取recipeLayoutsWithButtons列表
            Field field = RecipeGuiLayouts.class.getDeclaredField("recipeLayoutsWithButtons");
            field.setAccessible(true);
            List<IRecipeLayoutWithButtons<?>> layoutsList = (List<IRecipeLayoutWithButtons<?>>) field.get(layouts);
            
            // 遍历所有配方布局，找到鼠标悬停的那个
            for (IRecipeLayoutWithButtons<?> layout : layoutsList) {
                IRecipeLayoutDrawable<?> recipeLayout = layout.getRecipeLayout();
                if (recipeLayout.isMouseOver(mouseX, mouseY)) {
                    return Optional.of(recipeLayout);
                }
            }
            
            // 如果没有悬停的，返回第一个可见的配方
            if (!layoutsList.isEmpty()) {
                return Optional.of(layoutsList.getFirst().getRecipeLayout());
            }
        } catch (Exception e) {
            // 忽略反射错误
        }
        
        return Optional.empty();
    }
}
