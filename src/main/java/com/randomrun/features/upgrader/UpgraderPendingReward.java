package com.randomrun.features.upgrader;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import net.minecraft.world.item.ItemStack;

public final class UpgraderPendingReward {
    private static final long DURATION_NANOS = UpgraderMath.SPIN_MILLIS * 1_000_000L;
    private final ItemStack reward;
    private boolean started;
    private long startedNanos;
    private boolean skipped;
    private boolean settled;

    public UpgraderPendingReward(ItemStack reward) {
        this.reward = Objects.requireNonNull(reward, "reward").copy();
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
        return !settled && (skipped || started && nowNanos - startedNanos >= DURATION_NANOS);
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
        ItemStack remaining = reward.copy();
        if (!remaining.isEmpty()) {
            for (ItemStack stack : inventory) {
                if (stack.isEmpty() || !ItemStack.isSameItemSameComponents(stack, remaining)) continue;
                int moved = Math.min(remaining.getCount(), Math.max(0, stack.getMaxStackSize() - stack.getCount()));
                stack.grow(moved);
                remaining.shrink(moved);
                if (remaining.isEmpty()) break;
            }
            for (int slot = 0; slot < inventory.size() && !remaining.isEmpty(); slot++) {
                if (!inventory.get(slot).isEmpty()) continue;
                int moved = Math.min(remaining.getCount(), remaining.getMaxStackSize());
                inventory.set(slot, remaining.copyWithCount(moved));
                remaining.shrink(moved);
            }
        }
        Delivery delivery = new Delivery(List.copyOf(inventory), remaining.copy());
        settled = true;
        return Optional.of(delivery);
    }

    public record Delivery(List<ItemStack> inventory, ItemStack overflow) {}
}
