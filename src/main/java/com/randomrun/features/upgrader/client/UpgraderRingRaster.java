package com.randomrun.features.upgrader.client;

import com.randomrun.features.upgrader.UpgraderMath;

import java.util.ArrayList;
import java.util.List;

public final class UpgraderRingRaster {
    public record Span(int x, int y, int endX, boolean success) {}

    private final int outer;
    private final int inner;
    private final double chance;
    private final List<Span> spans;

    public UpgraderRingRaster(int outer, int inner, double chance) {
        if (inner < 0 || outer <= inner || !Double.isFinite(chance) || chance < 0 || chance > UpgraderMath.MAX_CHANCE)
            throw new IllegalArgumentException("Invalid chance ring");
        this.outer = outer;
        this.inner = inner;
        this.chance = chance;
        List<Span> output = new ArrayList<>();
        long outside = (long) outer * outer;
        long inside = (long) inner * inner;
        for (int y = -outer; y <= outer; y++) {
            int extent = (int) Math.sqrt(outside - (long) y * y);
            int hole = Math.abs(y) < inner ? (int) Math.ceil(Math.sqrt(inside - (long) y * y)) : 0;
            if (hole == 0) append(output, -extent, extent, y, chance);
            else {
                append(output, -extent, -hole, y, chance);
                append(output, hole, extent, y, chance);
            }
        }
        spans = List.copyOf(output);
    }

    private static void append(List<Span> output, int start, int end, int y, double chance) {
        if (start > end) return;
        int runStart = start;
        boolean previous = wins(start, y, chance);
        for (int x = start + 1; x <= end; x++) {
            boolean current = wins(x, y, chance);
            if (current != previous) {
                output.add(new Span(runStart, y, x, previous));
                runStart = x;
                previous = current;
            }
        }
        output.add(new Span(runStart, y, end + 1, previous));
    }

    private static boolean wins(int x, int y, double chance) {
        double turn = (Math.atan2(y, x) / (Math.PI * 2) - .25 + 1) % 1;
        return UpgraderMath.isWinningWheelTurn(turn, chance);
    }

    public boolean matches(int outer, int inner, double chance) {
        return this.outer == outer && this.inner == inner && this.chance == chance;
    }

    public List<Span> spans() { return spans; }
}
