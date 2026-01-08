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

        if (!isManagedBookmarkList(ingredientList, startIndex)) {
            return;
        }

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

        // 先对所有元素进行分组（从索引0开始，获取完整的分组信息）
        List<List<IElement<?>>> allGroupedElements = groupElements(ingredientList, 0);
        
        if (allGroupedElements.isEmpty()) {
            ci.cancel();
            return;
        }
        
        // 计算startIndex对应的组索引
        // 在垂直模式下，startIndex应该对应某个组的第一个元素
        int startGroupIndex = 0;
        int cumulativeElementCount = 0;
        
        for (int i = 0; i < allGroupedElements.size(); i++) {
            List<IElement<?>> group = allGroupedElements.get(i);
            int nextCumulativeCount = cumulativeElementCount + group.size();
            
            // 如果startIndex落在这个组的范围内，从这个组开始
            if (startIndex < nextCumulativeCount) {
                startGroupIndex = i;
                break;
            }
            
            cumulativeElementCount = nextCumulativeCount;
            
            // 如果已经遍历完所有组，从最后一个组开始
            if (i == allGroupedElements.size() - 1) {
                startGroupIndex = i;
            }
        }
        
        // 从startGroupIndex开始显示组
        int currentRow = 0;
        int currentCol = 0;
        
        for (int groupIdx = startGroupIndex; groupIdx < allGroupedElements.size(); groupIdx++) {
            List<IElement<?>> group = allGroupedElements.get(groupIdx);
            
            if (currentRow >= rows) {
                break;
            }
            
            // 每个组从新行开始（除了第一个组）
            if (groupIdx > startGroupIndex && currentCol != 0) {
                currentRow++;
                currentCol = 0;
                if (currentRow >= rows) {
                    break;
                }
            }
            
            boolean groupHasVisibleElements = false;
            
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
                    groupHasVisibleElements = true;
                }
                currentCol++;
            }
            
            // 组结束后，如果有可见元素，准备下一行
            if (groupHasVisibleElements && currentRow < rows) {
                currentRow++;
                currentCol = 0;
            }
        }

        ci.cancel();
    }
    
    /**
     * 按组分组元素
     * 每个RESULT类型的项都开始新的一行（即使它们有相同的groupId）
     */
    private List<List<IElement<?>>> groupElements(List<IElement<?>> ingredientList, int startIndex) {
        List<List<IElement<?>>> result = new ArrayList<>();
        BookmarkManager manager = BookmarkManager.getInstance();
        
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
            BookmarkGroup group = manager.getGroup(groupId);
            List<IElement<?>> groupElements = new ArrayList<>();
            
            // NEI风格：每个RESULT类型的项都开始新的一行
            // 只收集从当前RESULT到下一个RESULT之间的元素
            if (item.isOutput() || item.getType() == BookmarkItem.BookmarkItemType.ITEM) {
                // 这是一个组头或普通物品，开始新的一行
                groupElements.add(element);
                processedIndices.add(i);
                
                if (group != null && !group.isExpanded()) {
                    // 折叠状态：只显示这个组头
                    // 不收集后续的INGREDIENT
                } else {
                    // 展开状态：收集紧随其后的INGREDIENT元素（同一个groupId，直到遇到下一个RESULT）
                    for (int j = i + 1; j < ingredientList.size(); j++) {
                        if (processedIndices.contains(j)) {
                            continue;
                        }
                        
                        IElement<?> nextElement = ingredientList.get(j);
                        if (!nextElement.isVisible()) {
                            continue;
                        }
                        
                        Optional<IBookmark> nextBookmarkOpt = nextElement.getBookmark();
                        if (nextBookmarkOpt.isEmpty()) {
                            break; // 遇到非书签元素，停止
                        }
                        
                        BookmarkItem nextItem = manager.findBookmarkItem(nextBookmarkOpt.get());
                        if (nextItem == null) {
                            break;
                        }
                        
                        // 如果遇到另一个RESULT或ITEM，停止（它会开始新的一行）
                        if (nextItem.isOutput() || nextItem.getType() == BookmarkItem.BookmarkItemType.ITEM) {
                            break;
                        }
                        
                        // 只收集同一个groupId的INGREDIENT
                        if (nextItem.getGroupId() == groupId && nextItem.isIngredient()) {
                            groupElements.add(nextElement);
                            processedIndices.add(j);
                        }
                    }
                }
            } else {
                // 这是一个INGREDIENT，但前面没有对应的RESULT
                // 作为单独元素处理
                groupElements.add(element);
                processedIndices.add(i);
            }
            
            if (!groupElements.isEmpty()) {
                result.add(groupElements);
            }
        }
        
        return result;
    }

    /**
     * 检查元素是否在BookmarkManager中有记录
     */
    private boolean isManagedBookmarkList(List<IElement<?>> ingredientList, int startIndex) {
        BookmarkManager manager = BookmarkManager.getInstance();
        
        // 检查前几个有书签信息的元素是否在BookmarkManager中
        int checkedCount = 0;
        int managedCount = 0;
        
        for (int i = startIndex; i < ingredientList.size() && checkedCount < 5; i++) {
            IElement<?> element = ingredientList.get(i);
            Optional<IBookmark> bookmarkOpt = element.getBookmark();
            
            if (bookmarkOpt.isPresent()) {
                checkedCount++;
                // 检查这个书签是否在BookmarkManager中
                BookmarkItem item = manager.findBookmarkItem(bookmarkOpt.get());
                if (item != null) {
                    managedCount++;
                }
            }
        }
        
        // 如果没有找到任何书签元素，不是书签列表
        if (checkedCount == 0) {
            return false;
        }
        

        return managedCount > 0;
    }
}
