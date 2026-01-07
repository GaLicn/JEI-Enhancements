package com.gali.jei_enhancements.bookmark;

import com.gali.jei_enhancements.JEIEnhancements;
import com.google.gson.*;
import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.gui.bookmarks.IBookmark;
import mezz.jei.gui.bookmarks.IngredientBookmark;
import net.minecraft.client.Minecraft;
import net.minecraft.world.item.ItemStack;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 管理书签物品的自定义数量显示
 * 支持配方组和数据持久化
 */
public class BookmarkQuantityManager {
    
    private static final BookmarkQuantityManager INSTANCE = new BookmarkQuantityManager();
    private static final String SAVE_FILE_NAME = "jei_enhancements_quantities.json";
    
    // 存储单独书签的自定义数量
    private final Map<String, Integer> bookmarkQuantities = new HashMap<>();
    
    // 存储所有配方组
    private final List<RecipeBookmarkGroup> recipeGroups = new ArrayList<>();
    
    // 书签key到组的映射
    private final Map<String, RecipeBookmarkGroup> bookmarkToGroup = new HashMap<>();
    
    // 标记是否需要保存
    private boolean dirty = false;
    
    public static BookmarkQuantityManager getInstance() {
        return INSTANCE;
    }
    
    /**
     * 检查是否有任何自定义数量
     */
    public boolean hasAnyCustomQuantity() {
        return !bookmarkQuantities.isEmpty() || !recipeGroups.isEmpty();
    }
    
    /**
     * 检查特定书签是否有自定义数量
     */
    public boolean hasCustomQuantity(IBookmark bookmark) {
        String key = getBookmarkKey(bookmark);
        return bookmarkToGroup.containsKey(key) || bookmarkQuantities.containsKey(key);
    }
    
    /**
     * 获取书签的自定义数量
     */
    public int getQuantity(IBookmark bookmark) {
        String key = getBookmarkKey(bookmark);
        
        // 首先检查是否属于某个组
        RecipeBookmarkGroup group = bookmarkToGroup.get(key);
        if (group != null) {
            RecipeBookmarkGroup.GroupMember member = group.getMember(key);
            if (member != null) {
                return group.getMemberQuantity(member);
            }
        }
        
        return bookmarkQuantities.getOrDefault(key, -1);
    }
    
    /**
     * 设置书签的自定义数量
     */
    public void setQuantity(IBookmark bookmark, int quantity) {
        String key = getBookmarkKey(bookmark);
        
        // 检查是否属于某个组
        RecipeBookmarkGroup group = bookmarkToGroup.get(key);
        if (group != null) {
            RecipeBookmarkGroup.GroupMember member = group.getMember(key);
            if (member != null) {
                group.updateMultiplierFromMember(member, quantity);
                markDirty();
                return;
            }
        }
        
        // 单独的书签
        if (quantity <= 0) {
            bookmarkQuantities.remove(key);
        } else {
            if (quantity > 9999) {
                quantity = 9999;
            }
            bookmarkQuantities.put(key, quantity);
        }
        markDirty();
    }
    
    /**
     * 调整书签数量
     */
    public void adjustQuantity(IBookmark bookmark, int delta) {
        String key = getBookmarkKey(bookmark);
        
        // 检查是否属于某个组
        RecipeBookmarkGroup group = bookmarkToGroup.get(key);
        if (group != null) {
            group.adjustMultiplier(delta);
            markDirty();
            return;
        }
        
        // 单独的书签
        int current = getQuantity(bookmark);
        if (current < 0) {
            current = 1;
        }
        int newQuantity = current + delta;
        setQuantity(bookmark, newQuantity);
    }
    
    /**
     * 创建一个新的配方组
     */
    public RecipeBookmarkGroup createRecipeGroup() {
        RecipeBookmarkGroup group = new RecipeBookmarkGroup();
        recipeGroups.add(group);
        markDirty();
        return group;
    }
    
    /**
     * 将书签添加到配方组
     * 注意：JEI不允许重复书签，所以如果物品已经在其他组中，需要特殊处理
     */
    public void addToGroup(RecipeBookmarkGroup group, IBookmark bookmark, int baseQuantity, boolean isOutput) {
        String key = getBookmarkKey(bookmark);
        
        // 检查是否已经在其他组中
        RecipeBookmarkGroup existingGroup = bookmarkToGroup.get(key);
        if (existingGroup != null && existingGroup != group) {
            // 物品已经在另一个组中，NEI风格：合并到现有组或跳过
            // 这里我们选择跳过，因为JEI不支持重复书签
            JEIEnhancements.LOGGER.debug("Bookmark {} already in another group, skipping", key);
            return;
        }
        
        // 检查是否已经在当前组中（防止重复添加）
        if (group.containsKey(key)) {
            JEIEnhancements.LOGGER.debug("Bookmark {} already in this group, skipping", key);
            return;
        }
        
        // 添加到组
        group.addMember(bookmark, key, baseQuantity, isOutput);
        bookmarkToGroup.put(key, group);
        
        // 移除单独的数量
        bookmarkQuantities.remove(key);
        markDirty();
    }
    
    /**
     * 切换组的展开/折叠状态（NEI风格）
     */
    public void toggleGroupExpanded(RecipeBookmarkGroup group) {
        if (group != null) {
            group.toggleExpanded();
            markDirty();
        }
    }
    
    /**
     * 设置组的展开状态
     */
    public void setGroupExpanded(RecipeBookmarkGroup group, boolean expanded) {
        if (group != null) {
            group.setExpanded(expanded);
            markDirty();
        }
    }
    
    /**
     * 展开/折叠所有组
     */
    public void toggleAllGroups(Boolean expanded) {
        if (expanded == null) {
            // 如果没有指定，则根据当前状态切换
            expanded = recipeGroups.stream().noneMatch(RecipeBookmarkGroup::isExpanded);
        }
        for (RecipeBookmarkGroup group : recipeGroups) {
            group.setExpanded(expanded);
        }
        markDirty();
    }
    
    /**
     * 获取书签所属的组
     */
    public RecipeBookmarkGroup getGroup(IBookmark bookmark) {
        String key = getBookmarkKey(bookmark);
        return bookmarkToGroup.get(key);
    }
    
    /**
     * 检查书签是否属于某个组
     */
    public boolean isInGroup(IBookmark bookmark) {
        String key = getBookmarkKey(bookmark);
        return bookmarkToGroup.containsKey(key);
    }
    
    /**
     * 获取书签的唯一标识
     */
    public String getBookmarkKey(IBookmark bookmark) {
        if (bookmark instanceof IngredientBookmark<?> ingredientBookmark) {
            ITypedIngredient<?> ingredient = ingredientBookmark.getIngredient();
            Object obj = ingredient.getIngredient();
            
            if (obj instanceof ItemStack stack) {
                // 使用物品ID作为key
                ResourceLocation itemId = BuiltInRegistries.ITEM.getKey(stack.getItem());
                String key = itemId.toString();
                
                // 如果有NBT/组件，添加到key中
                if (stack.getComponentsPatch() != null && !stack.getComponentsPatch().isEmpty()) {
                    key += ":" + stack.getComponentsPatch().hashCode();
                }
                return key;
            }
            
            return ingredient.getType().getUid() + ":" + obj.hashCode();
        }
        return String.valueOf(bookmark.hashCode());
    }
    
    /**
     * 标记需要保存
     */
    private void markDirty() {
        dirty = true;
    }
    
    /**
     * 清除所有数据
     */
    public void clearAll() {
        bookmarkQuantities.clear();
        recipeGroups.clear();
        bookmarkToGroup.clear();
        markDirty();
    }
    
    /**
     * 移除特定书签的数量
     */
    public void remove(IBookmark bookmark) {
        String key = getBookmarkKey(bookmark);
        bookmarkQuantities.remove(key);
        bookmarkToGroup.remove(key);
        markDirty();
    }
    
    // ==================== 持久化相关 ====================
    
    /**
     * 获取保存文件路径
     */
    private Path getSaveFilePath() {
        Minecraft mc = Minecraft.getInstance();
        Path configDir = mc.gameDirectory.toPath().resolve("config");
        return configDir.resolve(SAVE_FILE_NAME);
    }
    
    /**
     * 保存数据到文件
     */
    public void save() {
        if (!dirty) {
            return;
        }
        
        try {
            Path savePath = getSaveFilePath();
            Files.createDirectories(savePath.getParent());
            
            JsonObject root = new JsonObject();
            
            // 保存单独的书签数量
            JsonObject quantities = new JsonObject();
            for (Map.Entry<String, Integer> entry : bookmarkQuantities.entrySet()) {
                quantities.addProperty(entry.getKey(), entry.getValue());
            }
            root.add("quantities", quantities);
            
            // 保存配方组
            JsonArray groups = new JsonArray();
            for (RecipeBookmarkGroup group : recipeGroups) {
                if (group.isEmpty()) continue;
                
                JsonObject groupObj = new JsonObject();
                groupObj.addProperty("multiplier", group.getMultiplier());
                groupObj.addProperty("expanded", group.isExpanded());
                
                JsonArray members = new JsonArray();
                for (RecipeBookmarkGroup.GroupMember member : group.getMembers()) {
                    JsonObject memberObj = new JsonObject();
                    memberObj.addProperty("key", (String) member.getBookmarkKey());
                    memberObj.addProperty("baseQuantity", member.getBaseQuantity());
                    memberObj.addProperty("isOutput", member.isOutput());
                    members.add(memberObj);
                }
                groupObj.add("members", members);
                groups.add(groupObj);
            }
            root.add("groups", groups);
            
            // 写入文件
            Gson gson = new GsonBuilder().setPrettyPrinting().create();
            String json = gson.toJson(root);
            Files.writeString(savePath, json, StandardCharsets.UTF_8);
            
            dirty = false;
            JEIEnhancements.LOGGER.debug("Saved bookmark quantities to {}", savePath);
            
        } catch (IOException e) {
            JEIEnhancements.LOGGER.error("Failed to save bookmark quantities", e);
        }
    }
    
    /**
     * 从文件加载数据
     */
    public void load() {
        try {
            Path savePath = getSaveFilePath();
            if (!Files.exists(savePath)) {
                return;
            }
            
            String json = Files.readString(savePath, StandardCharsets.UTF_8);
            JsonObject root = JsonParser.parseString(json).getAsJsonObject();
            
            // 清除现有数据
            bookmarkQuantities.clear();
            recipeGroups.clear();
            bookmarkToGroup.clear();
            
            // 加载单独的书签数量
            if (root.has("quantities")) {
                JsonObject quantities = root.getAsJsonObject("quantities");
                for (Map.Entry<String, JsonElement> entry : quantities.entrySet()) {
                    bookmarkQuantities.put(entry.getKey(), entry.getValue().getAsInt());
                }
            }
            
            // 加载配方组
            if (root.has("groups")) {
                JsonArray groups = root.getAsJsonArray("groups");
                for (JsonElement groupElem : groups) {
                    JsonObject groupObj = groupElem.getAsJsonObject();
                    
                    RecipeBookmarkGroup group = new RecipeBookmarkGroup();
                    group.setMultiplier(groupObj.get("multiplier").getAsDouble());
                    
                    // 加载展开状态（NEI风格）
                    if (groupObj.has("expanded")) {
                        group.setExpanded(groupObj.get("expanded").getAsBoolean());
                    }
                    
                    JsonArray members = groupObj.getAsJsonArray("members");
                    for (JsonElement memberElem : members) {
                        JsonObject memberObj = memberElem.getAsJsonObject();
                        String key = memberObj.get("key").getAsString();
                        int baseQuantity = memberObj.get("baseQuantity").getAsInt();
                        boolean isOutput = memberObj.get("isOutput").getAsBoolean();
                        
                        // 添加成员（使用key作为占位符，实际书签会在渲染时匹配）
                        group.addMemberByKey(key, baseQuantity, isOutput);
                        bookmarkToGroup.put(key, group);
                    }
                    
                    if (!group.isEmpty()) {
                        recipeGroups.add(group);
                    }
                }
            }
            
            dirty = false;
            JEIEnhancements.LOGGER.info("Loaded bookmark quantities from {}", savePath);
            
        } catch (Exception e) {
            JEIEnhancements.LOGGER.error("Failed to load bookmark quantities", e);
        }
    }
    
    /**
     * 通过key获取数量（用于渲染时，书签对象可能不同但key相同）
     */
    public int getQuantityByKey(String key) {
        // 首先检查是否属于某个组
        RecipeBookmarkGroup group = bookmarkToGroup.get(key);
        if (group != null) {
            RecipeBookmarkGroup.GroupMember member = group.getMember(key);
            if (member != null) {
                return group.getMemberQuantity(member);
            }
        }
        
        return bookmarkQuantities.getOrDefault(key, -1);
    }
}
