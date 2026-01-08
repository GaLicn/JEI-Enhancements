package com.gali.jei_enhancements.bookmark;

import com.gali.jei_enhancements.JEIEnhancements;
import com.google.gson.*;
import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.gui.bookmarks.IBookmark;
import mezz.jei.gui.bookmarks.IngredientBookmark;
import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/**
 * - BookmarkItem: 书签项，包含groupId、itemKey、数量、类型等信息
 * - 同一物品可以在不同组中独立存在
 * - 每个组有自己的倍率(multiplier)
 */
public class BookmarkManager {
    
    private static final BookmarkManager INSTANCE = new BookmarkManager();
    private static final String SAVE_FILE_NAME = "jei_enhancements_bookmarks.json";
    
    // 所有书签项
    private final List<BookmarkItem> bookmarkItems = new ArrayList<>();
    
    // 组信息
    private final Map<Integer, BookmarkGroup> groups = new HashMap<>();
    
    // JEI书签到BookmarkItem的映射（使用IdentityHashMap，因为同一物品可能有多个JEI书签实例）
    private final Map<IBookmark, BookmarkItem> jeiBookmarkMap = new IdentityHashMap<>();
    
    // 默认组ID
    public static final int DEFAULT_GROUP_ID = 0;
    
    // 下一个组ID
    private int nextGroupId = 1;
    
    // 是否允许重复添加（用于Mixin）
    private boolean allowDuplicates = false;
    
    // 当前正在添加的组ID（用于关联新书签）
    private int currentAddingGroupId = DEFAULT_GROUP_ID;
    
    // 标记是否需要保存
    private boolean dirty = false;
    
    public static BookmarkManager getInstance() {
        return INSTANCE;
    }
    
    public BookmarkManager() {
        // 初始化默认组
        groups.put(DEFAULT_GROUP_ID, new BookmarkGroup(DEFAULT_GROUP_ID));
    }
    

    public boolean isAllowDuplicates() {
        return allowDuplicates;
    }
    
    public void setAllowDuplicates(boolean allow) {
        this.allowDuplicates = allow;
    }
    
    public int getCurrentAddingGroupId() {
        return currentAddingGroupId;
    }
    
    public void setCurrentAddingGroupId(int groupId) {
        this.currentAddingGroupId = groupId;
    }
    

    /**
     * 创建新组
     */
    public int createGroup() {
        int groupId = nextGroupId++;
        groups.put(groupId, new BookmarkGroup(groupId));
        markDirty();
        return groupId;
    }
    
    /**
     * 获取组
     */
    public BookmarkGroup getGroup(int groupId) {
        return groups.get(groupId);
    }
    
    /**
     * 获取所有组
     */
    public java.util.Collection<BookmarkGroup> getAllGroups() {
        return groups.values();
    }
    
    /**
     * 删除组及其所有书签项
     */
    public void removeGroup(int groupId) {
        if (groupId == DEFAULT_GROUP_ID) {
            // 不能删除默认组，只清空其内容
            bookmarkItems.removeIf(item -> item.getGroupId() == DEFAULT_GROUP_ID);
        } else {
            bookmarkItems.removeIf(item -> item.getGroupId() == groupId);
            groups.remove(groupId);
        }
        // 清理jeiBookmarkMap
        jeiBookmarkMap.entrySet().removeIf(entry -> entry.getValue().getGroupId() == groupId);
        markDirty();
    }
    
    /**
     * 获取所有非空组ID
     */
    public Set<Integer> getActiveGroupIds() {
        Set<Integer> activeIds = new LinkedHashSet<>();
        for (BookmarkItem item : bookmarkItems) {
            activeIds.add(item.getGroupId());
        }
        return activeIds;
    }

    /**
     * 添加书签项
     */
    public BookmarkItem addBookmarkItem(int groupId, String itemKey, int baseQuantity, 
            BookmarkItem.BookmarkItemType type, IBookmark jeiBookmark) {
        
        // 确保组存在
        if (!groups.containsKey(groupId)) {
            groups.put(groupId, new BookmarkGroup(groupId));
        }
        
        BookmarkItem item = new BookmarkItem(groupId, itemKey, baseQuantity, type);
        item.setLinkedBookmark(jeiBookmark);
        bookmarkItems.add(item);
        
        // 建立JEI书签到BookmarkItem的映射
        if (jeiBookmark != null) {
            jeiBookmarkMap.put(jeiBookmark, item);
        }
        
        markDirty();
        return item;
    }
    
    /**
     * 根据JEI书签查找对应的BookmarkItem（通过映射表）
     */
    public BookmarkItem findBookmarkItem(IBookmark bookmark) {
        // 首先尝试从映射表查找
        BookmarkItem item = jeiBookmarkMap.get(bookmark);
        if (item != null) {
            return item;
        }

        return null;
    }
    
    /**
     * 尝试将JEI书签与已保存的BookmarkItem关联
     * 按顺序匹配第一个itemKey相同且未关联的BookmarkItem
     */
    public void tryLinkBookmark(IBookmark bookmark) {
        // 确保数据已加载
        ensureLoaded();
        
        // 如果已经有映射，跳过
        if (jeiBookmarkMap.containsKey(bookmark)) {
            return;
        }
        
        String itemKey = getItemKey(bookmark);
        
        // 按顺序查找第一个itemKey相同且未关联的BookmarkItem
        for (BookmarkItem item : bookmarkItems) {
            if (item.getItemKey().equals(itemKey) && item.getLinkedBookmark() == null) {
                // 建立映射
                item.setLinkedBookmark(bookmark);
                jeiBookmarkMap.put(bookmark, item);
                return;
            }
        }
    }
    
    // 标记是否已加载
    private boolean loaded = false;
    
    /**
     * 确保数据已加载（懒加载）- 公开方法供mixin调用
     */
    public void ensureLoaded() {
        if (!loaded) {
            load();
            loaded = true;
        }
    }
    
    /**
     * 清除所有JEI书签映射
     */
    public void clearMappings() {
        jeiBookmarkMap.clear();
        for (BookmarkItem item : bookmarkItems) {
            item.setLinkedBookmark(null);
        }
    }
    
    /**
     * 建立JEI书签到BookmarkItem的映射
     */
    public void linkBookmark(IBookmark bookmark, BookmarkItem item) {
        jeiBookmarkMap.put(bookmark, item);
    }
    
    /**
     * 根据JEI书签和组ID查找对应的BookmarkItem
     */
    public BookmarkItem findBookmarkItem(IBookmark bookmark, int groupId) {
        String itemKey = getItemKey(bookmark);
        for (BookmarkItem item : bookmarkItems) {
            if (item.getGroupId() == groupId && item.getItemKey().equals(itemKey)) {
                return item;
            }
        }
        return null;
    }
    
    /**
     * 获取指定组的所有书签项
     */
    public List<BookmarkItem> getGroupItems(int groupId) {
        List<BookmarkItem> result = new ArrayList<>();
        for (BookmarkItem item : bookmarkItems) {
            if (item.getGroupId() == groupId) {
                result.add(item);
            }
        }
        return result;
    }
    
    /**
     * 获取所有书签项
     */
    public List<BookmarkItem> getAllItems() {
        return new ArrayList<>(bookmarkItems);
    }
    
    /**
     * 当JEI书签被删除时调用（仅处理单个书签，不处理组头）
     */
    public void onBookmarkRemoved(IBookmark bookmark) {
        // 首先尝试从映射表查找
        BookmarkItem item = jeiBookmarkMap.remove(bookmark);
        
        if (item != null) {
            // 组头的删除由mixin处理，这里只删除单个成员
            bookmarkItems.remove(item);
            markDirty();
        }
    }
    

    /**
     * 获取书签项的当前数量
     * 直接返回item的amount
     */
    public int getQuantity(BookmarkItem item) {
        return (int) item.getAmount();
    }
    
    /**
     * @param item 要调整的物品
     * @param shift 调整的multiplier增量
     */
    public void shiftItemAmount(BookmarkItem item, long shift) {
        if (item == null) return;
        
        BookmarkGroup group = groups.get(item.getGroupId());
        
        // 如果是组头（RESULT类型）且在非默认组，调整整个配方（同一个RESULT下的所有INGREDIENT）
        if (item.isOutput() && item.getGroupId() != DEFAULT_GROUP_ID) {
            // 调整这个配方的所有物品
            shiftRecipeAmount(item, shift);
            
            // 如果是crafting chain模式，重新计算组内的配方关系
            if (group != null && group.isCraftingChainEnabled()) {
                recalculateCraftingChainInGroup(item.getGroupId());
            }
            return;
        }
        
        // 非组头物品，只调整当前物品
        item.shiftMultiplier(shift);
        markDirty();
    }
    
    /**
     * 调整配方的数量（RESULT及其关联的INGREDIENT）
     * 如果组内只有一个RESULT，则更新组内所有物品
     */
    private void shiftRecipeAmount(BookmarkItem resultItem, long shift) {
        int groupId = resultItem.getGroupId();
        List<BookmarkItem> items = getGroupItems(groupId);
        
        if (items.isEmpty()) return;
        
        // 计算新的multiplier
        long currentMultiplier = resultItem.getMultiplier();
        long newMultiplier = shiftMultiplier(currentMultiplier, shift, 1);
        
        // 统计组内RESULT的数量
        int resultCount = 0;
        for (BookmarkItem item : items) {
            if (item.isOutput()) {
                resultCount++;
            }
        }
        
        // 如果组内只有一个RESULT，更新组内所有物品
        if (resultCount <= 1) {
            for (BookmarkItem item : items) {
                item.setMultiplier(newMultiplier);
            }
        } else {
            // 多个RESULT的情况，只更新当前RESULT及其后面的INGREDIENT（直到下一个RESULT）
            int resultIndex = items.indexOf(resultItem);
            if (resultIndex < 0) return;
            
            // 更新RESULT
            resultItem.setMultiplier(newMultiplier);
            
            // 更新同一配方的INGREDIENT（紧跟在RESULT后面的INGREDIENT）
            for (int i = resultIndex + 1; i < items.size(); i++) {
                BookmarkItem nextItem = items.get(i);
                if (nextItem.isOutput()) {
                    // 遇到下一个RESULT，停止
                    break;
                }
                if (nextItem.isIngredient()) {
                    nextItem.setMultiplier(newMultiplier);
                }
            }
        }
        
        markDirty();
    }
    
    /**
     * 调整单个子组的数量（不触发crafting chain联动）
     */
    private void shiftSubGroupAmount(int groupId, long shift) {
        List<BookmarkItem> items = getGroupItems(groupId);
        if (items.isEmpty()) return;
        
        // 找出当前最小的multiplier
        long minMultiplier = Long.MAX_VALUE;
        for (BookmarkItem item : items) {
            minMultiplier = Math.min(minMultiplier, item.getMultiplier());
        }
        
        if (minMultiplier == Long.MAX_VALUE) {
            minMultiplier = 1;
        }
        
        // 计算新的multiplier
        long newMultiplier = shiftMultiplier(minMultiplier, shift, 1);
        
        // 更新所有物品的数量
        for (BookmarkItem item : items) {
            item.setMultiplier(newMultiplier);
        }
        
        markDirty();
    }
    
    /**
     * 调整组内所有物品的数量
     * 所有物品的multiplier同步调整
     * 如果是crafting chain模式，还会影响逻辑同组的其他子组
     */
    public void shiftGroupAmount(int groupId, long shift) {
        BookmarkGroup group = groups.get(groupId);
        if (group == null) return;
        
        List<BookmarkItem> items = getGroupItems(groupId);
        if (items.isEmpty()) return;
        
        // 找出当前最小的multiplier
        long minMultiplier = Long.MAX_VALUE;
        for (BookmarkItem item : items) {
            minMultiplier = Math.min(minMultiplier, item.getMultiplier());
        }
        
        if (minMultiplier == Long.MAX_VALUE) {
            minMultiplier = 1;
        }
        
        // 计算新的multiplier
        long newMultiplier = shiftMultiplier(minMultiplier, shift, 1);
        
        // 更新所有物品的数量
        for (BookmarkItem item : items) {
            item.setMultiplier(newMultiplier);
        }
        
        // 如果是crafting chain模式，重新计算组内配方关系
        if (group.isCraftingChainEnabled()) {
            recalculateCraftingChainInGroup(groupId);
        }
        
        markDirty();
    }
    
    /**
     * 重新计算组内的crafting chain
     * 
     * 核心逻辑（参考NEI的RecipeChainMath.refresh）：
     * 1. 建立INGREDIENT到RESULT的映射（preferredItems）
     * 2. 从顶层配方开始，计算每个INGREDIENT的需求量
     * 3. 如果某个INGREDIENT有对应的RESULT能提供，累加需求量到那个RESULT
     * 4. 只有当需求量超过当前产出量时，才增加合成次数
     */
    public void recalculateCraftingChainInGroup(int groupId) {
        BookmarkGroup group = groups.get(groupId);
        if (group == null || !group.isCraftingChainEnabled()) return;
        
        List<BookmarkItem> items = getGroupItems(groupId);
        if (items.isEmpty()) return;
        
        // 分离RESULT和INGREDIENT
        List<BookmarkItem> results = new ArrayList<>();
        List<BookmarkItem> ingredients = new ArrayList<>();
        
        for (BookmarkItem item : items) {
            if (item.isOutput()) {
                results.add(item);
            } else if (item.isIngredient()) {
                ingredients.add(item);
            }
        }
        
        if (results.isEmpty()) return;
        
        // 建立INGREDIENT到RESULT的映射（NEI的preferredItems）
        java.util.Map<BookmarkItem, BookmarkItem> preferredItems = new java.util.HashMap<>();
        for (BookmarkItem result : results) {
            collectPreferredItems(result, ingredients, results, preferredItems, new HashSet<>());
        }
        
        // 找到顶层配方（第一个RESULT）
        BookmarkItem firstResult = results.get(0);
        
        // 用于累加每个RESULT的需求量
        java.util.Map<BookmarkItem, Long> requiredAmount = new java.util.HashMap<>();
        
        // 用于跟踪每个RESULT当前的产出量（计算过程中使用）
        java.util.Map<BookmarkItem, Long> currentAmount = new java.util.HashMap<>();
        
        // 初始化：非顶层配方的产出量为0
        for (BookmarkItem result : results) {
            if (result == firstResult) {
                currentAmount.put(result, result.getAmount());
            } else {
                currentAmount.put(result, 0L);
            }
        }
        
        // 顶层配方的multiplier
        long topMultiplier = firstResult.getMultiplier();
        
        // 从顶层配方开始，递归计算所有配方的需求量
        calculateChainRequirements(firstResult, topMultiplier, ingredients, preferredItems, 
                requiredAmount, currentAmount, new HashSet<>());
        
        // 最后，根据计算结果更新所有配方的multiplier
        for (BookmarkItem result : results) {
            if (result == firstResult) continue;
            
            long amount = currentAmount.getOrDefault(result, 0L);
            if (amount > 0) {
                long multiplier = (long) Math.ceil((double) amount / result.getFactor());
                result.setMultiplier(multiplier);
                
                // 同步更新这个配方的INGREDIENT
                List<BookmarkItem> recipeIngrs = findRecipeIngredients(result, ingredients);
                for (BookmarkItem ingr : recipeIngrs) {
                    ingr.setMultiplier(multiplier);
                }
            }
        }
        
        markDirty();
    }
    
    /**
     * 递归计算配方链的需求量（参考NEI的calculateSuitableRecipe）
     * 
     * 关键逻辑：
     * 1. 累加需求量到requiredAmount
     * 2. 只有当需求量超过当前产出量时，才增加合成次数（shift）
     * 3. 增加合成次数后，递归处理该配方的INGREDIENT
     */
    private void calculateChainRequirements(BookmarkItem resultItem, long multiplier,
            List<BookmarkItem> allIngredients,
            java.util.Map<BookmarkItem, BookmarkItem> preferredItems,
            java.util.Map<BookmarkItem, Long> requiredAmount,
            java.util.Map<BookmarkItem, Long> currentAmount,
            Set<BookmarkItem> visited) {
        
        if (visited.contains(resultItem)) return;
        visited.add(resultItem);
        
        // 找到这个配方的INGREDIENT
        List<BookmarkItem> recipeIngredients = findRecipeIngredients(resultItem, allIngredients);
        
        // 对于每个INGREDIENT，检查是否有配方能提供它
        for (BookmarkItem ingrItem : recipeIngredients) {
            BookmarkItem prefResult = preferredItems.get(ingrItem);
            if (prefResult != null) {
                // 计算这个INGREDIENT需要多少
                long ingrNeeded = ingrItem.getFactor() * multiplier;
                
                // 累加到提供这个物品的RESULT的需求量上
                long prevRequired = requiredAmount.getOrDefault(prefResult, 0L);
                long newRequired = prevRequired + ingrNeeded;
                requiredAmount.put(prefResult, newRequired);
                
                // 计算需要增加多少合成次数（NEI的shift计算）
                // shift = ceil((requiredAmount - currentAmount) / factor)
                long prevAmount = currentAmount.getOrDefault(prefResult, 0L);
                long shift = (long) Math.ceil((double)(newRequired - prevAmount) / prefResult.getFactor());
                
                if (shift > 0) {
                    // 增加这个配方的产出量
                    long newAmount = prevAmount + shift * prefResult.getFactor();
                    currentAmount.put(prefResult, newAmount);
                    
                    // 递归处理这个配方的INGREDIENT（只传入新增的shift）
                    calculateChainRequirements(prefResult, shift, allIngredients, preferredItems, 
                            requiredAmount, currentAmount, visited);
                }
            }
        }
        
        visited.remove(resultItem);
    }
    
    /**
     * 收集INGREDIENT到RESULT的映射
     */
    private void collectPreferredItems(BookmarkItem sourceResult, List<BookmarkItem> allIngredients, 
            List<BookmarkItem> allResults, java.util.Map<BookmarkItem, BookmarkItem> preferredItems, 
            Set<BookmarkItem> visited) {
        
        if (visited.contains(sourceResult)) return;
        visited.add(sourceResult);
        
        // 找到属于这个RESULT配方的INGREDIENT
        List<BookmarkItem> recipeIngredients = findRecipeIngredients(sourceResult, allIngredients);
        
        for (BookmarkItem ingrItem : recipeIngredients) {
            if (preferredItems.containsKey(ingrItem)) continue;
            
            // 查找能提供这个INGREDIENT的RESULT
            for (BookmarkItem resultItem : allResults) {
                if (resultItem == sourceResult) continue;
                if (visited.contains(resultItem)) continue;
                
                // 检查这个RESULT是否能提供这个INGREDIENT（itemKey相同）
                if (resultItem.getItemKey().equals(ingrItem.getItemKey())) {
                    preferredItems.put(ingrItem, resultItem);
                    // 递归收集这个RESULT的配方的INGREDIENT
                    collectPreferredItems(resultItem, allIngredients, allResults, preferredItems, visited);
                    break;
                }
            }
        }
        
        visited.remove(sourceResult);
    }
    
    /**
     * 找到属于某个RESULT配方的INGREDIENT（紧跟在RESULT后面的INGREDIENT）
     */
    private List<BookmarkItem> findRecipeIngredients(BookmarkItem result, List<BookmarkItem> allIngredients) {
        List<BookmarkItem> recipeIngredients = new ArrayList<>();
        List<BookmarkItem> allItems = getAllItems();
        
        int resultIndex = allItems.indexOf(result);
        if (resultIndex < 0) return recipeIngredients;
        
        // 收集紧跟在这个RESULT后面的INGREDIENT
        for (int i = resultIndex + 1; i < allItems.size(); i++) {
            BookmarkItem item = allItems.get(i);
            if (item.isOutput()) {
                // 遇到下一个RESULT，停止
                break;
            }
            if (item.isIngredient() && allIngredients.contains(item)) {
                recipeIngredients.add(item);
            }
        }
        
        return recipeIngredients;
    }
    
    /**
     * 重新计算crafting chain（当开启crafting chain模式时调用）
     * 从指定组开始，计算所有关联组的数量
     */
    public void recalculateCraftingChain(int groupId) {
        // 现在只计算组内的crafting chain
        recalculateCraftingChainInGroup(groupId);
    }
    
    /**
     * multiplier调整算法
     */
    private long shiftMultiplier(long multiplier, long shift, long minMultiplier) {
        // 这样可以让数量按shift的倍数变化
        long currentMultiplier;
        if (shift > 0) {
            currentMultiplier = ((multiplier + shift) / shift) * shift;
        } else {
            currentMultiplier = multiplier + shift;
        }
        
        // 确保不小于最小值，不大于最大值
        if (currentMultiplier <= 0 && multiplier > 1) {
            return 1;
        }
        return Math.min(Integer.MAX_VALUE, Math.max(minMultiplier, currentMultiplier));
    }
    
    /**
     * 调整组的倍率
     */
    public void adjustGroupMultiplier(int groupId, double delta) {
        shiftGroupAmount(groupId, (long) delta);
    }
    
    /**
     * 只删除组（不删除组内的书签项）
     */
    public void removeGroupOnly(int groupId) {
        if (groupId != DEFAULT_GROUP_ID) {
            groups.remove(groupId);
            markDirty();
        }
    }
    

    /**
     * 从JEI书签获取物品key
     */
    public String getItemKey(IBookmark bookmark) {
        if (bookmark instanceof IngredientBookmark<?> ingredientBookmark) {
            ITypedIngredient<?> ingredient = ingredientBookmark.getIngredient();
            return getItemKeyFromIngredient(ingredient);
        }
        return String.valueOf(bookmark.hashCode());
    }
    
    /**
     * 从ITypedIngredient获取物品key
     * 支持ItemStack、FluidStack和其他类型（如Mekanism的ChemicalStack）
     */
    public String getItemKeyFromIngredient(ITypedIngredient<?> ingredient) {
        Object obj = ingredient.getIngredient();
        
        if (obj instanceof ItemStack stack) {
            return getItemKeyFromStack(stack);
        }
        
        // 对于流体和其他类型，尝试获取更稳定的标识符
        String typeUid = ingredient.getType().getUid().toString();
        
        // 尝试使用反射获取流体/化学物质的注册名称
        String stableKey = getStableKeyForObject(obj);
        if (stableKey != null) {
            return typeUid + ":" + stableKey;
        }
        
        // 回退到使用toString()，通常比hashCode()更稳定
        return typeUid + ":" + obj.toString();
    }
    
    /**
     * 尝试获取对象的稳定key（用于流体、化学物质等）
     */
    private String getStableKeyForObject(Object obj) {
        try {
            // 尝试NeoForge FluidStack
            if (obj.getClass().getName().contains("FluidStack")) {
                // 尝试获取getFluid().builtInRegistryHolder().key().location()
                java.lang.reflect.Method getFluid = obj.getClass().getMethod("getFluid");
                Object fluid = getFluid.invoke(obj);
                if (fluid != null) {
                    // 尝试获取注册名称
                    java.lang.reflect.Method builtInRegistryHolder = fluid.getClass().getMethod("builtInRegistryHolder");
                    Object holder = builtInRegistryHolder.invoke(fluid);
                    if (holder != null) {
                        java.lang.reflect.Method key = holder.getClass().getMethod("key");
                        Object resourceKey = key.invoke(holder);
                        if (resourceKey != null) {
                            java.lang.reflect.Method location = resourceKey.getClass().getMethod("location");
                            Object loc = location.invoke(resourceKey);
                            if (loc != null) {
                                return loc.toString();
                            }
                        }
                    }
                }
            }
            
            // 尝试Mekanism ChemicalStack
            if (obj.getClass().getName().contains("ChemicalStack")) {
                // 尝试获取getType().getRegistryName() 或 getChemical().getRegistryName()
                java.lang.reflect.Method getChemical = null;
                try {
                    getChemical = obj.getClass().getMethod("getChemical");
                } catch (NoSuchMethodException e) {
                    try {
                        getChemical = obj.getClass().getMethod("getType");
                    } catch (NoSuchMethodException e2) {
                        // ignore
                    }
                }
                
                if (getChemical != null) {
                    Object chemical = getChemical.invoke(obj);
                    if (chemical != null) {
                        // 尝试获取注册名称
                        java.lang.reflect.Method getRegistryName = null;
                        try {
                            getRegistryName = chemical.getClass().getMethod("getRegistryName");
                        } catch (NoSuchMethodException e) {
                            // 尝试其他方法
                            try {
                                // Mekanism 1.21+ 使用不同的API
                                java.lang.reflect.Method builtInRegistryHolder = chemical.getClass().getMethod("builtInRegistryHolder");
                                Object holder = builtInRegistryHolder.invoke(chemical);
                                if (holder != null) {
                                    java.lang.reflect.Method key = holder.getClass().getMethod("key");
                                    Object resourceKey = key.invoke(holder);
                                    if (resourceKey != null) {
                                        java.lang.reflect.Method location = resourceKey.getClass().getMethod("location");
                                        Object loc = location.invoke(resourceKey);
                                        if (loc != null) {
                                            return loc.toString();
                                        }
                                    }
                                }
                            } catch (Exception ex) {
                                // ignore
                            }
                        }
                        
                        if (getRegistryName != null) {
                            Object regName = getRegistryName.invoke(chemical);
                            if (regName != null) {
                                return regName.toString();
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            // 反射失败，返回null使用回退方案
            JEIEnhancements.LOGGER.debug("Failed to get stable key for object: " + obj.getClass().getName(), e);
        }
        
        return null;
    }
    
    /**
     * 从ItemStack获取物品key
     */
    public String getItemKeyFromStack(ItemStack stack) {
        ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(stack.getItem());
        String key = itemId.toString();
        
        if (stack.getComponentsPatch() != null && !stack.getComponentsPatch().isEmpty()) {
            key += ":" + stack.getComponentsPatch().hashCode();
        }
        return key;
    }
    

    private void markDirty() {
        dirty = true;
    }
    
    public void save() {
        if (!dirty) return;
        
        try {
            Path savePath = getSaveFilePath();
            Files.createDirectories(savePath.getParent());
            
            JsonObject root = new JsonObject();
            root.addProperty("nextGroupId", nextGroupId);
            
            // 保存组信息
            JsonObject groupsObj = new JsonObject();
            for (Map.Entry<Integer, BookmarkGroup> entry : groups.entrySet()) {
                JsonObject groupObj = new JsonObject();
                groupObj.addProperty("expanded", entry.getValue().isExpanded());
                groupObj.addProperty("craftingChain", entry.getValue().isCraftingChainEnabled());
                groupObj.addProperty("linkedGroupId", entry.getValue().getLinkedGroupId());
                groupsObj.add(String.valueOf(entry.getKey()), groupObj);
            }
            root.add("groups", groupsObj);
            
            // 保存书签项
            JsonArray itemsArray = new JsonArray();
            for (BookmarkItem item : bookmarkItems) {
                JsonObject itemObj = new JsonObject();
                itemObj.addProperty("groupId", item.getGroupId());
                itemObj.addProperty("itemKey", item.getItemKey());
                itemObj.addProperty("factor", item.getFactor());
                itemObj.addProperty("amount", item.getAmount());
                itemObj.addProperty("type", item.getType().ordinal());
                itemsArray.add(itemObj);
            }
            root.add("items", itemsArray);
            
            Gson gson = new GsonBuilder().setPrettyPrinting().create();
            Files.writeString(savePath, gson.toJson(root), StandardCharsets.UTF_8);
            
            dirty = false;

        } catch (Exception e) {
            JEIEnhancements.LOGGER.error("Failed to save bookmark data", e);
        }
    }
    
    public void load() {
        try {
            Path savePath = getSaveFilePath();
            if (!Files.exists(savePath)) {
                loaded = true;
                return;
            }
            
            String json = Files.readString(savePath, StandardCharsets.UTF_8);
            JsonObject root = JsonParser.parseString(json).getAsJsonObject();
            
            // 清除现有数据
            bookmarkItems.clear();
            groups.clear();
            jeiBookmarkMap.clear();
            groups.put(DEFAULT_GROUP_ID, new BookmarkGroup(DEFAULT_GROUP_ID));
            
            if (root.has("nextGroupId")) {
                nextGroupId = root.get("nextGroupId").getAsInt();
            }
            
            // 加载组信息
            if (root.has("groups")) {
                JsonObject groupsObj = root.getAsJsonObject("groups");
                for (Map.Entry<String, JsonElement> entry : groupsObj.entrySet()) {
                    int groupId = Integer.parseInt(entry.getKey());
                    JsonObject groupObj = entry.getValue().getAsJsonObject();
                    
                    BookmarkGroup group = new BookmarkGroup(groupId);
                    if (groupObj.has("multiplier")) {
                        group.setMultiplier(groupObj.get("multiplier").getAsDouble());
                    }
                    if (groupObj.has("expanded")) {
                        group.setExpanded(groupObj.get("expanded").getAsBoolean());
                    }
                    if (groupObj.has("craftingChain")) {
                        group.setCraftingChainEnabled(groupObj.get("craftingChain").getAsBoolean());
                    }
                    if (groupObj.has("linkedGroupId")) {
                        group.setLinkedGroupId(groupObj.get("linkedGroupId").getAsInt());
                    }
                    groups.put(groupId, group);
                }
            }
            
            // 加载书签项
            if (root.has("items")) {
                JsonArray itemsArray = root.getAsJsonArray("items");
                for (JsonElement elem : itemsArray) {
                    JsonObject itemObj = elem.getAsJsonObject();
                    int groupId = itemObj.get("groupId").getAsInt();
                    String itemKey = itemObj.get("itemKey").getAsString();
                    
                    // 兼容旧版本：优先使用factor，否则使用baseQuantity
                    long factor = 1;
                    if (itemObj.has("factor")) {
                        factor = itemObj.get("factor").getAsLong();
                    } else if (itemObj.has("baseQuantity")) {
                        factor = itemObj.get("baseQuantity").getAsInt();
                    }
                    
                    BookmarkItem.BookmarkItemType type = BookmarkItem.BookmarkItemType.values()[
                            itemObj.get("type").getAsInt()];
                    
                    BookmarkItem item = new BookmarkItem(groupId, itemKey, factor, type);
                    
                    // 加载amount
                    if (itemObj.has("amount")) {
                        item.setAmount(itemObj.get("amount").getAsLong());
                    }
                    
                    bookmarkItems.add(item);
                }
            }
            
            dirty = false;
            loaded = true;

        } catch (Exception e) {
            JEIEnhancements.LOGGER.error("Failed to load bookmark data", e);
            loaded = true;
        }
    }
    
    private Path getSaveFilePath() {
        Minecraft mc = Minecraft.getInstance();
        return mc.gameDirectory.toPath().resolve("config").resolve(SAVE_FILE_NAME);
    }
    
    /**
     * 清除所有数据
     */
    public void clearAll() {
        bookmarkItems.clear();
        groups.clear();
        jeiBookmarkMap.clear();
        groups.put(DEFAULT_GROUP_ID, new BookmarkGroup(DEFAULT_GROUP_ID));
        nextGroupId = 1;
        loaded = false;
        markDirty();
    }
    
    /**
     * 将多个书签项合并到一个组
     * 如果这些书签项已经在同一个组中，不做任何操作
     * 如果在不同组中，将它们合并到第一个书签项的组中（如果是默认组则创建新组）
     */
    public void mergeItemsIntoGroup(List<BookmarkItem> items) {
        if (items == null || items.size() < 2) {
            return;
        }
        
        // 检查是否所有项都在同一个组
        Set<Integer> groupIds = new HashSet<>();
        for (BookmarkItem item : items) {
            groupIds.add(item.getGroupId());
        }
        
        if (groupIds.size() == 1 && !groupIds.contains(DEFAULT_GROUP_ID)) {
            // 已经在同一个非默认组中，不需要合并
            return;
        }
        
        // 确定目标组ID
        int targetGroupId;
        BookmarkItem firstItem = items.get(0);
        
        if (firstItem.getGroupId() == DEFAULT_GROUP_ID) {
            // 第一个项在默认组，创建新组
            targetGroupId = createGroup();
        } else {
            // 使用第一个项的组
            targetGroupId = firstItem.getGroupId();
        }
        
        // 将所有项移动到目标组
        for (BookmarkItem item : items) {
            if (item.getGroupId() != targetGroupId) {
                int oldGroupId = item.getGroupId();
                item.setGroupId(targetGroupId);
                
                // 如果旧组变空了，删除它
                if (oldGroupId != DEFAULT_GROUP_ID) {
                    boolean hasOtherItems = false;
                    for (BookmarkItem other : bookmarkItems) {
                        if (other.getGroupId() == oldGroupId && !items.contains(other)) {
                            hasOtherItems = true;
                            break;
                        }
                    }
                    if (!hasOtherItems) {
                        groups.remove(oldGroupId);
                    }
                }
            }
        }
        
        // 设置第一个项为输出（组头）
        firstItem.setType(BookmarkItem.BookmarkItemType.RESULT);
        
        // 其他项设置为原料
        for (int i = 1; i < items.size(); i++) {
            items.get(i).setType(BookmarkItem.BookmarkItemType.INGREDIENT);
        }
        
        markDirty();
    }
    
    /**
     * 将书签项从组中分离出来（移动到默认组）
     */
    public void separateItemFromGroup(BookmarkItem item) {
        if (item == null || item.getGroupId() == DEFAULT_GROUP_ID) {
            return;
        }
        
        int oldGroupId = item.getGroupId();
        item.setGroupId(DEFAULT_GROUP_ID);
        item.setType(BookmarkItem.BookmarkItemType.ITEM);
        
        // 检查旧组是否还有其他项
        List<BookmarkItem> remainingItems = getGroupItems(oldGroupId);
        if (remainingItems.isEmpty()) {
            groups.remove(oldGroupId);
        } else if (remainingItems.size() == 1) {
            // 只剩一个项，也移到默认组
            BookmarkItem lastItem = remainingItems.get(0);
            lastItem.setGroupId(DEFAULT_GROUP_ID);
            lastItem.setType(BookmarkItem.BookmarkItemType.ITEM);
            groups.remove(oldGroupId);
        }
        
        markDirty();
    }
}
