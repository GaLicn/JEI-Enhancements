package com.gali.jei_enhancements.bookmark;

import mezz.jei.gui.overlay.IngredientListSlot;
import net.minecraft.client.gui.GuiGraphics;

import java.util.*;

/**
 * 处理组合并的拖动操作（NEI风格）
 * 
 * NEI的组逻辑：
 * - 左键拖动：将多行连接成一个组（改变groupId，不是真正合并）
 * - 右键拖动：将行从组中排除
 * - 右键点击组面板：切换crafting chain模式（[变绿）
 * - 各组头保留，只是逻辑上属于同一个组
 */
public class GroupingDragHandler {
    
    private static final GroupingDragHandler INSTANCE = new GroupingDragHandler();
    
    // 组面板宽度
    public static final int GROUP_PANEL_WIDTH = 8;
    
    // 颜色定义（NEI风格）
    private static final int GROUP_CHAIN_COLOR = 0xFF45DA75;  // 绿色 - crafting chain模式
    private static final int GROUP_NONE_COLOR = 0xFF666666;   // 灰色 - 普通组
    private static final int HIGHLIGHT_COLOR = 0x80FFFFFF;    // 高亮色
    private static final int DRAG_COLOR = 0x6045DA75;         // 拖动高亮色
    
    // 拖动状态
    private boolean isDragging = false;
    private int dragButton = -1;  // 0=左键, 1=右键
    private int startRowIndex = -1;
    private int endRowIndex = -1;
    private int startGroupId = BookmarkManager.DEFAULT_GROUP_ID;
    
    // 当前网格信息
    private int gridX = 0;
    private int gridY = 0;
    private int slotHeight = 18;
    private int columns = 1;
    private int rows = 1;
    
    // 行到组ID的映射（用于预览）
    private Map<Integer, Integer> rowToGroupId = new HashMap<>();
    
    public static GroupingDragHandler getInstance() {
        return INSTANCE;
    }
    
    /**
     * 更新网格信息
     */
    public void updateGridInfo(int x, int y, int slotHeight, int columns, int rows) {
        this.gridX = x;
        this.gridY = y;
        this.slotHeight = slotHeight;
        this.columns = columns;
        this.rows = rows;
    }
    
    /**
     * 检查鼠标是否在组面板区域
     */
    public boolean isInGroupPanelArea(int mouseX, int mouseY) {
        if (!BookmarkLayoutManager.getInstance().isVerticalMode()) {
            return false;
        }
        
        int panelX = gridX - GROUP_PANEL_WIDTH;
        int panelHeight = rows * slotHeight;
        
        return mouseX >= panelX && mouseX < gridX && 
               mouseY >= gridY && mouseY < gridY + panelHeight;
    }
    
    /**
     * 获取鼠标所在的行索引
     */
    public int getRowIndexAt(int mouseY) {
        if (mouseY < gridY) return -1;
        int row = (mouseY - gridY) / slotHeight;
        return row < rows ? row : -1;
    }
    
    /**
     * 处理鼠标点击
     * @param button 0=左键, 1=右键
     * @return 是否处理了点击
     */
    public boolean handleClick(int mouseX, int mouseY, int button, List<IngredientListSlot> slots) {
        if (!isInGroupPanelArea(mouseX, mouseY)) {
            return false;
        }
        
        int rowIndex = getRowIndexAt(mouseY);
        if (rowIndex < 0) {
            return false;
        }
        
        // 右键单击：切换crafting chain模式
        if (button == 1) {
            BookmarkItem item = findBookmarkItemAtRow(rowIndex, slots);
            if (item != null && item.getGroupId() != BookmarkManager.DEFAULT_GROUP_ID) {
                BookmarkGroup group = BookmarkManager.getInstance().getGroup(item.getGroupId());
                if (group != null) {
                    group.toggleCraftingChain();
                    BookmarkManager.getInstance().save();
                    return true;
                }
            }
        }
        
        return false;
    }
    
    /**
     * 开始拖动
     * @param button 0=左键, 1=右键
     */
    public boolean startDrag(int mouseX, int mouseY, int button, List<IngredientListSlot> slots) {
        if (!isInGroupPanelArea(mouseX, mouseY)) {
            return false;
        }
        
        int rowIndex = getRowIndexAt(mouseY);
        if (rowIndex < 0) {
            return false;
        }
        
        // 构建当前的行到组ID映射
        buildRowToGroupIdMap(slots);
        
        BookmarkItem item = findBookmarkItemAtRow(rowIndex, slots);
        int groupId = (item != null) ? item.getGroupId() : BookmarkManager.DEFAULT_GROUP_ID;
        
        // 左键：创建新组或扩展组
        // 右键：从组中排除（只有非默认组才能排除）
        if (button == 0) {
            // 左键拖动
            isDragging = true;
            dragButton = 0;
            startRowIndex = rowIndex;
            endRowIndex = rowIndex;
            // 如果当前行在默认组，创建新组；否则使用当前组
            startGroupId = (groupId == BookmarkManager.DEFAULT_GROUP_ID) ? Integer.MIN_VALUE : groupId;
            return true;
        } else if (button == 1 && groupId != BookmarkManager.DEFAULT_GROUP_ID) {
            // 右键拖动（从组中排除）
            isDragging = true;
            dragButton = 1;
            startRowIndex = rowIndex;
            endRowIndex = rowIndex;
            startGroupId = BookmarkManager.DEFAULT_GROUP_ID;  // 排除到默认组
            return true;
        }
        
        return false;
    }
    
    /**
     * 更新拖动位置
     */
    public void updateDrag(int mouseY) {
        if (!isDragging) return;
        
        int rowIndex = getRowIndexAt(mouseY);
        if (rowIndex >= 0) {
            endRowIndex = rowIndex;
        }
    }
    
    /**
     * 结束拖动，执行组操作
     */
    public void endDrag(List<IngredientListSlot> slots) {
        if (!isDragging) {
            return;
        }
        
        isDragging = false;
        
        // 执行组操作
        int minRow = Math.min(startRowIndex, endRowIndex);
        int maxRow = Math.max(startRowIndex, endRowIndex);
        
        if (dragButton == 0) {
            // 左键：将行加入组
            includeRowsInGroup(minRow, maxRow, slots);
        } else if (dragButton == 1) {
            // 右键：将行从组中排除
            excludeRowsFromGroup(minRow, maxRow, slots);
        }
        
        reset();
    }
    
    /**
     * 取消拖动
     */
    public void cancelDrag() {
        isDragging = false;
        reset();
    }
    
    private void reset() {
        startRowIndex = -1;
        endRowIndex = -1;
        startGroupId = BookmarkManager.DEFAULT_GROUP_ID;
        dragButton = -1;
        rowToGroupId.clear();
    }
    
    /**
     * 构建行到组ID的映射
     */
    private void buildRowToGroupIdMap(List<IngredientListSlot> slots) {
        rowToGroupId.clear();
        BookmarkManager manager = BookmarkManager.getInstance();
        
        for (int row = 0; row < rows; row++) {
            BookmarkItem item = findBookmarkItemAtRow(row, slots);
            if (item != null) {
                rowToGroupId.put(row, item.getGroupId());
            } else {
                rowToGroupId.put(row, BookmarkManager.DEFAULT_GROUP_ID);
            }
        }
    }
    
    /**
     * 查找指定行的书签项
     */
    private BookmarkItem findBookmarkItemAtRow(int rowIndex, List<IngredientListSlot> slots) {
        BookmarkManager manager = BookmarkManager.getInstance();
        
        int startSlot = rowIndex * columns;
        int endSlot = startSlot + columns;
        
        for (int i = startSlot; i < endSlot && i < slots.size(); i++) {
            IngredientListSlot slot = slots.get(i);
            if (slot.getOptionalElement().isPresent()) {
                var element = slot.getOptionalElement().get();
                var bookmarkOpt = element.getBookmark();
                if (bookmarkOpt.isPresent()) {
                    BookmarkItem item = manager.findBookmarkItem(bookmarkOpt.get());
                    if (item != null) {
                        return item;
                    }
                }
            }
        }
        
        return null;
    }
    
    /**
     * 将行加入组（左键拖动）
     * NEI风格：只改变groupId，不移动位置，不改变类型
     * 每个原来的组头保持为组头
     */
    private void includeRowsInGroup(int minRow, int maxRow, List<IngredientListSlot> slots) {
        BookmarkManager manager = BookmarkManager.getInstance();
        
        // 收集所有受影响行的所有书签项（不只是第一个）
        List<BookmarkItem> allItemsInRange = new ArrayList<>();
        Set<Integer> existingGroupIds = new LinkedHashSet<>();  // 保持顺序
        
        for (int row = minRow; row <= maxRow; row++) {
            List<BookmarkItem> rowItems = findAllBookmarkItemsAtRow(row, slots);
            allItemsInRange.addAll(rowItems);
            for (BookmarkItem item : rowItems) {
                if (item.getGroupId() != BookmarkManager.DEFAULT_GROUP_ID) {
                    existingGroupIds.add(item.getGroupId());
                }
            }
        }
        
        if (allItemsInRange.isEmpty()) {
            return;
        }
        
        // 确定目标组ID：
        // 1. 如果startGroupId是Integer.MIN_VALUE，创建新组
        // 2. 否则使用第一个已存在的非默认组
        // 3. 如果都是默认组，创建新组
        int targetGroupId;
        if (startGroupId == Integer.MIN_VALUE) {
            targetGroupId = manager.createGroup();
        } else if (!existingGroupIds.isEmpty()) {
            targetGroupId = existingGroupIds.iterator().next();
        } else {
            targetGroupId = manager.createGroup();
        }
        
        // 收集所有需要合并的组的所有项（包括不在选择范围内的）
        Set<Integer> groupsToMerge = new HashSet<>(existingGroupIds);
        List<BookmarkItem> allItemsToMerge = new ArrayList<>();
        
        for (int groupId : groupsToMerge) {
            if (groupId != targetGroupId) {
                allItemsToMerge.addAll(manager.getGroupItems(groupId));
            }
        }
        
        // 也添加选择范围内的默认组项
        for (BookmarkItem item : allItemsInRange) {
            if (item.getGroupId() == BookmarkManager.DEFAULT_GROUP_ID) {
                allItemsToMerge.add(item);
            }
        }
        
        // 只改变groupId，不改变位置和类型
        for (BookmarkItem item : allItemsToMerge) {
            int oldGroupId = item.getGroupId();
            if (oldGroupId != targetGroupId) {
                item.setGroupId(targetGroupId);
                // 不改变类型！保持原来的RESULT/INGREDIENT/ITEM类型
            }
        }
        
        // 清理空组（只删除组定义，不删除项）
        for (int groupId : groupsToMerge) {
            if (groupId != targetGroupId && groupId != BookmarkManager.DEFAULT_GROUP_ID) {
                manager.removeGroupOnly(groupId);
            }
        }
        
        manager.save();
    }
    
    /**
     * 查找指定行的所有书签项（不只是第一个）
     */
    private List<BookmarkItem> findAllBookmarkItemsAtRow(int rowIndex, List<IngredientListSlot> slots) {
        List<BookmarkItem> result = new ArrayList<>();
        BookmarkManager manager = BookmarkManager.getInstance();
        
        int startSlot = rowIndex * columns;
        int endSlot = startSlot + columns;
        
        for (int i = startSlot; i < endSlot && i < slots.size(); i++) {
            IngredientListSlot slot = slots.get(i);
            if (slot.getOptionalElement().isPresent()) {
                var element = slot.getOptionalElement().get();
                var bookmarkOpt = element.getBookmark();
                if (bookmarkOpt.isPresent()) {
                    BookmarkItem item = manager.findBookmarkItem(bookmarkOpt.get());
                    if (item != null && !result.contains(item)) {
                        result.add(item);
                    }
                }
            }
        }
        
        return result;
    }
    
    /**
     * 将行从组中排除（右键拖动）
     * NEI风格：只改变groupId到默认组，不改变类型
     */
    private void excludeRowsFromGroup(int minRow, int maxRow, List<IngredientListSlot> slots) {
        BookmarkManager manager = BookmarkManager.getInstance();
        Set<Integer> affectedGroupIds = new HashSet<>();
        
        for (int row = minRow; row <= maxRow; row++) {
            List<BookmarkItem> rowItems = findAllBookmarkItemsAtRow(row, slots);
            for (BookmarkItem item : rowItems) {
                if (item.getGroupId() != BookmarkManager.DEFAULT_GROUP_ID) {
                    affectedGroupIds.add(item.getGroupId());
                    item.setGroupId(BookmarkManager.DEFAULT_GROUP_ID);
                    // 不改变类型！保持原来的RESULT/INGREDIENT/ITEM类型
                }
            }
        }
        
        // 清理空组
        for (int groupId : affectedGroupIds) {
            List<BookmarkItem> remaining = manager.getGroupItems(groupId);
            if (remaining.isEmpty()) {
                manager.removeGroupOnly(groupId);
            }
        }
        
        manager.save();
    }
    
    /**
     * 清理空组
     */
    private void cleanupEmptyGroup(BookmarkManager manager, int groupId) {
        List<BookmarkItem> remaining = manager.getGroupItems(groupId);
        if (remaining.isEmpty()) {
            manager.removeGroupOnly(groupId);
        }
    }
    
    /**
     * 渲染组面板
     */
    public void render(GuiGraphics guiGraphics, int mouseX, int mouseY, List<IngredientListSlot> slots) {
        if (!BookmarkLayoutManager.getInstance().isVerticalMode()) {
            return;
        }
        
        BookmarkManager manager = BookmarkManager.getInstance();
        
        // 构建行到组ID的映射
        Map<Integer, Integer> currentRowToGroupId = new HashMap<>();
        for (int row = 0; row < rows; row++) {
            BookmarkItem item = findBookmarkItemAtRow(row, slots);
            if (item != null) {
                currentRowToGroupId.put(row, item.getGroupId());
            }
        }
        
        // 如果正在拖动，应用预览效果
        if (isDragging) {
            currentRowToGroupId = applyDragPreview(currentRowToGroupId);
        }
        
        // 渲染组括号
        renderGroupBrackets(guiGraphics, currentRowToGroupId, manager);
        
        // 渲染拖动高亮
        if (isDragging) {
            renderDragHighlight(guiGraphics);
        } else if (isInGroupPanelArea(mouseX, mouseY)) {
            // 渲染悬停高亮
            int rowIndex = getRowIndexAt(mouseY);
            if (rowIndex >= 0) {
                renderRowHighlight(guiGraphics, rowIndex, HIGHLIGHT_COLOR);
            }
        }
    }
    
    /**
     * 应用拖动预览效果
     */
    private Map<Integer, Integer> applyDragPreview(Map<Integer, Integer> original) {
        Map<Integer, Integer> preview = new HashMap<>(original);
        
        int minRow = Math.min(startRowIndex, endRowIndex);
        int maxRow = Math.max(startRowIndex, endRowIndex);
        
        for (int row = minRow; row <= maxRow; row++) {
            if (preview.containsKey(row)) {
                preview.put(row, startGroupId);
            }
        }
        
        return preview;
    }
    
    /**
     * 渲染组括号
     * NEI风格：每个RESULT开头的子组画单独的括号
     * 同一个groupId的子组用相同颜色，表示它们是逻辑同组
     */
    private void renderGroupBrackets(GuiGraphics guiGraphics, Map<Integer, Integer> rowToGroupId, BookmarkManager manager) {
        // 找出每个子组的行范围（以RESULT开头的连续行）
        List<int[]> subGroupRanges = new ArrayList<>();  // [startRow, endRow, groupId]
        
        int currentSubGroupStart = -1;
        int currentGroupId = BookmarkManager.DEFAULT_GROUP_ID;
        
        for (int row = 0; row < rows; row++) {
            Integer groupId = rowToGroupId.get(row);
            
            if (groupId != null && groupId != BookmarkManager.DEFAULT_GROUP_ID) {
                if (currentSubGroupStart == -1) {
                    // 开始新的子组
                    currentSubGroupStart = row;
                    currentGroupId = groupId;
                } else if (groupId.equals(currentGroupId)) {
                    // 继续当前子组（同一个groupId的连续行）
                } else {
                    // 不同的groupId，结束当前子组，开始新的
                    subGroupRanges.add(new int[]{currentSubGroupStart, row - 1, currentGroupId});
                    currentSubGroupStart = row;
                    currentGroupId = groupId;
                }
            } else {
                // 默认组或空行
                if (currentSubGroupStart != -1) {
                    // 结束当前子组
                    subGroupRanges.add(new int[]{currentSubGroupStart, row - 1, currentGroupId});
                    currentSubGroupStart = -1;
                    currentGroupId = BookmarkManager.DEFAULT_GROUP_ID;
                }
            }
        }
        
        // 处理最后一个子组
        if (currentSubGroupStart != -1) {
            subGroupRanges.add(new int[]{currentSubGroupStart, rows - 1, currentGroupId});
        }
        
        // 绘制每个子组的括号
        for (int[] range : subGroupRanges) {
            int startRow = range[0];
            int endRow = range[1];
            int groupId = range[2];
            
            // 确定颜色：crafting chain模式为绿色，否则为灰色
            BookmarkGroup group = manager.getGroup(groupId);
            int color;
            if (groupId == Integer.MIN_VALUE) {
                // 预览中的新组
                color = DRAG_COLOR;
            } else if (group != null && group.isCraftingChainEnabled()) {
                color = GROUP_CHAIN_COLOR;  // 绿色
            } else {
                color = GROUP_NONE_COLOR;   // 灰色
            }
            
            drawGroupBracket(guiGraphics, startRow, endRow, color);
        }
    }
    
    /**
     * 绘制组括号 [ 形状
     */
    private void drawGroupBracket(GuiGraphics guiGraphics, int startRow, int endRow, int color) {
        int halfWidth = GROUP_PANEL_WIDTH / 2;
        int heightPadding = slotHeight / 4;
        int leftPosition = gridX - halfWidth - 1;
        
        int top = gridY + startRow * slotHeight;
        int bottom = gridY + (endRow + 1) * slotHeight;
        
        // 上横线
        guiGraphics.fill(leftPosition, top + heightPadding, leftPosition + halfWidth, top + heightPadding + 1, color);
        
        // 竖线
        guiGraphics.fill(leftPosition, top + heightPadding, leftPosition + 1, bottom - heightPadding, color);
        
        // 下横线
        guiGraphics.fill(leftPosition, bottom - heightPadding - 1, leftPosition + halfWidth, bottom - heightPadding, color);
    }
    
    /**
     * 渲染拖动高亮
     */
    private void renderDragHighlight(GuiGraphics guiGraphics) {
        int minRow = Math.min(startRowIndex, endRowIndex);
        int maxRow = Math.max(startRowIndex, endRowIndex);
        
        int color = (dragButton == 0) ? DRAG_COLOR : 0x60FF4444;  // 左键绿色，右键红色
        
        for (int row = minRow; row <= maxRow; row++) {
            renderRowHighlight(guiGraphics, row, color);
        }
    }
    
    /**
     * 渲染行高亮
     */
    private void renderRowHighlight(GuiGraphics guiGraphics, int rowIndex, int color) {
        int x = gridX - GROUP_PANEL_WIDTH;
        int y = gridY + rowIndex * slotHeight;
        guiGraphics.fill(x, y, x + GROUP_PANEL_WIDTH, y + slotHeight, color);
    }
    
    public boolean isDragging() {
        return isDragging;
    }
    
    public int getDragButton() {
        return dragButton;
    }
}
