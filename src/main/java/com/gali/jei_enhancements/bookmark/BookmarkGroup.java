package com.gali.jei_enhancements.bookmark;

/**
 * 书签组（NEI风格）
 * 每个组有：
 * - groupId: 唯一标识
 * - multiplier: 倍率（用于按比例调整数量）
 * - expanded: 是否展开显示
 * - craftingChainEnabled: 是否开启crafting chain模式（[变绿）
 */
public class BookmarkGroup {
    
    private final int groupId;
    private double multiplier = 1.0;
    private boolean expanded = true;
    private boolean craftingChainEnabled = false;  // NEI的crafting chain模式
    
    public BookmarkGroup(int groupId) {
        this.groupId = groupId;
    }
    
    public int getGroupId() {
        return groupId;
    }
    
    public double getMultiplier() {
        return multiplier;
    }
    
    public void setMultiplier(double multiplier) {
        this.multiplier = Math.max(0.0, Math.min(multiplier, 9999.0));
    }
    
    public void adjustMultiplier(double delta) {
        setMultiplier(this.multiplier + delta);
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
