package com.gali.jei_enhancements.mixin.accessor;

import mezz.jei.gui.recipes.IRecipeLayoutWithButtons;
import mezz.jei.gui.recipes.RecipeGuiLayouts;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.List;

@Mixin(value = RecipeGuiLayouts.class, remap = false)
public interface RecipeGuiLayoutsAccessor {
    @Accessor("recipeLayoutsWithButtons")
    List<IRecipeLayoutWithButtons<?>> jeiEnhancements$getRecipeLayoutsWithButtons();
}
