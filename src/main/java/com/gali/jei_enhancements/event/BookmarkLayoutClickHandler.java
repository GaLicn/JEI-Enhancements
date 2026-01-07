package com.gali.jei_enhancements.event;

import com.gali.jei_enhancements.bookmark.BookmarkLayoutManager;
import mezz.jei.api.runtime.IBookmarkOverlay;
import mezz.jei.api.runtime.IJeiRuntime;
import mezz.jei.common.util.ImmutableRect2i;
import mezz.jei.core.collect.ListMultiMap;
import mezz.jei.gui.overlay.IngredientGrid;
import mezz.jei.gui.overlay.IngredientGridWithNavigation;
import mezz.jei.gui.overlay.IngredientListRenderer;
import mezz.jei.gui.overlay.bookmarks.BookmarkOverlay;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import org.jetbrains.annotations.Nullable;

import java.lang.reflect.Field;
import java.util.List;

/**
 * 处理书签页码区域的点击事件
 * 点击页码区域切换水平/纵向排列
 */
public class BookmarkLayoutClickHandler {

    @Nullable
    private static IJeiRuntime jeiRuntime = null;

    public static void setJeiRuntime(@Nullable IJeiRuntime runtime) {
        jeiRuntime = runtime;
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public void onMouseClicked(ScreenEvent.MouseButtonPressed.Pre event) {
        // 只处理左键点击
        if (event.getButton() != 0) {
            return;
        }

        if (jeiRuntime == null) {
            return;
        }

        double mouseX = event.getMouseX();
        double mouseY = event.getMouseY();

        IBookmarkOverlay bookmarkOverlay = jeiRuntime.getBookmarkOverlay();
        
        if (!(bookmarkOverlay instanceof BookmarkOverlay overlay)) {
            return;
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
