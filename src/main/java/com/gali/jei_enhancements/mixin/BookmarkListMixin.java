package com.gali.jei_enhancements.mixin;

import com.gali.jei_enhancements.JEIEnhancements;
import com.gali.jei_enhancements.bookmark.BookmarkItem;
import com.gali.jei_enhancements.bookmark.BookmarkManager;
import com.gali.jei_enhancements.bookmark.IBookmarkPageAccessor;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.ingredients.IIngredientHelper;
import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.api.recipe.IFocusFactory;
import mezz.jei.api.recipe.IRecipeManager;
import mezz.jei.api.runtime.IIngredientManager;
import mezz.jei.gui.bookmarks.BookmarkList;
import mezz.jei.gui.bookmarks.IBookmark;
import mezz.jei.gui.bookmarks.IngredientBookmark;
import mezz.jei.gui.config.IBookmarkConfig;
import mezz.jei.gui.overlay.ingredients.IIngredientGridSource.SourceListChangedListener;
import mezz.jei.gui.overlay.elements.IElement;
import net.minecraft.core.RegistryAccess;
import net.minecraft.world.item.ItemStack;
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
    private IIngredientManager ingredientManager;

    @Shadow @Final
    private IRecipeManager recipeManager;

    @Shadow @Final
    private IFocusFactory focusFactory;

    @Shadow @Final
    private RegistryAccess registryAccess;

    @Shadow @Final
    private IBookmarkConfig bookmarkConfig;

    @Shadow @Final
    private IGuiHelper guiHelper;

    @Unique
    private boolean jei_enhancements$restoredFromConfig = false;
    
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
    private void onAdd(IBookmark value, CallbackInfoReturnable<Boolean> cir) {
        BookmarkManager manager = BookmarkManager.getInstance();
        if (manager.isAllowDuplicates()) {
            // RecipeBookmarkHelper 会在 add() 后自行注册配方中的类型化成员。
            // 防止第一次通知将尚未完成的配方当作普通列表处理。
            jei_enhancements$restoredFromConfig = true;
        }
        // 只有恢复JEI配置时，才允许按itemKey关联已保存的BookmarkItem。
        if (manager.isRestoringBookmarks()) {
            manager.tryLinkBookmark(value);
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
    private void onAddReturn(IBookmark value, CallbackInfoReturnable<Boolean> cir) {
        if (!cir.getReturnValueZ()) {
            return;
        }

        BookmarkManager manager = BookmarkManager.getInstance();
        if (!manager.isAllowDuplicates() && manager.findBookmarkItem(value) == null) {
            manager.setRestoringBookmarks(false);
            jei_enhancements$restoredFromConfig = true;
            manager.registerPlainBookmark(value);
            manager.save();
            jei_enhancements$notifyListeners();
        }
    }

    @Override
    @Unique
    public void jeiEnhancements$clearPage(int pageId) {
        BookmarkManager manager = BookmarkManager.getInstance();
        List<IBookmark> pageBookmarks = bookmarksList.stream()
                .filter(bookmark -> {
                    BookmarkItem item = manager.findBookmarkItem(bookmark);
                    return item != null && item.getPageId() == pageId;
                })
                .toList();

        for (IBookmark bookmark : pageBookmarks) {
            jei_enhancements$removeBookmarkByIdentity(bookmark);
            manager.onBookmarkRemoved(bookmark);
        }
        manager.removeBookmarksFromPage(pageId);
        bookmarkConfig.saveBookmarks(recipeManager, focusFactory, guiHelper, ingredientManager, registryAccess, bookmarksList);
        jei_enhancements$notifyListeners();
    }

    @Override
    @Unique
    public void jeiEnhancements$refreshPage() {
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

            // 把新的顺序写回管理器。否则重进存档时会按旧的 bookmarkItems 顺序重建，拖拽结果丢失。
            manager.reorderItemsByBookmarks(bookmarksList);

            // 通知监听器刷新UI
            jei_enhancements$notifyListeners();

            // 立即落盘（原版 moveBookmark 同样会在移动后保存书签配置）
            manager.save();

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

    @Unique
    private int jei_enhancements$getBookmarkBaseQuantity(IBookmark bookmark) {
        if (bookmark instanceof IngredientBookmark<?> ingredientBookmark) {
            ITypedIngredient<?> ingredient = ingredientBookmark.getIngredient();
            if (ingredient.getIngredient() instanceof ItemStack stack) {
                return Math.max(1, stack.getCount());
            }
            @SuppressWarnings("unchecked")
            IIngredientHelper<Object> helper =
                    (IIngredientHelper<Object>) (IIngredientHelper<?>)
                            ingredientManager.getIngredientHelper(ingredient.getType());
            long amount = helper.getAmount(ingredient.getIngredient());
            if (amount > 0) {
                return (int) Math.min(Integer.MAX_VALUE, amount);
            }
        }
        return 1;
    }
    
    @Inject(method = "notifyListenersOfChange", at = @At("TAIL"))
    private void onNotifyListenersOfChangeTail(CallbackInfo ci) {
        if (jei_enhancements$restoredFromConfig) {
            return;
        }

        BookmarkManager manager = BookmarkManager.getInstance();
        manager.ensureLoaded();

        if (bookmarksList.isEmpty()) {
            return;
        }

        List<BookmarkItem> allItems = manager.getAllItems();

        // 有已保存数据：按存档条目重建 JEI 书签列表
        if (!allItems.isEmpty()) {
            jei_enhancements$restoredFromConfig = true;
            jei_enhancements$restoreManagedBookmarks(manager, allItems);
            return;
        }

        // 没有已保存数据：用当前 JEI 书签列表初始化（兼容旧存档/手动添加的书签）
        manager.clearMappings();
        for (IBookmark bookmark : bookmarksList) {
            if (manager.findBookmarkItem(bookmark) != null) {
                continue;
            }
            String itemKey = manager.getItemKey(bookmark);
            int baseQuantity = jei_enhancements$getBookmarkBaseQuantity(bookmark);
            manager.addBookmarkItem(BookmarkManager.DEFAULT_GROUP_ID, itemKey, baseQuantity, BookmarkItem.BookmarkItemType.ITEM, bookmark);
        }
        manager.save();
        jei_enhancements$restoredFromConfig = true;
    }
    
    /**
     * 按存档中的书签条目重建 JEI 书签列表。
     * <p>
     * 候选书签按 itemKey 分桶后逐个取用；取不到时跳过而不克隆，
     * 因为克隆出的实例与原实例 equals 相等，会破坏 bookmarksSet 与 bookmarksList 的一致性。
     */
    @Unique
    private void jei_enhancements$restoreManagedBookmarks(BookmarkManager manager, List<BookmarkItem> allItems) {
        manager.setRestoringBookmarks(true);
        try {
            Map<String, List<IBookmark>> available = new HashMap<>();
            for (IBookmark bookmark : bookmarksList) {
                String itemKey = manager.getItemKey(bookmark);
                available.computeIfAbsent(itemKey, key -> new ArrayList<>()).add(bookmark);
            }

            manager.clearMappings();
            bookmarksList.clear();
            bookmarksSet.clear();

            for (BookmarkItem item : allItems) {
                List<IBookmark> candidates = available.get(item.getItemKey());
                if (candidates == null || candidates.isEmpty()) {
                    // 存档条目数多于 JEI 实际书签数，属数据不一致。
                    // 此处不能凭空克隆书签：克隆实例与模板 equals 相等，bookmarksSet 不会增长，
                    // 但 bookmarksList 会多出一个，最终同一物品在同一页重复渲染。
                    JEIEnhancements.LOGGER.warn("Could not find JEI bookmark for item: {}", item.getItemKey());
                    continue;
                }
                IBookmark bookmark = candidates.remove(0);

                bookmarksList.add(bookmark);
                bookmarksSet.add(bookmark);
                item.setLinkedBookmark(bookmark);
                manager.linkBookmark(bookmark, item);
            }

            // 将模组存档中不存在的普通 JEI 书签保留在第一页。
            int firstPageId = manager.getFirstPageId();
            for (List<IBookmark> remaining : available.values()) {
                for (IBookmark bookmark : remaining) {
                    bookmarksList.add(bookmark);
                    bookmarksSet.add(bookmark);
                    manager.registerPlainBookmark(bookmark, firstPageId);
                }
            }

            manager.save();
        } finally {
            manager.setRestoringBookmarks(false);
        }
    }

    /**
     * 拦截remove方法
     * 完全接管remove逻辑，使用对象引用（identity）来删除特定实例。
     */
    @Inject(method = "remove", at = @At("HEAD"), cancellable = true)
    private void onRemove(IBookmark ingredient, CallbackInfoReturnable<Boolean> cir) {
        BookmarkManager manager = BookmarkManager.getInstance();
        
        // 检查这个书签是否在管理器中
        BookmarkItem item = manager.findBookmarkItem(ingredient);
        
        if (item != null) {
            boolean removed = false;
            
            // 检查是否是组头（RESULT类型）
            if (item.getType() == BookmarkItem.BookmarkItemType.RESULT) {
                // 只删除这个配方（RESULT+它的INGREDIENT），不是整个组
                removed = jei_enhancements$removeRecipe(manager, item);
            } else {
                // 只删除这个单独的书签
                removed = jei_enhancements$removeBookmarkByIdentity(ingredient);
                // 通知BookmarkManager删除单个书签
                manager.onBookmarkRemoved(ingredient);
            }
            
            // 通知监听器刷新UI
            if (removed) {
                jei_enhancements$notifyListeners();
            }
            
            // 取消原始方法，返回是否成功删除
            cir.setReturnValue(removed);
        } else {
            // 未建立映射的书签虽不由管理器管理，但仍必须按对象引用删除。
            // 交回 JEI 原生 remove 会先用 equals 查 bookmarksSet，再删除 bookmarksList 中
            // 第一个相等元素；当列表存在相等实例时，删掉的可能与目标不是同一个，导致目标残留。
            boolean removed = jei_enhancements$removeBookmarkByIdentity(ingredient);
            if (removed) {
                jei_enhancements$notifyListeners();
            }
            cir.setReturnValue(removed);
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
