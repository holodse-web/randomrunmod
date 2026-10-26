package com.randomrun.infrastructure.security;

import java.util.Random;

public final class AntiCheatTickVerification {
    private static int checks;

    public static int verify() {
        checks = 0;
        for (double value : new double[] {
            0, -0.0, Double.MIN_VALUE, Double.MIN_NORMAL, -Double.MIN_VALUE, -1,
            Double.MAX_VALUE, -Double.MAX_VALUE, Double.NaN,
            Double.POSITIVE_INFINITY, Double.NEGATIVE_INFINITY
        }) verifyComparisons(value);

        for (double threshold : new double[] {1.2, 15.0}) {
            double square = threshold * threshold;
            double lower = square;
            double upper = square;
            for (int i = 0; i < 32; i++) {
                verifyComparisons(lower);
                verifyComparisons(upper);
                lower = Math.nextDown(lower);
                upper = Math.nextUp(upper);
            }
            verifyComparisons(Math.nextDown(threshold) * Math.nextDown(threshold));
            verifyComparisons(Math.nextUp(threshold) * Math.nextUp(threshold));
        }

        Random random = new Random(263_270);
        for (int i = 0; i < 10_000; i++) {
            verifyComparisons(Double.longBitsToDouble(random.nextLong()));
            double dx = random.nextDouble(-2, 2);
            double dy = random.nextDouble(-20, 20);
            double dz = random.nextDouble(-2, 2);
            verifyComparisons(dx * dx + dz * dz);
            verifyComparisons(dx * dx + dy * dy + dz * dz);
        }
        return checks;
    }

    public static void main(String[] args) {
        System.out.println("Anti-cheat tick PASS " + verify() + " checks");
    }

    private static void verifyComparisons(double squaredDistance) {
        check(AntiCheatService.exceedsSpeedLimit(squaredDistance) == (Math.sqrt(squaredDistance) > 1.2),
            "speed threshold changed for " + Double.toHexString(squaredDistance));
        check(AntiCheatService.exceedsTeleportLimit(squaredDistance) == (Math.sqrt(squaredDistance) > 15.0),
            "teleport threshold changed for " + Double.toHexString(squaredDistance));
    }

    private static void check(boolean condition, String message) {
        checks++;
        if (!condition) throw new AssertionError(message);
    }
}
