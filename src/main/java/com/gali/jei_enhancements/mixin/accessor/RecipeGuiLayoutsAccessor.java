package com.gali.jei_enhancements.mixin.accessor;

import mezz.jei.gui.recipes.RecipeGuiLayouts;
import mezz.jei.gui.recipes.RecipeLayoutWithButtons;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.List;

@Mixin(value = RecipeGuiLayouts.class, remap = false)
public interface RecipeGuiLayoutsAccessor {
    @Accessor("recipeLayoutsWithButtons")
    List<RecipeLayoutWithButtons<?>> jeiEnhancements$getRecipeLayoutsWithButtons();
}
