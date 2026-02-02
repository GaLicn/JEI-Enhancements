package com.gali.jei_enhancements.mixin;

import com.gali.jei_enhancements.bookmark.BookmarkLayoutManager;
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

    /**
     * 拦截getPageCount方法，在垂直模式下返回基于组数量的页数
     */
    @Inject(method = "getPageCount", at = @At("HEAD"), cancellable = true)
    private void onGetPageCount(CallbackInfoReturnable<Integer> cir) {
        if (!BookmarkLayoutManager.getInstance().isVerticalMode()) {
            return;
        }
        
        // 获取外部类实例
        IngredientGridWithNavigation outer = jei_enhancements$getOuter();
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
        if (!BookmarkLayoutManager.getInstance().isVerticalMode()) {
            return;
        }
        
        IngredientGridWithNavigation outer = jei_enhancements$getOuter();
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
        if (!BookmarkLayoutManager.getInstance().isVerticalMode()) {
            return;
        }
        
        IngredientGridWithNavigation outer = jei_enhancements$getOuter();
        if (outer == null) {
            return;
        }
        
        if (outer instanceof IVerticalPagingAccessor accessor) {
            List<int[]> groupRanges = accessor.jei_enhancements$getGroupRanges();
            if (groupRanges != null && !groupRanges.isEmpty()) {
                boolean result = accessor.jei_enhancements$nextPage();
                // 触发布局更新
                try {
                    java.lang.reflect.Method updateLayout = IngredientGridWithNavigation.class.getDeclaredMethod("updateLayout", boolean.class);
                    updateLayout.setAccessible(true);
                    updateLayout.invoke(outer, false);
                } catch (Exception e) {
                    // ignore
                }
                cir.setReturnValue(result);
            }
        }
    }
    
    /**
     * 拦截previousPage方法，在垂直模式下使用基于组的翻页
     */
    @Inject(method = "previousPage", at = @At("HEAD"), cancellable = true)
    private void onPreviousPage(CallbackInfoReturnable<Boolean> cir) {
        if (!BookmarkLayoutManager.getInstance().isVerticalMode()) {
            return;
        }
        
        IngredientGridWithNavigation outer = jei_enhancements$getOuter();
        if (outer == null) {
            return;
        }
        
        if (outer instanceof IVerticalPagingAccessor accessor) {
            List<int[]> groupRanges = accessor.jei_enhancements$getGroupRanges();
            if (groupRanges != null && !groupRanges.isEmpty()) {
                boolean result = accessor.jei_enhancements$previousPage();
                // 触发布局更新
                try {
                    java.lang.reflect.Method updateLayout = IngredientGridWithNavigation.class.getDeclaredMethod("updateLayout", boolean.class);
                    updateLayout.setAccessible(true);
                    updateLayout.invoke(outer, false);
                } catch (Exception e) {
                    // ignore
                }
                cir.setReturnValue(result);
            }
        }
    }
    
    /**
     * 获取外部类实例
     */
    @Unique
    private IngredientGridWithNavigation jei_enhancements$getOuter() {
        try {
            // 内部类有一个隐式的this$0字段指向外部类
            java.lang.reflect.Field outerField = this.getClass().getDeclaredField("this$0");
            outerField.setAccessible(true);
            return (IngredientGridWithNavigation) outerField.get(this);
        } catch (Exception e) {
            return null;
        }
    }
}
