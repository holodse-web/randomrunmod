package com.randomrun.features.upgrader;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import net.minecraft.world.item.ItemStack;

public final class UpgraderPendingReward {
    private final long durationNanos;
    private final List<ItemStack> rewards;
    private boolean started;
    private long startedNanos;
    private boolean skipped;
    private boolean settled;

    public UpgraderPendingReward(ItemStack reward) {
        this(Objects.requireNonNull(reward, "reward").isEmpty() ? List.of() : List.of(reward), UpgraderMath.SPIN_MILLIS);
    }

    public UpgraderPendingReward(List<ItemStack> rewards, long durationMillis) {
        List<ItemStack> copies = new ArrayList<>();
        for (ItemStack stack : Objects.requireNonNull(rewards, "rewards")) if (!stack.isEmpty()) copies.add(stack.copy());
        this.rewards = List.copyOf(copies);
        this.durationNanos = Math.max(0, durationMillis) * 1_000_000L;
    }

    public synchronized void start(long startedNanos) {
        if (!started && !settled) {
            this.startedNanos = startedNanos;
            started = true;
        }
    }

    public synchronized void skip() {
        skipped = true;
    }

    public synchronized boolean ready(long nowNanos) {
        return !settled && (skipped || started && nowNanos - startedNanos >= durationNanos);
    }

    public synchronized Optional<Delivery> settle(List<ItemStack> currentInventory, long nowNanos) {
        if (!ready(nowNanos)) return Optional.empty();
        if (currentInventory == null || currentInventory.size() != UpgraderTransaction.INVENTORY_SLOTS) {
            throw new IllegalArgumentException("invalid_inventory");
        }
        List<ItemStack> inventory = new ArrayList<>(UpgraderTransaction.INVENTORY_SLOTS);
        for (ItemStack stack : currentInventory) {
            if (stack == null) throw new IllegalArgumentException("invalid_inventory");
            inventory.add(stack.copy());
        }
        List<ItemStack> overflow = new ArrayList<>();
        for (ItemStack reward : rewards) {
            ItemStack remaining = reward.copy();
            for (ItemStack stack : inventory) {
                if (remaining.isEmpty()) break;
                if (stack.isEmpty() || !ItemStack.isSameItemSameComponents(stack, remaining)) continue;
                int moved = Math.min(remaining.getCount(), Math.max(0, stack.getMaxStackSize() - stack.getCount()));
                stack.grow(moved);
                remaining.shrink(moved);
            }
            for (int slot = 0; slot < inventory.size() && !remaining.isEmpty(); slot++) {
                if (!inventory.get(slot).isEmpty()) continue;
                int moved = Math.min(remaining.getCount(), remaining.getMaxStackSize());
                inventory.set(slot, remaining.copyWithCount(moved));
                remaining.shrink(moved);
            }
            if (!remaining.isEmpty()) overflow.add(remaining);
        }
        Delivery delivery = new Delivery(List.copyOf(inventory), List.copyOf(overflow));
        settled = true;
        return Optional.of(delivery);
    }

    public record Delivery(List<ItemStack> inventory, List<ItemStack> overflow) {}
}
