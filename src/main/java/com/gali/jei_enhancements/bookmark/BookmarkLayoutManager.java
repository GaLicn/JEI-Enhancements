package com.gali.jei_enhancements.bookmark;

import com.gali.jei_enhancements.JEIEnhancements;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraft.client.Minecraft;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * 管理书签的布局模式（水平/纵向排列）
 */
public class BookmarkLayoutManager {
    
    private static final BookmarkLayoutManager INSTANCE = new BookmarkLayoutManager();
    private static final String SAVE_FILE_NAME = "jei_enhancements_layout.json";
    
    /**
     * 布局模式
     */
    public enum LayoutMode {
        HORIZONTAL,  // 水平排列（默认，先填满一行再换行）
        VERTICAL     // 纵向排列（先填满一列再换列）
    }
    
    private LayoutMode currentMode = LayoutMode.HORIZONTAL;
    private boolean dirty = false;
    
    public static BookmarkLayoutManager getInstance() {
        return INSTANCE;
    }
    
    public LayoutMode getCurrentMode() {
        return currentMode;
    }
    
    public void setCurrentMode(LayoutMode mode) {
        if (this.currentMode != mode) {
            this.currentMode = mode;
            this.dirty = true;
        }
    }
    
    /**
     * 切换布局模式
     */
    public void toggleMode() {
        if (currentMode == LayoutMode.HORIZONTAL) {
            setCurrentMode(LayoutMode.VERTICAL);
        } else {
            setCurrentMode(LayoutMode.HORIZONTAL);
        }
    }
    
    /**
     * 是否是纵向模式
     */
    public boolean isVerticalMode() {
        return currentMode == LayoutMode.VERTICAL;
    }
    
    private Path getSaveFilePath() {
        Minecraft mc = Minecraft.getInstance();
        Path configDir = mc.gameDirectory.toPath().resolve("config");
        return configDir.resolve(SAVE_FILE_NAME);
    }
    
    public void save() {
        if (!dirty) {
            return;
        }
        
        try {
            Path savePath = getSaveFilePath();
            Files.createDirectories(savePath.getParent());
            
            JsonObject root = new JsonObject();
            root.addProperty("layoutMode", currentMode.name());
            
            Files.writeString(savePath, root.toString(), StandardCharsets.UTF_8);
            dirty = false;
            
        } catch (Exception e) {
            JEIEnhancements.LOGGER.error("Failed to save layout settings", e);
        }
    }
    
    public void load() {
        try {
            Path savePath = getSaveFilePath();
            if (!Files.exists(savePath)) {
                return;
            }
            
            String json = Files.readString(savePath, StandardCharsets.UTF_8);
            JsonObject root = JsonParser.parseString(json).getAsJsonObject();
            
            if (root.has("layoutMode")) {
                String modeName = root.get("layoutMode").getAsString();
                try {
                    currentMode = LayoutMode.valueOf(modeName);
                } catch (IllegalArgumentException e) {
                    currentMode = LayoutMode.HORIZONTAL;
                }
            }
            
            dirty = false;
            
        } catch (Exception e) {
            JEIEnhancements.LOGGER.error("Failed to load layout settings", e);
        }
    }
}
