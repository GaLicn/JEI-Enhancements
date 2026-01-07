package com.gali.jei_enhancements.mixin;

import com.gali.jei_enhancements.JEIEnhancements;
import com.gali.jei_enhancements.bookmark.BookmarkItem;
import com.gali.jei_enhancements.bookmark.BookmarkManager;
import mezz.jei.gui.bookmarks.BookmarkList;
import mezz.jei.gui.bookmarks.IBookmark;
import mezz.jei.gui.overlay.IIngredientGridSource.SourceListChangedListener;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Iterator;
import java.util.List;
import java.util.Set;

/**
 * 修改JEI的BookmarkList，允许同一物品多次添加到书签
 * NEI风格：同一物品可以在不同组中独立存在
 * 
 * 关键问题：JEI的IngredientBookmark使用uid实现equals()，
 * 导致同一物品的不同实例被认为相等。
 * 我们需要基于对象引用（identity）来删除，而不是基于equals。
 */
@Mixin(value = BookmarkList.class, remap = false)
public class BookmarkListMixin {
    
    @Shadow @Final
    private Set<IBookmark> bookmarksSet;
    
    @Shadow @Final
    private List<IBookmark> bookmarksList;
    
    @Shadow @Final
    private List<SourceListChangedListener> listeners;
    
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
     * 
     * 问题：JEI的remove使用equals()来匹配书签，但同一物品的不同实例equals相等。
     * 当两个分组有同一物品时，删除一个会错误地影响另一个。
     * 
     * 解决方案：完全接管remove逻辑，使用对象引用（identity）来删除特定实例。
     * 特殊情况：如果删除的是组头（RESULT类型），则删除整个组的所有书签。
     */
    @Inject(method = "remove", at = @At("HEAD"), cancellable = true)
    private void onRemove(IBookmark bookmark, CallbackInfoReturnable<Boolean> cir) {
        BookmarkManager manager = BookmarkManager.getInstance();
        
        // 检查这个书签是否在我们的管理器中
        BookmarkItem item = manager.findBookmarkItem(bookmark);
        
        if (item != null) {
            boolean removed = false;
            
            // 检查是否是组头（RESULT类型）
            if (item.getType() == BookmarkItem.BookmarkItemType.RESULT) {
                // 删除整个组的所有JEI书签
                removed = removeEntireGroup(manager, item.getGroupId());
                JEIEnhancements.LOGGER.debug("Removed entire group {} from JEI", item.getGroupId());
            } else {
                // 只删除这个单独的书签
                removed = removeBookmarkByIdentity(bookmark);
                // 通知BookmarkManager删除单个书签
                manager.onBookmarkRemoved(bookmark);
                JEIEnhancements.LOGGER.debug("Removed single bookmark by identity");
            }
            
            // 通知监听器刷新UI
            if (removed) {
                notifyListeners();
            }
            
            // 取消原始方法，返回是否成功删除
            cir.setReturnValue(removed);
        } else {
            // 不是我们管理的书签，让JEI正常处理
            // 但仍然通知manager（以防万一）
            manager.onBookmarkRemoved(bookmark);
        }
    }
    
    /**
     * 通知所有监听器刷新UI
     */
    private void notifyListeners() {
        for (SourceListChangedListener listener : listeners) {
            listener.onSourceListChanged();
        }
    }
    
    /**
     * 按对象引用删除单个书签
     */
    private boolean removeBookmarkByIdentity(IBookmark bookmark) {
        // 从bookmarksList中按引用删除（不是equals）
        boolean removedFromList = false;
        Iterator<IBookmark> listIterator = bookmarksList.iterator();
        while (listIterator.hasNext()) {
            if (listIterator.next() == bookmark) {  // 使用 == 而不是 equals
                listIterator.remove();
                removedFromList = true;
                break;
            }
        }
        
        // 从bookmarksSet中按引用删除
        boolean removedFromSet = false;
        Iterator<IBookmark> setIterator = bookmarksSet.iterator();
        while (setIterator.hasNext()) {
            if (setIterator.next() == bookmark) {  // 使用 == 而不是 equals
                setIterator.remove();
                removedFromSet = true;
                break;
            }
        }
        
        return removedFromList || removedFromSet;
    }
    
    /**
     * 删除整个组的所有JEI书签
     */
    private boolean removeEntireGroup(BookmarkManager manager, int groupId) {
        // 获取该组的所有BookmarkItem
        List<BookmarkItem> groupItems = manager.getGroupItems(groupId);
        boolean anyRemoved = false;
        
        // 收集所有需要删除的JEI书签引用
        for (BookmarkItem groupItem : groupItems) {
            IBookmark linkedBookmark = groupItem.getLinkedBookmark();
            if (linkedBookmark != null) {
                // 从JEI的列表和集合中按引用删除
                Iterator<IBookmark> listIterator = bookmarksList.iterator();
                while (listIterator.hasNext()) {
                    if (listIterator.next() == linkedBookmark) {
                        listIterator.remove();
                        anyRemoved = true;
                        break;
                    }
                }
                
                Iterator<IBookmark> setIterator = bookmarksSet.iterator();
                while (setIterator.hasNext()) {
                    if (setIterator.next() == linkedBookmark) {
                        setIterator.remove();
                        break;
                    }
                }
            }
        }
        
        // 通知BookmarkManager删除整个组
        manager.removeGroup(groupId);
        
        return anyRemoved;
    }
}
