package com.gali.jei_enhancements.mixin.accessor;

import mezz.jei.gui.overlay.IngredientGridWithNavigation;
import mezz.jei.gui.overlay.bookmarks.BookmarkOverlay;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(value = BookmarkOverlay.class, remap = false)
public interface BookmarkOverlayAccessor {
    @Accessor("contents")
    IngredientGridWithNavigation jeiEnhancements$getContents();
}
