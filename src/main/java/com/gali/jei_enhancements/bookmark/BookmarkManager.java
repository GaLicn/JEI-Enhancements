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
     * 获取书签项的当前数量（基础数量 * 组倍率）
     */
    public int getQuantity(BookmarkItem item) {
        BookmarkGroup group = groups.get(item.getGroupId());
        if (group != null) {
            return (int) Math.ceil(item.getBaseQuantity() * group.getMultiplier());
        }
        return item.getBaseQuantity();
    }
    
    /**
     * 调整组的倍率
     */
    public void adjustGroupMultiplier(int groupId, double delta) {
        BookmarkGroup group = groups.get(groupId);
        if (group != null) {
            group.adjustMultiplier(delta);
            markDirty();
        }
    }
    

    /**
     * 从JEI书签获取物品key
     */
    public String getItemKey(IBookmark bookmark) {
        if (bookmark instanceof IngredientBookmark<?> ingredientBookmark) {
            ITypedIngredient<?> ingredient = ingredientBookmark.getIngredient();
            Object obj = ingredient.getIngredient();
            
            if (obj instanceof ItemStack stack) {
                return getItemKeyFromStack(stack);
            }
            
            return ingredient.getType().getUid() + ":" + obj.hashCode();
        }
        return String.valueOf(bookmark.hashCode());
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
                groupObj.addProperty("multiplier", entry.getValue().getMultiplier());
                groupObj.addProperty("expanded", entry.getValue().isExpanded());
                groupsObj.add(String.valueOf(entry.getKey()), groupObj);
            }
            root.add("groups", groupsObj);
            
            // 保存书签项
            JsonArray itemsArray = new JsonArray();
            for (BookmarkItem item : bookmarkItems) {
                JsonObject itemObj = new JsonObject();
                itemObj.addProperty("groupId", item.getGroupId());
                itemObj.addProperty("itemKey", item.getItemKey());
                itemObj.addProperty("baseQuantity", item.getBaseQuantity());
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
                    group.setMultiplier(groupObj.get("multiplier").getAsDouble());
                    if (groupObj.has("expanded")) {
                        group.setExpanded(groupObj.get("expanded").getAsBoolean());
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
                    int baseQuantity = itemObj.get("baseQuantity").getAsInt();
                    BookmarkItem.BookmarkItemType type = BookmarkItem.BookmarkItemType.values()[
                            itemObj.get("type").getAsInt()];
                    
                    BookmarkItem item = new BookmarkItem(groupId, itemKey, baseQuantity, type);
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
}
