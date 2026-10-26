package com.randomrun.features.challenges.classic.data;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.world.item.Item;

public class SpeedrunTargetProvider {
    public static List<SpeedrunTarget> getAllTargets(boolean includeUnobtainableItems) {
        List<SpeedrunTarget> targets = new ArrayList<>();

        List<Item> items = ItemDifficulty.getAllItems(includeUnobtainableItems);
        for (Item item : items) {
            targets.add(new SpeedrunTarget(item));
        }
        
        return targets;
    }

    public static SpeedrunTarget getRandomTarget(boolean includeUnobtainableItems) {
        List<Item> items = ItemDifficulty.getAllItems(includeUnobtainableItems);
        if (items.isEmpty()) {
            return null;
        }
        return new SpeedrunTarget(items.get(new java.util.Random().nextInt(items.size())));
    }

    public static List<SpeedrunTarget> getTargetsForDifficulty(ItemDifficulty.Difficulty difficulty, boolean includeUnobtainableItems) {
        List<SpeedrunTarget> targets = new ArrayList<>();

        List<Item> items = includeUnobtainableItems
            ? ItemDifficulty.getAllItems(true)
            : ItemDifficulty.getItemsForDifficulty(difficulty);
        for (Item item : items) {
            if (ItemDifficulty.getDifficulty(item) == difficulty
                    && (includeUnobtainableItems || !ItemDifficulty.isUnobtainable(item))) {
                targets.add(new SpeedrunTarget(item));
            }
        }
        
        return targets;
    }
}
