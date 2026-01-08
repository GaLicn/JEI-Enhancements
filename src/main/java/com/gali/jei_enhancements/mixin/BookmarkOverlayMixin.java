package com.gali.jei_enhancements.mixin;

import com.gali.jei_enhancements.bookmark.BookmarkLayoutManager;
import com.gali.jei_enhancements.bookmark.BookmarkQuantityRenderer;
import com.gali.jei_enhancements.bookmark.GroupingDragHandler;
import mezz.jei.gui.bookmarks.BookmarkList;
import mezz.jei.gui.overlay.IngredientGrid;
import mezz.jei.gui.overlay.IngredientGridWithNavigation;
import mezz.jei.gui.overlay.IngredientListSlot;
import mezz.jei.gui.overlay.bookmarks.BookmarkOverlay;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;
import java.util.stream.Collectors;

@Mixin(value = BookmarkOverlay.class, remap = false)
public abstract class BookmarkOverlayMixin {

    @Shadow @Final private IngredientGridWithNavigation contents;
    @Shadow @Final private BookmarkList bookmarkList;
    
    @Shadow public abstract boolean isListDisplayed();

    /**
     * 在绘制书签后，渲染自定义数量和组面板
     */
    @Inject(method = "drawScreen", at = @At("TAIL"))
    private void onDrawScreenTail(Minecraft minecraft, GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTicks, CallbackInfo ci) {
        if (isListDisplayed()) {
            // 更新网格信息
            jei_enhancements$updateGridInfo();
            
            // 渲染组面板（[符号和拖动效果）
            List<IngredientListSlot> slots = jei_enhancements$getSlots();
            GroupingDragHandler.getInstance().render(guiGraphics, mouseX, mouseY, slots);
            
            // 渲染自定义数量
            BookmarkQuantityRenderer.renderQuantities(guiGraphics, contents, bookmarkList);
        }
    }
    
    /**
     * 更新GroupingDragHandler的网格信息
     */
    @Unique
    private void jei_enhancements$updateGridInfo() {
        if (!BookmarkLayoutManager.getInstance().isVerticalMode()) {
            return;
        }
        
        List<IngredientListSlot> slots = jei_enhancements$getSlots();
        if (slots.isEmpty()) {
            return;
        }
        
        // 计算网格信息
        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE;
        int slotWidth = 18, slotHeight = 18;
        
        for (IngredientListSlot slot : slots) {
            var area = slot.getArea();
            minX = Math.min(minX, area.getX());
            minY = Math.min(minY, area.getY());
            maxX = Math.max(maxX, area.getX() + area.getWidth());
            maxY = Math.max(maxY, area.getY() + area.getHeight());
            slotWidth = area.getWidth();
            slotHeight = area.getHeight();
        }
        
        int columns = (maxX - minX) / slotWidth;
        int rows = (maxY - minY) / slotHeight;
        
        GroupingDragHandler.getInstance().updateGridInfo(minX, minY, slotHeight, columns, rows);
    }
    
    /**
     * 获取所有slot
     */
    @Unique
    private List<IngredientListSlot> jei_enhancements$getSlots() {
        return contents.getSlots().collect(Collectors.toList());
    }
}
