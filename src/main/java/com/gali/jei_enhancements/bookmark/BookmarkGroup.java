package com.gali.jei_enhancements.bookmark;

/**
 * - groupId: 唯一标识
 * - linkedGroupId: 逻辑链接的组ID（用于crafting chain）
 * - expanded: 是否展开显示
 * - craftingChainEnabled: 是否开启crafting chain模式（[变绿）
 */
public class BookmarkGroup {
    
    private final int groupId;
    private int linkedGroupId;  // 逻辑链接的组ID，-1表示没有链接
    private boolean expanded = true;
    private boolean craftingChainEnabled = false;
    
    public BookmarkGroup(int groupId) {
        this.groupId = groupId;
        this.linkedGroupId = -1;  // 默认没有链接
    }
    
    public int getGroupId() {
        return groupId;
    }
    
    /**
     * 获取逻辑链接的组ID
     * 多个组可以链接到同一个linkedGroupId，表示它们是逻辑同组
     */
    public int getLinkedGroupId() {
        return linkedGroupId;
    }
    
    public void setLinkedGroupId(int linkedGroupId) {
        this.linkedGroupId = linkedGroupId;
    }
    
    /**
     * 是否有逻辑链接
     */
    public boolean hasLink() {
        return linkedGroupId >= 0;
    }
    
    // 兼容旧代码
    public double getMultiplier() {
        return 1.0;
    }
    
    public void setMultiplier(double multiplier) {
        // 不再使用组级别的multiplier
    }
    
    public void adjustMultiplier(double delta) {
        // 不再使用组级别的multiplier
    }
    
    public boolean isExpanded() {
        return expanded;
    }
    
    public void setExpanded(boolean expanded) {
        this.expanded = expanded;
    }
    
    public void toggleExpanded() {
        this.expanded = !this.expanded;
    }
    
    /**
     * 是否开启了crafting chain模式
     * 开启后[符号变绿，会计算组内配方的倍率关系
     */
    public boolean isCraftingChainEnabled() {
        return craftingChainEnabled;
    }
    
    public void setCraftingChainEnabled(boolean enabled) {
        this.craftingChainEnabled = enabled;
    }
    
    public void toggleCraftingChain() {
        this.craftingChainEnabled = !this.craftingChainEnabled;
    }
}
