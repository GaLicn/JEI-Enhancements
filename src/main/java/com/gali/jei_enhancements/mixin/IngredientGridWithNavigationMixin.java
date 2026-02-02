package com.gali.jei_enhancements.mixin;

import com.gali.jei_enhancements.bookmark.BookmarkItem;
import com.gali.jei_enhancements.bookmark.BookmarkLayoutManager;
import com.gali.jei_enhancements.bookmark.BookmarkManager;
import com.gali.jei_enhancements.bookmark.IVerticalPagingAccessor;
import mezz.jei.gui.PageNavigation;
import mezz.jei.gui.bookmarks.IBookmark;
import mezz.jei.gui.overlay.IIngredientGridSource;
import mezz.jei.gui.overlay.IngredientGrid;
import mezz.jei.gui.overlay.IngredientGridWithNavigation;
import mezz.jei.gui.overlay.IngredientListSlot;
import mezz.jei.gui.overlay.elements.IElement;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * 修改IngredientGridWithNavigation的分页逻辑
 * 在垂直布局模式下，基于组数量而不是元素数量来计算分页
 */
@Mixin(value = IngredientGridWithNavigation.class, remap = false)
public abstract class IngredientGridWithNavigationMixin implements IVerticalPagingAccessor {

    @Shadow @Final
    private IngredientGrid ingredientGrid;

    @Shadow @Final
    private IIngredientGridSource ingredientSource;

    @Shadow
    private int firstItemIndex;
    
    @Shadow @Final
    private PageNavigation navigation;

    // 缓存的组信息
    @Unique
    private List<int[]> jei_enhancements$groupRanges = null;
    
    @Unique
    private int jei_enhancements$lastElementCount = -1;
    
    @Unique
    private int jei_enhancements$currentGroupIndex = 0;
    
    @Unique
    private int jei_enhancements$rowsPerPage = 1;

    /**
     * 拦截updateLayout方法，在垂直布局模式下修正firstItemIndex和分页
     */
    @Inject(method = "updateLayout", at = @At("HEAD"))
    private void onUpdateLayoutHead(boolean resetToFirstPage, CallbackInfo ci) {
        if (!BookmarkLayoutManager.getInstance().isVerticalMode()) {
            jei_enhancements$groupRanges = null;
            return;
        }

        List<IElement<?>> ingredientList = ingredientSource.getElements();
        if (ingredientList.isEmpty()) {
            jei_enhancements$groupRanges = null;
            return;
        }

        // 检查是否是书签列表
        if (!jei_enhancements$isManagedBookmarkList(ingredientList)) {
            jei_enhancements$groupRanges = null;
            return;
        }

        // 计算每页行数
        jei_enhancements$rowsPerPage = jei_enhancements$calculateRowsPerPage();

        // 重新计算组范围（如果元素数量变化了）
        if (jei_enhancements$groupRanges == null || jei_enhancements$lastElementCount != ingredientList.size()) {
            jei_enhancements$groupRanges = jei_enhancements$calculateGroupRanges(ingredientList);
            jei_enhancements$lastElementCount = ingredientList.size();
        }
        
        if (resetToFirstPage) {
            jei_enhancements$currentGroupIndex = 0;
            firstItemIndex = 0;
        } else {
            // 根据firstItemIndex计算当前组索引
            jei_enhancements$currentGroupIndex = jei_enhancements$findGroupIndexForElementIndex(firstItemIndex);
        }

        // 确保firstItemIndex对应当前组的开始位置
        if (jei_enhancements$groupRanges != null && !jei_enhancements$groupRanges.isEmpty() 
                && jei_enhancements$currentGroupIndex < jei_enhancements$groupRanges.size()) {
            firstItemIndex = jei_enhancements$groupRanges.get(jei_enhancements$currentGroupIndex)[0];
        }
    }
    
    /**
     * 在updateLayout结束后更新页码显示
     */
    @Inject(method = "updateLayout", at = @At("TAIL"))
    private void onUpdateLayoutTail(boolean resetToFirstPage, CallbackInfo ci) {
        if (!BookmarkLayoutManager.getInstance().isVerticalMode()) {
            return;
        }
        
        if (jei_enhancements$groupRanges == null || jei_enhancements$groupRanges.isEmpty()) {
            return;
        }
        
        // 强制更新页码显示
        navigation.updatePageNumber();
    }
    
    /**
     * 计算每页可显示的行数
     */
    @Unique
    private int jei_enhancements$calculateRowsPerPage() {
        List<IngredientListSlot> slots = new ArrayList<>();
        ingredientGrid.getSlots().forEach(slots::add);
        
        if (slots.isEmpty()) {
            return 1;
        }
        
        int minY = Integer.MAX_VALUE, maxY = Integer.MIN_VALUE;
        int slotHeight = slots.get(0).getArea().getHeight();
        
        for (IngredientListSlot slot : slots) {
            if (!slot.isBlocked()) {
                int y = slot.getArea().getY();
                minY = Math.min(minY, y);
                maxY = Math.max(maxY, y);
            }
        }
        
        if (minY == Integer.MAX_VALUE) {
            return 1;
        }
        
        return (maxY - minY) / slotHeight + 1;
    }
    
    /**
     * 根据元素索引找到对应的组索引
     */
    @Unique
    private int jei_enhancements$findGroupIndexForElementIndex(int elementIndex) {
        if (jei_enhancements$groupRanges == null || jei_enhancements$groupRanges.isEmpty()) {
            return 0;
        }
        
        for (int i = 0; i < jei_enhancements$groupRanges.size(); i++) {
            int[] range = jei_enhancements$groupRanges.get(i);
            if (elementIndex >= range[0] && elementIndex <= range[1]) {
                return i;
            }
            if (elementIndex < range[0]) {
                return Math.max(0, i - 1);
            }
        }
        
        return jei_enhancements$groupRanges.size() - 1;
    }

    /**
     * 计算所有组的范围 [startIndex, endIndex]
     */
    @Unique
    private List<int[]> jei_enhancements$calculateGroupRanges(List<IElement<?>> ingredientList) {
        List<int[]> ranges = new ArrayList<>();
        BookmarkManager manager = BookmarkManager.getInstance();
        
        int groupStart = 0;
        boolean foundFirstGroup = false;
        
        for (int i = 0; i < ingredientList.size(); i++) {
            IElement<?> element = ingredientList.get(i);
            Optional<IBookmark> bookmarkOpt = element.getBookmark();
            
            if (bookmarkOpt.isPresent()) {
                BookmarkItem item = manager.findBookmarkItem(bookmarkOpt.get());
                if (item != null) {
                    // 如果是RESULT或ITEM类型，这是一个新组的开始
                    if (item.isOutput() || item.getType() == BookmarkItem.BookmarkItemType.ITEM) {
                        if (foundFirstGroup && i > groupStart) {
                            // 保存前一个组的范围
                            ranges.add(new int[]{groupStart, i - 1});
                        }
                        groupStart = i;
                        foundFirstGroup = true;
                    }
                }
            }
        }
        
        // 添加最后一个组
        if (foundFirstGroup && groupStart < ingredientList.size()) {
            ranges.add(new int[]{groupStart, ingredientList.size() - 1});
        } else if (!foundFirstGroup && !ingredientList.isEmpty()) {
            // 没有找到任何组头，把所有元素作为一个组
            ranges.add(new int[]{0, ingredientList.size() - 1});
        }
        
        return ranges;
    }

    /**
     * 检查是否是受管理的书签列表
     */
    @Unique
    private boolean jei_enhancements$isManagedBookmarkList(List<IElement<?>> ingredientList) {
        BookmarkManager manager = BookmarkManager.getInstance();
        
        int checkedCount = 0;
        int managedCount = 0;
        
        for (int i = 0; i < ingredientList.size() && checkedCount < 5; i++) {
            IElement<?> element = ingredientList.get(i);
            Optional<IBookmark> bookmarkOpt = element.getBookmark();
            
            if (bookmarkOpt.isPresent()) {
                checkedCount++;
                BookmarkItem item = manager.findBookmarkItem(bookmarkOpt.get());
                if (item != null) {
                    managedCount++;
                }
            }
        }
        
        return checkedCount > 0 && managedCount > 0;
    }
    
    /**
     * 获取垂直模式下的总页数
     */
    @Override
    @Unique
    public int jei_enhancements$getPageCount() {
        if (jei_enhancements$groupRanges == null || jei_enhancements$groupRanges.isEmpty()) {
            return 1;
        }
        int groupCount = jei_enhancements$groupRanges.size();
        return Math.max(1, (int) Math.ceil((double) groupCount / jei_enhancements$rowsPerPage));
    }
    
    /**
     * 获取垂直模式下的当前页码
     */
    @Override
    @Unique
    public int jei_enhancements$getPageNumber() {
        if (jei_enhancements$rowsPerPage <= 0) {
            return 0;
        }
        return jei_enhancements$currentGroupIndex / jei_enhancements$rowsPerPage;
    }
    
    /**
     * 垂直模式下翻到下一页
     */
    @Override
    @Unique
    public boolean jei_enhancements$nextPage() {
        if (jei_enhancements$groupRanges == null || jei_enhancements$groupRanges.isEmpty()) {
            return false;
        }
        
        int nextGroupIndex = jei_enhancements$currentGroupIndex + jei_enhancements$rowsPerPage;
        if (nextGroupIndex >= jei_enhancements$groupRanges.size()) {
            nextGroupIndex = 0; // 循环到第一页
        }
        
        jei_enhancements$currentGroupIndex = nextGroupIndex;
        firstItemIndex = jei_enhancements$groupRanges.get(nextGroupIndex)[0];
        return true;
    }
    
    /**
     * 垂直模式下翻到上一页
     */
    @Override
    @Unique
    public boolean jei_enhancements$previousPage() {
        if (jei_enhancements$groupRanges == null || jei_enhancements$groupRanges.isEmpty()) {
            return false;
        }
        
        int prevGroupIndex = jei_enhancements$currentGroupIndex - jei_enhancements$rowsPerPage;
        if (prevGroupIndex < 0) {
            // 循环到最后一页
            int totalGroups = jei_enhancements$groupRanges.size();
            int lastPageStartGroup = ((totalGroups - 1) / jei_enhancements$rowsPerPage) * jei_enhancements$rowsPerPage;
            prevGroupIndex = lastPageStartGroup;
        }
        
        jei_enhancements$currentGroupIndex = prevGroupIndex;
        firstItemIndex = jei_enhancements$groupRanges.get(prevGroupIndex)[0];
        return true;
    }
    
    /**
     * 获取组范围列表
     */
    @Override
    @Unique
    public List<int[]> jei_enhancements$getGroupRanges() {
        return jei_enhancements$groupRanges;
    }
    
    /**
     * 获取每页行数
     */
    @Override
    @Unique
    public int jei_enhancements$getRowsPerPage() {
        return jei_enhancements$rowsPerPage;
    }
}
