package com.randomrun.features.upgrader.client;

import com.randomrun.features.upgrader.UpgraderMath;

public final class UpgraderWheel {
    private UpgraderWheel() {}

    public static double spinTurns(double progress, double roll, double chance) {
        return spinTurns(progress, roll, chance, 0);
    }

    public static double spinTurns(double progress, double roll, double chance, double startTurn) {
        if (!Double.isFinite(progress)) throw new IllegalArgumentException("Invalid spin progress");
        double start = wrap01(startTurn);
        double end = stoppedTurn(roll, chance);
        double t = Math.clamp(progress, 0.0, 1.0);
        if (t >= 1.0) return end;
        double acceleration = .12;
        double travel = t < acceleration ? t * t / (2 * acceleration) : t - acceleration / 2;
        double phase = travel / (1 - acceleration / 2);
        double eased = 1 - Math.pow(1 - phase, 3);
        double delta = wrap01(end - start);
        return start + (10 + delta) * eased;
    }

    public static double stoppedTurn(double roll, double chance) {
        return UpgraderMath.wheelTurn(roll, chance);
    }

    public static double presentationRoll(double actualRoll, double chance, double presentationSample) {
        UpgraderMath.wheelTurn(actualRoll, chance);
        if (chance <= 0 || !Double.isFinite(presentationSample)
                || presentationSample < 0 || presentationSample >= 1) {
            throw new IllegalArgumentException("Invalid presentation sample");
        }
        if (UpgraderMath.wins(actualRoll, chance) || presentationSample >= .20) return actualRoll;
        double gap = .004 + .008 * (presentationSample / .20);
        return 1 - gap;
    }

    public static double wrap01(double turn) {
        if (!Double.isFinite(turn)) throw new IllegalArgumentException("Invalid wheel angle");
        double wrapped = turn - Math.floor(turn);
        return wrapped >= 1 ? 0 : wrapped;
    }
}
