package com.gali.jei_enhancements.bookmark;

import mezz.jei.gui.bookmarks.BookmarkList;
import mezz.jei.gui.bookmarks.IBookmark;
import mezz.jei.gui.overlay.IngredientGridWithNavigation;
import mezz.jei.gui.overlay.IngredientListSlot;
import mezz.jei.gui.overlay.elements.IElement;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

import java.util.Optional;

/**
 * 渲染书签的自定义数量
 * 只渲染通过Ctrl+滚轮设置了自定义数量的书签
 */
public class BookmarkQuantityRenderer {

    /**
     * 渲染所有书签的自定义数量
     */
    public static void renderQuantities(GuiGraphics guiGraphics, IngredientGridWithNavigation contents, BookmarkList bookmarkList) {
        BookmarkQuantityManager manager = BookmarkQuantityManager.getInstance();
        
        // 如果没有任何自定义数量，不需要渲染
        if (!manager.hasAnyCustomQuantity()) {
            return;
        }
        
        Font font = Minecraft.getInstance().font;
        
        contents.getSlots().forEach(slot -> {
            renderSlotQuantity(guiGraphics, font, slot, manager);
        });
    }
    
    /**
     * 渲染单个槽位的数量
     */
    private static void renderSlotQuantity(GuiGraphics guiGraphics, Font font, IngredientListSlot slot, BookmarkQuantityManager manager) {
        IElement<?> element = slot.getElement();
        if (element == null) {
            return;
        }
        
        Optional<IBookmark> bookmarkOpt = element.getBookmark();
        if (bookmarkOpt.isEmpty()) {
            return;
        }
        
        IBookmark bookmark = bookmarkOpt.get();
        
        // 只渲染有自定义数量的书签
        if (!manager.hasCustomQuantity(bookmark)) {
            return;
        }
        
        int quantity = manager.getQuantity(bookmark);
        if (quantity <= 0) {
            return;
        }
        
        String quantityStr = formatQuantity(quantity);
        
        // 获取渲染区域
        var area = slot.getRenderArea();
        int x = area.x();
        int y = area.y();
        
        // 在右下角渲染数量，类似于物品堆叠数量的显示
        int textWidth = font.width(quantityStr);
        int textX = x + 17 - textWidth;
        int textY = y + 9;
        
        // 绘制阴影文字
        guiGraphics.pose().pushPose();
        guiGraphics.pose().translate(0, 0, 200); // 确保在物品上方渲染
        
        // 绘制阴影
        guiGraphics.drawString(font, quantityStr, textX + 1, textY + 1, 0x3F3F3F, false);
        // 绘制主文字 (使用绿色来区分自定义数量)
        guiGraphics.drawString(font, quantityStr, textX, textY, 0x55FF55, false);
        
        guiGraphics.pose().popPose();
    }
    
    /**
     * 格式化数量显示
     */
    private static String formatQuantity(int quantity) {
        if (quantity >= 1000000) {
            return String.format("%.1fM", quantity / 1000000.0);
        } else if (quantity >= 10000) {
            return String.format("%.0fK", quantity / 1000.0);
        } else if (quantity >= 1000) {
            return String.format("%.1fK", quantity / 1000.0);
        }
        return String.valueOf(quantity);
    }
}
