package com.gali.jei_enhancements.bookmark;

import mezz.jei.gui.bookmarks.IBookmark;
import org.jetbrains.annotations.Nullable;

/**
 * - amount: 当前总数量
 * - factor: 配方中每次合成的数量（基础数量）
 *
 */
public class BookmarkItem {

    public enum BookmarkItemType {
        ITEM,       // 普通物品（单独添加的书签）
        RESULT,     // 配方输出
        INGREDIENT  // 配方输入
    }
    
    private int groupId;
    private final String itemKey;

    private long amount;      // 当前总数量
    private final long factor; // 配方中每次合成的数量（基础数量）
    
    private BookmarkItemType type;
    
    // 关联的JEI书签（用于渲染）
    @Nullable
    private IBookmark linkedBookmark;
    
    public BookmarkItem(int groupId, String itemKey, long factor, BookmarkItemType type) {
        this.groupId = groupId;
        this.itemKey = itemKey;
        this.factor = Math.max(1, factor);
        this.amount = this.factor; // 初始数量等于factor（即multiplier=1）
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
    
    /**
     * 获取配方中每次合成的数量（基础数量）
     */
    public long getFactor() {
        return factor;
    }
    
    /**
     * 获取当前总数量
     */
    public long getAmount() {
        return amount;
    }
    
    /**
     * 设置当前总数量
     */
    public void setAmount(long amount) {
        this.amount = Math.max(factor, amount); // 最小为factor（即multiplier=1）
    }
    
    /**
     * 获取合成次数（multiplier = amount / factor）
     */
    public long getMultiplier() {
        return (long) Math.ceil((double) amount / factor);
    }
    
    /**
     * 设置合成次数，自动计算amount
     */
    public void setMultiplier(long multiplier) {
        this.amount = factor * Math.max(1, multiplier);
    }
    
    /**
     * 调整合成次数
     * @param shift 调整量（正数增加，负数减少）
     * @return 新的multiplier
     */
    public long shiftMultiplier(long shift) {
        long currentMultiplier = getMultiplier();
        long newMultiplier = Math.max(1, currentMultiplier + shift);
        setMultiplier(newMultiplier);
        return newMultiplier;
    }

    public int getBaseQuantity() {
        return (int) factor;
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
                ", factor=" + factor +
                ", amount=" + amount +
                ", multiplier=" + getMultiplier() +
                ", type=" + type +
                '}';
    }
}
