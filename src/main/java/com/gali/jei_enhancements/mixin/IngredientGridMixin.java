package com.gali.jei_enhancements.mixin;

import com.gali.jei_enhancements.bookmark.BookmarkItem;
import com.gali.jei_enhancements.bookmark.BookmarkManager;
import mezz.jei.api.ingredients.IIngredientHelper;
import mezz.jei.api.ingredients.IIngredientRenderer;
import mezz.jei.api.ingredients.IIngredientType;
import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.api.runtime.IIngredientManager;
import mezz.jei.common.gui.JeiTooltip;
import mezz.jei.gui.bookmarks.IBookmark;
import mezz.jei.gui.overlay.IngredientGrid;
import mezz.jei.gui.overlay.IngredientGridTooltipHelper;
import mezz.jei.gui.overlay.elements.IElement;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Optional;

/**
 * 拦截书签的tooltip显示，在按住Alt时添加操作说明
 */
@Mixin(value = IngredientGrid.class, remap = false)
public abstract class IngredientGridMixin {

    @Shadow @Final
    private IIngredientManager ingredientManager;

    @Shadow @Final
    private IngredientGridTooltipHelper tooltipHelper;

    /**
     * 拦截drawTooltip方法，添加Alt键操作提示
     */
    @Inject(method = "drawTooltip", at = @At("HEAD"), cancellable = true)
    private <T> void onDrawTooltip(GuiGraphics guiGraphics, int mouseX, int mouseY, IElement<T> element, CallbackInfo ci) {
        // 构建tooltip
        ITypedIngredient<T> typedIngredient = element.getTypedIngredient();
        IIngredientType<T> ingredientType = typedIngredient.getType();
        IIngredientRenderer<T> ingredientRenderer = ingredientManager.getIngredientRenderer(ingredientType);
        IIngredientHelper<T> ingredientHelper = ingredientManager.getIngredientHelper(ingredientType);
        
        JeiTooltip tooltip = new JeiTooltip();
        element.getTooltip(tooltip, tooltipHelper, ingredientRenderer, ingredientHelper);
        
        // 检查是否是书签
        Optional<IBookmark> bookmarkOpt = element.getBookmark();
        
        // 如果按住Shift，显示书签数量的组数信息（仅对书签有效）
        if (Screen.hasShiftDown() && bookmarkOpt.isPresent()) {
            IBookmark bookmark = bookmarkOpt.get();
            BookmarkManager manager = BookmarkManager.getInstance();
            BookmarkItem item = manager.findBookmarkItem(bookmark);
            
            if (item != null && item.getAmount() > 0) {
                String countDetails = getCountDetails(typedIngredient, item.getAmount());
                if (countDetails != null) {
                    tooltip.add(Component.literal(countDetails).withStyle(style -> style.withColor(0x55FFFF)));
                }
            }
        }
        
        // 如果按住Alt，添加操作说明
        if (Screen.hasAltDown() && bookmarkOpt.isPresent()) {
            tooltip.add(Component.empty());
            tooltip.add(Component.translatable("jei_enhancements.tooltip.header").withStyle(style -> style.withColor(0xFFFF55)));
            
            // 通用操作
            tooltip.add(Component.translatable("jei_enhancements.tooltip.ctrl_scroll").withStyle(style -> style.withColor(0xAAAAAA)));
            tooltip.add(Component.translatable("jei_enhancements.tooltip.ctrl_alt_scroll").withStyle(style -> style.withColor(0xAAAAAA)));
            tooltip.add(Component.translatable("jei_enhancements.tooltip.ctrl_shift_a").withStyle(style -> style.withColor(0xAAAAAA)));
            tooltip.add(Component.translatable("jei_enhancements.tooltip.left_drag_down").withStyle(style -> style.withColor(0xAAAAAA)));
            tooltip.add(Component.translatable("jei_enhancements.tooltip.left_drag_up").withStyle(style -> style.withColor(0xAAAAAA)));
            tooltip.add(Component.translatable("jei_enhancements.tooltip.right_drag").withStyle(style -> style.withColor(0xAAAAAA)));
            tooltip.add(Component.translatable("jei_enhancements.tooltip.right_click_bracket").withStyle(style -> style.withColor(0xAAAAAA)));
            tooltip.add(Component.translatable("jei_enhancements.tooltip.click_page").withStyle(style -> style.withColor(0xAAAAAA)));
        } else if (bookmarkOpt.isPresent()) {
            // 只有书签才显示Alt提示
            tooltip.add(Component.empty());
            tooltip.add(Component.translatable("jei_enhancements.tooltip.hold_alt").withStyle(style -> style.withColor(0x555555)));
        }
        
        tooltip.draw(guiGraphics, mouseX, mouseY, typedIngredient, ingredientRenderer, ingredientManager);
        ci.cancel();
    }
    
    /**
     * 获取数量的组数详情
     */
    private <T> String getCountDetails(ITypedIngredient<T> typedIngredient, long quantity) {
        // 获取最大堆叠数
        int maxStackSize = 64; // 默认值
        
        Optional<ItemStack> itemStackOpt = typedIngredient.getItemStack();
        if (itemStackOpt.isPresent()) {
            maxStackSize = itemStackOpt.get().getMaxStackSize();
        } else {
            // 流体/化学品使用1000L作为一组
            String typeUid = typedIngredient.getType().getUid();
            if (typeUid.contains("fluid") || typeUid.contains("chemical") || typeUid.contains("gas") || typeUid.contains("slurry")) {
                maxStackSize = 1000;
                return getFluidCountDetails(quantity, maxStackSize);
            }
        }
        
        if (maxStackSize > 1 && quantity > maxStackSize) {
            long stacks = quantity / maxStackSize;
            long remainder = quantity % maxStackSize;
            
            if (remainder > 0) {
                return String.format("%d = %d × %d + %d", quantity, stacks, maxStackSize, remainder);
            } else {
                return String.format("%d = %d × %d", quantity, stacks, maxStackSize);
            }
        }
        
        return null;
    }
    
    /**
     * 获取流体数量的组数详情
     */
    private String getFluidCountDetails(long amount, int bucketSize) {
        if (amount > bucketSize) {
            long buckets = amount / bucketSize;
            long remainder = amount % bucketSize;
            
            if (remainder > 0) {
                return String.format("%dL = %d × %dL + %dL", amount, buckets, bucketSize, remainder);
            } else {
                return String.format("%dL = %d × %dL", amount, buckets, bucketSize);
            }
        }
        return null;
    }
}
