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
 */
public class BookmarkQuantityRenderer {
    
    // NEI风格的颜色
    private static final int GROUP_BG_COLOR = 0x30408040;      // 组背景色（绿色半透明）
    private static final int GROUP_BORDER_COLOR = 0x80408040;  // 组边框颜色
    private static final int COLLAPSED_INDICATOR_COLOR = 0xFFFFFF55; // 折叠指示器颜色（黄色）

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
     * 渲染所有书签的自定义数量和分组效果
     */
    public static void renderQuantities(GuiGraphics guiGraphics, IngredientGridWithNavigation contents, BookmarkList bookmarkList) {
        BookmarkManager manager = BookmarkManager.getInstance();
        
        // 如果没有任何书签项，不需要渲染
        if (manager.getAllItems().isEmpty()) {
            return;
        }
        
        Font font = Minecraft.getInstance().font;
        
        // 第一遍：收集分组信息
        Map<Integer, GroupRenderInfo> groupInfoMap = new HashMap<>();
        
        contents.getSlots().forEach(slot -> {
            IElement<?> element = slot.getElement();
            if (element == null) return;
            
            Optional<IBookmark> bookmarkOpt = element.getBookmark();
            if (bookmarkOpt.isEmpty()) return;
            
            BookmarkItem item = manager.findBookmarkItem(bookmarkOpt.get());
            if (item == null) return;
            
            int groupId = item.getGroupId();
            // 只有非默认组且组内有多个物品才显示背景
            if (groupId != BookmarkManager.DEFAULT_GROUP_ID) {
                int groupSize = manager.getGroupItems(groupId).size();
                if (groupSize > 1) {
                    var area = slot.getRenderArea();
                    GroupRenderInfo info = groupInfoMap.computeIfAbsent(groupId, g -> new GroupRenderInfo());
                    info.addSlot(area.x(), area.y(), area.width(), area.height());
                }
            }
        });
        
        // 第二遍：渲染分组背景
        groupInfoMap.forEach((groupId, info) -> {
            if (info.slotCount > 0) {
                renderGroupBackground(guiGraphics, info);
            }
        });
        
        // 第三遍：渲染数量
        contents.getSlots().forEach(slot -> {
            renderSlotQuantity(guiGraphics, font, slot, manager);
        });
    }
    
    /**
     * 渲染分组背景
     */
    private static void renderGroupBackground(GuiGraphics guiGraphics, GroupRenderInfo info) {
        // 绘制背景
        guiGraphics.fill(info.minX - 1, info.minY - 1, info.maxX + 1, info.maxY + 1, GROUP_BG_COLOR);
        
        // 绘制边框
        guiGraphics.fill(info.minX - 1, info.minY - 1, info.maxX + 1, info.minY, GROUP_BORDER_COLOR); // 上
        guiGraphics.fill(info.minX - 1, info.maxY, info.maxX + 1, info.maxY + 1, GROUP_BORDER_COLOR); // 下
        guiGraphics.fill(info.minX - 1, info.minY, info.minX, info.maxY, GROUP_BORDER_COLOR); // 左
        guiGraphics.fill(info.maxX, info.minY, info.maxX + 1, info.maxY, GROUP_BORDER_COLOR); // 右
    }
    
    /**
     * 渲染单个槽位的数量
     */
    private static void renderSlotQuantity(GuiGraphics guiGraphics, Font font, IngredientListSlot slot, BookmarkManager manager) {
        IElement<?> element = slot.getElement();
        if (element == null) {
            return;
        }
        
        Optional<IBookmark> bookmarkOpt = element.getBookmark();
        if (bookmarkOpt.isEmpty()) {
            return;
        }
        
        IBookmark bookmark = bookmarkOpt.get();
        
        // 查找对应的BookmarkItem
        BookmarkItem item = manager.findBookmarkItem(bookmark);
        if (item == null) {
            return;
        }
        
        // 获取组信息
        BookmarkGroup group = manager.getGroup(item.getGroupId());
        
        // 渲染折叠指示器（NEI风格：折叠时在左上角显示组大小）
        if (group != null && !group.isExpanded()) {
            int groupSize = manager.getGroupItems(item.getGroupId()).size();
            if (groupSize > 1 && item.isOutput()) {
                renderCollapsedIndicator(guiGraphics, font, slot, groupSize);
            }
        }
        
        // 获取计算后的数量
        int quantity = manager.getQuantity(item);
        if (quantity <= 0) {
            return;
        }
        
        String quantityStr = formatQuantity(quantity);
        
        // 获取渲染区域
        var area = slot.getRenderArea();
        int x = area.x();
        int y = area.y();
        
        // 在右下角渲染数量
        int textWidth = font.width(quantityStr);
        int textX = x + 17 - textWidth;
        int textY = y + 9;
        
        // 绘制阴影文字
        guiGraphics.pose().pushPose();
        guiGraphics.pose().translate(0, 0, 200);
        
        // 绘制阴影
        guiGraphics.drawString(font, quantityStr, textX + 1, textY + 1, 0x3F3F3F, false);
        // 绘制主文字 (使用绿色来区分自定义数量)
        guiGraphics.drawString(font, quantityStr, textX, textY, 0x55FF55, false);
        
        guiGraphics.pose().popPose();
    }
    
    /**
     * 渲染折叠指示器
     */
    private static void renderCollapsedIndicator(GuiGraphics guiGraphics, Font font, IngredientListSlot slot, int groupSize) {
        var area = slot.getRenderArea();
        int x = area.x();
        int y = area.y();
        
        String indicator = "+" + (groupSize - 1);
        
        guiGraphics.pose().pushPose();
        guiGraphics.pose().translate(0, 0, 200);
        
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
