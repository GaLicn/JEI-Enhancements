package com.gali.jei_enhancements.recipe;

import com.gali.jei_enhancements.JEIEnhancements;
import com.gali.jei_enhancements.bookmark.BookmarkQuantityManager;
import com.gali.jei_enhancements.bookmark.RecipeBookmarkGroup;
import mezz.jei.api.gui.IRecipeLayoutDrawable;
import mezz.jei.api.gui.ingredient.IRecipeSlotView;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.helpers.ICodecHelper;
import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.runtime.IIngredientManager;
import mezz.jei.gui.bookmarks.BookmarkList;
import mezz.jei.gui.bookmarks.IngredientBookmark;
import mezz.jei.gui.bookmarks.BookmarkFactory;
import net.minecraft.core.NonNullList;
import net.minecraft.core.RegistryAccess;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 帮助类：将配方作为一组添加到书签
 * 实现NEI风格的Shift+A / Ctrl+Shift+A功能
 */
public class RecipeBookmarkHelper {

    /**
     * 配方成员信息
     */
    private static class RecipeMember {
        final ITypedIngredient<?> ingredient;
        final int quantity;
        final boolean isOutput;
        
        RecipeMember(ITypedIngredient<?> ingredient, int quantity, boolean isOutput) {
            this.ingredient = ingredient;
            this.quantity = quantity;
            this.isOutput = isOutput;
        }
    }

    /**
     * 将配方的输出和所有输入作为一组添加到书签
     * 
     * @param recipeLayout 配方布局
     * @param bookmarkList 书签列表
     * @param codecHelper 编解码帮助器
     * @param registryAccess 注册表访问
     * @param ingredientManager 原料管理器
     * @param saveCount 是否保存数量（创建配方组）
     * @return 是否成功添加
     */
    public static <R> boolean addRecipeToBookmarks(
            IRecipeLayoutDrawable<R> recipeLayout,
            BookmarkList bookmarkList,
            ICodecHelper codecHelper,
            RegistryAccess registryAccess,
            IIngredientManager ingredientManager,
            boolean saveCount
    ) {
        BookmarkFactory bookmarkFactory = new BookmarkFactory(codecHelper, registryAccess, ingredientManager);
        
        // 尝试从原始配方获取数量
        Map<String, Integer> ingredientCounts = new HashMap<>();
        int outputCount = 1;
        
        R recipe = recipeLayout.getRecipe();
        if (recipe instanceof RecipeHolder<?> holder) {
            Recipe<?> actualRecipe = holder.value();
            
            // 获取输出数量
            ItemStack result = actualRecipe.getResultItem(registryAccess);
            if (!result.isEmpty()) {
                outputCount = result.getCount();
            }
            
            // 获取输入数量
            if (actualRecipe instanceof CraftingRecipe craftingRecipe) {
                ingredientCounts = countCraftingIngredients(craftingRecipe);
            } else if (actualRecipe instanceof SmeltingRecipe || 
                       actualRecipe instanceof BlastingRecipe ||
                       actualRecipe instanceof SmokingRecipe ||
                       actualRecipe instanceof CampfireCookingRecipe) {
                // 熔炼类配方，输入都是1
                // 默认数量1
            }
        }
        
        IRecipeSlotsView slotsView = recipeLayout.getRecipeSlotsView();
        
        List<RecipeMember> outputs = new ArrayList<>();
        List<RecipeMember> inputs = new ArrayList<>();
        
        // 收集所有输出和输入
        for (IRecipeSlotView slotView : slotsView.getSlotViews()) {
            RecipeIngredientRole role = slotView.getRole();
            
            ITypedIngredient<?> ingredient = slotView.getDisplayedIngredient().orElse(null);
            if (ingredient == null) {
                continue;
            }
            
            int quantity = 1;
            
            if (role == RecipeIngredientRole.OUTPUT) {
                // 输出使用从配方获取的数量
                quantity = outputCount;
                if (quantity <= 0) {
                    quantity = getIngredientQuantity(ingredient);
                }
            } else if (role == RecipeIngredientRole.INPUT) {
                // 输入尝试从统计的数量中获取
                String key = getIngredientKey(ingredient);
                quantity = ingredientCounts.getOrDefault(key, 1);
            }
            
            ingredient = ingredientManager.normalizeTypedIngredient(ingredient);
            
            if (role == RecipeIngredientRole.OUTPUT) {
                outputs.add(new RecipeMember(ingredient, quantity, true));
            } else if (role == RecipeIngredientRole.INPUT) {
                inputs.add(new RecipeMember(ingredient, quantity, false));
            }
        }
        
        if (outputs.isEmpty() && inputs.isEmpty()) {
            return false;
        }
        
        boolean added = false;
        RecipeBookmarkGroup group = null;
        
        // 如果需要保存数量，创建配方组
        if (saveCount) {
            group = BookmarkQuantityManager.getInstance().createRecipeGroup();
        }
        
        // 先添加输出
        for (RecipeMember member : outputs) {
            IngredientBookmark<?> bookmark = addIngredientToBookmark(
                    member.ingredient, bookmarkList, bookmarkFactory);
            if (bookmark != null) {
                added = true;
                if (group != null) {
                    BookmarkQuantityManager.getInstance().addToGroup(
                            group, bookmark, member.quantity, true);
                }
            }
        }
        
        // 再添加输入
        for (RecipeMember member : inputs) {
            IngredientBookmark<?> bookmark = addIngredientToBookmark(
                    member.ingredient, bookmarkList, bookmarkFactory);
            if (bookmark != null) {
                added = true;
                if (group != null) {
                    BookmarkQuantityManager.getInstance().addToGroup(
                            group, bookmark, member.quantity, false);
                }
            }
        }
        
        if (added) {
            JEIEnhancements.LOGGER.info("Added recipe to bookmarks: {} outputs, {} inputs, saveCount={}", 
                    outputs.size(), inputs.size(), saveCount);
            
            // 保存数据
            if (saveCount) {
                BookmarkQuantityManager.getInstance().save();
            }
        }
        
        return added;
    }
    
    /**
     * 统计合成配方中每种原料的数量
     */
    private static Map<String, Integer> countCraftingIngredients(CraftingRecipe recipe) {
        Map<String, Integer> counts = new HashMap<>();
        
        NonNullList<Ingredient> ingredients = recipe.getIngredients();
        for (Ingredient ingredient : ingredients) {
            if (ingredient.isEmpty()) {
                continue;
            }
            
            // 获取这个Ingredient的第一个匹配物品作为key
            ItemStack[] stacks = ingredient.getItems();
            if (stacks.length > 0) {
                String key = getItemStackKey(stacks[0]);
                counts.merge(key, 1, Integer::sum);
            }
        }
        
        return counts;
    }
    
    /**
     * 获取物品的唯一标识（用于统计数量）
     */
    private static String getItemStackKey(ItemStack stack) {
        return stack.getItem().toString();
    }
    
    /**
     * 获取ITypedIngredient的唯一标识
     */
    private static String getIngredientKey(ITypedIngredient<?> ingredient) {
        Object obj = ingredient.getIngredient();
        if (obj instanceof ItemStack stack) {
            return getItemStackKey(stack);
        }
        return obj.toString();
    }
    
    /**
     * 获取原料的数量
     */
    private static int getIngredientQuantity(ITypedIngredient<?> ingredient) {
        Object obj = ingredient.getIngredient();
        if (obj instanceof ItemStack stack) {
            return stack.getCount();
        }
        return 1;
    }
    
    /**
     * 添加单个原料到书签
     */
    private static <T> IngredientBookmark<T> addIngredientToBookmark(
            ITypedIngredient<T> ingredient,
            BookmarkList bookmarkList,
            BookmarkFactory bookmarkFactory
    ) {
        IngredientBookmark<T> bookmark = bookmarkFactory.create(ingredient);
        
        // 检查是否已存在
        if (bookmarkList.contains(bookmark)) {
            return bookmark;
        }
        
        // 添加书签
        boolean added = bookmarkList.add(bookmark);
        
        return added ? bookmark : null;
    }
}
