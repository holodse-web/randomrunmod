package com.randomrun.features.voice.ui;

import com.randomrun.ui.util.InterfaceStyle;

import com.randomrun.features.voice.control.VoiceController;
import com.randomrun.features.voice.engine.VoiceEngine;
import com.randomrun.infrastructure.ModConfig;
import com.randomrun.infrastructure.RandomRunMod;
import com.randomrun.infrastructure.util.LanguageManager;
import com.randomrun.ui.util.GuiText;
import com.randomrun.ui.util.Icons;
import com.randomrun.ui.util.Theme;
import com.randomrun.ui.util.Ui;

import java.util.List;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;






public final class VoiceHud implements HudElement {
    private static final int COMPASS_WIDTH = 182;
    private static final int COMPASS_HEIGHT = 13;
    private static final float PX_PER_DEGREE = 1.0f;
    private static final int METER_SEGMENTS = 12;
    private static final int METER_HEIGHT = 48;

    private String shownHeard = "";
    private long shownHeardAt;
    private float smoothLevel;
    private float smoothPeak;

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, DeltaTracker tick) {
        VoiceController voice = VoiceController.get();
        Minecraft mc = Minecraft.getInstance();
        if (!voice.isActive() || mc.player == null) return;
        Font font = mc.font;

        float canvas = com.randomrun.ui.util.UiScale.factor();
        int width = com.randomrun.ui.util.UiScale.width();
        int height = com.randomrun.ui.util.UiScale.height();
        g.pose().pushMatrix();
        g.pose().scale(canvas, canvas);
        int top = 3;
        ModConfig config = RandomRunMod.getInstance().getConfig();
        if (config != null && config.getHudPosition() == ModConfig.HudPosition.TOP_CENTER) top += 58;

        float yaw = mc.player.getViewYRot(tick.getGameTimeDeltaPartialTick(true));
        drawCompass(g, font, width / 2, top, yaw);
        drawVoice(g, font, voice, width, height);
        g.pose().popMatrix();
    }



    private void drawCompass(GuiGraphicsExtractor g, Font font, int cx, int top, float yaw) {
        Theme theme = Theme.current();
        int left = cx - COMPASS_WIDTH / 2;
        int bottom = top + COMPASS_HEIGHT;


        if (InterfaceStyle.isClassic()) {
            Ui.bevel(g, left, top, left + COMPASS_WIDTH, bottom + 1, 0x30302B, .92f, true);
        } else {
            for (int x = 0; x < COMPASS_WIDTH; x += 2) {
                float e = edge(x / (float) COMPASS_WIDTH);
                g.fill(left + x, top, left + x + 2, bottom, Ui.argb(0x000000, 0.42f * e));
                g.fill(left + x, bottom, left + x + 2, bottom + 1, Ui.argb(theme.accent, 0.55f * e));
            }
        }

        float heading = Mth.wrapDegrees(yaw);
        float half = COMPASS_WIDTH / 2f / PX_PER_DEGREE;
        int first = (int) Math.floor((heading - half) / 5f) * 5;
        for (int deg = first; deg <= heading + half; deg += 5) {
            float offset = Mth.wrapDegrees(deg - heading) * PX_PER_DEGREE;
            float e = edge((offset + COMPASS_WIDTH / 2f) / COMPASS_WIDTH);
            if (e <= 0.02f) continue;
            int x = Math.round(cx + offset);
            int norm = Math.floorMod(deg, 360);
            if (norm % 90 == 0) {
                int color = norm == 180 ? 0xFF5C5C : 0xFFFFFF;
                GuiText.centeredText(g, font, label(norm), x + 1, top + 3, Ui.argb(color, e));
            } else if (norm % 45 == 0) {
                if (InterfaceStyle.isClassic()) {
                    Ui.centeredScaled(g, font, Component.literal(label(norm)), x, top + 3f, 1f, Ui.argb(0xB4B4BE, 0.9f * e));
                } else {
                    Ui.centeredScaled(g, font, Component.literal(label(norm)), x + 0.5f, top + 4f, 0.7f, Ui.argb(0xB4B4BE, 0.9f * e));
                }
            } else if (norm % 15 == 0) {
                g.fill(x, top + 4, x + 1, bottom - 3, Ui.argb((InterfaceStyle.isClassic() ? 0xE2DFD2 : 0xD4D4D8), 0.55f * e));
            } else {
                g.fill(x, top + 6, x + 1, bottom - 4, Ui.argb((InterfaceStyle.isClassic() ? 0xC0BDB2 : 0xA1A1AA), 0.3f * e));
            }
        }


        int accent = 0xFF000000 | theme.accent;
        g.fill(cx - 2, bottom + 1, cx + 3, bottom + 2, accent);
        g.fill(cx - 1, bottom + 2, cx + 2, bottom + 3, accent);
        g.fill(cx, bottom + 3, cx + 1, bottom + 4, accent);
        g.fill(cx, top, cx + 1, top + 2, Ui.argb(theme.accent, 0.9f));

        int bearing = Math.floorMod(Math.round(heading) + 180, 360);
        if (InterfaceStyle.isClassic()) {
            Ui.centeredScaled(g, font, Component.literal(bearing + "°"), cx, bottom + 5f, 1f, 0xFFE2DFD2);
        } else {
            Ui.centeredScaled(g, font, Component.literal(bearing + "°"), cx + 0.5f, bottom + 5f, 0.75f, 0xFFD4D4D8);
        }
    }


    private static float edge(float t) {
        float d = Mth.clamp(Math.min(t, 1f - t) / 0.28f, 0f, 1f);
        return d * d * (3f - 2f * d);
    }


    private static String label(int deg) {
        return LanguageManager.translate("randomrun.voice.compass." + switch (deg) {
            case 0 -> "s";
            case 90 -> "w";
            case 180 -> "n";
            case 270 -> "e";
            case 45 -> "sw";
            case 135 -> "nw";
            case 225 -> "ne";
            default -> "se";
        });
    }



    private void drawVoice(GuiGraphicsExtractor g, Font font, VoiceController voice, int width, int height) {
        VoiceEngine engine = VoiceEngine.get();
        long now = System.currentTimeMillis();
        VoiceEngine.State state = engine.state();
        boolean hearing = state == VoiceEngine.State.LISTENING && engine.isHearingVoice();
        float rawLevel = engine.level();
        smoothLevel += (rawLevel - smoothLevel) * 0.35f;
        smoothPeak = Math.max(smoothLevel, smoothPeak - 0.012f);


        int meterRight = width - 6;
        int meterLeft = meterRight - 4;
        int meterBottom = height - 6;
        int segment = METER_HEIGHT / METER_SEGMENTS;
        if (InterfaceStyle.isClassic()) {
            Ui.bevel(g, meterLeft - 2, meterBottom - METER_HEIGHT - 2, meterRight + 2, meterBottom + 2, 0x30302B, .95f, true);
        }
        for (int i = 0; i < METER_SEGMENTS; i++) {
            float t = (i + 0.5f) / METER_SEGMENTS;
            int y1 = meterBottom - i * segment;
            int y0 = y1 - segment + 1;
            int color = t < 0.6f ? (InterfaceStyle.isClassic() ? 0xA8D477 : 0x4ADE80) : t < 0.85f ? (InterfaceStyle.isClassic() ? 0xFFD56B : 0xFACC15) : (InterfaceStyle.isClassic() ? 0xFF8B7B : 0xF87171);
            boolean lit = t <= smoothLevel;
            g.fill(meterLeft + 1, y0 + 1, meterRight + 1, y1 + 1, Ui.argb(0x000000, 0.35f));
            g.fill(meterLeft, y0, meterRight, y1, Ui.argb(color, lit ? 1f : 0.16f));
        }
        if (smoothPeak > 0.04f) {
            int py = meterBottom - Math.round(smoothPeak * METER_HEIGHT);
            g.fill(meterLeft - 1, py, meterRight + 1, py + 1, 0xE6FFFFFF);
        }


        int micColor = switch (state) {
            case ERROR -> (InterfaceStyle.isClassic() ? 0xFFFF8B7B : 0xFFFF6B6B);
            case LOADING -> (InterfaceStyle.isClassic() ? 0xFFFFD56B : 0xFFFACC15);
            case LISTENING -> hearing ? (InterfaceStyle.isClassic() ? 0xFFA8D477 : 0xFF7CF29A) : 0xFFE4E4E7;
            case OFF -> (InterfaceStyle.isClassic() ? 0xFF9A978D : 0xFF71717A);
        };
        int micW = Icons.MIC.width, micH = Icons.MIC.height;
        int micX = meterLeft - 12 - micW;
        int micY = meterBottom - micH - 2;
        float bob = hearing ? (float) Math.sin(now / 70.0) * 0.6f * Math.min(1f, smoothLevel * 2f) : 0f;
        g.pose().pushMatrix();
        g.pose().translate(0, bob);
        Icons.MIC.draw(g, micX + 1, micY + 1, 0x80000000);
        Icons.MIC.draw(g, micX, micY, micColor);
        g.pose().popMatrix();
        if (hearing) drawWaves(g, micX, micY, micW, now);
        if (state == VoiceEngine.State.LOADING) Ui.spinner(g, micX + micW / 2, micY + 4, 9, (InterfaceStyle.isClassic() ? 0xFFD56B : 0xFACC15));


        int textRight = micX - 8;
        int lineY = micY + 3;

        int maxText = Math.max(50, textRight - (width / 2 + 94));
        String partial = engine.partial();
        if (!voice.heard().equals(shownHeard)) {
            shownHeard = voice.heard();
            shownHeardAt = voice.heardAt();
        }
        if (state == VoiceEngine.State.ERROR) {
            shadowRight(g, font, LanguageManager.translate("randomrun.voice.hud.error." + engine.error()), textRight, lineY, (InterfaceStyle.isClassic() ? 0xFFFF8B7B : 0xFFFF6B6B));
        } else if (state == VoiceEngine.State.LOADING) {
            shadowRight(g, font, LanguageManager.translate("randomrun.voice.hud.loading"), textRight, lineY, (InterfaceStyle.isClassic() ? 0xFFFFD56B : 0xFFFACC15));
        } else if (!partial.isEmpty()) {
            String text = partial + ".".repeat((int) ((now / 300) % 4));
            shadowRight(g, font, tail(font, text, maxText), textRight, lineY, (InterfaceStyle.isClassic() ? 0xFFC0BDB2 : 0xFFA1A1AA));
        } else if (!shownHeard.isEmpty() && now - shownHeardAt < 5000) {
            float in = Ui.easeOut((now - shownHeardAt) / 220f);
            float out = 1f - Mth.clamp((now - shownHeardAt - 3800) / 1200f, 0f, 1f);
            int y = lineY + Math.round((1f - in) * 6f);
            shadowRight(g, font, tail(font, "«" + shownHeard + "»", maxText), textRight, y,
                Ui.argb(0xFFFFFF, Math.max(0.05f, in * out)));
        } else {
            float pulse = 0.35f + 0.25f * (float) Math.sin(now / 500.0);
            shadowRight(g, font, LanguageManager.translate(hearing ? "randomrun.voice.hud.hearing" : "randomrun.voice.hud.hint"),
                textRight, lineY, Ui.argb((InterfaceStyle.isClassic() ? 0xE2DFD2 : 0xD4D4D8), pulse));
        }


        int y = lineY - 13;
        if (!voice.status().isEmpty() && now - voice.statusAt() < 3500) {
            float in = Ui.easeOut((now - voice.statusAt()) / 180f);
            float out = 1f - Mth.clamp((now - voice.statusAt() - 2700) / 800f, 0f, 1f);
            int x = textRight + Math.round((1f - in) * 10f);
            shadowRight(g, font, Ui.ellipsize(font, voice.status(), maxText), x, y, Ui.argb(voice.statusColor() & 0xFFFFFF, Math.max(0.05f, in * out)));
            y -= 12;
        }
        List<String> labels = voice.queueLabels(4);
        int rainbow = InterfaceStyle.isClassic() ? 0xFFBCD999 : 0xFF000000 | Mth.hsvToRgb((now % 4000L) / 4000f, 0.5f, 1f);
        for (int i = 0; i < labels.size(); i++) {
            String text = Ui.ellipsize(font, labels.get(i), maxText) + (i == 0 ? "  ▶" : "  ·");
            float scale = InterfaceStyle.isClassic() ? 1f : i == 0 ? 0.9f : 0.8f;
            int color = i == 0 ? rainbow : Ui.argb(InterfaceStyle.isClassic() ? 0xC0BDB2 : 0xA1A1AA, 1f - i * 0.18f);
            g.pose().pushMatrix();
            g.pose().translate(textRight, y);
            g.pose().scale(scale, scale);
            GuiText.text(g, font, text, -font.width(text), 0, color, true);
            g.pose().popMatrix();
            y -= 10;
        }
    }


    private void drawWaves(GuiGraphicsExtractor g, int micX, int micY, int micW, long now) {
        float level = Math.min(1f, smoothLevel * 1.4f);
        for (int ring = 0; ring < 2; ring++) {
            if (ring == 1 && level < 0.35f) continue;
            float phase = ((now / 380f) + ring * 0.5f) % 1f;
            float alpha = (1f - phase) * (ring == 0 ? 0.9f : 0.6f) * (0.35f + 0.65f * level);
            int spread = 2 + ring * 3 + Math.round(phase * 2f);
            int color = Ui.argb((InterfaceStyle.isClassic() ? 0xA8D477 : 0x7CF29A), alpha);
            int cy = micY + 3;
            int lx = micX - spread, rx = micX + micW - 1 + spread;
            g.fill(lx, cy - 1, lx + 1, cy + 4, color);
            g.fill(lx + 1, cy - 2, lx + 2, cy - 1, color);
            g.fill(lx + 1, cy + 4, lx + 2, cy + 5, color);
            g.fill(rx, cy - 1, rx + 1, cy + 4, color);
            g.fill(rx - 1, cy - 2, rx, cy - 1, color);
            g.fill(rx - 1, cy + 4, rx, cy + 5, color);
        }
    }


    private static String tail(Font font, String text, int maxWidth) {
        if (font.width(text) <= maxWidth) return text;
        int start = 0;
        while (start < text.length() && font.width("…" + text.substring(start)) > maxWidth) start++;
        return "…" + text.substring(start);
    }

    private static void shadowRight(GuiGraphicsExtractor g, Font font, String text, int right, int y, int color) {
        GuiText.text(g, font, text, right - font.width(text), y, color, true);
    }
}
