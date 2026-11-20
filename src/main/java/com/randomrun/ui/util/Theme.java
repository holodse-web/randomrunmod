package com.randomrun.ui.util;

import com.randomrun.infrastructure.RandomRunMod;

public enum Theme {
    AMETHYST("amethyst", 0x000000, 0x4B0082, 0x3A0066, 0x150030, 0x6A0DAD, 0x302B63, 0x6930C3, 0xD042FF, 0xB98CFF),
    OCEAN("ocean", 0x00060D, 0x0B3D6B, 0x072845, 0x061626, 0x1E6FB8, 0x15385E, 0x2E7DD1, 0x7DD3FC, 0x5CC8FF),
    EMERALD("emerald", 0x000A04, 0x0B4D2C, 0x06331D, 0x04150C, 0x16A34A, 0x14432B, 0x22A45A, 0xA7F3C0, 0x4ADE80),
    CRIMSON("crimson", 0x0A0002, 0x5C0A14, 0x3A060D, 0x1C0407, 0xB0213A, 0x4A1420, 0xC0283F, 0xFDA4AF, 0xFF6B81),
    AMBER("amber", 0x0A0600, 0x5C3A06, 0x3D2604, 0x1C1203, 0xC27C0E, 0x4D3310, 0xC98A1E, 0xFDE68A, 0xFFC247),
    GRAPHITE("graphite", 0x000000, 0x2A2A2E, 0x18181B, 0x111113, 0x52525B, 0x2A2A2E, 0x52525B, 0xFFFFFF, 0xE4E4E7),
    CLASSIC_AMETHYST("amethyst", 0x28232B, 0x302A33, 0x221F25, 0x353237, 0x777078, 0x666168, 0x7D7580, 0xE8DEEC, 0xC7A7DC),
    CLASSIC_OCEAN("ocean", 0x24282B, 0x2B3033, 0x202426, 0x323638, 0x707B80, 0x606B70, 0x75828A, 0xDEE8E9, 0x86C9D1),
    CLASSIC_EMERALD("emerald", 0x252921, 0x2D3228, 0x21251D, 0x333831, 0x74816B, 0x626F5A, 0x78876E, 0xE0E8D5, 0xA8D477),
    CLASSIC_CRIMSON("crimson", 0x2C2523, 0x352B28, 0x26201F, 0x393330, 0x887169, 0x73615B, 0x8C7770, 0xEDDFD6, 0xE6997E),
    CLASSIC_AMBER("amber", 0x2B261E, 0x352E24, 0x251F19, 0x38342C, 0x85795E, 0x706650, 0x887C61, 0xEDE5D2, 0xE5C56B),
    CLASSIC_GRAPHITE("graphite", 0x29251F, 0x322C24, 0x231F1A, 0x363632, 0x808078, 0x686862, 0x808079, 0xEBEBDD, 0xB8D878);

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
        return all[(ordinal() + 1) % 6 + (InterfaceStyle.isClassic() ? 6 : 0)];
    }

    public Theme previous() {
        Theme[] all = values();
        return all[(ordinal() + 5) % 6 + (InterfaceStyle.isClassic() ? 6 : 0)];
    }

    public static Theme fromId(String id) {
        for (Theme theme : values()) if (theme.id.equals(id)) return theme;
        return AMETHYST;
    }

    public static Theme current() {
        RandomRunMod mod = RandomRunMod.getInstance();
        Theme base = mod == null || mod.getConfig() == null ? AMETHYST : fromId(mod.getConfig().getTheme());
        return InterfaceStyle.isClassic() ? values()[base.ordinal() % 6 + 6] : base;
    }

    public record ButtonPalette(int base, int hover, int border, int overlay) {
        public static final ButtonPalette GREEN = new ButtonPalette(0x1A5C1A, 0x2D8F2D, 0x32CD32, 0xFFFFFF);
        public static final ButtonPalette YELLOW = new ButtonPalette(0x8C7014, 0xC9A030, 0xFFAA00, 0xFFFFFF);
        public static final ButtonPalette RED = new ButtonPalette(0x8C1414, 0xC93030, 0xFF5555, 0xFFFFFF);

        public static ButtonPalette theme() {
            Theme theme = Theme.current();
            return new ButtonPalette(theme.buttonBase, theme.buttonHover,
                InterfaceStyle.isClassic() ? theme.border : theme.buttonHover, theme.overlay);
        }

        public static ButtonPalette of(int base, int hover) {
            return new ButtonPalette(base, hover, hover, 0xFFFFFF);
        }
    }
}
