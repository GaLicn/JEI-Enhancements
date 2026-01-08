package com.gali.jei_enhancements.recipe;

import com.gali.jei_enhancements.bookmark.BookmarkItem;
import com.gali.jei_enhancements.bookmark.BookmarkManager;
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
import net.minecraft.core.RegistryAccess;
import net.minecraft.world.item.ItemStack;

import java.util.ArrayList;
import java.util.LinkedHashMap;
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
        final ItemStack itemStack;
        int quantity;
        final boolean isOutput;
        
        RecipeMember(ITypedIngredient<?> ingredient, int quantity, boolean isOutput) {
            this.ingredient = ingredient;
            this.quantity = quantity;
            this.isOutput = isOutput;
            
            Object obj = ingredient.getIngredient();
            if (obj instanceof ItemStack stack) {
                this.itemStack = stack.copy();
            } else {
                this.itemStack = null;
            }
        }
        
        void addQuantity(int amount) {
            this.quantity += amount;
        }
    }

    /**
     * 将配方的输出和所有输入作为一组添加到书签
     * 只添加主产物（第一个输出）和所有输入，不添加副产物
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
        BookmarkManager manager = BookmarkManager.getInstance();
        
        IRecipeSlotsView slotsView = recipeLayout.getRecipeSlotsView();
        
        // 使用LinkedHashMap保持顺序，同时合并相同物品
        Map<String, RecipeMember> outputMap = new LinkedHashMap<>();
        Map<String, RecipeMember> inputMap = new LinkedHashMap<>();
        
        // 收集并合并所有输出和输入
        for (IRecipeSlotView slotView : slotsView.getSlotViews()) {
            RecipeIngredientRole role = slotView.getRole();
            
            ITypedIngredient<?> ingredient = slotView.getDisplayedIngredient().orElse(null);
            if (ingredient == null) {
                continue;
            }
            
            // 先获取数量，再normalize（normalize可能会重置数量）
            int quantity = getIngredientQuantity(ingredient);
            if (quantity <= 0) quantity = 1;
            
            ingredient = ingredientManager.normalizeTypedIngredient(ingredient);
            String itemKey = getItemKey(ingredient);
            
            if (role == RecipeIngredientRole.OUTPUT) {
                if (outputMap.containsKey(itemKey)) {
                    outputMap.get(itemKey).addQuantity(quantity);
                } else {
                    outputMap.put(itemKey, new RecipeMember(ingredient, quantity, true));
                }
            } else if (role == RecipeIngredientRole.INPUT) {
                if (inputMap.containsKey(itemKey)) {
                    inputMap.get(itemKey).addQuantity(quantity);
                } else {
                    inputMap.put(itemKey, new RecipeMember(ingredient, quantity, false));
                }
            }
        }
        
        List<RecipeMember> outputs = new ArrayList<>(outputMap.values());
        List<RecipeMember> inputs = new ArrayList<>(inputMap.values());
        
        // 必须有主产物
        if (outputs.isEmpty()) {
            return false;
        }
        
        // 创建新组（如果需要保存数量）
        int groupId = saveCount ? manager.createGroup() : BookmarkManager.DEFAULT_GROUP_ID;
        
        // 开启允许重复模式
        manager.setAllowDuplicates(true);
        manager.setCurrentAddingGroupId(groupId);
        
        try {
            boolean added = false;
            
            // 1. 只添加第一个输出作为主产物（RESULT）- 不添加副产物
            RecipeMember mainOutput = outputs.get(0);
            if (addMemberToBookmarks(mainOutput, groupId, bookmarkList, bookmarkFactory, manager, 
                    BookmarkItem.BookmarkItemType.RESULT)) {
                added = true;
            }
            
            // 2. 添加所有输入（INGREDIENT）
            for (RecipeMember member : inputs) {
                if (addMemberToBookmarks(member, groupId, bookmarkList, bookmarkFactory, manager,
                        BookmarkItem.BookmarkItemType.INGREDIENT)) {
                    added = true;
                }
            }
            
            // 不添加副产物（outputs中索引>0的项）
            
            if (added) {
                manager.save();
            }
            
            return added;
            
        } finally {
            // 关闭允许重复模式
            manager.setAllowDuplicates(false);
            manager.setCurrentAddingGroupId(BookmarkManager.DEFAULT_GROUP_ID);
        }
    }
    
    /**
     * 添加成员到书签
     */
    private static <T> boolean addMemberToBookmarks(
            RecipeMember member,
            int groupId,
            BookmarkList bookmarkList,
            BookmarkFactory bookmarkFactory,
            BookmarkManager manager,
            BookmarkItem.BookmarkItemType type
    ) {
        @SuppressWarnings("unchecked")
        ITypedIngredient<T> ingredient = (ITypedIngredient<T>) member.ingredient;
        IngredientBookmark<T> bookmark = bookmarkFactory.create(ingredient);
        
        // 获取itemKey
        String itemKey = getItemKey(member.ingredient);
        
        // 添加到JEI书签列表（Mixin会绕过重复检测）
        bookmarkList.add(bookmark);
        
        // 添加到BookmarkManager
        manager.addBookmarkItem(groupId, itemKey, member.quantity, type, bookmark);

        
        return true;
    }
    
    /**
     * 获取物品的唯一key
     */
    private static String getItemKey(ITypedIngredient<?> ingredient) {
        // 使用BookmarkManager的方法来获取统一的key
        return BookmarkManager.getInstance().getItemKeyFromIngredient(ingredient);
    }
    
    private static int getIngredientQuantity(ITypedIngredient<?> ingredient) {
        Object obj = ingredient.getIngredient();
        if (obj instanceof ItemStack stack) {
            return stack.getCount();
        }
        return 1;
    }
}
