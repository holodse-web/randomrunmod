package com.randomrun.features.upgrader;

import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Item;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Collections;
import java.util.IdentityHashMap;

public final class UpgraderTransaction {
    public static final int INVENTORY_SLOTS = 36;
    public static final int MAX_SELECTED_ITEMS = 10;
    public static final int MAX_SELECTED_TYPES = 3;

    private UpgraderTransaction() {}

    public record Plan(long stake, List<ItemStack> lossInventory, List<ItemStack> winInventory) {}
    public record Consumption(long stake, List<ItemStack> inventory) {}

    public static Plan withCompensation(Plan plan, ItemStack compensation) {
        if (compensation.isEmpty()) return plan;
        long value = UpgraderValues.value(compensation);
        if (value <= 0 || value > UpgraderCompensation.budget(plan.stake())) {
            throw new IllegalArgumentException("invalid_compensation");
        }
        List<ItemStack> loss = plan.lossInventory().stream().map(ItemStack::copy)
            .collect(java.util.stream.Collectors.toCollection(ArrayList::new));
        if (!insert(loss, compensation.copy())) throw new IllegalArgumentException("inventory_full");
        return new Plan(plan.stake(), List.copyOf(loss), plan.winInventory());
    }

    public static Plan plan(List<ItemStack> inventory, Map<Integer, Integer> amounts,
                            Map<Integer, ItemStack> expected, ItemStack reward) {
        if (reward.isEmpty()) throw new IllegalArgumentException("invalid_selection");
        Consumption consumption = consume(inventory, amounts, expected);
        List<ItemStack> win = consumption.inventory().stream().map(ItemStack::copy)
            .collect(java.util.stream.Collectors.toCollection(ArrayList::new));
        if (!insert(win, reward.copy())) throw new IllegalArgumentException("inventory_full");
        return new Plan(consumption.stake(), consumption.inventory(), List.copyOf(win));
    }

    public static Consumption consume(List<ItemStack> inventory, Map<Integer, Integer> amounts,
                                      Map<Integer, ItemStack> expected) {
        if (inventory.size() != INVENTORY_SLOTS || amounts.isEmpty() || amounts.size() > INVENTORY_SLOTS) {
            throw new IllegalArgumentException("invalid_selection");
        }
        long selectedItems = 0;
        for (var entry : amounts.entrySet()) {
            Integer slot = entry.getKey();
            Integer count = entry.getValue();
            if (slot == null || count == null || slot < 0 || slot >= INVENTORY_SLOTS || count <= 0) {
                throw new IllegalArgumentException("invalid_selection");
            }
            selectedItems += count;
            if (selectedItems > MAX_SELECTED_ITEMS) throw new IllegalArgumentException("selection_limit");
        }
        List<ItemStack> loss = inventory.stream().map(ItemStack::copy)
            .collect(java.util.stream.Collectors.toCollection(ArrayList::new));
        long stake = 0;
        Set<Item> selectedTypes = Collections.newSetFromMap(new IdentityHashMap<>());
        for (var entry : amounts.entrySet()) {
            Integer slot = entry.getKey();
            Integer count = entry.getValue();
            ItemStack actual = inventory.get(slot);
            ItemStack snapshot = expected.get(slot);
            if (actual.isEmpty() || snapshot == null || count > actual.getCount()
                    || count > actual.getMaxStackSize()
                    || !ItemStack.isSameItemSameComponents(actual, snapshot)) {
                throw new IllegalArgumentException("inventory_changed");
            }
            long contribution = UpgraderValues.value(actual.copyWithCount(count));
            if (contribution <= 0) throw new IllegalArgumentException("forbidden_item");
            selectedTypes.add(actual.getItem());
            if (selectedTypes.size() > MAX_SELECTED_TYPES) throw new IllegalArgumentException("selection_types_limit");
            stake = Math.addExact(stake, contribution);
            loss.get(slot).shrink(count);
        }
        return new Consumption(stake, List.copyOf(loss));
    }

    private static boolean insert(List<ItemStack> inventory, ItemStack reward) {
        for (ItemStack stack : inventory) {
            if (!stack.isEmpty() && ItemStack.isSameItemSameComponents(stack, reward)) {
                int moved = Math.min(reward.getCount(), Math.max(0, stack.getMaxStackSize() - stack.getCount()));
                stack.grow(moved);
                reward.shrink(moved);
                if (reward.isEmpty()) return true;
            }
        }
        for (int slot = 0; slot < inventory.size(); slot++) {
            if (inventory.get(slot).isEmpty()) {
                int moved = Math.min(reward.getCount(), reward.getMaxStackSize());
                inventory.set(slot, reward.copyWithCount(moved));
                reward.shrink(moved);
                if (reward.isEmpty()) return true;
            }
        }
        return false;
    }
}
