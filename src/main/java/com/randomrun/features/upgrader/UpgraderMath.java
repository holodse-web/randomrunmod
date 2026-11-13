package com.randomrun.features.upgrader;

import java.util.ArrayList;
import java.util.List;
import java.util.random.RandomGenerator;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public final class UpgraderMath {
    public static final double MAX_CHANCE = 0.75;
    public static final long SPIN_MILLIS = 6000;
    public static final long RESULT_HOLD_MILLIS = 350;
    public static final double BONUS_APPEAR_CHANCE = 0.25;
    public static final long STREAK_STEP_MILLIS = 1800;
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

    public static long totalMillis(int flips) {
        return SPIN_MILLIS + Math.max(0, flips) * STREAK_STEP_MILLIS;
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
