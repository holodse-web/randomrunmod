package com.randomrun.ui.util;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;

public final class BackgroundRenderer {
    private static final BackgroundRenderer INSTANCE = new BackgroundRenderer();
    private GuiQuadBatch texture;
    private int cachedWidth, cachedHeight;
    private Theme cachedTheme;

    private BackgroundRenderer() {}
    public static BackgroundRenderer getInstance() { return INSTANCE; }

    public void extractBackground(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        renderBackgroundOnly(context);
    }

    public void renderVeil(GuiGraphicsExtractor context, float alpha) {
        if (alpha <= .004f) return;
        Minecraft mc = Minecraft.getInstance();
        int width = UiScale.active() ? UiScale.width() : mc.getWindow().getGuiScaledWidth();
        int height = UiScale.active() ? UiScale.height() : mc.getWindow().getGuiScaledHeight();
        context.fill(0, 0, width, height, Ui.argb(0x171610, alpha));
    }

    public void renderBackgroundOnly(GuiGraphicsExtractor context) {
        Minecraft mc = Minecraft.getInstance();
        int width = UiScale.active() ? UiScale.width() : mc.getWindow().getGuiScaledWidth();
        int height = UiScale.active() ? UiScale.height() : mc.getWindow().getGuiScaledHeight();
        renderBackground(context, width, height);
    }

    public void renderBackground(GuiGraphicsExtractor context, int width, int height) {
        Theme theme = Theme.current();
        if (!InterfaceStyle.isClassic()) {
            int middle = height / 2;
            context.fillGradient(0, 0, width, middle, 0xFF000000 | theme.bgTop, 0xFF000000 | theme.bgMid);
            context.fillGradient(0, middle, width, height, 0xFF000000 | theme.bgMid, 0xFF000000 | theme.bgBottom);
            context.fillGradient(0, 0, width, Math.min(60, height), 0xFF000000, 0x00000000);
            return;
        }
        if (texture == null || cachedWidth != width || cachedHeight != height || cachedTheme != theme) {
            GuiQuadBatch.Builder batch = new GuiQuadBatch.Builder(Math.max(4, width * height / 16));
            batch.fill(0, 0, width, height, 0xFF000000 | theme.bgMid);
            for (int y = 0; y < height; y += 4) {
                for (int x = 0; x < width; x += 4) {
                    int seed = ((x / 4) & 7) * 73428767 ^ ((y / 4) & 7) * 912931;
                    seed ^= seed >>> 13;
                    float tone = (seed & 15) / 15f;
                    int color = Ui.lerp(0xFF000000 | theme.bgTop, 0xFF000000 | theme.bgMid, tone);
                    batch.fill(x, y, Math.min(width, x + 4), Math.min(height, y + 4), color);
                    if ((seed & 31) == 7) batch.fill(x, y, Math.min(width, x + 2), Math.min(height, y + 2), 0xFF000000 | theme.bgBottom);
                }
            }
            texture = batch.build();
            cachedWidth = width; cachedHeight = height; cachedTheme = theme;
        }
        texture.extract(context);

    }
}
