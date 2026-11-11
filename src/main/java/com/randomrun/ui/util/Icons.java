package com.randomrun.ui.util;

import net.minecraft.client.gui.GuiGraphicsExtractor;

public enum Icons {
    PLUS(
        "...##...",
        "...##...",
        "...##...",
        "########",
        "########",
        "...##...",
        "...##...",
        "...##..."),
    CLOCK(
        ".#####.",
        "#..#..#",
        "#..#..#",
        "#..##.#",
        "#.....#",
        "#.....#",
        ".#####."),
    MINUS(
        "########",
        "########"),
    SMALL_CROSS(
        "##....##",
        ".##..##.",
        "..####..",
        "...##...",
        "...##...",
        "..####..",
        ".##..##.",
        "##....##"),
    CROSS(
        "##....##",
        "###..###",
        ".######.",
        "..####..",
        "..####..",
        ".######.",
        "###..###",
        "##....##"),
    CHECK(
        "......##",
        ".....###",
        "#...###.",
        "##.###..",
        "#####...",
        ".###....",
        "..#....."),
    ARROW(
        "....#...",
        "....##..",
        "########",
        "########",
        "....##..",
        "....#..."),
    CHEVRON_LEFT(
        "..##",
        ".##.",
        "##..",
        "##..",
        ".##.",
        "..##"),
    CHEVRON_RIGHT(
        "##..",
        ".##.",
        "..##",
        "..##",
        ".##.",
        "##.."),
    PENCIL(
        ".....##.",
        "....####",
        "...####.",
        "..####..",
        ".####...",
        "####....",
        "##......",
        "#......."),
    CROWN(
        "#..##..#",
        "##.##.##",
        "########",
        "########",
        ".######."),
    EYE(
        "..####..",
        ".#....#.",
        "#..##..#",
        "#..##..#",
        ".#....#.",
        "..####.."),
    LOCK(
        "..###..",
        ".#...#.",
        ".#...#.",
        "#######",
        "###.###",
        "###.###",
        "#######"),
    REFRESH(
        "..####.#",
        ".#....##",
        "#....###",
        "#.......",
        "#......#",
        ".#....#.",
        "..####..");

    private final int[][] runs;
    public final int width, height;

    Icons(String... rows) {
        height = rows.length;
        width = rows[0].length();
        runs = new int[rows.length][];
        for (int y = 0; y < rows.length; y++) {
            String row = rows[y];
            int[] buffer = new int[row.length() * 2];
            int count = 0;
            for (int x = 0; x < row.length(); x++) {
                if (row.charAt(x) != '#') continue;
                int start = x;
                while (x + 1 < row.length() && row.charAt(x + 1) == '#') x++;
                buffer[count++] = start;
                buffer[count++] = x + 1;
            }
            runs[y] = java.util.Arrays.copyOf(buffer, count);
        }
    }

    public void draw(GuiGraphicsExtractor graphics, int x, int y, int argb) {
        for (int row = 0; row < height; row++) {
            int[] r = runs[row];
            for (int i = 0; i < r.length; i += 2) graphics.fill(x + r[i], y + row, x + r[i + 1], y + row + 1, argb);
        }
    }

    public void drawCentered(GuiGraphicsExtractor graphics, int left, int top, int boxWidth, int boxHeight, int argb) {
        draw(graphics, left + (boxWidth - width) / 2, top + (boxHeight - height) / 2, argb);
    }
}
