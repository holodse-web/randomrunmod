package com.randomrun.ui.theme;

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
