package com.gali.jei_enhancements.mixin;

import com.gali.jei_enhancements.JEIEnhancements;
import com.gali.jei_enhancements.bookmark.BookmarkManager;
import mezz.jei.gui.bookmarks.BookmarkList;
import mezz.jei.gui.bookmarks.IBookmark;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Set;

/**
 * 修改JEI的BookmarkList，允许同一物品多次添加到书签
 * NEI风格：同一物品可以在不同组中独立存在
 */
@Mixin(value = BookmarkList.class, remap = false)
public class BookmarkListMixin {
    
    @Shadow @Final
    private Set<IBookmark> bookmarksSet;
    
    /**
     * 拦截contains方法
     * 当BookmarkManager标记为"允许重复"时，总是返回false，允许添加
     */
    @Inject(method = "contains", at = @At("HEAD"), cancellable = true)
    private void onContains(IBookmark value, CallbackInfoReturnable<Boolean> cir) {
        if (BookmarkManager.getInstance().isAllowDuplicates()) {
            // 允许重复模式：总是返回false，让add方法可以添加
            cir.setReturnValue(false);
        }
    }
    
    /**
     * 拦截remove方法
     * 通知BookmarkManager处理删除逻辑
     */
    @Inject(method = "remove", at = @At("HEAD"))
    private void onRemove(IBookmark bookmark, CallbackInfoReturnable<Boolean> cir) {
        BookmarkManager.getInstance().onBookmarkRemoved(bookmark);
    }
}
