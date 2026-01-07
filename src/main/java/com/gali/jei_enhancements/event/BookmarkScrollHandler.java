package com.gali.jei_enhancements.event;

import com.gali.jei_enhancements.bookmark.BookmarkQuantityManager;
import mezz.jei.api.runtime.IJeiRuntime;
import mezz.jei.api.runtime.IBookmarkOverlay;
import mezz.jei.gui.bookmarks.IBookmark;
import mezz.jei.gui.input.IClickableIngredientInternal;
import mezz.jei.gui.overlay.bookmarks.BookmarkOverlay;
import mezz.jei.gui.overlay.elements.IElement;
import net.minecraft.client.gui.screens.Screen;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;
import java.util.stream.Stream;

/**
 * 处理书签区域的滚轮事件
 * 实现NEI风格的Ctrl+滚轮调整书签数量的功能
 */
public class BookmarkScrollHandler {

    @Nullable
    private static IJeiRuntime jeiRuntime = null;

    public static void setJeiRuntime(@Nullable IJeiRuntime runtime) {
        jeiRuntime = runtime;
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public void onMouseScrolled(ScreenEvent.MouseScrolled.Pre event) {
        // 检查是否按住Ctrl键
        if (!Screen.hasControlDown()) {
            return;
        }

        if (jeiRuntime == null) {
            return;
        }

        double mouseX = event.getMouseX();
        double mouseY = event.getMouseY();
        double scrollDelta = event.getScrollDeltaY();

        IBookmarkOverlay bookmarkOverlay = jeiRuntime.getBookmarkOverlay();
        
        // 检查是否是BookmarkOverlay实例
        if (!(bookmarkOverlay instanceof BookmarkOverlay overlay)) {
            return;
        }

        // 检查鼠标是否在书签区域
        if (!overlay.isMouseOver(mouseX, mouseY)) {
            return;
        }

        // 获取鼠标下的书签
        Stream<IClickableIngredientInternal<?>> ingredientStream = overlay.getIngredientUnderMouse(mouseX, mouseY);
        Optional<IClickableIngredientInternal<?>> clickedIngredient = ingredientStream.findFirst();

        if (clickedIngredient.isEmpty()) {
            return;
        }

        IElement<?> element = clickedIngredient.get().getElement();
        Optional<IBookmark> bookmarkOpt = element.getBookmark();

        if (bookmarkOpt.isEmpty()) {
            return;
        }

        IBookmark bookmark = bookmarkOpt.get();

        // 计算调整量
        int delta = (int) Math.signum(scrollDelta);

        // 如果同时按住Shift，则以更大的步进调整 (64)
        if (Screen.hasShiftDown()) {
            delta *= 64;
        }
        // 如果同时按住Alt，则以中等步进调整 (10)
        else if (Screen.hasAltDown()) {
            delta *= 10;
        }

        // 调整数量
        BookmarkQuantityManager.getInstance().adjustQuantity(bookmark, delta);
        
        // 保存数据
        BookmarkQuantityManager.getInstance().save();

        // 取消事件，防止JEI的默认滚轮行为
        event.setCanceled(true);
    }
}
