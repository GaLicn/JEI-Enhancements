package com.gali.jei_enhancements.mixin;

import com.gali.jei_enhancements.JEIEnhancements;
import com.gali.jei_enhancements.bookmark.BookmarkItem;
import com.gali.jei_enhancements.bookmark.BookmarkManager;
import com.gali.jei_enhancements.bookmark.IdentityDistinctBookmark;
import com.gali.jei_enhancements.bookmark.IBookmarkPageAccessor;
import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.gui.bookmarks.BookmarkList;
import mezz.jei.gui.bookmarks.IBookmark;
import mezz.jei.gui.bookmarks.IngredientBookmark;
import mezz.jei.gui.bookmarks.BookmarkFactory;
import mezz.jei.gui.overlay.ingredients.IIngredientGridSource.SourceListChangedListener;
import mezz.jei.gui.overlay.elements.IElement;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 修改JEI的BookmarkList，允许同一物品多次添加到书签
 */
@Mixin(value = BookmarkList.class, remap = false)
public class BookmarkListMixin implements IBookmarkPageAccessor {
    
    @Shadow @Final
    private Set<IBookmark> bookmarksSet;
    
    @Shadow @Final
    private List<IBookmark> bookmarksList;
    
    @Shadow @Final
    private List<SourceListChangedListener> listeners;

    @Shadow @Final
    private BookmarkFactory bookmarkFactory;
    
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
     * 拦截add方法
     * 当JEI加载书签时，尝试与BookmarkItem建立映射
     */
    @Inject(method = "add", at = @At("HEAD"))
    private void onAdd(IBookmark bookmark, CallbackInfoReturnable<Boolean> cir) {
        BookmarkManager manager = BookmarkManager.getInstance();
        // 尝试将新添加的JEI书签与已保存的BookmarkItem关联
        if (manager.isRestoringBookmarks()) {
            manager.tryLinkBookmark(bookmark);
        }
    }

    @Inject(method = "getElements", at = @At("HEAD"), cancellable = true)
    private void onGetElements(CallbackInfoReturnable<List<IElement<?>>> cir) {
        BookmarkManager manager = BookmarkManager.getInstance();
        cir.setReturnValue(bookmarksList.stream()
                .filter(manager::isBookmarkOnCurrentPage)
                .<IElement<?>>map(IBookmark::getElement)
                .toList());
    }

    @Inject(method = "add", at = @At("RETURN"))
    private void onAddReturn(IBookmark bookmark, CallbackInfoReturnable<Boolean> cir) {
        if (cir.getReturnValueZ() && BookmarkManager.getInstance().findBookmarkItem(bookmark) == null) {
            BookmarkManager manager = BookmarkManager.getInstance();
            manager.registerPlainBookmark(bookmark);
            manager.save();
            // 注册页归属发生在 JEI 通知之后，补发一次列表变更事件。
            jei_enhancements$notifyListeners();
        }
    }

    @Override
    @Unique
    public void jeiEnhancements$clearPage(int pageId) {
        BookmarkManager manager = BookmarkManager.getInstance();
        List<IBookmark> pageBookmarks = bookmarksList.stream().filter(bookmark -> {
            BookmarkItem item = manager.findBookmarkItem(bookmark);
            return item != null && item.getPageId() == pageId;
        }).toList();
        for (IBookmark bookmark : pageBookmarks) {
            // 走 JEI 原生删除入口，确保其书签配置文件也同步更新。
            ((BookmarkList) (Object) this).remove(bookmark);
        }
        manager.removeBookmarksFromPage(pageId);
        jei_enhancements$notifyListeners();
    }
    
    /**
     * 拦截moveBookmark方法
     * 完全接管移动逻辑，使用对象引用来正确处理同一物品的多个实例
     * 组头（RESULT类型）不能被移动，也不能被其他书签替换位置
     */
    @Inject(method = "moveBookmark", at = @At("HEAD"), cancellable = true)
    private void onMoveBookmark(IBookmark previousBookmark, IBookmark newBookmark, int offset, CallbackInfo ci) {
        BookmarkManager manager = BookmarkManager.getInstance();
        
        // 查找两个书签对应的BookmarkItem
        // newBookmark是被拖动的书签，previousBookmark是目标位置的书签
        BookmarkItem draggedItem = manager.findBookmarkItem(newBookmark);
        BookmarkItem targetItem = manager.findBookmarkItem(previousBookmark);
        
        // 如果有任何一个书签在我们的管理器中，完全接管移动逻辑
        if (draggedItem != null || targetItem != null) {
            // 检查是否涉及组头
            if (draggedItem != null && draggedItem.getType() == BookmarkItem.BookmarkItemType.RESULT) {
                // 组头不能被移动
                ci.cancel();
                return;
            }
            
            if (targetItem != null && targetItem.getType() == BookmarkItem.BookmarkItemType.RESULT) {
                // 不能移动到组头的位置（替换组头）
                ci.cancel();
                return;
            }
            
            // 如果两个书签都在我们的管理器中，且属于不同的组，阻止移动
            if (draggedItem != null && targetItem != null) {
                if (draggedItem.getGroupId() != targetItem.getGroupId()) {
                    // 跨组移动，取消操作
                    ci.cancel();
                    return;
                }
            }
            
            // 同组内移动，使用对象引用来正确处理
            // 使用 == 来查找正确的索引，而不是 equals
            int targetIndex = jei_enhancements$indexOfByIdentity(previousBookmark);
            int draggedIndex = jei_enhancements$indexOfByIdentity(newBookmark);
            
            if (targetIndex == -1 || draggedIndex == -1) {
                ci.cancel();
                return;
            }
            
            int newIndex = targetIndex + offset;
            if (newIndex == draggedIndex) {
                ci.cancel();
                return;
            }
            
            if (newIndex < 0) {
                newIndex += bookmarksList.size();
            }
            newIndex %= bookmarksList.size();
            
            // 执行移动（使用对象引用）
            jei_enhancements$removeByIdentity(newBookmark);
            bookmarksList.add(newIndex, newBookmark);
            
            // 通知监听器刷新UI
            jei_enhancements$notifyListeners();
            
            // 取消原始方法
            ci.cancel();
        }
        // 非管理的书签，让JEI正常处理
    }
    
    /**
     * 使用对象引用查找索引
     */
    @Unique
    private int jei_enhancements$indexOfByIdentity(IBookmark bookmark) {
        for (int i = 0; i < bookmarksList.size(); i++) {
            if (bookmarksList.get(i) == bookmark) {
                return i;
            }
        }
        return -1;
    }
    
    /**
     * 使用对象引用从列表中移除（不从Set中移除）
     */
    @Unique
    private void jei_enhancements$removeByIdentity(IBookmark bookmark) {
        Iterator<IBookmark> iterator = bookmarksList.iterator();
        while (iterator.hasNext()) {
            if (iterator.next() == bookmark) {
                iterator.remove();
                break;
            }
        }
    }
    
    /**
     * 拦截setFromConfigFile方法
     * JEI从配置文件加载书签后，需要根据BookmarkManager的数据恢复重复的书签
     */
    @Inject(method = "setFromConfigFile", at = @At("TAIL"))
    private void onSetFromConfigFile(List<IBookmark> bookmarks, CallbackInfo ci) {

        BookmarkManager manager = BookmarkManager.getInstance();
        manager.setRestoringBookmarks(true);
        manager.ensureLoaded();
        
        // 先保留 JEI 自己管理的普通书签，只补齐本模组记录的条目。
        List<BookmarkItem> allItems = manager.getAllItems();
        manager.clearMappings();
        Map<String, List<IBookmark>> available = new HashMap<>();
        Map<String, IngredientBookmark<?>> templates = new HashMap<>();
        for (IBookmark bookmark : bookmarksList) {
            available.computeIfAbsent(manager.getItemKey(bookmark), key -> new ArrayList<>()).add(bookmark);
            if (bookmark instanceof IngredientBookmark<?> ingredientBookmark) {
                templates.putIfAbsent(manager.getItemKey(bookmark), ingredientBookmark);
            }
        }

        for (BookmarkItem item : allItems) {
            List<IBookmark> candidates = available.get(item.getItemKey());
            IBookmark bookmark = candidates == null || candidates.isEmpty() ? null : candidates.removeFirst();
            if (bookmark == null) {
                IngredientBookmark<?> template = templates.get(item.getItemKey());
                if (template != null) {
                    bookmark = bookmarkFactory.create(template.getIngredient());
                    ((IdentityDistinctBookmark) bookmark).jeiEnhancements$setIdentityDistinct(true);
                    bookmarksList.add(bookmark);
                    bookmarksSet.add(bookmark);
                }
            }
            if (bookmark != null) {
                item.setLinkedBookmark(bookmark);
                manager.linkBookmark(bookmark, item);
            } else {
                JEIEnhancements.LOGGER.warn("Could not find JEI bookmark for item: {}", item.getItemKey());
            }
        }

        // 兼容旧存档：此前未由本模组管理的 JEI 书签统一迁移到第一页。
        for (IBookmark bookmark : bookmarksList) {
            if (manager.findBookmarkItem(bookmark) == null) {
                manager.registerPlainBookmark(bookmark);
            }
        }
        manager.save();
        manager.setRestoringBookmarks(false);
    }
    
    /**
     * 拦截remove方法
     * 完全接管remove逻辑，使用对象引用（identity）来删除特定实例。
     */
    @Inject(method = "remove", at = @At("HEAD"), cancellable = true)
    private void onRemove(IBookmark bookmark, CallbackInfoReturnable<Boolean> cir) {
        BookmarkManager manager = BookmarkManager.getInstance();
        
        // 检查这个书签是否在管理器中
        BookmarkItem item = manager.findBookmarkItem(bookmark);
        
        if (item != null) {
            boolean removed = false;
            
            // 检查是否是组头（RESULT类型）
            if (item.getType() == BookmarkItem.BookmarkItemType.RESULT) {
                // 只删除这个配方（RESULT+它的INGREDIENT），不是整个组
                removed = jei_enhancements$removeRecipe(manager, item);
            } else {
                // 只删除这个单独的书签
                removed = jei_enhancements$removeBookmarkByIdentity(bookmark);
                // 通知BookmarkManager删除单个书签
                manager.onBookmarkRemoved(bookmark);
            }
            
            // 通知监听器刷新UI
            if (removed) {
                jei_enhancements$notifyListeners();
            }
            
            // 取消原始方法，返回是否成功删除
            cir.setReturnValue(removed);
        } else {
            // 通知manager（以防万一）
            manager.onBookmarkRemoved(bookmark);
        }
    }
    
    /**
     * 通知所有监听器刷新UI
     */
    @Unique
    private void jei_enhancements$notifyListeners() {
        for (SourceListChangedListener listener : listeners) {
            listener.onSourceListChanged();
        }
    }
    
    /**
     * 按对象引用删除单个书签
     */
    @Unique
    private boolean jei_enhancements$removeBookmarkByIdentity(IBookmark bookmark) {
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
     * 删除单个配方（RESULT+它后面的INGREDIENT）
     * 不删除整个组，只删除这个配方
     */
    @Unique
    private boolean jei_enhancements$removeRecipe(BookmarkManager manager, BookmarkItem resultItem) {
        int groupId = resultItem.getGroupId();
        List<BookmarkItem> groupItems = manager.getGroupItems(groupId);
        boolean anyRemoved = false;
        
        // 找到这个RESULT在组内的位置
        int resultIndex = groupItems.indexOf(resultItem);
        if (resultIndex < 0) {
            return false;
        }
        
        // 收集要删除的物品（这个RESULT + 它后面的INGREDIENT）
        List<BookmarkItem> itemsToRemove = new java.util.ArrayList<>();
        itemsToRemove.add(resultItem);
        
        // 收集紧跟在这个RESULT后面的INGREDIENT
        for (int i = resultIndex + 1; i < groupItems.size(); i++) {
            BookmarkItem item = groupItems.get(i);
            if (item.isOutput()) {
                // 遇到下一个RESULT，停止
                break;
            }
            if (item.isIngredient()) {
                itemsToRemove.add(item);
            }
        }
        
        // 从JEI中删除这些书签
        for (BookmarkItem item : itemsToRemove) {
            IBookmark linkedBookmark = item.getLinkedBookmark();
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
            
            // 从BookmarkManager中删除
            manager.onBookmarkRemoved(linkedBookmark);
        }
        
        // 检查组是否为空，如果为空则删除组
        List<BookmarkItem> remainingItems = manager.getGroupItems(groupId);
        if (remainingItems.isEmpty()) {
            manager.removeGroupOnly(groupId);
        }
        
        manager.save();
        
        return anyRemoved;
    }
    
    /**
     * 删除整个组的所有JEI书签
     */
    @Unique
    private boolean jei_enhancements$removeEntireGroup(BookmarkManager manager, int groupId) {
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
