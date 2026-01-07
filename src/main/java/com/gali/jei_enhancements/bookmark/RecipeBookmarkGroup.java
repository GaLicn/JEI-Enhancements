package com.gali.jei_enhancements.bookmark;

import mezz.jei.gui.bookmarks.IBookmark;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * 配方书签组
 * 存储一个配方的输出和所有输入，作为一个整体
 * 调整组内任意物品数量时，其他成员按比例调整
 * 
 * NEI风格实现：
 * - 每个物品只能属于一个组（JEI不允许重复书签）
 * - 使用bookmarkKey来唯一标识成员
 * - 支持折叠/展开显示
 */
public class RecipeBookmarkGroup {
    
    // 组内的书签成员
    private final List<GroupMember> members = new ArrayList<>();
    
    // 用于快速查找的key集合
    private final Set<String> memberKeys = new HashSet<>();
    
    // 组的唯一ID
    private final int groupId;
    
    // 当前的倍率（用于按比例调整）
    private double multiplier = 1.0;
    
    // 是否展开显示（NEI风格）
    private boolean expanded = true;
    
    private static int nextGroupId = 1;
    
    public RecipeBookmarkGroup() {
        this.groupId = nextGroupId++;
    }
    
    /**
     * 组成员：包含书签和基础数量
     */
    public static class GroupMember {
        private IBookmark bookmark;  // 可能为null（从文件加载时）
        private final String bookmarkKey;
        private final int baseQuantity; // 配方中的基础数量
        private final boolean isOutput;  // 是否是输出物品
        
        public GroupMember(IBookmark bookmark, String bookmarkKey, int baseQuantity, boolean isOutput) {
            this.bookmark = bookmark;
            this.bookmarkKey = bookmarkKey;
            this.baseQuantity = baseQuantity;
            this.isOutput = isOutput;
        }
        
        // 从文件加载时使用，没有实际的bookmark对象
        public GroupMember(String bookmarkKey, int baseQuantity, boolean isOutput) {
            this.bookmark = null;
            this.bookmarkKey = bookmarkKey;
            this.baseQuantity = baseQuantity;
            this.isOutput = isOutput;
        }
        
        public IBookmark getBookmark() {
            return bookmark;
        }
        
        public void setBookmark(IBookmark bookmark) {
            this.bookmark = bookmark;
        }
        
        public String getBookmarkKey() {
            return bookmarkKey;
        }
        
        public int getBaseQuantity() {
            return baseQuantity;
        }
        
        public boolean isOutput() {
            return isOutput;
        }
    }
    
    public int getGroupId() {
        return groupId;
    }
    
    /**
     * 检查key是否已经在组中（防止重复添加）
     */
    public boolean containsKey(String bookmarkKey) {
        return memberKeys.contains(bookmarkKey);
    }
    
    public void addMember(IBookmark bookmark, String bookmarkKey, int baseQuantity, boolean isOutput) {
        if (memberKeys.contains(bookmarkKey)) {
            // 已存在，不重复添加
            return;
        }
        members.add(new GroupMember(bookmark, bookmarkKey, baseQuantity, isOutput));
        memberKeys.add(bookmarkKey);
    }
    
    /**
     * 通过key添加成员（从文件加载时使用）
     */
    public void addMemberByKey(String bookmarkKey, int baseQuantity, boolean isOutput) {
        if (memberKeys.contains(bookmarkKey)) {
            return;
        }
        members.add(new GroupMember(bookmarkKey, baseQuantity, isOutput));
        memberKeys.add(bookmarkKey);
    }
    
    public List<GroupMember> getMembers() {
        return members;
    }
    
    public double getMultiplier() {
        return multiplier;
    }
    
    public void setMultiplier(double multiplier) {
        this.multiplier = Math.max(0.0, Math.min(multiplier, 9999.0));
    }
    
    /**
     * 调整倍率
     */
    public void adjustMultiplier(double delta) {
        setMultiplier(this.multiplier + delta);
    }
    
    /**
     * 获取成员的当前数量（基础数量 * 倍率）
     */
    public int getMemberQuantity(GroupMember member) {
        return (int) Math.ceil(member.getBaseQuantity() * multiplier);
    }
    
    /**
     * 根据某个成员的新数量，计算并更新倍率
     */
    public void updateMultiplierFromMember(GroupMember member, int newQuantity) {
        if (member.getBaseQuantity() > 0) {
            setMultiplier((double) newQuantity / member.getBaseQuantity());
        }
    }
    
    /**
     * 检查书签key是否属于这个组
     */
    public boolean containsBookmark(String bookmarkKey) {
        return memberKeys.contains(bookmarkKey);
    }
    
    /**
     * 获取指定key的成员信息
     */
    public GroupMember getMember(String bookmarkKey) {
        for (GroupMember member : members) {
            if (member.getBookmarkKey().equals(bookmarkKey)) {
                return member;
            }
        }
        return null;
    }
    
    /**
     * 是否展开显示（NEI风格）
     */
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
     * 获取组的第一个成员（折叠时显示）
     */
    public GroupMember getFirstMember() {
        return members.isEmpty() ? null : members.get(0);
    }
    
    /**
     * 获取输出成员（通常是第一个）
     */
    public GroupMember getOutputMember() {
        for (GroupMember member : members) {
            if (member.isOutput()) {
                return member;
            }
        }
        return getFirstMember();
    }
    
    public boolean isEmpty() {
        return members.isEmpty();
    }
    
    public int size() {
        return members.size();
    }
}
