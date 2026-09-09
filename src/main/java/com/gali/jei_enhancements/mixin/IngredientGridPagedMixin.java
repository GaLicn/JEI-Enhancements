package com.gali.jei_enhancements.mixin;

import com.gali.jei_enhancements.bookmark.BookmarkLayoutManager;
import com.gali.jei_enhancements.bookmark.BookmarkManager;
import com.gali.jei_enhancements.bookmark.IVerticalPagingAccessor;
import mezz.jei.gui.input.IPaged;
import mezz.jei.gui.overlay.IngredientGridWithNavigation;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

/**
 * Mixin到IngredientGridWithNavigation的内部类IngredientGridPaged
 * 修改分页计算逻辑
 */
@Mixin(targets = "mezz.jei.gui.overlay.IngredientGridWithNavigation$IngredientGridPaged", remap = false)
public abstract class IngredientGridPagedMixin implements IPaged {

    @org.spongepowered.asm.mixin.Shadow(aliases = "this$0")
    private IngredientGridWithNavigation this$0;

    /**
     * 拦截getPageCount方法，在垂直模式下返回基于组数量的页数
     */
    @Inject(method = "getPageCount", at = @At("HEAD"), cancellable = true)
    private void onGetPageCount(CallbackInfoReturnable<Integer> cir) {
        BookmarkManager manager = BookmarkManager.getInstance();
        IngredientGridWithNavigation outer = jei_enhancements$getOuter();
        if (manager.getPageCount() > 1 && outer instanceof IVerticalPagingAccessor accessor
                && accessor.jei_enhancements$isManagedBookmarkList()) {
            cir.setReturnValue(manager.getPageCount());
            return;
        }
        if (!BookmarkLayoutManager.getInstance().isVerticalMode()) {
            return;
        }
        
        if (outer == null) {
            return;
        }
        
        // 检查是否是垂直模式的书签列表
        if (outer instanceof IVerticalPagingAccessor accessor) {
            List<int[]> groupRanges = accessor.jei_enhancements$getGroupRanges();
            if (groupRanges != null && !groupRanges.isEmpty()) {
                int pageCount = accessor.jei_enhancements$getPageCount();
                if (pageCount > 0) {
                    cir.setReturnValue(pageCount);
                }
            }
        }
    }
    
    /**
     * 拦截getPageNumber方法，在垂直模式下返回基于组的页码
     */
    @Inject(method = "getPageNumber", at = @At("HEAD"), cancellable = true)
    private void onGetPageNumber(CallbackInfoReturnable<Integer> cir) {
        BookmarkManager manager = BookmarkManager.getInstance();
        IngredientGridWithNavigation outer = jei_enhancements$getOuter();
        if (manager.getPageCount() > 1 && outer instanceof IVerticalPagingAccessor accessor
                && accessor.jei_enhancements$isManagedBookmarkList()) {
            cir.setReturnValue(manager.getCurrentPageIndex());
            return;
        }
        if (!BookmarkLayoutManager.getInstance().isVerticalMode()) {
            return;
        }
        
        if (outer == null) {
            return;
        }
        
        if (outer instanceof IVerticalPagingAccessor accessor) {
            List<int[]> groupRanges = accessor.jei_enhancements$getGroupRanges();
            if (groupRanges != null && !groupRanges.isEmpty()) {
                cir.setReturnValue(accessor.jei_enhancements$getPageNumber());
            }
        }
    }
    
    /**
     * 拦截nextPage方法，在垂直模式下使用基于组的翻页
     */
    @Inject(method = "nextPage", at = @At("HEAD"), cancellable = true)
    private void onNextPage(CallbackInfoReturnable<Boolean> cir) {
        BookmarkManager manager = BookmarkManager.getInstance();
        IngredientGridWithNavigation outer = jei_enhancements$getOuter();
        if (manager.getPageCount() > 1 && outer instanceof IVerticalPagingAccessor accessor
                && accessor.jei_enhancements$isManagedBookmarkList()) {
            manager.nextPage();
            manager.save();
            this$0.updateLayout(true);
            cir.setReturnValue(true);
            return;
        }
        if (!BookmarkLayoutManager.getInstance().isVerticalMode()) {
            return;
        }
        
        if (outer == null) {
            return;
        }
        
        if (outer instanceof IVerticalPagingAccessor accessor) {
            List<int[]> groupRanges = accessor.jei_enhancements$getGroupRanges();
            if (groupRanges != null && !groupRanges.isEmpty()) {
                boolean result = accessor.jei_enhancements$nextPage();
                outer.updateLayout(false);
                cir.setReturnValue(result);
            }
        }
    }
    
    /**
     * 拦截previousPage方法，在垂直模式下使用基于组的翻页
     */
    @Inject(method = "previousPage", at = @At("HEAD"), cancellable = true)
    private void onPreviousPage(CallbackInfoReturnable<Boolean> cir) {
        BookmarkManager manager = BookmarkManager.getInstance();
        IngredientGridWithNavigation outer = jei_enhancements$getOuter();
        if (manager.getPageCount() > 1 && outer instanceof IVerticalPagingAccessor accessor
                && accessor.jei_enhancements$isManagedBookmarkList()) {
            manager.previousPage();
            manager.save();
            this$0.updateLayout(true);
            cir.setReturnValue(true);
            return;
        }
        if (!BookmarkLayoutManager.getInstance().isVerticalMode()) {
            return;
        }
        
        if (outer == null) {
            return;
        }
        
        if (outer instanceof IVerticalPagingAccessor accessor) {
            List<int[]> groupRanges = accessor.jei_enhancements$getGroupRanges();
            if (groupRanges != null && !groupRanges.isEmpty()) {
                boolean result = accessor.jei_enhancements$previousPage();
                outer.updateLayout(false);
                cir.setReturnValue(result);
            }
        }
    }
    
    /**
     * 获取外部类实例
     */
    @Unique
    private IngredientGridWithNavigation jei_enhancements$getOuter() {
        return this$0;
    }
}
