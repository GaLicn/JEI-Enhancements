package com.gali.jei_enhancements.mixin;

import com.gali.jei_enhancements.jei.JEIEnhancementsPlugin;
import com.gali.jei_enhancements.recipe.RecipeBookmarkHelper;
import com.mojang.blaze3d.platform.InputConstants;
import mezz.jei.api.gui.IRecipeLayoutDrawable;
import mezz.jei.api.runtime.IIngredientManager;
import mezz.jei.gui.bookmarks.BookmarkList;
import mezz.jei.gui.input.UserInput;
import mezz.jei.gui.recipes.RecipeGuiLayouts;
import mezz.jei.gui.recipes.RecipesGui;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
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
    @Inject(method = "keyPressed", at = @At("HEAD"), cancellable = true, require = 0, remap = false)
    private void onKeyPressed(int keyCode, int scanCode, int modifiers, CallbackInfoReturnable<Boolean> cir) {
        // A的keyCode是65
        if (keyCode != 65) {
            return;
        }
        
        // 获取必要的组件
        IIngredientManager ingredientManager = JEIEnhancementsPlugin.getIngredientManager();
        
        if (ingredientManager == null) {
            return;
        }
        
        // 检查是否是Ctrl+Shift+A (添加配方组，带数量)
        if (Screen.hasControlDown() && Screen.hasShiftDown()) {
            Optional<IRecipeLayoutDrawable<?>> hoveredLayout = getHoveredRecipeLayout();
            
            if (hoveredLayout.isPresent()) {
                boolean added = RecipeBookmarkHelper.addRecipeToBookmarks(
                        hoveredLayout.get(),
                        bookmarks,
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
                        ingredientManager,
                        false // saveCount = false for Shift+A
                );
                
                if (added) {
                    cir.setReturnValue(true);
                }
            }
        }
    }

    @Inject(method = "handleInput", at = @At("HEAD"), cancellable = true, require = 0, remap = false)
    private void onHandleInput(UserInput input, CallbackInfoReturnable<Boolean> cir) {
        if (input == null) {
            return;
        }
        if (input.getKey().getType() != InputConstants.Type.KEYSYM) {
            return;
        }
        if (input.getKey().getValue() != 65) {
            return;
        }

        IIngredientManager ingredientManager = JEIEnhancementsPlugin.getIngredientManager();
        if (ingredientManager == null) {
            return;
        }

        if (Screen.hasControlDown() && Screen.hasShiftDown()) {
            Optional<IRecipeLayoutDrawable<?>> hoveredLayout = getHoveredRecipeLayout();
            if (hoveredLayout.isPresent()) {
                boolean added = RecipeBookmarkHelper.addRecipeToBookmarks(
                        hoveredLayout.get(),
                        bookmarks,
                        ingredientManager,
                        true
                );
                if (added) {
                    cir.setReturnValue(true);
                }
            }
        } else if (Screen.hasShiftDown() && !Screen.hasControlDown()) {
            Optional<IRecipeLayoutDrawable<?>> hoveredLayout = getHoveredRecipeLayout();
            if (hoveredLayout.isPresent()) {
                boolean added = RecipeBookmarkHelper.addRecipeToBookmarks(
                        hoveredLayout.get(),
                        bookmarks,
                        ingredientManager,
                        false
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
            List<?> layoutsList = jei_enhancements$getRecipeLayoutsWithButtons(layouts);
            if (layoutsList.isEmpty()) {
                return Optional.empty();
            }
            
            // 遍历所有配方布局，找到鼠标悬停的那个
            for (Object layout : layoutsList) {
                IRecipeLayoutDrawable<?> recipeLayout = (IRecipeLayoutDrawable<?>) jei_enhancements$invokeRecipeLayout(layout);
                if (recipeLayout.isMouseOver(mouseX, mouseY)) {
                    return Optional.of(recipeLayout);
                }
            }
            
            // 如果没有悬停的，返回第一个可见的配方
            Object first = layoutsList.get(0);
            IRecipeLayoutDrawable<?> recipeLayout = (IRecipeLayoutDrawable<?>) jei_enhancements$invokeRecipeLayout(first);
            return Optional.of(recipeLayout);
        } catch (Exception e) {
            // 忽略反射错误
        }
        
        return Optional.empty();
    }

    private static Object jei_enhancements$invokeRecipeLayout(Object layout) throws Exception {
        try {
            // JEI 1.20.1: RecipeLayoutWithButtons is a record, accessor is recipeLayout()
            return layout.getClass().getMethod("recipeLayout").invoke(layout);
        } catch (NoSuchMethodException ignored) {
        }
        // Older / other builds
        return layout.getClass().getMethod("getRecipeLayout").invoke(layout);
    }

    private static List<?> jei_enhancements$getRecipeLayoutsWithButtons(RecipeGuiLayouts layouts) throws IllegalAccessException {
        try {
            Field field = RecipeGuiLayouts.class.getDeclaredField("recipeLayoutsWithButtons");
            field.setAccessible(true);
            return (List<?>) field.get(layouts);
        } catch (NoSuchFieldException ignored) {
        }

        for (Field field : RecipeGuiLayouts.class.getDeclaredFields()) {
            if (!List.class.isAssignableFrom(field.getType())) {
                continue;
            }
            field.setAccessible(true);
            Object value = field.get(layouts);
            if (!(value instanceof List<?> list) || list.isEmpty()) {
                continue;
            }
            Object first = list.get(0);
            if (jei_enhancements$hasRecipeLayoutAccessor(first)) {
                return list;
            }
        }
        return List.of();
    }

    private static boolean jei_enhancements$hasRecipeLayoutAccessor(Object layout) {
        return jei_enhancements$hasMethod(layout, "recipeLayout") || jei_enhancements$hasMethod(layout, "getRecipeLayout");
    }

    private static boolean jei_enhancements$hasMethod(Object obj, String name) {
        try {
            Method m = obj.getClass().getMethod(name);
            return m.getReturnType() != Void.TYPE;
        } catch (NoSuchMethodException e) {
            return false;
        }
    }
}
