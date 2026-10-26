package com.randomrun.ui.util;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

public final class GuiText {
    private GuiText() {}

    private static int legacyColor(int color) {
        return (color & 0xFC000000) == 0 ? color | 0xFF000000 : color;
    }

    public static void text(GuiGraphicsExtractor graphics, Font font, String text, int x, int y, int color) {
        text(graphics, font, text, x, y, color, true);
    }
    public static void text(GuiGraphicsExtractor graphics, Font font, String text, int x, int y, int color, boolean shadow) {
        graphics.text(font, text, x, y, legacyColor(color), shadow);
    }
    public static void text(GuiGraphicsExtractor graphics, Font font, Component text, int x, int y, int color) {
        text(graphics, font, text, x, y, color, true);
    }
    public static void text(GuiGraphicsExtractor graphics, Font font, Component text, int x, int y, int color, boolean shadow) {
        graphics.text(font, text, x, y, legacyColor(color), shadow);
    }
    public static void text(GuiGraphicsExtractor graphics, Font font, FormattedCharSequence text, int x, int y, int color) {
        text(graphics, font, text, x, y, color, true);
    }
    public static void text(GuiGraphicsExtractor graphics, Font font, FormattedCharSequence text, int x, int y, int color, boolean shadow) {
        graphics.text(font, text, x, y, legacyColor(color), shadow);
    }
    public static void centeredText(GuiGraphicsExtractor graphics, Font font, String text, int x, int y, int color) {
        graphics.centeredText(font, text, x, y, legacyColor(color));
    }
    public static void centeredText(GuiGraphicsExtractor graphics, Font font, Component text, int x, int y, int color) {
        graphics.centeredText(font, text, x, y, legacyColor(color));
    }
    public static void centeredText(GuiGraphicsExtractor graphics, Font font, FormattedCharSequence text, int x, int y, int color) {
        graphics.centeredText(font, text, x, y, legacyColor(color));
    }
}
