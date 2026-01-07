package com.gali.jei_enhancements.bookmark;

import mezz.jei.gui.bookmarks.BookmarkList;
import mezz.jei.gui.bookmarks.IBookmark;
import mezz.jei.gui.overlay.IngredientGridWithNavigation;
import mezz.jei.gui.overlay.IngredientListSlot;
import mezz.jei.gui.overlay.elements.IElement;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * 渲染书签的自定义数量和NEI风格的分组效果
 * - 自定义数量：通过Ctrl+滚轮设置
 * - 分组效果：背景色、边框、折叠指示器
 */
public class BookmarkQuantityRenderer {
    
    // NEI风格的颜色
    private static final int COLLAPSED_BG_COLOR = 0x40404080;  // 折叠状态背景色
    private static final int EXPANDED_BG_COLOR = 0x30408040;   // 展开状态背景色
    private static final int GROUP_BORDER_COLOR = 0x80808080;  // 组边框颜色
    private static final int COLLAPSED_INDICATOR_COLOR = 0xFFFFFF55; // 折叠指示器颜色（黄色）

    /**
     * 渲染所有书签的自定义数量和分组效果
     */
    public static void renderQuantities(GuiGraphics guiGraphics, IngredientGridWithNavigation contents, BookmarkList bookmarkList) {
        BookmarkQuantityManager manager = BookmarkQuantityManager.getInstance();
        
        // 如果没有任何自定义数量，不需要渲染
        if (!manager.hasAnyCustomQuantity()) {
            return;
        }
        
        Font font = Minecraft.getInstance().font;
        
        // 第一遍：收集分组信息，用于绘制边框
        Map<RecipeBookmarkGroup, GroupRenderInfo> groupInfoMap = new HashMap<>();
        
        contents.getSlots().forEach(slot -> {
            IElement<?> element = slot.getElement();
            if (element == null) return;
            
            Optional<IBookmark> bookmarkOpt = element.getBookmark();
            if (bookmarkOpt.isEmpty()) return;
            
            RecipeBookmarkGroup group = manager.getGroup(bookmarkOpt.get());
            if (group != null && group.size() > 1) {
                var area = slot.getRenderArea();
                GroupRenderInfo info = groupInfoMap.computeIfAbsent(group, g -> new GroupRenderInfo());
                info.addSlot(area.x(), area.y(), area.width(), area.height());
            }
        });
        
        // 第二遍：渲染分组背景和边框
        groupInfoMap.forEach((group, info) -> {
            renderGroupBackground(guiGraphics, group, info);
        });
        
        // 第三遍：渲染数量和折叠指示器
        contents.getSlots().forEach(slot -> {
            renderSlotQuantity(guiGraphics, font, slot, manager);
        });
    }
    
    /**
     * 分组渲染信息
     */
    private static class GroupRenderInfo {
        int minX = Integer.MAX_VALUE;
        int minY = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int maxY = Integer.MIN_VALUE;
        int slotCount = 0;
        
        void addSlot(int x, int y, int width, int height) {
            minX = Math.min(minX, x);
            minY = Math.min(minY, y);
            maxX = Math.max(maxX, x + width);
            maxY = Math.max(maxY, y + height);
            slotCount++;
        }
    }
    
    /**
     * 渲染分组背景（NEI风格）
     */
    private static void renderGroupBackground(GuiGraphics guiGraphics, RecipeBookmarkGroup group, GroupRenderInfo info) {
        if (info.slotCount <= 1) return;
        
        int bgColor = group.isExpanded() ? EXPANDED_BG_COLOR : COLLAPSED_BG_COLOR;
        
        // 绘制背景
        guiGraphics.fill(info.minX - 1, info.minY - 1, info.maxX + 1, info.maxY + 1, bgColor);
        
        // 绘制边框
        int borderColor = GROUP_BORDER_COLOR;
        guiGraphics.fill(info.minX - 1, info.minY - 1, info.maxX + 1, info.minY, borderColor); // 上
        guiGraphics.fill(info.minX - 1, info.maxY, info.maxX + 1, info.maxY + 1, borderColor); // 下
        guiGraphics.fill(info.minX - 1, info.minY, info.minX, info.maxY, borderColor); // 左
        guiGraphics.fill(info.maxX, info.minY, info.maxX + 1, info.maxY, borderColor); // 右
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
        RecipeBookmarkGroup group = manager.getGroup(bookmark);
        
        // 渲染折叠指示器（NEI风格：折叠时在左上角显示组大小）
        if (group != null && !group.isExpanded() && group.size() > 1) {
            renderCollapsedIndicator(guiGraphics, font, slot, group);
        }
        
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
     * 渲染折叠指示器（NEI风格）
     * 在左上角显示组内物品数量
     */
    private static void renderCollapsedIndicator(GuiGraphics guiGraphics, Font font, IngredientListSlot slot, RecipeBookmarkGroup group) {
        var area = slot.getRenderArea();
        int x = area.x();
        int y = area.y();
        
        String indicator = "+" + (group.size() - 1);
        
        guiGraphics.pose().pushPose();
        guiGraphics.pose().translate(0, 0, 200);
        
        // 在左上角绘制折叠指示器
        guiGraphics.drawString(font, indicator, x + 1, y + 1, 0x3F3F3F, false);
        guiGraphics.drawString(font, indicator, x, y, COLLAPSED_INDICATOR_COLOR, false);
        
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
