package com.gali.jei_enhancements.mixin;

import com.gali.jei_enhancements.bookmark.BookmarkLayoutManager;
import com.gali.jei_enhancements.bookmark.BookmarkManager;
import com.gali.jei_enhancements.bookmark.BookmarkQuantityRenderer;
import com.gali.jei_enhancements.bookmark.GroupingDragHandler;
import com.gali.jei_enhancements.bookmark.IPageManagementAccessor;
import mezz.jei.api.gui.handlers.IGuiProperties;
import mezz.jei.common.Internal;
import mezz.jei.common.gui.elements.DrawableNineSliceTexture;
import mezz.jei.common.gui.textures.Textures;
import mezz.jei.common.util.ImmutableRect2i;
import mezz.jei.gui.bookmarks.BookmarkList;
import mezz.jei.gui.overlay.ingredients.IngredientGridWithNavigation;
import mezz.jei.gui.overlay.ingredients.IngredientListSlot;
import mezz.jei.gui.overlay.ScreenPropertiesCache;
import mezz.jei.gui.overlay.bookmarks.BookmarkOverlay;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.List;
import java.util.stream.Collectors;

@Mixin(value = BookmarkOverlay.class, remap = false)
public abstract class BookmarkOverlayMixin implements IPageManagementAccessor {

    @Shadow @Final private IngredientGridWithNavigation contents;
    @Shadow @Final private BookmarkList bookmarkList;
    @Shadow @Final private ScreenPropertiesCache screenPropertiesCache;

    @Unique
    private ImmutableRect2i jei_enhancements$addPageArea = ImmutableRect2i.EMPTY;

    @Unique
    private ImmutableRect2i jei_enhancements$removePageArea = ImmutableRect2i.EMPTY;
    
    @Shadow public abstract boolean isListDisplayed();

    @Inject(method = "isListDisplayed", at = @At("RETURN"), cancellable = true)
    private void jei_enhancements$showEmptyBookmarkPages(CallbackInfoReturnable<Boolean> cir) {
        if (!cir.getReturnValueZ()
                && BookmarkManager.getInstance().getPageCount() >= 1
                && jei_enhancements$computePageManagementVisible()) {
            cir.setReturnValue(true);
        }
    }

    @Inject(method = "updateBounds", at = @At("TAIL"))
    private void onUpdateBoundsTail(IGuiProperties guiProperties, CallbackInfo ci) {
        jei_enhancements$updatePageButtonAreas();
    }

    /**
     * 在绘制书签后，渲染自定义数量和组面板
     */
    @Inject(method = "drawScreen", at = @At("TAIL"))
    private void onDrawScreenTail(Minecraft minecraft, GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTicks, CallbackInfo ci) {
        jei_enhancements$updatePageButtonAreas();
        jei_enhancements$drawPageButtons(guiGraphics, mouseX, mouseY);

        if (isListDisplayed()) {
            // 更新网格信息
            jei_enhancements$updateGridInfo();
            
            // 渲染组面板（[符号和拖动效果）
            List<IngredientListSlot> slots = jei_enhancements$getSlots();
            GroupingDragHandler.getInstance().render(guiGraphics, mouseX, mouseY, slots);
            
            // 渲染自定义数量
            BookmarkQuantityRenderer.renderQuantities(guiGraphics, contents, bookmarkList);
        }
    }

    @Unique
    private void jei_enhancements$updatePageButtonAreas() {
        // 仅在当前屏幕确实显示书签列表时计算按钮区域。
        if (!jei_enhancements$computePageManagementVisible()) {
            jei_enhancements$addPageArea = ImmutableRect2i.EMPTY;
            jei_enhancements$removePageArea = ImmutableRect2i.EMPTY;
            return;
        }

        ImmutableRect2i back = contents.getBackButtonArea();
        ImmutableRect2i next = contents.getNextPageButtonArea();
        ImmutableRect2i background = contents.getBackgroundArea();

        // 优先使用JEI导航按钮之间的区域放置加减按钮。
        int navigationHeight = back.isEmpty()
                ? Math.min(20, Math.max(8, background.getHeight()))
                : back.getHeight();
        int size = Math.min(12, Math.min(background.getWidth(), background.getHeight()));
        size = Math.min(size, Math.max(1, navigationHeight - 4));

        if (!back.isEmpty() && !next.isEmpty()) {
            int buttonY = back.getY() + (back.getHeight() - size) / 2;
            int labelLeft = back.getX() + back.getWidth();
            int labelRight = next.getX();
            ImmutableRect2i remove = new ImmutableRect2i(labelLeft + 2, buttonY, size, size);
            ImmutableRect2i add = new ImmutableRect2i(labelRight - size - 2, buttonY, size, size);
            if (labelRight - labelLeft >= size * 2 + 6
                    && jei_enhancements$isInside(background, remove)
                    && jei_enhancements$isInside(background, add)) {
                jei_enhancements$removePageArea = remove;
                jei_enhancements$addPageArea = add;
                return;
            }
        }

        // 导航隐藏或空间不足时，将两个按钮限制在书签背景区域内。
        int gap = 3;
        int fallbackSize = Math.min(size, (background.getWidth() - gap) / 2);
        if (fallbackSize <= 0) {
            jei_enhancements$addPageArea = ImmutableRect2i.EMPTY;
            jei_enhancements$removePageArea = ImmutableRect2i.EMPTY;
            return;
        }
        int totalWidth = fallbackSize * 2 + gap;
        int buttonX = background.getX() + (background.getWidth() - totalWidth) / 2;
        int buttonY = Math.max(background.getY(), background.getY() + background.getHeight() - fallbackSize - 2);
        buttonY = Math.min(buttonY, background.getY() + background.getHeight() - fallbackSize);
        jei_enhancements$removePageArea = new ImmutableRect2i(buttonX, buttonY, fallbackSize, fallbackSize);
        jei_enhancements$addPageArea = new ImmutableRect2i(buttonX + fallbackSize + gap, buttonY, fallbackSize, fallbackSize);
    }

    @Unique
    private boolean jei_enhancements$computePageManagementVisible() {
        // ScreenPropertiesCache用于区分当前游戏界面和ESC等其他屏幕。
        Minecraft minecraft = Minecraft.getInstance();
        if (!screenPropertiesCache.hasValidScreen() || minecraft.screen == null) {
            return false;
        }
        return screenPropertiesCache.getGuiProperties()
                .map(properties -> properties.getScreenClass().isInstance(minecraft.screen))
                .orElse(false)
                && !contents.getBackgroundArea().isEmpty();
    }

    @Unique
    private boolean jei_enhancements$isInside(ImmutableRect2i outer, ImmutableRect2i inner) {
        return inner.getX() >= outer.getX()
                && inner.getY() >= outer.getY()
                && inner.getX() + inner.getWidth() <= outer.getX() + outer.getWidth()
                && inner.getY() + inner.getHeight() <= outer.getY() + outer.getHeight();
    }

    @Unique
    private void jei_enhancements$drawPageButtons(GuiGraphics guiGraphics, int mouseX, int mouseY) {
        if (!jei_enhancements$computePageManagementVisible() || jei_enhancements$addPageArea.isEmpty()) {
            return;
        }

        // 使用JEI原生按钮纹理，保持按钮的边框和悬停效果一致。
        Textures textures = Internal.getTextures();
        jei_enhancements$drawPageButton(guiGraphics, textures, jei_enhancements$addPageArea, mouseX, mouseY);
        jei_enhancements$drawPageButton(guiGraphics, textures, jei_enhancements$removePageArea, mouseX, mouseY);

        net.minecraft.client.gui.Font font = Minecraft.getInstance().font;
        jei_enhancements$drawPageButtonLabel(guiGraphics, font, "+", jei_enhancements$addPageArea);
        jei_enhancements$drawPageButtonLabel(guiGraphics, font, "-", jei_enhancements$removePageArea);
    }

    @Unique
    private void jei_enhancements$drawPageButton(GuiGraphics guiGraphics, Textures textures,
            ImmutableRect2i area, int mouseX, int mouseY) {
        if (area.isEmpty()) {
            return;
        }
        boolean hovered = area.contains(mouseX, mouseY);
        DrawableNineSliceTexture texture = textures.getButtonForState(false, true, hovered);
        texture.draw(guiGraphics, area.getX(), area.getY(), area.getWidth(), area.getHeight());
    }

    @Unique
    private void jei_enhancements$drawPageButtonLabel(GuiGraphics guiGraphics, net.minecraft.client.gui.Font font,
            String label, ImmutableRect2i area) {
        if (area.isEmpty()) {
            return;
        }
        int x = area.getX() + (area.getWidth() - font.width(label)) / 2;
        int y = area.getY() + (area.getHeight() - font.lineHeight) / 2;
        guiGraphics.drawString(font, label, x, y, 0xFFFFFFFF, false);
    }

    @Override
    @Unique
    public ImmutableRect2i jeiEnhancements$getAddPageArea() {
        return jei_enhancements$addPageArea;
    }

    @Override
    @Unique
    public ImmutableRect2i jeiEnhancements$getRemovePageArea() {
        return jei_enhancements$removePageArea;
    }

    @Override
    @Unique
    public boolean jeiEnhancements$isPageManagementVisible() {
        return jei_enhancements$computePageManagementVisible();
    }
    
    /**
     * 更新GroupingDragHandler的网格信息
     */
    @Unique
    private void jei_enhancements$updateGridInfo() {
        if (!BookmarkLayoutManager.getInstance().isVerticalMode()) {
            return;
        }
        
        List<IngredientListSlot> slots = jei_enhancements$getSlots();
        if (slots.isEmpty()) {
            return;
        }
        
        // 计算网格信息
        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE;
        int slotWidth = 18, slotHeight = 18;
        
        for (IngredientListSlot slot : slots) {
            var area = slot.getArea();
            minX = Math.min(minX, area.getX());
            minY = Math.min(minY, area.getY());
            maxX = Math.max(maxX, area.getX() + area.getWidth());
            maxY = Math.max(maxY, area.getY() + area.getHeight());
            slotWidth = area.getWidth();
            slotHeight = area.getHeight();
        }
        
        int columns = (maxX - minX) / slotWidth;
        int rows = (maxY - minY) / slotHeight;
        
        GroupingDragHandler.getInstance().updateGridInfo(minX, minY, slotHeight, columns, rows);
    }
    
    /**
     * 获取所有slot
     */
    @Unique
    private List<IngredientListSlot> jei_enhancements$getSlots() {
        return contents.getSlots().collect(Collectors.toList());
    }
}
