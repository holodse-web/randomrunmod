package com.randomrun.challenges.advancement.data;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.randomrun.main.RandomRunMod;
import net.minecraft.client.MinecraftClient;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.resource.ResourceManager;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

public class AdvancementLoader {
    public static class AdvancementInfo {
        public final Identifier id;
        public final Text title;
        public final Text description;
        public final ItemStack icon;
        public final AchievementDifficulty.Difficulty difficulty;
        
        public AdvancementInfo(Identifier id, Text title, Text description, ItemStack icon) {
            this.id = id;
            this.title = title;
            this.description = description;
            this.icon = icon;
            this.difficulty = AchievementDifficulty.getDifficulty(id);
        }
    }
    
    private static List<AdvancementInfo> cachedAdvancements = null;
    
    public static List<AdvancementInfo> getAdvancements() {
        if (cachedAdvancements != null && !cachedAdvancements.isEmpty()) return cachedAdvancements;
        
        cachedAdvancements = new ArrayList<>();
        
        // Try to load from FabricLoader mod container for "minecraft"
        try {
            java.util.Optional<net.fabricmc.loader.api.ModContainer> minecraft = net.fabricmc.loader.api.FabricLoader.getInstance().getModContainer("minecraft");
            if (minecraft.isPresent()) {
                java.nio.file.Path root = minecraft.get().getRootPaths().get(0);
                java.nio.file.Path advancementsPath = root.resolve("data/minecraft/advancements");
                
                if (java.nio.file.Files.exists(advancementsPath)) {
                    java.nio.file.Files.walk(advancementsPath)
                        .filter(path -> path.toString().endsWith(".json"))
                        .forEach(path -> {
                            try {
                                JsonObject json = JsonParser.parseReader(java.nio.file.Files.newBufferedReader(path)).getAsJsonObject();
                                if (json.has("display")) {
                                    JsonObject display = json.getAsJsonObject("display");
                                    
                                    // Get Title
                                    Text title = parseText(display.get("title"));
                                    
                                    // Get Description
                                    Text description = parseText(display.get("description"));
                                    
                                    // Get Icon
                                    JsonObject iconObj = display.getAsJsonObject("icon");
                                    String itemStr = iconObj.get("item").getAsString();
                                    ItemStack icon = new ItemStack(Registries.ITEM.get(new Identifier(itemStr)));
                                    
                                    // Construct ID
                                    // Path is like .../data/minecraft/advancements/story/root.json
                                    // Relativize to data/minecraft/advancements
                                    java.nio.file.Path relative = advancementsPath.relativize(path);
                                    String relativeStr = relative.toString().replace(java.io.File.separator, "/");
                                    if (relativeStr.endsWith(".json")) {
                                        relativeStr = relativeStr.substring(0, relativeStr.length() - 5);
                                    }
                                    Identifier advId = new Identifier("minecraft", relativeStr);
                                    
                                    // Filter out recipes just in case (though we look in advancements folder)
                                    if (!relativeStr.contains("recipes/")) {
                                        cachedAdvancements.add(new AdvancementInfo(advId, title, description, icon));
                                    }
                                }
                            } catch (Exception e) {
                                // Ignore malformed files
                            }
                        });
                }
            }
        } catch (Exception e) {
            RandomRunMod.LOGGER.error("Failed to load vanilla advancements via FabricLoader", e);
        }
        
        // Fallback to Resource Manager if empty (e.g. if structure changes or in dev env)
        if (cachedAdvancements.isEmpty()) {
            ResourceManager resourceManager = MinecraftClient.getInstance().getResourceManager();
            resourceManager.findResources("advancements", path -> path.getPath().endsWith(".json"))
                .forEach((id, resource) -> {
                     if (!id.getNamespace().equals("minecraft")) return;
                     try {
                         JsonObject json = JsonParser.parseReader(new InputStreamReader(resource.getInputStream())).getAsJsonObject();
                         if (json.has("display")) {
                             JsonObject display = json.getAsJsonObject("display");
                             Text title = parseText(display.get("title"));
                             Text description = parseText(display.get("description"));
                             JsonObject iconObj = display.getAsJsonObject("icon");
                             String itemStr = iconObj.get("item").getAsString();
                             ItemStack icon = new ItemStack(Registries.ITEM.get(new Identifier(itemStr)));
                             
                             String path = id.getPath();
                             if (path.startsWith("advancements/")) {
                                 path = path.substring("advancements/".length());
                             }
                             if (path.endsWith(".json")) {
                                 path = path.substring(0, path.length() - 5);
                             }
                             Identifier advId = new Identifier(id.getNamespace(), path);
                             cachedAdvancements.add(new AdvancementInfo(advId, title, description, icon));
                         }
                    } catch (Exception e) { }
                });
        }
            
        return cachedAdvancements;
    }
    
    private static Text parseText(JsonElement element) {
        if (element.isJsonPrimitive()) {
            return Text.translatable(element.getAsString());
        }
        
        // Handle {"translate": "key"} style
        if (element.isJsonObject()) {
            JsonObject obj = element.getAsJsonObject();
            if (obj.has("translate")) {
                String key = obj.get("translate").getAsString();
                if (obj.has("with")) {
                    // TODO: Handle args if needed, but for titles it's usually simple
                }
                return Text.translatable(key);
            }
            if (obj.has("text")) {
                return Text.literal(obj.get("text").getAsString());
            }
        }
        
        // Fallback using Minecraft's serializer if possible, or just toString as last resort
        try {
            // Use fromJson(String) if fromJson(JsonElement) is not available
            return Text.Serialization.fromJson(element.toString());
        } catch (Exception e) {
            return Text.literal(element.toString());
        }
    }
}
