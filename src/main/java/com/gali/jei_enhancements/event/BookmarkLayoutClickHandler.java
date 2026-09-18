package com.gali.jei_enhancements.event;

import com.gali.jei_enhancements.bookmark.BookmarkGroup;
import com.gali.jei_enhancements.bookmark.BookmarkItem;
import com.gali.jei_enhancements.bookmark.BookmarkLayoutManager;
import com.gali.jei_enhancements.bookmark.BookmarkManager;
import com.gali.jei_enhancements.bookmark.GroupingDragHandler;
import com.gali.jei_enhancements.bookmark.IBookmarkPageAccessor;
import com.gali.jei_enhancements.bookmark.IPageManagementAccessor;
import com.gali.jei_enhancements.mixin.accessor.BookmarkOverlayAccessor;
import mezz.jei.api.runtime.IBookmarkOverlay;
import mezz.jei.api.runtime.IJeiRuntime;
import mezz.jei.common.util.ImmutableRect2i;
import mezz.jei.gui.bookmarks.IBookmark;
import mezz.jei.gui.overlay.ingredients.IngredientGridWithNavigation;
import mezz.jei.gui.overlay.ingredients.IngredientListSlot;
import mezz.jei.gui.overlay.bookmarks.BookmarkOverlay;
import mezz.jei.gui.overlay.elements.IElement;
import net.minecraft.client.gui.screens.Screen;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import org.jetbrains.annotations.Nullable;

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
    private static IJeiRuntime jeiRuntime;

    public static void setJeiRuntime(@Nullable IJeiRuntime runtime) {
        jeiRuntime = runtime;
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public void onMouseClicked(ScreenEvent.MouseButtonPressed.Pre event) {
        BookmarkOverlay overlay = getActiveOverlay();
        if (overlay == null) {
            return;
        }

        int button = event.getButton();
        double mouseX = event.getMouseX();
        double mouseY = event.getMouseY();

        IPageManagementAccessor pageButtons = (IPageManagementAccessor) overlay;
        if (button == 0 && pageButtons.jeiEnhancements$isPageManagementVisible()) {
            BookmarkManager manager = BookmarkManager.getInstance();
            if (pageButtons.jeiEnhancements$getAddPageArea().contains(mouseX, mouseY)) {
                manager.addPageAfterCurrent();
                manager.save();
                forceRefreshBookmarks(overlay);
                event.setCanceled(true);
                return;
            }
            if (pageButtons.jeiEnhancements$getRemovePageArea().contains(mouseX, mouseY)) {
                int removedPage = manager.removeCurrentPage();
                if (removedPage >= 0) {
                    IBookmarkPageAccessor pageAccessor =
                            (IBookmarkPageAccessor) ((BookmarkOverlayAccessor) overlay).jeiEnhancements$getBookmarkList();
                    pageAccessor.jeiEnhancements$clearPage(removedPage);
                    manager.save();
                    forceRefreshBookmarks(overlay);
                }
                event.setCanceled(true);
                return;
            }
        }

        // 检查是否在组面板区域
        if (BookmarkLayoutManager.getInstance().isVerticalMode()) {
            GroupingDragHandler dragHandler = GroupingDragHandler.getInstance();
            List<IngredientListSlot> slots = getSlots(overlay);
            // 左键或右键拖动开始（右键单击的处理移到释放时）
            if ((button == 0 || button == 1)
                    && dragHandler.startDrag((int) mouseX, (int) mouseY, button, slots)) {
                event.setCanceled(true);
                return;
            }
        }

        // 只处理左键点击
        if (button != 0) {
            return;
        }

        // NEI风格：Alt+点击切换分组展开/折叠
        if (Screen.hasAltDown() && handleGroupToggle(overlay, mouseX, mouseY)) {
            event.setCanceled(true);
            return;
        }

        // 检查是否点击在页码区域
        if (pageButtons.jeiEnhancements$isPageManagementVisible()
                && isClickOnPageArea(overlay, mouseX, mouseY)) {
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
        if (getActiveOverlay() == null) {
            if (dragHandler.isDragging()) {
                dragHandler.cancelDrag();
            }
            return;
        }
        if (dragHandler.isDragging()) {
            dragHandler.updateDrag((int) event.getMouseY());
            // 不取消事件，让渲染继续
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public void onMouseReleased(ScreenEvent.MouseButtonReleased.Pre event) {
        int button = event.getButton();
        // 只处理左键和右键
        if ((button != 0 && button != 1)
                || !BookmarkLayoutManager.getInstance().isVerticalMode()) {
            return;
        }

        BookmarkOverlay overlay = getActiveOverlay();
        GroupingDragHandler dragHandler = GroupingDragHandler.getInstance();
        if (!dragHandler.isDragging() || dragHandler.getDragButton() != button) {
            return;
        }

        if (overlay != null) {
            List<IngredientListSlot> slots = getSlots(overlay);
            // 判断是单击还是拖动（如果起始行和结束行相同，则是单击）
            if (button == 1 && dragHandler.isSingleClick()) {
                // 右键单击：切换crafting chain模式
                dragHandler.cancelDrag();
                if (dragHandler.handleClick((int) event.getMouseX(), (int) event.getMouseY(), button, slots)) {
                    // 刷新显示
                    forceRefreshBookmarks(overlay);
                }
            } else {
                // 拖动操作
                dragHandler.endDrag(slots);
                // 保存并刷新
                BookmarkManager.getInstance().save();
                forceRefreshBookmarks(overlay);
            }
        } else {
            dragHandler.cancelDrag();
        }
        event.setCanceled(true);
    }

    @Nullable
    private static BookmarkOverlay getActiveOverlay() {
        if (jeiRuntime == null) {
            return null;
        }

        IBookmarkOverlay bookmarkOverlay = jeiRuntime.getBookmarkOverlay();
        if (!(bookmarkOverlay instanceof BookmarkOverlay overlay)
                || !(overlay instanceof IPageManagementAccessor pageManagement)
                || !pageManagement.jeiEnhancements$isPageManagementVisible()) {
            return null;
        }
        return overlay;
    }

    /**
     * 获取书签槽位列表
     */
    private List<IngredientListSlot> getSlots(BookmarkOverlay overlay) {
        IngredientGridWithNavigation contents = ((BookmarkOverlayAccessor) overlay).jeiEnhancements$getContents();
        return contents.getSlots().collect(Collectors.toList());
    }

    /**
     * 处理分组展开/折叠切换
     */
    private boolean handleGroupToggle(BookmarkOverlay overlay, double mouseX, double mouseY) {
        IngredientGridWithNavigation contents = ((BookmarkOverlayAccessor) overlay).jeiEnhancements$getContents();
        Optional<IngredientListSlot> slotOpt = contents.getSlots()
                .filter(slot -> {
                    // 查找鼠标下的槽位
                    var area = slot.getRenderArea();
                    return mouseX >= area.x() && mouseX < area.x() + area.width()
                            && mouseY >= area.y() && mouseY < area.y() + area.height();
                })
                .findFirst();

        if (slotOpt.isEmpty()) {
            return false;
        }

        IElement<?> element = slotOpt.get().getElement();
        if (element == null) {
            return false;
        }

        Optional<IBookmark> bookmarkOpt = element.getBookmark();
        if (bookmarkOpt.isEmpty()) {
            return false;
        }

        BookmarkManager manager = BookmarkManager.getInstance();
        BookmarkItem item = manager.findBookmarkItem(bookmarkOpt.get());
        if (item == null) {
            return false;
        }

        BookmarkGroup group = manager.getGroup(item.getGroupId());
        if (group == null || manager.getGroupItems(item.getGroupId()).size() <= 1) {
            return false;
        }

        // 切换展开/折叠状态
        group.toggleExpanded();
        manager.save();
        // 刷新显示
        forceRefreshBookmarks(overlay);
        return true;
    }

    /**
     * 检查是否点击在页码区域（两个按钮之间的区域）
     */
    private boolean isClickOnPageArea(BookmarkOverlay overlay, double mouseX, double mouseY) {
        IngredientGridWithNavigation contents = ((BookmarkOverlayAccessor) overlay).jeiEnhancements$getContents();
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
        return pageAreaWidth > 0
                && mouseX >= pageAreaX && mouseX < pageAreaX + pageAreaWidth
                && mouseY >= pageAreaY && mouseY < pageAreaY + pageAreaHeight;
    }

    /**
     * 强制刷新书签显示
     */
    private void forceRefreshBookmarks(BookmarkOverlay overlay) {
        // 通过Accessor调用updateLayout来刷新
        ((BookmarkOverlayAccessor) overlay).jeiEnhancements$getContents().updateLayout(false);
    }
}
