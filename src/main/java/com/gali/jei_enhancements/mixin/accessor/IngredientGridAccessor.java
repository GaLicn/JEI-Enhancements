package com.gali.jei_enhancements.mixin.accessor;

import mezz.jei.gui.overlay.ingredients.IngredientGrid;
import mezz.jei.gui.overlay.ingredients.IngredientListRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(value = IngredientGrid.class, remap = false)
public interface IngredientGridAccessor {
    @Accessor("ingredientListRenderer")
    IngredientListRenderer jeiEnhancements$getIngredientListRenderer();
}
