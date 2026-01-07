package com.gali.jei_enhancements.mixin;

import com.gali.jei_enhancements.JEIEnhancements;
import com.gali.jei_enhancements.bookmark.BookmarkGroup;
import com.gali.jei_enhancements.bookmark.BookmarkItem;
import com.gali.jei_enhancements.bookmark.BookmarkLayoutManager;
import com.gali.jei_enhancements.bookmark.BookmarkManager;
import mezz.jei.api.gui.drawable.IDrawable;
import mezz.jei.api.ingredients.IIngredientType;
import mezz.jei.api.ingredients.rendering.BatchRenderElement;
import mezz.jei.core.collect.ListMultiMap;
import mezz.jei.gui.bookmarks.IBookmark;
import mezz.jei.gui.overlay.IngredientListRenderer;
import mezz.jei.gui.overlay.IngredientListSlot;
import mezz.jei.gui.overlay.elements.IElement;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * 修改IngredientListRenderer的set方法
 * 支持纵向排列模式：每个书签/书签组占一行
 */
@Mixin(value = IngredientListRenderer.class, remap = false)
public abstract class IngredientListRendererMixin {

    @Shadow @Final
    private List<IngredientListSlot> slots;

    @Shadow @Final
    private ListMultiMap<IIngredientType<?>, BatchRenderElement<?>> renderElementsByType;

    @Shadow @Final
    private List<IDrawable> renderOverlays;

    @Shadow
    private int blocked;

    @Shadow
    protected abstract void addRenderElement(IngredientListSlot ingredientListSlot);

    @Inject(method = "set", at = @At("HEAD"), cancellable = true)
    private void onSet(int startIndex, List<IElement<?>> ingredientList, CallbackInfo ci) {
        if (!BookmarkLayoutManager.getInstance().isVerticalMode()) {
            return;
        }
        
        // 检查是否是书签列表（通过检查元素是否有书签信息）
        boolean isBookmarkList = false;
        for (int i = startIndex; i < ingredientList.size() && i < startIndex + 5; i++) {
            IElement<?> element = ingredientList.get(i);
            if (element.getBookmark().isPresent()) {
                isBookmarkList = true;
                break;
            }
        }
        
        if (!isBookmarkList) {
            return;
        }

        JEIEnhancements.LOGGER.debug("=== IngredientListRendererMixin.onSet (VERTICAL MODE) ===");

        List<IngredientListSlot> activeSlots = new ArrayList<>();
        for (IngredientListSlot slot : slots) {
            if (!slot.isBlocked()) {
                activeSlots.add(slot);
            }
        }

        if (activeSlots.isEmpty()) {
            return;
        }

        int minX = Integer.MAX_VALUE, maxX = Integer.MIN_VALUE;
        int minY = Integer.MAX_VALUE, maxY = Integer.MIN_VALUE;
        
        for (IngredientListSlot slot : activeSlots) {
            int x = slot.getArea().getX();
            int y = slot.getArea().getY();
            minX = Math.min(minX, x);
            maxX = Math.max(maxX, x);
            minY = Math.min(minY, y);
            maxY = Math.max(maxY, y);
        }

        int slotWidth = activeSlots.get(0).getArea().getWidth();
        int slotHeight = activeSlots.get(0).getArea().getHeight();
        int columns = (maxX - minX) / slotWidth + 1;
        int rows = (maxY - minY) / slotHeight + 1;

        blocked = 0;
        renderElementsByType.clear();
        renderOverlays.clear();

        for (IngredientListSlot slot : slots) {
            if (slot.isBlocked()) {
                slot.clear();
                blocked++;
            } else {
                slot.clear();
            }
        }

        List<List<IElement<?>>> groupedElements = groupElements(ingredientList, startIndex);
        
        int currentRow = 0;
        int currentCol = 0;
        
        for (List<IElement<?>> group : groupedElements) {
            if (currentRow >= rows) {
                break;
            }
            
            // 每个组从新行开始
            if (currentCol != 0) {
                currentRow++;
                currentCol = 0;
                if (currentRow >= rows) {
                    break;
                }
            }
            
            for (IElement<?> element : group) {
                if (!element.isVisible()) {
                    continue;
                }
                
                // 如果当前行放不下，换到下一行
                if (currentCol >= columns) {
                    currentRow++;
                    currentCol = 0;
                    if (currentRow >= rows) {
                        break;
                    }
                }
                
                int slotIndex = currentRow * columns + currentCol;
                if (slotIndex < activeSlots.size()) {
                    IngredientListSlot slot = activeSlots.get(slotIndex);
                    slot.setElement(element);
                    addRenderElement(slot);
                }
                currentCol++;
            }
            
            // 组结束后，准备下一行
            if (!group.isEmpty()) {
                currentRow++;
                currentCol = 0;
            }
        }

        ci.cancel();
    }
    
    /**
     * 按组分组元素（NEI风格）
     */
    private List<List<IElement<?>>> groupElements(List<IElement<?>> ingredientList, int startIndex) {
        List<List<IElement<?>>> result = new ArrayList<>();
        BookmarkManager manager = BookmarkManager.getInstance();
        
        // 用于跟踪已处理的组ID
        Set<Integer> processedGroupIds = new HashSet<>();
        // 用于跟踪已处理的元素索引
        Set<Integer> processedIndices = new HashSet<>();
        
        for (int i = startIndex; i < ingredientList.size(); i++) {
            if (processedIndices.contains(i)) {
                continue;
            }
            
            IElement<?> element = ingredientList.get(i);
            if (!element.isVisible()) {
                processedIndices.add(i);
                continue;
            }
            
            Optional<IBookmark> bookmarkOpt = element.getBookmark();
            if (bookmarkOpt.isEmpty()) {
                // 没有书签信息，作为单独元素
                List<IElement<?>> singleGroup = new ArrayList<>();
                singleGroup.add(element);
                result.add(singleGroup);
                processedIndices.add(i);
                continue;
            }
            
            BookmarkItem item = manager.findBookmarkItem(bookmarkOpt.get());
            
            if (item == null) {
                // 没有在BookmarkManager中找到，作为单独元素
                List<IElement<?>> singleGroup = new ArrayList<>();
                singleGroup.add(element);
                result.add(singleGroup);
                processedIndices.add(i);
                continue;
            }
            
            int groupId = item.getGroupId();
            
            if (processedGroupIds.contains(groupId)) {
                // 这个组已经处理过了
                processedIndices.add(i);
                continue;
            }
            
            BookmarkGroup group = manager.getGroup(groupId);
            List<IElement<?>> groupElements = new ArrayList<>();
            
            if (group != null && !group.isExpanded()) {
                // 折叠状态：只显示输出物品
                if (item.isOutput()) {
                    groupElements.add(element);
                }
                processedIndices.add(i);
                
                // 标记同组其他成员为已处理
                for (int j = startIndex; j < ingredientList.size(); j++) {
                    if (j == i) continue;
                    IElement<?> otherElement = ingredientList.get(j);
                    Optional<IBookmark> otherBookmarkOpt = otherElement.getBookmark();
                    if (otherBookmarkOpt.isPresent()) {
                        BookmarkItem otherItem = manager.findBookmarkItem(otherBookmarkOpt.get());
                        if (otherItem != null && otherItem.getGroupId() == groupId) {
                            processedIndices.add(j);
                        }
                    }
                }
            } else {
                // 展开状态：收集这个组的所有元素
                for (int j = startIndex; j < ingredientList.size(); j++) {
                    IElement<?> otherElement = ingredientList.get(j);
                    if (!otherElement.isVisible()) {
                        continue;
                    }
                    
                    Optional<IBookmark> otherBookmarkOpt = otherElement.getBookmark();
                    if (otherBookmarkOpt.isPresent()) {
                        BookmarkItem otherItem = manager.findBookmarkItem(otherBookmarkOpt.get());
                        if (otherItem != null && otherItem.getGroupId() == groupId) {
                            groupElements.add(otherElement);
                            processedIndices.add(j);
                        }
                    }
                }
            }
            
            if (!groupElements.isEmpty()) {
                result.add(groupElements);
            }
            processedGroupIds.add(groupId);
        }
        
        return result;
    }
}
