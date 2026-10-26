package com.randomrun.features.upgrader;

public final class UpgraderMath {
    public static final double MAX_CHANCE = 0.75;
    public static final long SPIN_MILLIS = 6000;
    public static final long RESULT_HOLD_MILLIS = 350;

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
}
