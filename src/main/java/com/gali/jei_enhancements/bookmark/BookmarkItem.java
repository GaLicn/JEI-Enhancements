package com.gali.jei_enhancements.bookmark;

import mezz.jei.gui.bookmarks.IBookmark;
import org.jetbrains.annotations.Nullable;

/**
 * - groupId: 所属组ID
 * - itemKey: 物品唯一标识
 * - baseQuantity: 配方中的基础数量
 * - type: 类型（输出/输入/普通物品）
 */
public class BookmarkItem {

    public enum BookmarkItemType {
        ITEM,       // 普通物品（单独添加的书签）
        RESULT,     // 配方输出
        INGREDIENT  // 配方输入
    }
    
    private int groupId;
    private final String itemKey;
    private final int baseQuantity;
    private BookmarkItemType type;
    
    // 关联的JEI书签（用于渲染）
    @Nullable
    private IBookmark linkedBookmark;
    
    public BookmarkItem(int groupId, String itemKey, int baseQuantity, BookmarkItemType type) {
        this.groupId = groupId;
        this.itemKey = itemKey;
        this.baseQuantity = baseQuantity;
        this.type = type;
    }
    
    public int getGroupId() {
        return groupId;
    }
    
    public void setGroupId(int groupId) {
        this.groupId = groupId;
    }
    
    public String getItemKey() {
        return itemKey;
    }
    
    public int getBaseQuantity() {
        return baseQuantity;
    }
    
    public BookmarkItemType getType() {
        return type;
    }
    
    public void setType(BookmarkItemType type) {
        this.type = type;
    }
    
    @Nullable
    public IBookmark getLinkedBookmark() {
        return linkedBookmark;
    }
    
    public void setLinkedBookmark(@Nullable IBookmark bookmark) {
        this.linkedBookmark = bookmark;
    }
    
    /**
     * 是否是输出物品（组头）
     */
    public boolean isOutput() {
        return type == BookmarkItemType.RESULT;
    }
    
    /**
     * 是否是输入物品
     */
    public boolean isIngredient() {
        return type == BookmarkItemType.INGREDIENT;
    }
    
    @Override
    public String toString() {
        return "BookmarkItem{" +
                "groupId=" + groupId +
                ", itemKey='" + itemKey + '\'' +
                ", baseQuantity=" + baseQuantity +
                ", type=" + type +
                '}';
    }
}
