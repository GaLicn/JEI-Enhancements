package com.gali.jei_enhancements.mixin;

import com.gali.jei_enhancements.bookmark.BookmarkQuantityRenderer;
import mezz.jei.gui.bookmarks.BookmarkList;
import mezz.jei.gui.overlay.IngredientGridWithNavigation;
import mezz.jei.gui.overlay.bookmarks.BookmarkOverlay;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = BookmarkOverlay.class, remap = false)
public abstract class BookmarkOverlayMixin {

    @Shadow @Final private IngredientGridWithNavigation contents;
    @Shadow @Final private BookmarkList bookmarkList;
    
    @Shadow public abstract boolean isListDisplayed();

    /**
     * 在绘制书签后，渲染自定义数量
     */
    @Inject(method = "drawScreen", at = @At("TAIL"))
    private void onDrawScreenTail(Minecraft minecraft, GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTicks, CallbackInfo ci) {
        if (isListDisplayed()) {
            BookmarkQuantityRenderer.renderQuantities(guiGraphics, contents, bookmarkList);
        }
    }
}
