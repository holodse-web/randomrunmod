package com.randomrun.features.upgrader;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.util.context.ContextMap;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.display.SlotDisplay;

final class UpgraderRecipeDisplayCache {
    private final ContextMap context;
    private final Map<SlotDisplay, List<ItemStack>> stacks = new HashMap<>();
    private int requests, resolutions;

    UpgraderRecipeDisplayCache(ContextMap context) { this.context = context; }

    List<ItemStack> resolve(SlotDisplay display) {
        requests++;
        if (!stable(display)) return resolveUncached(display);
        return stacks.computeIfAbsent(display, this::resolveUncached);
    }

    int requests() { return requests; }
    int resolutions() { return resolutions; }

    private List<ItemStack> resolveUncached(SlotDisplay display) {
        resolutions++;
        return display.resolveForStacks(context);
    }

    private static boolean stable(SlotDisplay display) {
        if (display instanceof SlotDisplay.ItemSlotDisplay || display instanceof SlotDisplay.ItemStackSlotDisplay
                || display instanceof SlotDisplay.TagSlotDisplay || display instanceof SlotDisplay.Empty) return true;
        if (display instanceof SlotDisplay.Composite composite) return composite.contents().stream().allMatch(UpgraderRecipeDisplayCache::stable);
        if (display instanceof SlotDisplay.WithRemainder remainder) return stable(remainder.input()) && stable(remainder.remainder());
        if (display instanceof SlotDisplay.OnlyWithComponent filtered) return stable(filtered.source());
        if (display instanceof SlotDisplay.WithAnyPotion potion) return stable(potion.display());
        return false;
    }
}
