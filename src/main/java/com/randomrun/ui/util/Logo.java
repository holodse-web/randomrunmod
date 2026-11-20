package com.randomrun.ui.util;

import net.minecraft.client.gui.GuiGraphicsExtractor;

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
    private static final int GRID_W = WORD[0].length() + 1;
    private static final int GRID_H = WORD.length + 3;
    private static final float DEFAULT_PROGRESS = 0.62f;

    private Logo() {}

    public static void draw(GuiGraphicsExtractor graphics, int x, int y, int width, int height, float alpha) {
        draw(graphics, x, y, width, height, alpha, DEFAULT_PROGRESS);
    }

    public static void draw(GuiGraphicsExtractor graphics, int x, int y, int width, int height, float alpha, float progress) {
        if (alpha <= 0.01f) return;
        int unit = Math.max(1, Math.min(width / GRID_W, height / GRID_H));
        int ox = x + (width - WORD[0].length() * unit) / 2;
        int oy = y + (height - GRID_H * unit) / 2;
        Theme theme = Theme.current();
        int face = Ui.argb(InterfaceStyle.isClassic() ? 0xC9C7BC : theme.accent, alpha);
        int side = Ui.argb(InterfaceStyle.isClassic() ? 0x252521 : Ui.lerp(0xFF000000 | theme.buttonHover, 0xFF000000, .45f), alpha);
        int highlight = Ui.argb(InterfaceStyle.isClassic() ? 0xF0EEE3 : Ui.lerp(0xFF000000 | theme.accent, 0xFFFFFFFF, .55f), alpha);
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
        int barY = oy + (WORD.length + 2) * unit;
        int barW = WORD[0].length() * unit;
        int filled = Math.round(barW * Math.max(0f, Math.min(1f, progress)));
        batch.fill(ox, barY, ox + barW, barY + unit, Ui.argb(theme.accent, alpha * 0.3f));
        if (filled > 0) batch.fill(ox, barY, ox + filled, barY + unit, Ui.argb(theme.accent, alpha));
        batch.build().extract(graphics);
    }

}
