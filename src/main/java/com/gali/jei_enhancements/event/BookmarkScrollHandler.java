package com.gali.jei_enhancements.event;

import com.gali.jei_enhancements.bookmark.BookmarkItem;
import com.gali.jei_enhancements.bookmark.BookmarkManager;
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
 * Ctrl+滚轮调整书签数量
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
        BookmarkManager manager = BookmarkManager.getInstance();
        
        // 查找对应的BookmarkItem
        BookmarkItem item = manager.findBookmarkItem(bookmark);
        if (item == null) {
            return;
        }

        // 计算调整量
        long shift = (long) Math.signum(scrollDelta);

        // Ctrl+Alt: 以更大的步进调整 (10)
        if (Screen.hasAltDown()) {
            shift *= 64;
        }

        // 调整数量
        manager.shiftItemAmount(item, shift);
        
        // 保存数据
        manager.save();

        // 取消事件，防止JEI的默认滚轮行为
        event.setCanceled(true);
    }
}
