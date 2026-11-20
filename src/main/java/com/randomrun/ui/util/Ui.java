package com.randomrun.ui.util;

import com.randomrun.infrastructure.RandomRunMod;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;

public final class Ui {
    public static final int TEXT = 0xFFFFFFFF;
    public static final int TEXT_MUTED = 0xFFA1A1AA;
    public static final int TEXT_DIM = 0xFF71717A;
    public static final int SUCCESS = 0xFF4ADE80;
    public static final int WARNING = 0xFFFACC15;
    public static final int DANGER = 0xFFF87171;
    public static final int INFO = 0xFF67E8F9;
    public static final int GOLD = 0xFFFFD700;

    private Ui() {}

    public static net.minecraft.client.gui.components.Tooltip modeTooltip(boolean enabled, String key, Object... args) {
        String description = com.randomrun.infrastructure.util.LanguageManager.translate(key, args);
        return net.minecraft.client.gui.components.Tooltip.create(com.randomrun.infrastructure.util.LanguageManager.text(
            enabled ? "randomrun.mode.tooltip.on" : "randomrun.mode.tooltip.off", description));
    }

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
        if (InterfaceStyle.isClassic()) {
            context.fill(left + 3, top + 3, right + 3, bottom + 3, argb(0x080807, .55f * fade));
            return;
        }
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

    public static void bevel(GuiGraphicsExtractor g, int left, int top, int right, int bottom,
                             int faceRgb, float fade, boolean inset) {
        if (right <= left || bottom <= top || fade <= 0f) return;
        g.fill(left, top, right, bottom, argb(0x10100E, fade));
        if (right - left < 3 || bottom - top < 3) return;
        g.fill(left + 1, top + 1, right - 1, bottom - 1, argb(faceRgb, fade));
        int light = lerp(0xFF000000 | faceRgb, 0xFFE9E7DB, inset ? .26f : .37f);
        int dark = lerp(0xFF000000 | faceRgb, 0xFF090908, .62f);
        int upper = argb(inset ? dark : light, fade);
        int lower = argb(inset ? light : dark, fade);
        g.fill(left + 1, top + 1, right - 1, top + 2, upper);
        g.fill(left + 1, top + 2, left + 2, bottom - 1, upper);
        g.fill(left + 2, bottom - 2, right - 1, bottom - 1, lower);
        g.fill(right - 2, top + 2, right - 1, bottom - 2, lower);
    }

    public static void slot(GuiGraphicsExtractor g, int left, int top, int right, int bottom, float fade) {
        bevel(g, left, top, right, bottom, 0x242520, fade, true);
    }

    public static void progress(GuiGraphicsExtractor g, int left, int top, int right, int bottom,
                                float progress, int rgb, float fade) {
        slot(g, left, top, right, bottom, fade);
        int end = left + 2 + Math.round(Math.max(0, right - left - 4) * Mth.clamp(progress, 0f, 1f));
        if (end <= left + 2 || bottom - top < 5) return;
        g.fill(left + 2, top + 2, end, bottom - 2, argb(rgb, fade));
        g.fill(left + 2, top + 2, end, top + 3, argb(lerp(0xFF000000 | rgb, 0xFFFFFFFF, .32f), fade));
        for (int x = left + 8; x < end; x += 8) g.fill(x, top + 3, x + 1, bottom - 2, argb(0x17200D, .3f * fade));
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
        if (InterfaceStyle.isClassic()) return 1f;
        if (!RandomRunMod.getInstance().getConfig().isAnimationsEnabled()) return 1f;
        if (!(Minecraft.getInstance().gui.screen() instanceof com.randomrun.ui.screen.main.AbstractRandomRunScreen screen)) return 1f;
        int width = Math.max(1, com.randomrun.ui.util.UiScale.width());
        long delay = 60L + (long) (Mth.clamp(left / (float) width, 0f, 1f) * 140f) + Mth.clamp(top, 0, 200) / 4L;
        return easeOut((System.currentTimeMillis() - screen.shownAt() - delay) / 520f);
    }

    public static float rowReveal(int panelLeft, int panelTop, int row) {
        float reveal = reveal(panelLeft, panelTop);
        if (reveal >= 1f) return 1f;
        return Mth.clamp((reveal - 0.3f) / 0.35f, 0f, 1f);
    }

    public static void panel(GuiGraphicsExtractor context, int left, int top, int right, int bottom, int bgRgb, int borderRgb, float fade) {
        if (InterfaceStyle.isClassic()) {
            shadow(context, left, top, right, bottom, fade);
            int stone = lerp(0xFF000000 | Theme.current().panel, 0xFF000000 | bgRgb, .18f);
            bevel(context, left, top, right, bottom, stone, fade, false);
            return;
        }
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
        int x1 = Math.max(left, cx - hw), x2 = Math.min(right, cx + hw), y1 = Math.max(top, cy - hh), y2 = Math.min(bottom, cy + hh);
        context.fill(x1, top, x2, top + 2, color);
        context.fill(x1, bottom - 2, x2, bottom, color);
        context.fill(left, y1, left + 2, y2, color);
        context.fill(right - 2, y1, right, y2, color);
        int glow = argb(0xFFFFFF, 0.5f * (1f - reveal) * fade);
        context.fill(x1, top, x2, top + 1, glow);
    }

    public static void card(GuiGraphicsExtractor context, int left, int top, int right, int bottom, int accentRgb, float fade) {
        if (InterfaceStyle.isClassic()) {
            slot(context, left, top, right, bottom, fade);
            return;
        }
        float reveal = reveal(left, top);
        fade *= Math.min(1f, reveal * 1.4f);
        context.fill(left, top, right, bottom, argb(0x000000, 0.45f * fade));
        int mid = (left + right) / 2, half = Math.round((right - left) / 2f * reveal);
        context.fill(Math.max(left, mid - half), top, Math.min(right, mid + half), top + 1, argb(accentRgb, 0.9f * fade));
        border(context, left, top, right, bottom, 1, argb(0xFFFFFF, 0.06f * fade));
    }

    public static void selectedItemCard(GuiGraphicsExtractor context, Font font, int left, int top, int right, int bottom,
                                        net.minecraft.world.item.Item item, boolean random, long selectedAt) {
        com.randomrun.ui.util.Theme theme = com.randomrun.ui.util.Theme.current();
        boolean empty = item == null && !random;
        int accent = random ? 0xFB923C : empty ? 0x71717A : theme.accent;
        card(context, left, top, right, bottom, accent, 1f);
        int slot = bottom - top - 8;
        int slotLeft = left + 4, slotTop = top + 4;
        if (InterfaceStyle.isClassic()) slot(context, slotLeft, slotTop, slotLeft + slot, slotTop + slot, 1f);
        else {
            context.fill(slotLeft, slotTop, slotLeft + slot, slotTop + slot, argb(0x000000, .35f));
            border(context, slotLeft, slotTop, slotLeft + slot, slotTop + slot, 1, argb(accent, .35f));
        }
        if (random) mystery(context, font, slotLeft + slot / 2, slotTop + slot / 2, Math.max(1f, slot / 14f), accent);
        float pop = selectedAt == 0 ? 1f : easeOut((System.currentTimeMillis() - selectedAt) / 260f);
        int textLeft = slotLeft + slot + 8;
        int textWidth = right - 6 - textLeft;
        String label = com.randomrun.infrastructure.util.LanguageManager.translate(random ? "randomrun.selected.random_label"
            : empty ? "randomrun.selected.empty_label" : "randomrun.selected.label");
        String name = random ? com.randomrun.infrastructure.util.LanguageManager.translate("randomrun.selected.random")
            : empty ? com.randomrun.infrastructure.util.LanguageManager.translate("randomrun.selected.empty")
            : net.minecraft.network.chat.Component.translatable(item.getDescriptionId()).getString();
        int middle = (top + bottom) / 2;
        int nameColor = argb(random ? accent : empty ? 0xA1A1AA : 0xFFFFFF, pop);
        int shift = Math.round((1f - pop) * 6f);
        if (font.width(name) <= textWidth) {
            GuiText.text(context, font, ellipsize(font, label, textWidth), textLeft, middle - 10, argb(0x8A8A93, 1f), false);
            GuiText.text(context, font, name, textLeft, middle + 1 + shift, nameColor, false);
            return;
        }
        int cut = name.length();
        while (cut > 0 && (font.width(name.substring(0, cut)) > textWidth || (cut < name.length() && name.charAt(cut) != ' '))) cut--;
        if (cut == 0) cut = font.plainSubstrByWidth(name, textWidth).length();
        GuiText.text(context, font, name.substring(0, cut).trim(), textLeft, middle - 10 + shift, nameColor, false);
        GuiText.text(context, font, ellipsize(font, name.substring(cut).trim(), textWidth), textLeft, middle + 1 + shift, nameColor, false);
    }

    public static void glowBorder(GuiGraphicsExtractor context, int left, int top, int right, int bottom, int thickness, int glowRgb) {
    }

    public static void divider(GuiGraphicsExtractor context, int left, int right, int y, int rgb, float fade) {
        if (InterfaceStyle.isClassic()) {
            context.fill(left, y, right, y + 1, argb(0x11110F, fade));
            context.fill(left, y + 1, right, y + 2, argb(0x7B7B70, .4f * fade));
            return;
        }
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

    public static void mystery(GuiGraphicsExtractor context, Font font, int cx, int cy, float scale, int rgb) {
        float pulse = 0.55f + 0.45f * (float) Math.sin(System.currentTimeMillis() / 380.0);
        centeredScaled(context, font, net.minecraft.network.chat.Component.literal("?"), cx + 0.5f * scale, cy - 3.5f * scale, scale, argb(rgb, pulse));
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
        if (InterfaceStyle.isClassic()) {
            int[][] points = {{-1,-1},{0,-1},{1,-1},{1,0},{1,1},{0,1},{-1,1},{-1,0}};
            int step = (int) ((System.currentTimeMillis() / 125L) % 8);
            for (int i = 0; i < points.length; i++) {
                int px = cx + points[i][0] * radius, py = cy + points[i][1] * radius;
                float alpha = .25f + .75f * Math.floorMod(i - step, 8) / 7f;
                context.fill(px - 1, py - 1, px + 1, py + 1, argb(rgb, alpha));
            }
            return;
        }
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

    public static void hoverSound() {
        if (!InterfaceStyle.isClassic()) playSound(SoundEvents.NOTE_BLOCK_BELL.value(), 1.8f, 0.25f);
    }

    public static void click() {
        playSound(SoundEvents.UI_BUTTON_CLICK.value(), 1.0f, 0.6f);
    }
}
