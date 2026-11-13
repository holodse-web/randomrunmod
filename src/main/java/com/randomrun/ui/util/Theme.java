package com.randomrun.ui.util;

import com.randomrun.infrastructure.RandomRunMod;

public enum Theme {
    AMETHYST("amethyst", 0x000000, 0x4B0082, 0x3A0066, 0x150030, 0x6A0DAD, 0x302B63, 0x6930C3, 0xD042FF, 0xB98CFF),
    OCEAN("ocean", 0x00060D, 0x0B3D6B, 0x072845, 0x061626, 0x1E6FB8, 0x15385E, 0x2E7DD1, 0x7DD3FC, 0x5CC8FF),
    EMERALD("emerald", 0x000A04, 0x0B4D2C, 0x06331D, 0x04150C, 0x16A34A, 0x14432B, 0x22A45A, 0xA7F3C0, 0x4ADE80),
    CRIMSON("crimson", 0x0A0002, 0x5C0A14, 0x3A060D, 0x1C0407, 0xB0213A, 0x4A1420, 0xC0283F, 0xFDA4AF, 0xFF6B81),
    AMBER("amber", 0x0A0600, 0x5C3A06, 0x3D2604, 0x1C1203, 0xC27C0E, 0x4D3310, 0xC98A1E, 0xFDE68A, 0xFFC247),
    GRAPHITE("graphite", 0x000000, 0x2A2A2E, 0x18181B, 0x111113, 0x52525B, 0x2A2A2E, 0x52525B, 0xFFFFFF, 0xE4E4E7);

    public final String id;
    public final int bgTop, bgMid, bgBottom;
    public final int panel, border;
    public final int buttonBase, buttonHover, overlay;
    public final int accent;

    Theme(String id, int bgTop, int bgMid, int bgBottom, int panel, int border, int buttonBase, int buttonHover, int overlay, int accent) {
        this.id = id;
        this.bgTop = bgTop;
        this.bgMid = bgMid;
        this.bgBottom = bgBottom;
        this.panel = panel;
        this.border = border;
        this.buttonBase = buttonBase;
        this.buttonHover = buttonHover;
        this.overlay = overlay;
        this.accent = accent;
    }

    public String nameKey() {
        return "randomrun.theme." + id;
    }

    public Theme next() {
        Theme[] all = values();
        return all[(ordinal() + 1) % all.length];
    }

    public static Theme fromId(String id) {
        for (Theme theme : values()) if (theme.id.equals(id)) return theme;
        return AMETHYST;
    }

    public static Theme current() {
        RandomRunMod mod = RandomRunMod.getInstance();
        return mod == null || mod.getConfig() == null ? AMETHYST : fromId(mod.getConfig().getTheme());
    }

    public record ButtonPalette(int base, int hover, int border, int overlay) {
        public static final ButtonPalette GREEN = new ButtonPalette(0x1A5C1A, 0x2D8F2D, 0x32CD32, 0xFFFFFF);
        public static final ButtonPalette YELLOW = new ButtonPalette(0x8C7014, 0xC9A030, 0xFFAA00, 0xFFFFFF);
        public static final ButtonPalette RED = new ButtonPalette(0x8C1414, 0xC93030, 0xFF5555, 0xFFFFFF);

        public static ButtonPalette theme() {
            Theme theme = Theme.current();
            return new ButtonPalette(theme.buttonBase, theme.buttonHover, theme.buttonHover, theme.overlay);
        }

        public static ButtonPalette of(int base, int hover) {
            return new ButtonPalette(base, hover, hover, 0xFFFFFF);
        }
    }
}
