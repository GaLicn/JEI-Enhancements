package com.gali.jei_enhancements.mixin;

import com.gali.jei_enhancements.jei.JEIEnhancementsPlugin;
import com.gali.jei_enhancements.mixin.accessor.RecipeGuiLayoutsAccessor;
import com.gali.jei_enhancements.recipe.RecipeBookmarkHelper;
import com.mojang.blaze3d.platform.InputConstants;
import mezz.jei.api.gui.IRecipeLayoutDrawable;
import mezz.jei.api.runtime.IIngredientManager;
import mezz.jei.gui.bookmarks.BookmarkList;
import mezz.jei.gui.input.UserInput;
import mezz.jei.gui.recipes.RecipeGuiLayouts;
import mezz.jei.gui.recipes.RecipeLayoutWithButtons;
import mezz.jei.gui.recipes.RecipesGui;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

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
            addHoveredRecipe(cir, ingredientManager, true);
        // 检查是否是Shift+A (添加配方组，不带数量)
        } else if (Screen.hasShiftDown() && !Screen.hasControlDown()) {
            addHoveredRecipe(cir, ingredientManager, false);
        }
    }

    @Inject(method = "handleInput", at = @At("HEAD"), cancellable = true, require = 0, remap = false)
    private void onHandleInput(UserInput input, CallbackInfoReturnable<Boolean> cir) {
        if (input == null
                || input.getKey().getType() != InputConstants.Type.KEYSYM
                || input.getKey().getValue() != 65) {
            return;
        }

        // 获取必要的组件
        IIngredientManager ingredientManager = JEIEnhancementsPlugin.getIngredientManager();
        if (ingredientManager == null) {
            return;
        }

        // 检查是否是Ctrl+Shift+A (添加配方组，带数量)
        if (Screen.hasControlDown() && Screen.hasShiftDown()) {
            addHoveredRecipe(cir, ingredientManager, true);
        // 检查是否是Shift+A (添加配方组，不带数量)
        } else if (Screen.hasShiftDown() && !Screen.hasControlDown()) {
            addHoveredRecipe(cir, ingredientManager, false);
        }
    }

    private void addHoveredRecipe(CallbackInfoReturnable<Boolean> cir,
            IIngredientManager ingredientManager, boolean saveCount) {
        Optional<IRecipeLayoutDrawable<?>> hoveredLayout = getHoveredRecipeLayout();
        if (hoveredLayout.isPresent()
                && RecipeBookmarkHelper.addRecipeToBookmarks(
                        hoveredLayout.get(), bookmarks, ingredientManager, saveCount)) {
            cir.setReturnValue(true);
        }
    }

    /**
     * 获取当前鼠标悬停的配方布局
     */
    private Optional<IRecipeLayoutDrawable<?>> getHoveredRecipeLayout() {
        Minecraft mc = Minecraft.getInstance();
        double mouseX = mc.mouseHandler.xpos() * mc.getWindow().getGuiScaledWidth()
                / mc.getWindow().getScreenWidth();
        double mouseY = mc.mouseHandler.ypos() * mc.getWindow().getGuiScaledHeight()
                / mc.getWindow().getScreenHeight();

        // 通过Accessor获取JEI配方布局列表
        List<RecipeLayoutWithButtons<?>> layoutsList =
                ((RecipeGuiLayoutsAccessor) layouts).jeiEnhancements$getRecipeLayoutsWithButtons();
        // 遍历所有配方布局，找到鼠标悬停的那个
        for (RecipeLayoutWithButtons<?> layout : layoutsList) {
            IRecipeLayoutDrawable<?> recipeLayout = layout.recipeLayout();
            if (recipeLayout.isMouseOver(mouseX, mouseY)) {
                return Optional.of(recipeLayout);
            }
        }

        // 如果没有悬停的，返回第一个可见的配方
        if (layoutsList.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(layoutsList.get(0).recipeLayout());
    }
}
