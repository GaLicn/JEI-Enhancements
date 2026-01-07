package com.gali.jei_enhancements.bookmark;

import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.gui.bookmarks.IBookmark;
import mezz.jei.gui.bookmarks.IngredientBookmark;
import net.minecraft.world.item.ItemStack;

import java.util.HashMap;
import java.util.Map;

/**
 * 管理书签物品的自定义数量显示
 * 实现NEI风格的Ctrl+滚轮调整数量功能
 */
public class BookmarkQuantityManager {
    
    private static final BookmarkQuantityManager INSTANCE = new BookmarkQuantityManager();
    
    // 存储书签的自定义数量，key是书签的唯一标识
    private final Map<Object, Integer> bookmarkQuantities = new HashMap<>();
    
    public static BookmarkQuantityManager getInstance() {
        return INSTANCE;
    }
    
    /**
     * 检查是否有任何自定义数量（用于决定是否渲染）
     */
    public boolean hasAnyCustomQuantity() {
        return !bookmarkQuantities.isEmpty();
    }
    
    /**
     * 检查特定书签是否有自定义数量
     */
    public boolean hasCustomQuantity(IBookmark bookmark) {
        Object key = getBookmarkKey(bookmark);
        return bookmarkQuantities.containsKey(key);
    }
    
    /**
     * 获取书签的自定义数量，如果没有设置则返回-1
     */
    public int getQuantity(IBookmark bookmark) {
        Object key = getBookmarkKey(bookmark);
        return bookmarkQuantities.getOrDefault(key, -1);
    }
    
    /**
     * 设置书签的自定义数量
     */
    public void setQuantity(IBookmark bookmark, int quantity) {
        Object key = getBookmarkKey(bookmark);
        if (quantity <= 0) {
            // 如果数量小于等于0，移除自定义数量
            bookmarkQuantities.remove(key);
        } else {
            // 限制最大数量
            if (quantity > 9999) {
                quantity = 9999;
            }
            bookmarkQuantities.put(key, quantity);
        }
    }
    
    /**
     * 调整书签数量 (通过滚轮)
     */
    public void adjustQuantity(IBookmark bookmark, int delta) {
        int current = getQuantity(bookmark);
        if (current < 0) {
            // 如果没有自定义数量，从1开始
            current = 1;
        }
        int newQuantity = current + delta;
        setQuantity(bookmark, newQuantity);
    }
    
    /**
     * 获取书签的唯一标识
     */
    private Object getBookmarkKey(IBookmark bookmark) {
        if (bookmark instanceof IngredientBookmark<?> ingredientBookmark) {
            ITypedIngredient<?> ingredient = ingredientBookmark.getIngredient();
            // 使用物品类型+物品本身作为key
            return ingredient.getType().getUid() + ":" + ingredient.getIngredient().hashCode();
        }
        return bookmark.hashCode();
    }
    
    /**
     * 清除所有自定义数量
     */
    public void clearAll() {
        bookmarkQuantities.clear();
    }
    
    /**
     * 移除特定书签的自定义数量
     */
    public void remove(IBookmark bookmark) {
        Object key = getBookmarkKey(bookmark);
        bookmarkQuantities.remove(key);
    }
}
