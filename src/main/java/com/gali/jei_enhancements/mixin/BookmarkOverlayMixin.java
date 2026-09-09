package com.gali.jei_enhancements.mixin;

import com.gali.jei_enhancements.bookmark.BookmarkLayoutManager;
import com.gali.jei_enhancements.bookmark.BookmarkQuantityRenderer;
import com.gali.jei_enhancements.bookmark.GroupingDragHandler;
import com.gali.jei_enhancements.bookmark.IPageManagementAccessor;
import mezz.jei.common.util.ImmutableRect2i;
import mezz.jei.common.Internal;
import mezz.jei.common.gui.elements.ScalableDrawable;
import mezz.jei.common.gui.textures.Textures;
import mezz.jei.gui.bookmarks.BookmarkList;
import mezz.jei.gui.overlay.ingredients.IngredientGrid;
import mezz.jei.gui.overlay.ingredients.IngredientGridWithNavigation;
import mezz.jei.gui.overlay.ingredients.IngredientListSlot;
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

import java.util.List;
import java.util.stream.Collectors;

@Mixin(value = BookmarkOverlay.class, remap = false)
public abstract class BookmarkOverlayMixin implements IPageManagementAccessor {

    @Shadow @Final private IngredientGridWithNavigation contents;
    @Shadow @Final private BookmarkList bookmarkList;

    @Unique private ImmutableRect2i jei_enhancements$addPageArea = ImmutableRect2i.EMPTY;
    @Unique private ImmutableRect2i jei_enhancements$removePageArea = ImmutableRect2i.EMPTY;
    
    @Shadow public abstract boolean isListDisplayed();

    /** bounds 完成后按钮区域已稳定，点击事件无需等待一次绘制。 */
    @Inject(method = "updateBounds", at = @At("TAIL"))
    private void onUpdateBoundsTail(mezz.jei.api.gui.handlers.IGuiProperties properties,
            java.util.Set<ImmutableRect2i> exclusionAreas, CallbackInfo ci) {
        jei_enhancements$updatePageButtonAreas();
    }

    /**
     * 在绘制书签后，渲染自定义数量和组面板
     */
    @Inject(method = "drawForeground", at = @At("TAIL"))
    private void onDrawForegroundTail(Minecraft minecraft, GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTicks, CallbackInfo ci) {
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
        ImmutableRect2i back = contents.getBackButtonArea();
        ImmutableRect2i next = contents.getNextPageButtonArea();
        int size = Math.min(12, Math.max(1, back.getHeight() - 4));
        if (back.isEmpty() || next.isEmpty()) {
            // 单页时 JEI 会隐藏导航按钮，但自定义页管理按钮仍需可见。
            ImmutableRect2i background = contents.getBackgroundArea();
            if (background.isEmpty()) return;
            int buttonY = background.getY() + background.getHeight() - size - 2;
            int centerX = background.getX() + background.getWidth() / 2;
            jei_enhancements$removePageArea = new ImmutableRect2i(centerX - size - 3, buttonY, size, size);
            jei_enhancements$addPageArea = new ImmutableRect2i(centerX + 3, buttonY, size, size);
            return;
        }

        int buttonY = back.getY() + (back.getHeight() - size) / 2;
        int labelLeft = back.getX() + back.getWidth();
        int labelRight = next.getX();
        int available = labelRight - labelLeft;
        if (available >= size * 2 + 6) {
            // 按 NEI 的页码布局，把管理按钮放在页码文字两侧。
            jei_enhancements$removePageArea = new ImmutableRect2i(labelLeft + 2, buttonY, size, size);
            jei_enhancements$addPageArea = new ImmutableRect2i(labelRight - size - 2, buttonY, size, size);
        } else {
            // 页码区域过窄时退到导航箭头外侧，仍保证按钮可点击。
            jei_enhancements$removePageArea = new ImmutableRect2i(back.getX() - size - 2, buttonY, size, size);
            jei_enhancements$addPageArea = new ImmutableRect2i(next.getX() + next.getWidth() + 2, buttonY, size, size);
        }
    }

    @Unique
    private void jei_enhancements$drawPageButtons(GuiGraphics graphics, int mouseX, int mouseY) {
        if (jei_enhancements$addPageArea.isEmpty()) return;
        // 直接复用 JEI GuiIconButton 使用的九宫格纹理，保持原生按钮背景和悬停效果。
        Textures textures = Internal.getTextures();
        jei_enhancements$drawJeiButton(graphics, textures, jei_enhancements$addPageArea, mouseX, mouseY);
        if (!jei_enhancements$removePageArea.isEmpty()) {
            jei_enhancements$drawJeiButton(graphics, textures, jei_enhancements$removePageArea, mouseX, mouseY);
        }

        var font = Minecraft.getInstance().font;
        jei_enhancements$drawButtonLabel(graphics, font, "+", jei_enhancements$addPageArea);
        if (!jei_enhancements$removePageArea.isEmpty()) {
            jei_enhancements$drawButtonLabel(graphics, font, "-", jei_enhancements$removePageArea);
        }
    }

    @Unique
    private void jei_enhancements$drawJeiButton(GuiGraphics graphics, Textures textures,
            ImmutableRect2i area, int mouseX, int mouseY) {
        boolean hovered = area.contains(mouseX, mouseY);
        ScalableDrawable texture = textures.getButtonForState(false, true, hovered);
        texture.draw(graphics, area.getX(), area.getY(), area.getWidth(), area.getHeight());
    }

    @Unique
    private void jei_enhancements$drawButtonLabel(GuiGraphics graphics, net.minecraft.client.gui.Font font,
            String label, ImmutableRect2i area) {
        int x = area.getX() + (area.getWidth() - font.width(label)) / 2;
        int y = area.getY() + (area.getHeight() - font.lineHeight) / 2;
        graphics.drawString(font, label, x, y, 0xFFFFFFFF, false);
    }

    @Override
    public ImmutableRect2i jeiEnhancements$getAddPageArea() {
        return jei_enhancements$addPageArea;
    }

    @Override
    public ImmutableRect2i jeiEnhancements$getRemovePageArea() {
        return jei_enhancements$removePageArea;
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
        
        // 坐标表示最后一个槽位的右/下边界，因此需将首槽位计入。
        int columns = (maxX - minX) / slotWidth + 1;
        int rows = (maxY - minY) / slotHeight + 1;
        
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
