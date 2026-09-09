package com.gali.jei_enhancements.mixin.accessor;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** 访问 JEI 50+ 分页控制器的布局刷新入口，避免通过反射调用私有方法。 */
@Mixin(targets = "mezz.jei.gui.overlay.ingredients.IngredientGridWithNavigationController", remap = false)
public interface IngredientGridNavigationControllerAccessor {
    @Invoker("updateLayoutStartingAt")
    void jeiEnhancements$updateLayoutStartingAt(int startIndex);
}
