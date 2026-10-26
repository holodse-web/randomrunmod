package com.randomrun.features.challenges.classic.world;

import net.minecraft.world.item.Item;

public class WorldNameGenerator {
    private static final String PREFIX = "RandomRun: ";
    
    public static String generate(Item item, WorldMode mode) {
        if (item == null) {
            throw new IllegalArgumentException("Item не может быть null");
        }
        String itemName = net.minecraft.network.chat.Component.translatable(item.getDescriptionId()).getString();
        return PREFIX + itemName + " (" + mode.getDisplayName() + ")";
    }
    
    public static String generate(Item item, WorldMode mode, long timeLimitMs) {
        String baseName = generate(item, mode);
        if (timeLimitMs > 0) {
            int seconds = (int) (timeLimitMs / 1000);
            return baseName.replace(")", " - " + seconds + "s)");
        }
        return baseName;
    }
}
