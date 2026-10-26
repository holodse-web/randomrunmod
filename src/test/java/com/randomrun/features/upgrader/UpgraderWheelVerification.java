package com.randomrun.features.upgrader;

import com.randomrun.features.upgrader.client.UpgraderWheel;

public final class UpgraderWheelVerification {
    private static int checks;
    private UpgraderWheelVerification() {}

    public static int verify() {
        checks = 0;
        for (double chance : new double[] {0.00001, 0.001, 0.01, 0.35, 0.55, 0.65, 0.75}) {
            boolean honest = true;
            for (int sample = 0; sample < 10000; sample++) {
                double roll = (sample + .5) / 10000.0;
                double underPointer = UpgraderWheel.stoppedTurn(roll, chance);
                honest &= UpgraderMath.isWinningWheelTurn(underPointer, chance) == UpgraderMath.wins(roll, chance);
            }
            check(honest, "rotating needle and stationary disk preserve honest odds at " + chance);
            double winning = chance * .5;
            double losing = chance + (1 - chance) * .5;
            check(UpgraderMath.isWinningWheelTurn(UpgraderWheel.stoppedTurn(winning, chance), chance),
                "tiny winning wedge lands under hairline");
            check(!UpgraderMath.isWinningWheelTurn(UpgraderWheel.stoppedTurn(losing, chance), chance),
                "losing sample remains outside colored sector");
            check(UpgraderWheel.spinTurns(0, winning, chance) == 0, "needle starts at bottom center");
            check(UpgraderWheel.spinTurns(1, winning, chance)
                == UpgraderWheel.stoppedTurn(winning, chance), "spin settles on exact final sector without rounding drift");
            check(UpgraderMath.isWinningWheelTurn(UpgraderWheel.spinTurns(1, 0, chance), chance),
                "inclusive winning boundary preserves a zero roll");
            check(!UpgraderMath.isWinningWheelTurn(UpgraderWheel.spinTurns(1, chance, chance), chance),
                "exclusive boundary does not turn a loss into a win");
            double beforeStop = UpgraderWheel.wrap01(UpgraderWheel.spinTurns(.99999, winning, chance));
            double atStop = UpgraderWheel.stoppedTurn(winning, chance);
            check(circularDistance(beforeStop, atStop) < 1e-9, "no visible position jump on reveal");
        }
        double previous = 0;
        boolean monotone = true;
        for (int frame = 1; frame < 1000; frame++) {
            double rotation = UpgraderWheel.spinTurns(frame / 1000.0, .312, .55);
            monotone &= rotation >= previous;
            previous = rotation;
        }
        check(monotone, "needle rotates in one direction rather than wobbling");
        check(UpgraderWheel.wrap01(-10.25) == .75, "needle turns normalize consistently");
        check(UpgraderWheel.stoppedTurn(.275, .55) == 0, "middle of winning interval points straight down");
        check(!UpgraderMath.isWinningWheelTurn(.5, .75), "maximum chance leaves a genuine losing sector");
        check(!UpgraderMath.isWinningWheelTurn(0, .95), "obsolete 95 percent chance is invalid");
        rejected(() -> UpgraderWheel.stoppedTurn(.1, .95), "needle refuses an obsolete chance above the cap");
        verifyStartingPosition();
        verifyPresentation();
        return checks;
    }

    private static void verifyStartingPosition() {
        for (double start : new double[] {0, .1, .5, .99}) {
            boolean monotone = true, continuous = true, exactStops = true, sameOutcome = true;
            for (double chance : new double[] {.00001, .01, .3, .5, .75}) {
                for (double roll : new double[] {0, chance * .5, chance, Math.nextDown(1.0)}) {
                    check(UpgraderWheel.spinTurns(0, roll, chance, start) == start,
                        "spin starts at its previous stopping position " + start);
                    double previous = start;
                    for (int frame = 1; frame < 1000; frame++) {
                        double rotation = UpgraderWheel.spinTurns(frame / 1000.0, roll, chance, start);
                        monotone &= rotation >= previous;
                        previous = rotation;
                    }
                    double end = UpgraderWheel.stoppedTurn(roll, chance);
                    exactStops &= UpgraderWheel.spinTurns(1, roll, chance, start) == end;
                    continuous &= circularDistance(UpgraderWheel.wrap01(
                        UpgraderWheel.spinTurns(.99999, roll, chance, start)), end) < 1e-9;
                }
                for (int index = 0; index < 10000; index++) {
                    double roll = (index + .5) / 10000.0;
                    double end = UpgraderWheel.spinTurns(1, roll, chance, start);
                    sameOutcome &= UpgraderMath.isWinningWheelTurn(end, chance) == UpgraderMath.wins(roll, chance);
                }
            }
            check(monotone, "saved starting position never reverses clockwise motion at " + start);
            check(continuous, "saved starting position has no jump on the final reveal at " + start);
            check(exactStops, "saved starting position cannot introduce endpoint rounding drift at " + start);
            check(sameOutcome, "saved starting position preserves the actual probability at " + start);
        }
        double start = 0;
        for (double roll : new double[] {.2, .9, .1}) {
            check(UpgraderWheel.spinTurns(0, roll, .5, start) == start,
                "three consecutive spins resume the exact previous stop");
            start = UpgraderWheel.spinTurns(1, roll, .5, start);
            check(start == UpgraderWheel.stoppedTurn(roll, .5), "chained spin still stops at the sampled sector");
        }
        double presentation = UpgraderWheel.presentationRoll(.9, .75, .1);
        check(!UpgraderMath.isWinningWheelTurn(UpgraderWheel.spinTurns(1, presentation, .75, start), .75),
            "a cosmetic near miss remains a loss when resumed from an earlier stop");
        check(UpgraderWheel.spinTurns(0, .9, .75, -10.25) == .75, "negative starting turns normalize");
        check(UpgraderWheel.spinTurns(-1, .9, .75, 2.1) == UpgraderWheel.wrap01(2.1),
            "progress below zero holds the normalized starting position");
        check(UpgraderWheel.spinTurns(2, .9, .75, .99) == UpgraderWheel.stoppedTurn(.9, .75),
            "progress above one preserves the exact stop");
        check(UpgraderWheel.spinTurns(.3, .2, .5) == UpgraderWheel.spinTurns(.3, .2, .5, 0),
            "the original three-argument API still starts at bottom");
        rejected(() -> UpgraderWheel.spinTurns(.2, .9, .75, Double.NaN), "invalid starting position rejected");
        rejected(() -> UpgraderWheel.spinTurns(.2, .9, .75, Double.POSITIVE_INFINITY), "infinite starting position rejected");
    }

    private static void verifyPresentation() {
        for (double chance : new double[] {1e-12, .00001, .01, .35, .55, .65, .75}) {
            int nearMisses = 0;
            double actualLoss = chance + (1 - chance) * .5;
            boolean bounded = true, winnersUnchanged = true, deterministic = true;
            for (int index = 0; index < 10000; index++) {
                double sample = (index + .5) / 10000.0;
                double displayed = UpgraderWheel.presentationRoll(actualLoss, chance, sample);
                if (displayed != actualLoss) {
                    nearMisses++;
                    double turn = UpgraderWheel.stoppedTurn(displayed, chance);
                    double beforeWinningSector = (1 - chance / 2) - turn;
                    bounded &= displayed >= .988 && displayed <= .996
                        && beforeWinningSector >= .004 - 1e-15 && beforeWinningSector < .012 + 1e-15
                        && !UpgraderMath.isWinningWheelTurn(turn, chance);
                }
                double actualWin = chance * sample;
                winnersUnchanged &= UpgraderWheel.presentationRoll(actualWin, chance, sample) == actualWin;
                deterministic &= UpgraderWheel.presentationRoll(actualLoss, chance, sample) == displayed;
            }
            check(nearMisses == 2000, "exactly twenty percent of loss presentation samples use near misses at " + chance);
            check(bounded, "near miss stays just before the success sector with a nonzero losing gap at " + chance);
            check(winnersUnchanged, "all winning positions remain unchanged at " + chance);
            check(deterministic, "one stored presentation sample always produces the same stop at " + chance);

            boolean sameOutcome = true, ordinaryLossesUnchanged = true;
            for (int index = 0; index < 10000; index++) {
                double roll = (index + .5) / 10000.0;
                for (double sample : new double[] {0, .1, Math.nextDown(.2), .2, .8, Math.nextDown(1.0)}) {
                    double displayed = UpgraderWheel.presentationRoll(roll, chance, sample);
                    sameOutcome &= UpgraderMath.wins(roll, chance) == UpgraderMath.wins(displayed, chance)
                        && UpgraderMath.wins(roll, chance)
                            == UpgraderMath.isWinningWheelTurn(UpgraderWheel.stoppedTurn(displayed, chance), chance);
                    if (sample >= .2) ordinaryLossesUnchanged &= displayed == roll;
                }
            }
            check(sameOutcome, "cosmetic positioning never changes a win or boosts the chance at " + chance);
            check(ordinaryLossesUnchanged, "other eighty percent of positions retain the exact raw sample at " + chance);
            check(!UpgraderMath.isWinningWheelTurn(UpgraderWheel.stoppedTurn(
                    UpgraderWheel.presentationRoll(chance, chance, 0), chance), chance),
                "an exact losing probability boundary stays a loss after presentation at " + chance);
        }
        rejected(() -> UpgraderWheel.presentationRoll(.9, .75, -.001), "negative presentation sample rejected");
        rejected(() -> UpgraderWheel.presentationRoll(.9, .75, 1), "presentation sample one excluded from uniform range");
        rejected(() -> UpgraderWheel.presentationRoll(.9, .75, Double.NaN), "NaN presentation sample rejected");
        rejected(() -> UpgraderWheel.presentationRoll(.9, .75, Double.POSITIVE_INFINITY), "infinite presentation sample rejected");
        rejected(() -> UpgraderWheel.presentationRoll(1, .75, .1), "invalid authoritative roll rejected");
        rejected(() -> UpgraderWheel.presentationRoll(Double.NaN, .75, .1), "NaN authoritative roll rejected");
        rejected(() -> UpgraderWheel.presentationRoll(.9, 0, .1), "zero chance cannot produce a presentation roll");
        rejected(() -> UpgraderWheel.presentationRoll(.9, .95, .1), "chance above the cap remains invalid in presentation");
    }

    private static double circularDistance(double left, double right) {
        double distance = Math.abs(left - right);
        return Math.min(distance, 1 - distance);
    }

    private static void check(boolean success, String description) {
        if (!success) throw new AssertionError(description);
        checks++;
    }

    private static void rejected(Runnable operation, String description) {
        try { operation.run(); }
        catch (IllegalArgumentException expected) { check(true, description); return; }
        throw new AssertionError(description);
    }
}
