package com.gali.jei_enhancements.bookmark;

import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.gui.bookmarks.BookmarkList;
import mezz.jei.gui.bookmarks.IBookmark;
import mezz.jei.gui.bookmarks.IngredientBookmark;
import mezz.jei.gui.overlay.IngredientGridWithNavigation;
import mezz.jei.gui.overlay.IngredientListSlot;
import mezz.jei.gui.overlay.elements.IElement;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.world.item.ItemStack;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * 渲染书签的自定义数量和背景色
 */
public class BookmarkQuantityRenderer {
    
    // NEI风格的颜色
    private static final int GROUP_BG_COLOR = 0x30408040;      // 组背景色（绿色半透明）
    private static final int GROUP_BORDER_COLOR = 0x80408040;  // 组边框颜色
    private static final int COLLAPSED_INDICATOR_COLOR = 0xFFFFFF55; // 折叠指示器颜色（黄色）
    
    // Ctrl按下时的颜色
    private static final int RESULT_BG_COLOR = 0x604040FF;     // 组头背景色（蓝色半透明）
    private static final int INGREDIENT_BG_COLOR = 0x60AA40AA; // 组员背景色（紫色半透明）

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
        
        // 第三遍：如果按住Ctrl，渲染每个书签的类型背景色
        boolean ctrlPressed = Screen.hasControlDown();
        if (ctrlPressed) {
            contents.getSlots().forEach(slot -> {
                renderCtrlBackground(guiGraphics, slot, manager);
            });
        }
        
        // 第四遍：渲染数量
        contents.getSlots().forEach(slot -> {
            renderSlotQuantity(guiGraphics, font, slot, manager);
        });
    }
    
    /**
     * 渲染Ctrl按下时的背景色
     * 组头（RESULT）显示蓝色，组员（INGREDIENT）显示紫色
     */
    private static void renderCtrlBackground(GuiGraphics guiGraphics, IngredientListSlot slot, BookmarkManager manager) {
        IElement<?> element = slot.getElement();
        if (element == null) return;
        
        Optional<IBookmark> bookmarkOpt = element.getBookmark();
        if (bookmarkOpt.isEmpty()) return;
        
        BookmarkItem item = manager.findBookmarkItem(bookmarkOpt.get());
        if (item == null) return;
        
        // 只有非默认组的书签才显示背景色
        if (item.getGroupId() == BookmarkManager.DEFAULT_GROUP_ID) return;
        
        var area = slot.getRenderArea();
        int x = area.x();
        int y = area.y();
        int width = area.width();
        int height = area.height();
        
        int bgColor;
        if (item.getType() == BookmarkItem.BookmarkItemType.RESULT) {
            bgColor = RESULT_BG_COLOR;  // 蓝色 - 组头
        } else if (item.getType() == BookmarkItem.BookmarkItemType.INGREDIENT) {
            bgColor = INGREDIENT_BG_COLOR;  // 紫色 - 组员
        } else {
            return;  // 普通物品不显示
        }
        
        guiGraphics.fill(x, y, x + width, y + height, bgColor);
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
        
        // 检测是否是流体/化学品类型
        boolean isFluidOrChemical = isFluidOrChemical(bookmark);
        
        // 格式化数量字符串
        String quantityStr = formatQuantity(quantity, isFluidOrChemical);
        
        // 获取渲染区域
        var area = slot.getRenderArea();
        int x = area.x();
        int y = area.y();
        
        // 计算文字宽度
        int textWidth = font.width(quantityStr);
        
        // 缩放0.5倍
        float scale = 0.5f;
        float textX;
        float textY = (y + 12) * 2;  // 底部位置
        
        if (isFluidOrChemical) {
            // 流体/化学品：左下角显示
            textX = x * 2 + 2;
        } else {
            // 固体：右下角显示
            textX = (x + 17) * 2 - textWidth;
        }
        
        // 绘制阴影文字
        guiGraphics.pose().pushPose();
        guiGraphics.pose().translate(0, 0, 200);
        guiGraphics.pose().scale(scale, scale, 1.0f);
        
        // 绘制阴影
        guiGraphics.drawString(font, quantityStr, (int)(textX + 1), (int)(textY + 1), 0x3F3F3F, false);
        // 绘制主文字 (使用绿色来区分自定义数量)
        guiGraphics.drawString(font, quantityStr, (int)textX, (int)textY, 0x55FF55, false);
        
        guiGraphics.pose().popPose();
    }
    
    /**
     * 检测书签是否是流体或化学品类型
     */
    private static boolean isFluidOrChemical(IBookmark bookmark) {
        if (bookmark instanceof IngredientBookmark<?> ingredientBookmark) {
            ITypedIngredient<?> ingredient = ingredientBookmark.getIngredient();
            Object obj = ingredient.getIngredient();
            
            // 如果不是ItemStack，就是流体或化学品
            if (!(obj instanceof ItemStack)) {
                return true;
            }
            
            // 检查类型UID
            String typeUid = ingredient.getType().getUid().toString();
            if (typeUid.contains("fluid") || typeUid.contains("chemical") || typeUid.contains("gas") 
                    || typeUid.contains("slurry") || typeUid.contains("pigment") || typeUid.contains("infuse")) {
                return true;
            }
        }
        return false;
    }
    
    /**
     * 渲染折叠指示器
     */
    private static void renderCollapsedIndicator(GuiGraphics guiGraphics, Font font, IngredientListSlot slot, int groupSize) {
        var area = slot.getRenderArea();
        int x = area.x();
        int y = area.y();
        
        String indicator = "+" + (groupSize - 1);
        
        // 缩放0.5倍
        float scale = 0.5f;
        float textX = x * 2 + 2;  // 左上角位置
        float textY = y * 2 + 2;
        
        guiGraphics.pose().pushPose();
        guiGraphics.pose().translate(0, 0, 200);
        guiGraphics.pose().scale(scale, scale, 1.0f);
        
        guiGraphics.drawString(font, indicator, (int)(textX + 1), (int)(textY + 1), 0x3F3F3F, false);
        guiGraphics.drawString(font, indicator, (int)textX, (int)textY, COLLAPSED_INDICATOR_COLOR, false);
        
        guiGraphics.pose().popPose();
    }
    
    /**
     * 格式化数量显示
     * @param quantity 数量
     * @param isFluid 是否是流体/化学品
     */
    private static String formatQuantity(int quantity, boolean isFluid) {
        String suffix = isFluid ? "L" : "";
        
        if (quantity >= 1000000) {
            return String.format("%.1fM%s", quantity / 1000000.0, suffix);
        } else if (quantity >= 10000) {
            return String.format("%.0fk%s", quantity / 1000.0, suffix);
        } else if (quantity >= 1000) {
            return String.format("%.1fk%s", quantity / 1000.0, suffix);
        }
        return quantity + suffix;
    }
}
