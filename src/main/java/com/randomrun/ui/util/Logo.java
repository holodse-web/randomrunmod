package com.randomrun.ui.util;

import com.randomrun.mixin.HudMixins.GuiGraphicsAccessor;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.renderpearl.api.textures.FilterMode;
import java.util.EnumMap;
import java.util.Map;
import java.util.Optional;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;

public final class Logo {
    private static final String[] WORD = {
        "#####..#####..##...##",
        "##..##.##..##.###.###",
        "##..##.##..##.#######",
        "#####..#####..##.#.##",
        "##.##..##.##..##...##",
        "##..##.##..##.##...##",
        "##..##.##..##.##...##"
    };
    private static final int LEAD = 8;
    private static final int GRID_W = LEAD + WORD[0].length() + 1;
    private static final int GRID_H = WORD.length + 3;
    private static final float DEFAULT_PROGRESS = 0.62f;
    private static final Map<Theme, Optional<Identifier>> CUSTOM = new EnumMap<>(Theme.class);

    private Logo() {}

    private static Optional<Identifier> custom() {
        return CUSTOM.computeIfAbsent(Theme.current(), theme -> {
            Identifier id = Identifier.fromNamespaceAndPath("randomrun", "textures/gui/logo_" + theme.name().toLowerCase() + ".png");
            return Minecraft.getInstance().getResourceManager().getResource(id).isPresent() ? Optional.of(id) : Optional.empty();
        });
    }

    public static void draw(GuiGraphicsExtractor graphics, int x, int y, int width, int height, float alpha) {
        draw(graphics, x, y, width, height, alpha, DEFAULT_PROGRESS);
    }

    public static void draw(GuiGraphicsExtractor graphics, int x, int y, int width, int height, float alpha, float progress) {
        if (alpha <= 0.01f) return;
        Optional<Identifier> image = custom();
        if (image.isPresent()) {
            drawImage(graphics, image.get(), x, y, width, height, alpha);
            return;
        }
        int unit = Math.max(1, Math.min(width / GRID_W, height / GRID_H));
        int ox = x + (width - WORD[0].length() * unit) / 2;
        int oy = y + (height - GRID_H * unit) / 2;
        Theme theme = Theme.current();
        int face = Ui.argb(theme.accent, alpha);
        int side = Ui.argb(Ui.lerp(0xFF000000 | theme.buttonHover, 0xFF000000, 0.45f) & 0xFFFFFF, alpha);
        int highlight = Ui.argb(Ui.lerp(0xFF000000 | theme.accent, 0xFFFFFFFF, 0.55f) & 0xFFFFFF, alpha);
        int cap = Math.max(1, unit / 4);

        GuiQuadBatch.Builder batch = new GuiQuadBatch.Builder(256);
        for (int row = 0; row < WORD.length; row++) {
            String line = WORD[row];
            for (int col = 0; col < line.length(); col++) {
                if (line.charAt(col) != '#') continue;
                int px = ox + (col + 1) * unit, py = oy + (row + 1) * unit;
                batch.fill(px, py, px + unit, py + unit, side);
            }
        }
        for (int row = 0; row < WORD.length; row++) {
            String line = WORD[row];
            for (int col = 0; col < line.length(); col++) {
                if (line.charAt(col) != '#') continue;
                int px = ox + col * unit, py = oy + row * unit;
                batch.fill(px, py, px + unit, py + unit, face);
                boolean open = row == 0 || WORD[row - 1].charAt(col) != '#';
                if (open) batch.fill(px, py, px + unit, py + cap, highlight);
            }
        }
        int[][] streaks = {{1, 6, 100}, {3, 4, 65}, {5, 2, 40}};
        for (int[] streak : streaks) {
            int py = oy + streak[0] * unit;
            for (int i = 0; i < streak[1]; i++) {
                float fade = streak[2] / 100f * (1f - i / (float) streak[1] * 0.8f);
                int px = ox - (2 + i) * unit;
                batch.fill(px, py, px + unit, py + unit, Ui.argb(theme.accent, alpha * fade));
            }
        }
        int barY = oy + (WORD.length + 2) * unit;
        int barW = WORD[0].length() * unit;
        int filled = Math.round(barW * Math.max(0f, Math.min(1f, progress)));
        batch.fill(ox, barY, ox + barW, barY + unit, Ui.argb(theme.accent, alpha * 0.3f));
        if (filled > 0) batch.fill(ox, barY, ox + filled, barY + unit, face);
        batch.build().extract(graphics);
    }

    private static void drawImage(GuiGraphicsExtractor graphics, Identifier id, int x, int y, int width, int height, float alpha) {
        try {
            var texture = Minecraft.getInstance().getTextureManager().getTexture(id);
            ((GuiGraphicsAccessor) graphics).randomrun$innerBlit(RenderPipelines.GUI_TEXTURED, texture.getTextureView(),
                RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR), x, y, x + width, y + height, 0f, 1f, 0f, 1f, ARGB.white(Math.min(1f, alpha)));
        } catch (RuntimeException error) {
            graphics.blit(RenderPipelines.GUI_TEXTURED, id, x, y, 0f, 0f, width, height, width, height, width, height, ARGB.white(Math.min(1f, alpha)));
        }
    }
}
