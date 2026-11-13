package com.randomrun.ui.util;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.resources.Identifier;

public final class TooltipStyle {
    private static long shownAt;
    private static long lastFrame;

    private TooltipStyle() {}

    public static boolean active() {
        Screen screen = Minecraft.getInstance().gui.screen();
        return com.randomrun.infrastructure.util.ModClasses.isOwn(screen);
    }

    public static boolean draw(GuiGraphicsExtractor graphics, int x, int y, int width, int height, Identifier style) {
        if (style != null || !active()) return false;
        long now = System.currentTimeMillis();
        if (now - lastFrame > 500) shownAt = now;
        lastFrame = now;
        float t = Ui.easeOut((now - shownAt) / 140f);
        Theme theme = Theme.current();
        int left = x - 4, top = y - 4, right = x + width + 4, bottom = y + height + 4;
        for (int i = 1; i <= 3; i++) {
            int a = (int) ((4 - i) * 22 * t);
            graphics.fill(left - i, top - i + 1, right + i, bottom + i, a << 24);
        }
        int bg = Ui.lerp(0xFF000000 | theme.panel, 0xFF0A0A0C, 0.55f);
        graphics.fill(left, top, right, bottom, Ui.argb(bg, 0.96f * t));
        graphics.fillGradient(left, top, right, top + Math.min(14, height + 10), Ui.argb(theme.buttonHover, 0.22f * t), Ui.argb(theme.buttonHover, 0f));
        Ui.border(graphics, left, top, right, bottom, 1, Ui.argb(theme.buttonHover, 0.85f * t));
        graphics.fill(left + 1, top + 1, right - 1, top + 2, Ui.argb(theme.accent, 0.9f * t));
        return true;
    }
}
