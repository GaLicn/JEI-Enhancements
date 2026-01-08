package com.gali.jei_enhancements.bookmark;

/**
 * 每个组有：
 * - groupId: 唯一标识
 * - multiplier: 倍率（用于按比例调整数量）
 * - expanded: 是否展开显示
 */
public class BookmarkGroup {
    
    private final int groupId;
    private double multiplier = 1.0;
    private boolean expanded = true;
    
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
}
