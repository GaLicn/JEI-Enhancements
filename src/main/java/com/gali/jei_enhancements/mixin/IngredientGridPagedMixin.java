package com.gali.jei_enhancements.mixin;

import com.gali.jei_enhancements.bookmark.BookmarkLayoutManager;
import com.gali.jei_enhancements.bookmark.BookmarkManager;
import com.gali.jei_enhancements.bookmark.IVerticalPagingAccessor;
import com.gali.jei_enhancements.bookmark.IBookmarkPageAccessor;
import com.gali.jei_enhancements.mixin.accessor.IngredientGridNavigationControllerAccessor;
import mezz.jei.gui.bookmarks.BookmarkList;
import mezz.jei.gui.input.IPaged;
import mezz.jei.gui.overlay.ingredients.IIngredientGridSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;

/**
 * 适配 JEI 50+ 的分页控制器，保留多书签页的页码状态。
 */
@Mixin(targets = "mezz.jei.gui.overlay.ingredients.IngredientGridWithNavigationController", remap = false)
public abstract class IngredientGridPagedMixin implements IPaged {

    @org.spongepowered.asm.mixin.Shadow @org.spongepowered.asm.mixin.Final
    private IIngredientGridSource ingredientSource;

    /**
     * 拦截getPageCount方法，在垂直模式下返回基于组数量的页数
     */
    @Inject(method = "getPageCount", at = @At("HEAD"), cancellable = true)
    private void onGetPageCount(CallbackInfoReturnable<Integer> cir) {
        BookmarkManager manager = BookmarkManager.getInstance();
        if (manager.getPageCount() > 1 && ingredientSource instanceof BookmarkList) {
            cir.setReturnValue(manager.getPageCount());
            return;
        }
        if (!BookmarkLayoutManager.getInstance().isVerticalMode()) {
            return;
        }
        
    }
    
    /**
     * 拦截getPageNumber方法，在垂直模式下返回基于组的页码
     */
    @Inject(method = "getPageNumber", at = @At("HEAD"), cancellable = true)
    private void onGetPageNumber(CallbackInfoReturnable<Integer> cir) {
        BookmarkManager manager = BookmarkManager.getInstance();
        if (manager.getPageCount() > 1 && ingredientSource instanceof BookmarkList) {
            cir.setReturnValue(manager.getCurrentPageIndex());
            return;
        }
        if (!BookmarkLayoutManager.getInstance().isVerticalMode()) {
            return;
        }
        
    }
    
    /**
     * 拦截nextPage方法，在垂直模式下使用基于组的翻页
     */
    @Inject(method = "nextPage", at = @At("HEAD"), cancellable = true)
    private void onNextPage(CallbackInfoReturnable<Boolean> cir) {
        BookmarkManager manager = BookmarkManager.getInstance();
        if (manager.getPageCount() > 1 && ingredientSource instanceof BookmarkList bookmarkList) {
            manager.nextPage();
            manager.save();
            ((IBookmarkPageAccessor) bookmarkList).jeiEnhancements$refreshPage();
            ((IngredientGridNavigationControllerAccessor) this).jeiEnhancements$updateLayoutStartingAt(0);
            cir.setReturnValue(true);
            return;
        }
        if (!BookmarkLayoutManager.getInstance().isVerticalMode()) {
            return;
        }
        
    }
    
    /**
     * 拦截previousPage方法，在垂直模式下使用基于组的翻页
     */
    @Inject(method = "previousPage", at = @At("HEAD"), cancellable = true)
    private void onPreviousPage(CallbackInfoReturnable<Boolean> cir) {
        BookmarkManager manager = BookmarkManager.getInstance();
        if (manager.getPageCount() > 1 && ingredientSource instanceof BookmarkList bookmarkList) {
            manager.previousPage();
            manager.save();
            ((IBookmarkPageAccessor) bookmarkList).jeiEnhancements$refreshPage();
            ((IngredientGridNavigationControllerAccessor) this).jeiEnhancements$updateLayoutStartingAt(0);
            cir.setReturnValue(true);
            return;
        }
        if (!BookmarkLayoutManager.getInstance().isVerticalMode()) {
            return;
        }
        
    }
}
