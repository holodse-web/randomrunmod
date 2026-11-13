package com.randomrun.ui.util;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;

public final class BackgroundRenderer {
    private static final BackgroundRenderer INSTANCE = new BackgroundRenderer();

    private BackgroundRenderer() {}

    public static BackgroundRenderer getInstance() {
        return INSTANCE;
    }

    public void extractBackground(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        renderBackgroundOnly(context);
    }

    public void renderVeil(GuiGraphicsExtractor context, float alpha) {
        if (alpha <= 0.004f) return;
        Minecraft mc = Minecraft.getInstance();
        boolean canvas = com.randomrun.ui.util.UiScale.active();
        int width = canvas ? com.randomrun.ui.util.UiScale.width() : mc.getWindow().getGuiScaledWidth();
        int height = canvas ? com.randomrun.ui.util.UiScale.height() : mc.getWindow().getGuiScaledHeight();
        Theme theme = Theme.current();
        int a = Math.round(Math.min(1f, alpha) * 255) << 24;
        int middle = height / 2;
        context.fillGradient(0, 0, width, middle, a | theme.bgTop, a | theme.bgMid);
        context.fillGradient(0, middle, width, height, a | theme.bgMid, a | theme.bgBottom);
    }

    public void renderBackgroundOnly(GuiGraphicsExtractor context) {
        Minecraft mc = Minecraft.getInstance();
        boolean canvas = com.randomrun.ui.util.UiScale.active();
        int width = canvas ? com.randomrun.ui.util.UiScale.width() : mc.getWindow().getGuiScaledWidth();
        int height = canvas ? com.randomrun.ui.util.UiScale.height() : mc.getWindow().getGuiScaledHeight();
        Theme theme = Theme.current();
        int middle = height / 2;
        context.fillGradient(0, 0, width, middle, 0xFF000000 | theme.bgTop, 0xFF000000 | theme.bgMid);
        context.fillGradient(0, middle, width, height, 0xFF000000 | theme.bgMid, 0xFF000000 | theme.bgBottom);
        context.fillGradient(0, 0, width, Math.min(60, height), 0xFF000000, 0x00000000);
    }
}
