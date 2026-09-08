package com.gali.jei_enhancements.mixin.accessor;

import mezz.jei.gui.overlay.IngredientGrid;
import mezz.jei.gui.overlay.IngredientGridWithNavigation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(value = IngredientGridWithNavigation.class, remap = false)
public interface IngredientGridWithNavigationAccessor {
    @Accessor("ingredientGrid")
    IngredientGrid jeiEnhancements$getIngredientGrid();
}
