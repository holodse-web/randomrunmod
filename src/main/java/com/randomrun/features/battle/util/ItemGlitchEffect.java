package com.randomrun.features.battle.util;

import com.randomrun.features.challenges.classic.data.ItemDifficulty;
import com.randomrun.infrastructure.RandomRunMod;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.Item;

public class ItemGlitchEffect {
    private static final long GLITCH_INTERVAL_MS = 400;
    private static final String FALLBACK_ITEM = "minecraft:barrier";
    
    private final List<String> itemIds = new ArrayList<>();
    private final List<Item> cachedItems = new ArrayList<>();
    
    private long lastUpdateTime = 0;
    private int currentIndex = 0;
    
    public ItemGlitchEffect(boolean allowUnobtainable) {
        initItems(allowUnobtainable);
    }
    
    private void initItems(boolean allowUnobtainable) {
        try {
            List<Item> items = ItemDifficulty.getAllItems(allowUnobtainable);
            cachedItems.addAll(items);
            
            for (Item item : items) {
                itemIds.add(BuiltInRegistries.ITEM.getKey(item).toString());
            }
            
            Collections.shuffle(itemIds);
        } catch (Exception e) {
            RandomRunMod.LOGGER.error("[Glitch] Ошибка инициализации", e);
            itemIds.add(FALLBACK_ITEM);
        }
    }
    
    public String getCurrentItemId() {
        if (itemIds.isEmpty()) {
            return FALLBACK_ITEM;
        }
        
        long now = System.currentTimeMillis();
        if (now - lastUpdateTime > GLITCH_INTERVAL_MS) {
            currentIndex = (currentIndex + 1) % itemIds.size();
            lastUpdateTime = now;
        }
        
        return itemIds.get(currentIndex);
    }
    
    public Item getCurrentItem() {
        if (cachedItems.isEmpty()) return null;
        return cachedItems.get(Math.min(currentIndex, cachedItems.size() - 1));
    }
    
    public void reset() {
        lastUpdateTime = 0;
        currentIndex = 0;
    }
}
