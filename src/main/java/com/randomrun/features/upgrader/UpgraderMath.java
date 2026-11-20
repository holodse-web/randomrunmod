package com.randomrun.features.upgrader;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.random.RandomGenerator;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public final class UpgraderMath {
    public static final double MAX_CHANCE = 0.75;
    public static final long SPIN_MILLIS = 6000;
    public static final long RESULT_HOLD_MILLIS = 350;
    public static final double BONUS_APPEAR_CHANCE = 0.25;
    public static final long FAST_SPIN_MILLIS = 2600;
    public static final long STREAK_STEP_MILLIS = 4200;
    public static final long FAST_STREAK_STEP_MILLIS = 1800;
    public static final double STREAK_WIN_CHANCE = .65;
    public static final int MAX_STREAK = 5;

    public static double bonusSize(double chance) {
        if (!Double.isFinite(chance) || chance <= 0) return 0;
        return chance <= .05 ? chance * .4 : Math.min(.10, chance * .5);
    }

    public static boolean inBonus(double roll, double chance, double size) {
        if (size <= 0 || !Double.isFinite(roll)) return false;
        double start = chance / 2.0 - size / 2.0;
        return roll >= start && roll < start + size;
    }

    public static long spinMillis(boolean fast) {
        return fast ? FAST_SPIN_MILLIS : SPIN_MILLIS;
    }

    public static long stepMillis(boolean fast) {
        return fast ? FAST_STREAK_STEP_MILLIS : STREAK_STEP_MILLIS;
    }

    public static long totalMillis(int flips, boolean fast) {
        return spinMillis(fast) + Math.max(0, flips) * stepMillis(fast);
    }

    public static ItemStack doubled(ItemStack current, Item runTarget, Set<Item> used, RandomGenerator random) {
        long value = UpgraderValues.value(current);
        if (value <= 0) return ItemStack.EMPTY;
        if (current.getItem() == runTarget) {
            int limit = Math.max(1, current.getMaxStackSize()) * 16;
            return current.getCount() * 2L <= limit ? current.copyWithCount(current.getCount() * 2) : ItemStack.EMPTY;
        }
        double want = value * 2.0;
        List<ItemStack> fits = new ArrayList<>();
        for (Item item : UpgraderValues.targets()) {
            if (item == runTarget || used.contains(item)) continue;
            long unit = UpgraderValues.unitValue(item);
            if (unit <= 0) continue;
            long count = Math.max(1, Math.round(want / unit));
            if (count > Math.clamp(item.getDefaultMaxStackSize(), 1, 64)) continue;
            double ratio = unit * count / (double) value;
            if (ratio >= 1.7 && ratio <= 2.6) fits.add(new ItemStack(item, (int) count));
        }
        if (fits.isEmpty()) return ItemStack.EMPTY;
        for (int i = fits.size() - 1; i > 0; i--) {
            int j = random.nextInt(i + 1);
            fits.set(j, fits.set(i, fits.get(j)));
        }
        fits.sort(Comparator.comparingInt(ItemStack::getCount));
        return fits.get(random.nextInt(Math.min(12, fits.size())));
    }

    private UpgraderMath() {}

    public static double chance(long stake, long target) {
        if (stake <= 0 || target <= 0) return 0.0;
        return Math.min(MAX_CHANCE, (double) stake / target);
    }

    public static boolean wins(double roll, double chance) {
        if (!Double.isFinite(roll) || roll < 0.0 || roll >= 1.0
                || !Double.isFinite(chance) || chance <= 0.0 || chance > MAX_CHANCE) {
            return false;
        }
        return roll < chance;
    }

    public static double wheelTurn(double roll, double chance) {
        if (!Double.isFinite(roll) || roll < 0 || roll >= 1
                || !Double.isFinite(chance) || chance < 0 || chance > MAX_CHANCE) {
            throw new IllegalArgumentException("Invalid roulette sample");
        }
        double turn = roll - chance / 2.0;
        return turn < 0 ? turn + 1.0 : turn;
    }

    public static boolean isWinningWheelTurn(double turn, double chance) {
        if (!Double.isFinite(turn) || turn < 0 || turn >= 1
                || !Double.isFinite(chance) || chance <= 0 || chance > MAX_CHANCE) return false;
        return turn < chance / 2.0 || turn >= 1.0 - chance / 2.0;
    }

    public static final class UpgraderCompensation {
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
}
