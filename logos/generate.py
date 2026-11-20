"""Export both interface logo sets from the Java renderer's glyphs and theme colors.

Run with Python and Pillow: python logos/generate.py
"""

from pathlib import Path
import re

from PIL import Image, ImageDraw


ROOT = Path(__file__).resolve().parents[1]
JAVA = ROOT / "src/main/java/com/randomrun/ui/util"
GLYPHS = re.findall(r'"([.#]+)"', (JAVA / "Logo.java").read_text(encoding="utf-8"))
THEMES = re.findall(
    r'^\s*(CLASSIC_)?[A-Z]+\("([a-z]+)", ([^\n]+)\)[,;]',
    (JAVA / "Theme.java").read_text(encoding="utf-8"),
    re.MULTILINE,
)


def rgb(number):
    return (number >> 16 & 255, number >> 8 & 255, number & 255)


def blend(first, second, amount):
    return tuple(int(a + (b - a) * amount) for a, b in zip(first, second))


def render(classic, colors, width=640, height=320, *, background=None, padding=0):
    image = Image.new("RGBA", (width, height), background)
    draw = ImageDraw.Draw(image)
    columns = len(GLYPHS[0]) + 1
    rows = len(GLYPHS) + 3
    unit = min((width - padding * 2) // columns, (height - padding * 2) // rows)
    left = (width - columns * unit) // 2
    top = (height - (len(GLYPHS) + 3) * unit) // 2
    accent = rgb(colors[-1])
    face = rgb(0xC9C7BC) if classic else accent
    side = rgb(0x252521) if classic else blend(rgb(colors[6]), (0, 0, 0), .45)
    highlight = rgb(0xF0EEE3) if classic else blend(accent, (255, 255, 255), .55)

    def fill(x, y, w, h, color):
        draw.rectangle((x, y, x + w - 1, y + h - 1), fill=color)

    for row, line in enumerate(GLYPHS):
        for column, pixel in enumerate(line):
            if pixel == "#":
                fill(left + (column + 1) * unit, top + (row + 1) * unit, unit, unit, side)
    for row, line in enumerate(GLYPHS):
        for column, pixel in enumerate(line):
            if pixel != "#":
                continue
            x, y = left + column * unit, top + row * unit
            fill(x, y, unit, unit, face)
            if row == 0 or GLYPHS[row - 1][column] != "#":
                fill(x, y, unit, max(1, unit // 4), highlight)
    bar_y = top + (len(GLYPHS) + 2) * unit
    bar_width = len(GLYPHS[0]) * unit
    fill(left, bar_y, bar_width, unit, (*accent, 76))
    fill(left, bar_y, round(bar_width * .62), unit, accent)
    return image


def main():
    for prefix, name, raw_colors in THEMES:
        classic = bool(prefix)
        colors = [int(value.strip(), 16) for value in raw_colors.split(",")]
        destination = ROOT / "logos" / ("classic" if classic else "modern")
        destination.mkdir(parents=True, exist_ok=True)
        logo = render(classic, colors)
        logo.save(destination / f"{name}.png")
        if not classic and name == "graphite":
            colors[-1] = 0x111111
            render(False, colors, background=(245, 245, 245), padding=32).save(ROOT / "docs/rrm-logo-black.png")
    print(f"Exported {len(THEMES)} logos into logos/classic and logos/modern")


if __name__ == "__main__":
    main()
