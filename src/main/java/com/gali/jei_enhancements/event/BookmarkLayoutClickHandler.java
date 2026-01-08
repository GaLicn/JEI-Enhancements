package com.gali.jei_enhancements.event;

import com.gali.jei_enhancements.JEIEnhancements;
import com.gali.jei_enhancements.bookmark.BookmarkGroup;
import com.gali.jei_enhancements.bookmark.BookmarkItem;
import com.gali.jei_enhancements.bookmark.BookmarkLayoutManager;
import com.gali.jei_enhancements.bookmark.BookmarkManager;
import com.gali.jei_enhancements.bookmark.GroupingDragHandler;
import mezz.jei.api.runtime.IBookmarkOverlay;
import mezz.jei.api.runtime.IJeiRuntime;
import mezz.jei.common.util.ImmutableRect2i;
import mezz.jei.core.collect.ListMultiMap;
import mezz.jei.gui.bookmarks.IBookmark;
import mezz.jei.gui.overlay.IngredientGrid;
import mezz.jei.gui.overlay.IngredientGridWithNavigation;
import mezz.jei.gui.overlay.IngredientListRenderer;
import mezz.jei.gui.overlay.IngredientListSlot;
import mezz.jei.gui.overlay.bookmarks.BookmarkOverlay;
import mezz.jei.gui.overlay.elements.IElement;
import net.minecraft.client.gui.screens.Screen;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * 处理书签页码区域的点击事件
 * - 点击页码区域切换水平/纵向排列
 * - Alt+点击分组物品切换展开/折叠
 * - 在组面板区域拖动合并组
 */
public class BookmarkLayoutClickHandler {

    @Nullable
    private static IJeiRuntime jeiRuntime = null;

    public static void setJeiRuntime(@Nullable IJeiRuntime runtime) {
        jeiRuntime = runtime;
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public void onMouseClicked(ScreenEvent.MouseButtonPressed.Pre event) {
        if (jeiRuntime == null) {
            return;
        }

        int button = event.getButton();
        double mouseX = event.getMouseX();
        double mouseY = event.getMouseY();

        IBookmarkOverlay bookmarkOverlay = jeiRuntime.getBookmarkOverlay();
        
        if (!(bookmarkOverlay instanceof BookmarkOverlay overlay)) {
            return;
        }
        
        // 检查是否在组面板区域
        if (BookmarkLayoutManager.getInstance().isVerticalMode()) {
            GroupingDragHandler dragHandler = GroupingDragHandler.getInstance();
            List<IngredientListSlot> slots = getSlots(overlay);
            
            // 右键单击：切换crafting chain模式
            if (button == 1 && dragHandler.handleClick((int) mouseX, (int) mouseY, button, slots)) {
                forceRefreshBookmarks(overlay);
                event.setCanceled(true);
                return;
            }
            
            // 左键或右键拖动开始
            if ((button == 0 || button == 1) && dragHandler.startDrag((int) mouseX, (int) mouseY, button, slots)) {
                event.setCanceled(true);
                return;
            }
        }

        // 只处理左键点击
        if (button != 0) {
            return;
        }

        // NEI风格：Alt+点击切换分组展开/折叠
        if (Screen.hasAltDown()) {
            if (handleGroupToggle(overlay, mouseX, mouseY)) {
                event.setCanceled(true);
                return;
            }
        }

        // 检查是否点击在页码区域
        if (isClickOnPageArea(overlay, mouseX, mouseY)) {
            // 切换布局模式
            BookmarkLayoutManager.getInstance().toggleMode();
            BookmarkLayoutManager.getInstance().save();
            
            // 强制刷新书签显示
            forceRefreshBookmarks(overlay);
            
            // 取消事件
            event.setCanceled(true);
        }
    }
    
    @SubscribeEvent(priority = EventPriority.HIGH)
    public void onMouseDragged(ScreenEvent.MouseDragged.Pre event) {
        if (!BookmarkLayoutManager.getInstance().isVerticalMode()) {
            return;
        }
        
        GroupingDragHandler dragHandler = GroupingDragHandler.getInstance();
        if (dragHandler.isDragging()) {
            dragHandler.updateDrag((int) event.getMouseY());
            // 不取消事件，让渲染继续
        }
    }
    
    @SubscribeEvent(priority = EventPriority.HIGH)
    public void onMouseReleased(ScreenEvent.MouseButtonReleased.Pre event) {
        int button = event.getButton();
        
        // 只处理左键和右键
        if (button != 0 && button != 1) {
            return;
        }
        
        if (!BookmarkLayoutManager.getInstance().isVerticalMode()) {
            return;
        }
        
        GroupingDragHandler dragHandler = GroupingDragHandler.getInstance();
        if (dragHandler.isDragging() && dragHandler.getDragButton() == button) {
            if (jeiRuntime != null) {
                IBookmarkOverlay bookmarkOverlay = jeiRuntime.getBookmarkOverlay();
                if (bookmarkOverlay instanceof BookmarkOverlay overlay) {
                    List<IngredientListSlot> slots = getSlots(overlay);
                    dragHandler.endDrag(slots);
                    
                    // 保存并刷新
                    BookmarkManager.getInstance().save();
                    forceRefreshBookmarks(overlay);
                }
            }
            event.setCanceled(true);
        }
    }
    
    /**
     * 获取书签槽位列表
     */
    private List<IngredientListSlot> getSlots(BookmarkOverlay overlay) {
        try {
            Field contentsField = BookmarkOverlay.class.getDeclaredField("contents");
            contentsField.setAccessible(true);
            IngredientGridWithNavigation contents = (IngredientGridWithNavigation) contentsField.get(overlay);
            return contents.getSlots().collect(Collectors.toList());
        } catch (Exception e) {
            return List.of();
        }
    }
    
    /**
     * 处理分组展开/折叠切换
     */
    private boolean handleGroupToggle(BookmarkOverlay overlay, double mouseX, double mouseY) {
        try {
            Field contentsField = BookmarkOverlay.class.getDeclaredField("contents");
            contentsField.setAccessible(true);
            IngredientGridWithNavigation contents = (IngredientGridWithNavigation) contentsField.get(overlay);
            
            // 查找鼠标下的槽位
            Optional<IngredientListSlot> slotOpt = contents.getSlots()
                .filter(slot -> {
                    var area = slot.getRenderArea();
                    return mouseX >= area.x() && mouseX < area.x() + area.width() &&
                           mouseY >= area.y() && mouseY < area.y() + area.height();
                })
                .findFirst();
            
            if (slotOpt.isEmpty()) {
                return false;
            }
            
            IngredientListSlot slot = slotOpt.get();
            IElement<?> element = slot.getElement();
            if (element == null) {
                return false;
            }
            
            Optional<IBookmark> bookmarkOpt = element.getBookmark();
            if (bookmarkOpt.isEmpty()) {
                return false;
            }
            
            BookmarkManager manager = BookmarkManager.getInstance();
            BookmarkItem item = manager.findBookmarkItem(bookmarkOpt.get());
            
            if (item != null) {
                BookmarkGroup group = manager.getGroup(item.getGroupId());
                int groupSize = manager.getGroupItems(item.getGroupId()).size();
                
                if (group != null && groupSize > 1) {
                    // 切换展开/折叠状态
                    group.toggleExpanded();
                    manager.save();
                    
                    // 刷新显示
                    forceRefreshBookmarks(overlay);
                    
                    return true;
                }
            }
            
        } catch (Exception e) {
            JEIEnhancements.LOGGER.error("Error handling group toggle", e);
        }
        
        return false;
    }

    /**
     * 检查是否点击在页码区域（两个按钮之间的区域）
     */
    private boolean isClickOnPageArea(BookmarkOverlay overlay, double mouseX, double mouseY) {
        try {
            // 通过反射获取contents字段
            Field contentsField = BookmarkOverlay.class.getDeclaredField("contents");
            contentsField.setAccessible(true);
            IngredientGridWithNavigation contents = (IngredientGridWithNavigation) contentsField.get(overlay);
            
            // 获取前后按钮区域
            ImmutableRect2i nextButtonArea = contents.getNextPageButtonArea();
            ImmutableRect2i backButtonArea = contents.getBackButtonArea();
            
            if (nextButtonArea.isEmpty() || backButtonArea.isEmpty()) {
                return false;
            }
            
            // 计算页码文字区域（两个按钮之间）
            int pageAreaX = backButtonArea.getX() + backButtonArea.getWidth();
            int pageAreaY = backButtonArea.getY();
            int pageAreaWidth = nextButtonArea.getX() - pageAreaX;
            int pageAreaHeight = backButtonArea.getHeight();
            
            // 检查点击是否在页码区域内
            return mouseX >= pageAreaX && mouseX < pageAreaX + pageAreaWidth &&
                   mouseY >= pageAreaY && mouseY < pageAreaY + pageAreaHeight;
            
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 强制刷新书签显示
     */
    private void forceRefreshBookmarks(BookmarkOverlay overlay) {
        try {
            // 通过反射获取contents
            Field contentsField = BookmarkOverlay.class.getDeclaredField("contents");
            contentsField.setAccessible(true);
            IngredientGridWithNavigation contents = (IngredientGridWithNavigation) contentsField.get(overlay);
            
            // 获取ingredientGrid
            Field ingredientGridField = IngredientGridWithNavigation.class.getDeclaredField("ingredientGrid");
            ingredientGridField.setAccessible(true);
            IngredientGrid ingredientGrid = (IngredientGrid) ingredientGridField.get(contents);
            
            // 获取ingredientListRenderer
            Field rendererField = IngredientGrid.class.getDeclaredField("ingredientListRenderer");
            rendererField.setAccessible(true);
            IngredientListRenderer renderer = (IngredientListRenderer) rendererField.get(ingredientGrid);
            
            // 清除渲染缓存
            Field renderElementsField = IngredientListRenderer.class.getDeclaredField("renderElementsByType");
            renderElementsField.setAccessible(true);
            ListMultiMap<?, ?> renderElements = (ListMultiMap<?, ?>) renderElementsField.get(renderer);
            renderElements.clear();
            
            Field renderOverlaysField = IngredientListRenderer.class.getDeclaredField("renderOverlays");
            renderOverlaysField.setAccessible(true);
            List<?> renderOverlays = (List<?>) renderOverlaysField.get(renderer);
            renderOverlays.clear();
            
            // 调用updateLayout来刷新
            contents.updateLayout(false);
            
        } catch (Exception e) {
            // 忽略错误
        }
    }
}
