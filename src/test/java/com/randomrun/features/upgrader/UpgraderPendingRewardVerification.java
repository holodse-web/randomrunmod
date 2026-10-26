package com.randomrun.features.upgrader;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public final class UpgraderPendingRewardVerification {
    private static int checks;
    private static final long MS = 1_000_000L;
    private UpgraderPendingRewardVerification() {}

    public static int verify() {
        checks = 0;
        check(UpgraderMath.SPIN_MILLIS == 6000, "delivery waits for the requested six-second visual");
        var pending = new UpgraderPendingReward(new ItemStack(Items.DIAMOND));
        check(!pending.ready(Long.MAX_VALUE), "a new pending reward never auto-finishes before start");
        check(pending.settle(empty(), Long.MAX_VALUE).isEmpty(), "time alone cannot claim an unstarted animation");
        pending.start(1000 * MS);
        check(!pending.ready(6999 * MS), "not ready at 5999 ms");
        check(pending.settle(empty(), 6999 * MS).isEmpty(), "no item before the needle stops");
        check(pending.ready(7000 * MS), "ready at exactly 6000 ms");
        var delivered = pending.settle(empty(), 7000 * MS).orElseThrow();
        check(count(delivered.inventory(), Items.DIAMOND) == 1 && delivered.overflow().isEmpty(), "one winning item at the deadline");
        check(!pending.ready(7001 * MS), "settled roll is no longer ready");
        pending.skip();
        pending.start(0);
        check(pending.settle(empty(), 7001 * MS).isEmpty(), "timer + close + repeated start cannot duplicate a reward");

        var repeatStart = new UpgraderPendingReward(new ItemStack(Items.EMERALD));
        repeatStart.start(0);
        repeatStart.start(5999 * MS);
        check(repeatStart.settle(empty(), 6000 * MS).isPresent(), "a repeated callback cannot restart the six-second countdown");

        var earlyClose = new UpgraderPendingReward(new ItemStack(Items.DIAMOND));
        earlyClose.skip();
        earlyClose.skip();
        check(earlyClose.ready(0), "closing before animation start remembers skip");
        check(count(earlyClose.settle(empty(), 0).orElseThrow().inventory(), Items.DIAMOND) == 1, "skip before ack/start reveals the already chosen reward immediately");
        earlyClose.start(0);
        check(earlyClose.settle(empty(), 6000 * MS).isEmpty(), "a late start after skip cannot pay again");
        var nextRoll = new UpgraderPendingReward(new ItemStack(Items.DIAMOND));
        check(!nextRoll.ready(7000 * MS), "a new roll does not inherit the previous screen's skip");

        var midClose = new UpgraderPendingReward(new ItemStack(Items.IRON_INGOT, 3));
        midClose.start(1000 * MS);
        midClose.skip();
        check(count(midClose.settle(empty(), 1001 * MS).orElseThrow().inventory(), Items.IRON_INGOT) == 3, "closing during the spin immediately delivers the same held quantity");
        check(midClose.settle(empty(), 7000 * MS).isEmpty(), "later timeout does not duplicate skipped delivery");

        List<ItemStack> changed = empty();
        changed.set(0, new ItemStack(Items.EMERALD, 7));
        changed.set(8, new ItemStack(Items.IRON_INGOT, 13));
        var current = skipped(new ItemStack(Items.DIAMOND));
        var changedResult = current.settle(changed, 0).orElseThrow();
        check(changedResult.inventory().get(0).is(Items.EMERALD) && changedResult.inventory().get(0).getCount() == 7,
            "a pickup occupying the former stake slot survives late settlement");
        check(changedResult.inventory().get(8).getCount() == 13 && count(changedResult.inventory(), Items.DIAMOND) == 1,
            "unrelated current stacks survive while the reward finds a different slot");
        check(changed.get(1).isEmpty() && changed.get(0).getCount() == 7, "planning never mutates the supplied live inventory");
        changedResult.inventory().get(0).shrink(1);
        check(changed.get(0).getCount() == 7, "delivery stacks do not alias the live source stacks");

        List<ItemStack> partial = full();
        partial.set(5, new ItemStack(Items.DIAMOND, 62));
        var merged = skipped(new ItemStack(Items.DIAMOND, 5)).settle(partial, 0).orElseThrow();
        check(merged.inventory().get(5).getCount() == 64, "partial compatible stack receives exactly its free space");
        check(merged.overflow().is(Items.DIAMOND) && merged.overflow().getCount() == 3, "only the uninserted remainder is dropped");
        check(partial.get(5).getCount() == 62, "merging does not mutate the live stack during planning");
        check(count(merged.inventory(), Items.COBBLESTONE) == 35 * 64, "full unrelated slots are never overwritten");

        List<ItemStack> mergeThenEmpty = full();
        mergeThenEmpty.set(0, new ItemStack(Items.DIAMOND, 63));
        mergeThenEmpty.set(4, ItemStack.EMPTY);
        var fits = skipped(new ItemStack(Items.DIAMOND, 4)).settle(mergeThenEmpty, 0).orElseThrow();
        check(fits.inventory().get(0).getCount() == 64 && fits.inventory().get(4).getCount() == 3
                && fits.inventory().get(4).is(Items.DIAMOND) && fits.overflow().isEmpty(), "merge first, then use an empty slot for the remainder");

        ItemStack named = new ItemStack(Items.DIAMOND, 63);
        named.set(DataComponents.CUSTOM_NAME, Component.literal("Keep my components"));
        List<ItemStack> distinct = full();
        distinct.set(1, named);
        var different = skipped(new ItemStack(Items.DIAMOND)).settle(distinct, 0).orElseThrow();
        check(different.inventory().get(1).getCount() == 63 && different.inventory().get(1).has(DataComponents.CUSTOM_NAME),
            "same item with different components is preserved without merging");
        check(different.overflow().getCount() == 1, "unmergeable full inventory drops the whole reward");

        List<ItemStack> full = full();
        var overflowing = skipped(new ItemStack(Items.IRON_INGOT, 2));
        var overflow = overflowing.settle(full, 0).orElseThrow();
        check(count(overflow.inventory(), Items.COBBLESTONE) == 36 * 64 && overflow.overflow().is(Items.IRON_INGOT)
                && overflow.overflow().getCount() == 2, "completely full current inventory keeps all items and drops the exact reward");
        overflowing.skip();
        check(overflowing.settle(full, 1).isEmpty(), "repeated disconnect/close never repeats an overflow drop");

        ItemStack sourceReward = new ItemStack(Items.DIAMOND, 5);
        var copied = skipped(sourceReward);
        ItemStack uiPreview = sourceReward.copy();
        sourceReward.setCount(1);
        var copiedResult = copied.settle(empty(), 0).orElseThrow();
        check(count(copiedResult.inventory(), Items.DIAMOND) == 5, "held reward is copied when the server accepts the outcome");
        check(sourceReward.getCount() == 1 && uiPreview.getCount() == 5, "delivery insertion cannot consume the source or UI reward preview");

        var emptyReward = new UpgraderPendingReward(ItemStack.EMPTY);
        emptyReward.start(0);
        check(emptyReward.settle(empty(), 5999 * MS).isEmpty(), "an uncompensated loss still waits for the reveal");
        var loss = emptyReward.settle(changed, 6000 * MS).orElseThrow();
        check(loss.overflow().isEmpty() && count(loss.inventory(), Items.EMERALD) == 7
                && count(loss.inventory(), Items.DIAMOND) == 0, "empty reward settles once without changing current items");
        check(emptyReward.settle(changed, 6001 * MS).isEmpty(), "empty reward also has exactly-once lifecycle");

        var invalid = skipped(new ItemStack(Items.DIAMOND));
        rejected(() -> invalid.settle(List.of(), 0), "zero-slot inventory is rejected without claiming");
        rejected(() -> invalid.settle(null, 0), "null inventory is rejected without claiming");
        List<ItemStack> tooMany = empty(); tooMany.add(ItemStack.EMPTY);
        rejected(() -> invalid.settle(tooMany, 0), "37-slot inventory is rejected without claiming");
        List<ItemStack> nullSlot = empty(); nullSlot.set(35, null);
        rejected(() -> invalid.settle(nullSlot, 0), "invalid last slot is rejected without claiming or mutating earlier slots");
        check(invalid.ready(0) && count(invalid.settle(empty(), 0).orElseThrow().inventory(), Items.DIAMOND) == 1,
            "valid retry after inventory validation failure still receives the reward once");

        var signedClock = new UpgraderPendingReward(new ItemStack(Items.DIAMOND));
        long nearWrap = Long.MAX_VALUE - 1000 * MS;
        signedClock.start(nearWrap);
        check(!signedClock.ready(nearWrap + 5999 * MS) && signedClock.ready(nearWrap + 6000 * MS),
            "monotonic nanoTime subtraction handles signed clock wrap");
        var negativeClock = new UpgraderPendingReward(new ItemStack(Items.DIAMOND));
        negativeClock.start(-9000 * MS);
        check(negativeClock.ready(-3000 * MS), "negative nanoTime values are valid start timestamps");
        return checks;
    }

    private static UpgraderPendingReward skipped(ItemStack reward) {
        var pending = new UpgraderPendingReward(reward);
        pending.skip();
        return pending;
    }

    private static List<ItemStack> empty() {
        List<ItemStack> result = new ArrayList<>();
        for (int slot = 0; slot < UpgraderTransaction.INVENTORY_SLOTS; slot++) result.add(ItemStack.EMPTY);
        return result;
    }

    private static List<ItemStack> full() {
        List<ItemStack> result = new ArrayList<>();
        for (int slot = 0; slot < UpgraderTransaction.INVENTORY_SLOTS; slot++) result.add(new ItemStack(Items.COBBLESTONE, 64));
        return result;
    }

    private static int count(List<ItemStack> inventory, Item item) {
        return inventory.stream().filter(stack -> stack.is(item)).mapToInt(ItemStack::getCount).sum();
    }

    private static void check(boolean condition, String description) {
        if (!condition) throw new AssertionError("Pending reward: " + description);
        checks++;
    }

    private static void rejected(Runnable action, String description) {
        try { action.run(); }
        catch (IllegalArgumentException expected) { check(true, description); return; }
        throw new AssertionError("Pending reward: " + description);
    }
}
