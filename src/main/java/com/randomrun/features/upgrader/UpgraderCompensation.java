package com.randomrun.features.upgrader;

import java.util.ArrayList;
import java.util.List;
import java.util.random.RandomGenerator;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public final class UpgraderCompensation {
    public static final long MINIMUM_STAKE = 500;
    public static final long MAXIMUM_VALUE = 1800;
    private static final List<Item> PRIZES = List.of(Items.STICK, Items.TORCH, Items.ARROW,
        Items.WHEAT_SEEDS, Items.WHEAT, Items.APPLE, Items.BREAD, Items.COAL,
        Items.IRON_NUGGET, Items.COPPER_INGOT, Items.IRON_INGOT);

    private UpgraderCompensation() {}

    public static long budget(long stake) {
        return stake < MINIMUM_STAKE ? 0 : Math.min(MAXIMUM_VALUE, stake / 20);
    }

    public static ItemStack choose(long stake, Item runTarget, Item upgradeTarget, RandomGenerator random) {
        long budget = budget(stake);
        if (budget <= 0) return ItemStack.EMPTY;
        List<ItemStack> options = new ArrayList<>();
        long bestValue = 0;
        for (Item item : PRIZES) {
            if (item == runTarget || item == upgradeTarget) continue;
            ItemStack stack = new ItemStack(item);
            long unit = UpgraderValues.value(stack);
            if (unit <= 0 || unit > budget) continue;
            int count = (int) Math.min(Math.min(4, stack.getMaxStackSize()), budget / unit);
            stack.setCount(count);
            long value = UpgraderValues.value(stack);
            if (value <= 0 || value > budget) continue;
            bestValue = Math.max(bestValue, value);
            options.add(stack);
        }
        if (options.isEmpty()) return ItemStack.EMPTY;
        long minimumValue = Math.max(1, bestValue * 3 / 5);
        options.removeIf(stack -> UpgraderValues.value(stack) < minimumValue);
        return options.get(random.nextInt(options.size())).copy();
    }
}
