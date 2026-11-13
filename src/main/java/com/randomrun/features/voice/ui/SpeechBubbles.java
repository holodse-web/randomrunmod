package com.randomrun.features.voice.ui;

import com.randomrun.features.voice.VoiceSpeech;
import com.randomrun.ui.util.GuiText;
import com.randomrun.ui.util.Icons;
import com.randomrun.ui.util.Theme;
import com.randomrun.ui.util.Ui;

import java.util.ArrayList;
import java.util.Deque;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * Speech bubbles over other players: what they just told the game by voice.
 * Pops in, types the words out, floats a little and fades.
 */
public final class SpeechBubbles implements HudElement {
    private static final long LIFE_MS = 4600;
    private static final long FADE_MS = 700;
    private static final double MAX_DISTANCE = 48.0;

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, DeltaTracker tick) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return;
        Map<UUID, Deque<VoiceSpeech.Line>> all = VoiceSpeech.lines();
        if (all.isEmpty()) return;
        long now = System.currentTimeMillis();
        float partial = tick.getGameTimeDeltaPartialTick(true);
        Font font = mc.font;

        Iterator<Map.Entry<UUID, Deque<VoiceSpeech.Line>>> it = all.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, Deque<VoiceSpeech.Line>> entry = it.next();
            List<VoiceSpeech.Line> lines;
            synchronized (entry.getValue()) {
                entry.getValue().removeIf(line -> now - line.at() > LIFE_MS);
                if (entry.getValue().isEmpty()) {
                    it.remove();
                    continue;
                }
                lines = new ArrayList<>(entry.getValue());
            }
            Player player = mc.level.getPlayerByUUID(entry.getKey());
            if (player == null || player == mc.player || player.isInvisibleTo(mc.player)) continue;
            double distance = mc.player.distanceTo(player);
            if (distance > MAX_DISTANCE) continue;

            Vec3 head = player.getPosition(partial).add(0, player.getBbHeight() + 0.5, 0);
            Vec3 ndc = mc.gameRenderer.projectPointToScreen(head);
            if (ndc.z <= 0 || ndc.z >= 1 || Math.abs(ndc.x) > 1.2 || Math.abs(ndc.y) > 1.2) continue; // behind or off screen
            float sx = (float) ((ndc.x + 1) / 2 * g.guiWidth());
            float sy = (float) ((1 - ndc.y) / 2 * g.guiHeight());
            float scale = (float) Mth.clamp(1.15 - distance / 40.0, 0.55, 1.0);

            // Newest line sits on the head, the older one above it.
            float y = sy;
            for (int i = lines.size() - 1; i >= 0; i--) {
                boolean newest = i == lines.size() - 1;
                float height = drawBubble(g, font, lines.get(i), now, sx, y, scale, newest);
                y -= (height + 3) * scale;
            }
        }
    }

    /** Returns the bubble height in unscaled pixels. */
    private float drawBubble(GuiGraphicsExtractor g, Font font, VoiceSpeech.Line line, long now, float x, float bottom, float scale, boolean newest) {
        long age = now - line.at();
        float pop = backOut(Math.min(1f, age / 220f));
        float alpha = 1f - Mth.clamp((age - (LIFE_MS - FADE_MS)) / (float) FADE_MS, 0f, 1f);
        if (!newest) alpha *= 0.6f;
        if (alpha <= 0.02f) return 0;
        // Type the phrase out, about 45 characters a second.
        int shown = Math.min(line.text().length(), 1 + (int) (age / 22));
        String text = line.text().substring(0, shown);
        String full = line.text();

        int textWidth = font.width(full);
        int iconW = 7; // mic drawn at 70%
        int padX = 5, gap = 4;
        int w = padX + iconW + gap + textWidth + padX;
        int h = 14;
        float lift = Math.min(1f, age / 300f) * 2f + (1f - pop) * 4f;

        g.pose().pushMatrix();
        g.pose().translate(x, bottom - lift * scale);
        g.pose().scale(scale * pop, scale * pop);
        int left = -w / 2, top = -h - 4;
        Theme theme = Theme.current();
        int bg = Ui.argb(0x0B0B10, 0.82f * alpha);
        // Body with clipped corners.
        g.fill(left + 1, top, left + w - 1, top + h, bg);
        g.fill(left, top + 1, left + 1, top + h - 1, bg);
        g.fill(left + w - 1, top + 1, left + w, top + h - 1, bg);
        // Accent line on top.
        g.fill(left + 2, top, left + w - 2, top + 1, Ui.argb(theme.accent, 0.9f * alpha));
        // Tail pointing at the head.
        if (newest) {
            g.fill(-3, top + h, 4, top + h + 1, bg);
            g.fill(-2, top + h + 1, 3, top + h + 2, bg);
            g.fill(-1, top + h + 2, 2, top + h + 3, bg);
        }
        // Mic, pulsing while the words are still appearing.
        boolean typing = shown < full.length();
        float pulse = typing ? 0.65f + 0.35f * (float) Math.sin(age / 60.0) : 1f;
        g.pose().pushMatrix();
        g.pose().translate(left + padX, top + 2.5f);
        g.pose().scale(0.7f, 0.7f);
        Icons.MIC.draw(g, 0, 0, Ui.argb(0x7CF29A, alpha * pulse));
        g.pose().popMatrix();
        GuiText.text(g, font, text, left + padX + iconW + gap, top + 3, Ui.argb(0xFFFFFF, alpha), false);
        g.pose().popMatrix();
        return h + 4;
    }

    private static float backOut(float t) {
        float c1 = 1.70158f, c3 = c1 + 1f;
        return 1f + c3 * (float) Math.pow(t - 1, 3) + c1 * (float) Math.pow(t - 1, 2);
    }
}
