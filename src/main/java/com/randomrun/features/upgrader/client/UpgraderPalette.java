package com.randomrun.features.upgrader.client;

public final class UpgraderPalette {
    public static final int WINDOW = 0xFF171A17;
    public static final int WINDOW_EDGE = 0xFFB48A4B;
    public static final int PANEL = 0xFF242923;
    public static final int EDGE = 0xFF68705B;
    public static final int STAKE = 0xFF2D2B20;
    public static final int STAKE_EDGE = 0xFFB39355;
    public static final int TARGET = 0xFF252D24;
    public static final int TARGET_EDGE = 0xFF9DAB7C;
    public static final int GOLD = 0xFFFFD45C;
    public static final int TEAL = 0xFFE8DEB4;
    public static final int TEXT = 0xFFFFF2D5;
    public static final int MUTED = 0xFFC9CCB8;
    public static final int GREEN = 0xFFA4E39A;
    public static final int RED = 0xFFF09B85;
    public static final int INK = 0xFF090F0B;
    public static final int SLOT = 0xFF323B2E;
    public static final int SLOT_EDGE = 0xFF667258;
    public static final int SLOT_HOVER = 0xFF4B5B3F;
    public static final int SELECTED = 0xFF6D5424;
    public static final int TARGET_SELECTED = 0xFF4D5937;
    public static final int BUTTON = 0xFF3B4A33;
    public static final int BUTTON_EDGE = 0xFFB1BC8C;
    public static final int BUTTON_DISABLED = 0xFF303A2B;
    public static final int BUTTON_DISABLED_EDGE = 0xFF6A7659;
    public static final int PRIMARY = 0xFFD59A32;
    public static final int PRIMARY_DISABLED = 0xFF765323;
    public static final int PRIMARY_DISABLED_EDGE = 0xFFC3954D;
    public static final int HEADER = 0xFF343D2D;
    public static final int DISC = 0xFF1A2119;
    public static final int SUCCESS_FILL = 0xFF1F3D25;
    public static final int FAILURE_FILL = 0xFF422820;
    public static final int RING_TRACK = 0xFF434C3B;
    public static final int RING_EDGE = 0xFFA5AA87;

    private UpgraderPalette() {}

    public static int chanceColor(double chance) {
        if (!Double.isFinite(chance) || chance <= 0) return MUTED;
        double value = Math.clamp(chance, 0, .75);
        return value <= .375 ? blend(RED, GOLD, value / .375)
            : blend(GOLD, GREEN, (value - .375) / .375);
    }

    private static int blend(int from, int to, double progress) {
        int red = (int) Math.round((from >> 16 & 255) + ((to >> 16 & 255) - (from >> 16 & 255)) * progress);
        int green = (int) Math.round((from >> 8 & 255) + ((to >> 8 & 255) - (from >> 8 & 255)) * progress);
        int blue = (int) Math.round((from & 255) + ((to & 255) - (from & 255)) * progress);
        return 0xFF000000 | red << 16 | green << 8 | blue;
    }
}
