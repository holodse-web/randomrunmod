package com.randomrun.ui.util;

import com.randomrun.infrastructure.RandomRunMod;
import com.randomrun.ui.theme.Theme;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;

public final class Ui {
    private static final long GLOW_CYCLE_MS = 5000L;
    private static final int GLOW_LENGTH = 30;
    public static final int TEXT = 0xFFFFFFFF;
    public static final int TEXT_MUTED = 0xFFA1A1AA;
    public static final int TEXT_DIM = 0xFF71717A;
    public static final int SUCCESS = 0xFF4ADE80;
    public static final int WARNING = 0xFFFACC15;
    public static final int DANGER = 0xFFF87171;
    public static final int INFO = 0xFF67E8F9;
    public static final int GOLD = 0xFFFFD700;

    private Ui() {}

    public static int argb(int rgb, float alpha) {
        return (Mth.clamp((int) (alpha * 255), 0, 255) << 24) | (rgb & 0xFFFFFF);
    }

    public static int argb(int rgb, int alpha) {
        return (Mth.clamp(alpha, 0, 255) << 24) | (rgb & 0xFFFFFF);
    }

    public static int lerp(int from, int to, float t) {
        t = Mth.clamp(t, 0f, 1f);
        int a = (int) Mth.lerp(t, (from >>> 24) & 0xFF, (to >>> 24) & 0xFF);
        int r = (int) Mth.lerp(t, (from >> 16) & 0xFF, (to >> 16) & 0xFF);
        int g = (int) Mth.lerp(t, (from >> 8) & 0xFF, (to >> 8) & 0xFF);
        int b = (int) Mth.lerp(t, from & 0xFF, to & 0xFF);
        return a << 24 | r << 16 | g << 8 | b;
    }

    public static float easeOut(float t) {
        t = Mth.clamp(t, 0f, 1f);
        return 1f - (1f - t) * (1f - t) * (1f - t);
    }

    public static float easeInOut(float t) {
        t = Mth.clamp(t, 0f, 1f);
        return t < .5f ? 4f * t * t * t : 1f - (float) Math.pow(-2f * t + 2f, 3) / 2f;
    }

    public static boolean inside(double mouseX, double mouseY, int x, int y, int w, int h) {
        return mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;
    }

    public static void shadow(GuiGraphicsExtractor context, int left, int top, int right, int bottom, float fade) {
        GuiQuadBatch.Builder batch = new GuiQuadBatch.Builder(8);
        for (int i = 1; i <= 6; i++) {
            int alpha = (int) ((7 - i) * 6 * fade);
            batch.fill(left - i, top - i, right + i, top, alpha << 24);
            batch.fill(left - i, bottom, right + i, bottom + i, alpha << 24);
            batch.fill(left - i, top, left, bottom, alpha << 24);
            batch.fill(right, top, right + i, bottom, alpha << 24);
        }
        batch.build().extract(context);
    }

    public static void border(GuiGraphicsExtractor context, int left, int top, int right, int bottom, int thickness, int argb) {
        context.fill(left, top, right, top + thickness, argb);
        context.fill(left, bottom - thickness, right, bottom, argb);
        context.fill(left, top + thickness, left + thickness, bottom - thickness, argb);
        context.fill(right - thickness, top + thickness, right, bottom - thickness, argb);
    }

    public static void panel(GuiGraphicsExtractor context, int left, int top, int right, int bottom, float fade) {
        Theme theme = Theme.current();
        panel(context, left, top, right, bottom, theme.panel, theme.border, fade);
    }

    public static float reveal(int left, int top) {
        if (!RandomRunMod.getInstance().getConfig().isAnimationsEnabled()) return 1f;
        if (!(Minecraft.getInstance().gui.screen() instanceof com.randomrun.ui.screen.main.AbstractRandomRunScreen screen)) return 1f;
        int width = Math.max(1, Minecraft.getInstance().getWindow().getGuiScaledWidth());
        long delay = 60L + (long) (Mth.clamp(left / (float) width, 0f, 1f) * 140f) + Mth.clamp(top, 0, 200) / 4L;
        return easeOut((System.currentTimeMillis() - screen.shownAt() - delay) / 520f);
    }

    public static void panel(GuiGraphicsExtractor context, int left, int top, int right, int bottom, int bgRgb, int borderRgb, float fade) {
        float reveal = reveal(left, top);
        shadow(context, left, top, right, bottom, fade * reveal);
        context.fill(left, top, right, bottom, argb(bgRgb, 0xD8 * fade * Math.min(1f, reveal * 1.6f) / 255f));
        if (reveal >= 1f) {
            border(context, left, top, right, bottom, 2, argb(borderRgb, fade));
            return;
        }
        int color = argb(borderRgb, fade);
        int cx = (left + right) / 2, cy = (top + bottom) / 2;
        int hw = Math.round((right - left) / 2f * reveal), hh = Math.round((bottom - top) / 2f * reveal);
        context.fill(cx - hw, top, cx + hw, top + 2, color);
        context.fill(cx - hw, bottom - 2, cx + hw, bottom, color);
        context.fill(left, cy - hh, left + 2, cy + hh, color);
        context.fill(right - 2, cy - hh, right, cy + hh, color);
        int glow = argb(0xFFFFFF, 0.5f * (1f - reveal) * fade);
        context.fill(cx - hw, top, cx + hw, top + 1, glow);
    }

    public static void card(GuiGraphicsExtractor context, int left, int top, int right, int bottom, int accentRgb, float fade) {
        float reveal = reveal(left, top);
        fade *= Math.min(1f, reveal * 1.4f);
        context.fill(left, top, right, bottom, argb(0x000000, 0.45f * fade));
        int half = Math.round((right - left) / 2f * reveal);
        context.fill((left + right) / 2 - half, top, (left + right) / 2 + half, top + 1, argb(accentRgb, 0.9f * fade));
        border(context, left, top, right, bottom, 1, argb(0xFFFFFF, 0.06f * fade));
    }

    public static void selectedItemCard(GuiGraphicsExtractor context, Font font, int left, int top, int right, int bottom,
                                        net.minecraft.world.item.Item item, boolean random, long selectedAt) {
        com.randomrun.ui.theme.Theme theme = com.randomrun.ui.theme.Theme.current();
        boolean empty = item == null && !random;
        int accent = random ? 0xFB923C : empty ? 0x71717A : theme.accent;
        card(context, left, top, right, bottom, accent, 1f);
        int slot = bottom - top - 8;
        int slotLeft = left + 4, slotTop = top + 4;
        context.fill(slotLeft, slotTop, slotLeft + slot, slotTop + slot, argb(0x000000, 0.35f));
        border(context, slotLeft, slotTop, slotLeft + slot, slotTop + slot, 1, argb(accent, 0.35f));
        float pop = selectedAt == 0 ? 1f : easeOut((System.currentTimeMillis() - selectedAt) / 260f);
        if (item == null) {
            float pulse = 0.75f + 0.25f * (float) Math.sin(System.currentTimeMillis() / 240.0);
            centeredScaled(context, font, net.minecraft.network.chat.Component.literal("?"), slotLeft + slot / 2f + 0.5f,
                slotTop + slot / 2f - 8f, 2f, argb(accent, pulse));
        }
        int textLeft = slotLeft + slot + 8;
        int textWidth = right - 6 - textLeft;
        String label = com.randomrun.infrastructure.util.LanguageManager.translate(random ? "randomrun.selected.random_label"
            : empty ? "randomrun.selected.empty_label" : "randomrun.selected.label");
        String name = random ? com.randomrun.infrastructure.util.LanguageManager.translate("randomrun.selected.random")
            : empty ? com.randomrun.infrastructure.util.LanguageManager.translate("randomrun.selected.empty")
            : net.minecraft.network.chat.Component.translatable(item.getDescriptionId()).getString();
        int middle = (top + bottom) / 2;
        GuiText.text(context, font, ellipsize(font, label, textWidth), textLeft, middle - 10, argb(0x8A8A93, 1f), false);
        int nameY = middle + 1 + Math.round((1f - pop) * 6f);
        GuiText.text(context, font, ellipsize(font, name, textWidth), textLeft, nameY, argb(random ? accent : empty ? 0xA1A1AA : 0xFFFFFF, pop), false);
    }

    public static void glowBorder(GuiGraphicsExtractor context, int left, int top, int right, int bottom, int thickness, int glowRgb) {
        if (!RandomRunMod.getInstance().getConfig().isAnimationsEnabled()) return;
        int width = right - left, height = bottom - top, perimeter = 2 * (width + height);
        if (perimeter <= 0) return;
        float head = (System.currentTimeMillis() % GLOW_CYCLE_MS) / (float) GLOW_CYCLE_MS * perimeter;
        GuiQuadBatch.Builder batch = new GuiQuadBatch.Builder(GLOW_LENGTH * 2);
        for (int i = -GLOW_LENGTH; i <= GLOW_LENGTH; i++) {
            int pos = Math.floorMod((int) head + i, perimeter);
            int alpha = (int) (255 * (1f - Math.abs(i) / (float) GLOW_LENGTH));
            if (alpha <= 0) continue;
            int color = alpha << 24 | (glowRgb & 0xFFFFFF);
            if (pos < width) batch.fill(left + pos, top, left + pos + 1, top + thickness, color);
            else if (pos < width + height) { int p = pos - width; batch.fill(right - thickness, top + p, right, top + p + 1, color); }
            else if (pos < 2 * width + height) { int p = pos - width - height; batch.fill(right - p - 1, bottom - thickness, right - p, bottom, color); }
            else { int p = pos - 2 * width - height; batch.fill(left, bottom - p - 1, left + thickness, bottom - p, color); }
        }
        batch.build().extract(context);
    }

    public static void divider(GuiGraphicsExtractor context, int left, int right, int y, int rgb, float fade) {
        int span = right - left;
        if (span <= 0) return;
        int steps = Math.max(1, Math.min(32, span / 4));
        for (int i = 0; i < steps; i++) {
            int x0 = left + span * i / steps, x1 = left + span * (i + 1) / steps;
            float t = (i + .5f) / steps;
            float edge = Math.min(t, 1f - t) * 2f;
            float strength = Math.min(1f, edge * 1.6f);
            context.fill(x0, y, x1, y + 1, argb(rgb, fade * (0.12f + 0.68f * strength)));
        }
    }

    public static int chip(GuiGraphicsExtractor context, Font font, Component text, int x, int y, int bgArgb, int fgArgb) {
        int w = font.width(text) + 8;
        context.fill(x, y, x + w, y + 13, bgArgb);
        GuiText.text(context, font, text, x + 4, y + 3, fgArgb, false);
        return w;
    }

    public static void centeredScaled(GuiGraphicsExtractor context, Font font, Component text, float cx, float y, float scale, int color) {
        context.pose().pushMatrix();
        context.pose().translate(cx, y);
        context.pose().scale(scale, scale);
        GuiText.centeredText(context, font, text, 0, 0, color);
        context.pose().popMatrix();
    }

    public static String ellipsize(Font font, String text, int maxWidth) {
        if (font.width(text) <= maxWidth) return text;
        String dots = "…";
        int end = text.length();
        while (end > 0 && font.width(text.substring(0, end) + dots) > maxWidth) end--;
        return text.substring(0, end) + dots;
    }

    public static void spinner(GuiGraphicsExtractor context, int cx, int cy, int radius, int rgb) {
        long t = System.currentTimeMillis();
        for (int i = 0; i < 8; i++) {
            double angle = Math.PI * 2 * i / 8 + t / 160.0;
            int px = cx + (int) Math.round(Math.cos(angle) * radius);
            int py = cy + (int) Math.round(Math.sin(angle) * radius);
            float alpha = ((i + (t / 120)) % 8) / 8f;
            context.fill(px - 1, py - 1, px + 1, py + 1, argb(rgb, 0.25f + 0.75f * alpha));
        }
    }

    public static void playSound(SoundEvent sound, float pitch, float volume) {
        var config = RandomRunMod.getInstance().getConfig();
        if (!config.isSoundEffectsEnabled()) return;
        Minecraft.getInstance().getSoundManager().play(SimpleSoundInstance.forUI(sound, pitch, volume * config.getSoundVolume() / 100f));
    }

    public static void click() {
        playSound(SoundEvents.UI_BUTTON_CLICK.value(), 1.0f, 0.6f);
    }
}
