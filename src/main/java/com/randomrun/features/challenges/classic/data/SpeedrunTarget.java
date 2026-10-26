package com.randomrun.features.challenges.classic.data;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.locale.Language;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import java.util.Locale;

public class SpeedrunTarget {
    private final Item item;
    private final ItemDifficulty.Difficulty difficulty;
    private final String targetId;
    private Language cachedLanguage;
    private Locale cachedLocale;
    private String displayName;
    private String searchName;
    private ItemStack previewStack;
    private DataComponentMap previewComponents;

    public SpeedrunTarget(Item item) {
        this.item = item;
        this.difficulty = ItemDifficulty.getDifficulty(item);
        this.targetId = BuiltInRegistries.ITEM.getKey(item).toString();
    }
    
    public Item getItem() {
        return item;
    }
    
    public ItemDifficulty.Difficulty getDifficulty() {
        return difficulty;
    }

    public String getDisplayName() {
        Language language = Language.getInstance();
        Locale locale = Locale.getDefault();
        if (cachedLanguage != language || !locale.equals(cachedLocale)) {
            displayName = net.minecraft.network.chat.Component.translatable(item.getDescriptionId()).getString();
            searchName = displayName.toLowerCase(locale);
            cachedLanguage = language;
            cachedLocale = locale;
        }
        return displayName;
    }

    public String getTargetId() {
        return targetId;
    }

    public ItemStack getDisplayStack() {
        return com.randomrun.ui.util.PreviewItems.stack(item);
    }

    public ItemStack getPreviewStack() {
        var holder = item.builtInRegistryHolder();
        DataComponentMap components = holder.areComponentsBound() ? holder.components() : null;
        if (previewStack == null || previewComponents != components) {
            previewStack = getDisplayStack();
            previewComponents = components;
        }
        return previewStack;
    }

    public boolean matchesDisplayName(String searchLower) {
        if (searchLower == null || searchLower.isEmpty()) return true;
        getDisplayName();
        return searchName.contains(searchLower);
    }

    public boolean matchesSearch(String searchLower) {
        if (searchLower == null || searchLower.isEmpty()) {
            return true;
        }

        if (matchesDisplayName(searchLower)) {
            return true;
        }

        return targetId.contains(searchLower);
    }
    
    @Override
    public String toString() {
        return "SpeedrunTarget{" + getTargetId() + "}";
    }
}
