package com.randomrun.features.upgrader.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import com.randomrun.features.upgrader.UpgraderMath;
import com.randomrun.features.upgrader.UpgraderTransaction;
import com.randomrun.ui.theme.Theme;
import com.randomrun.ui.util.Ui;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.OptionalLong;
import java.util.function.Function;
import java.util.function.ToLongFunction;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.state.gui.GuiElementRenderState;
import org.joml.Matrix3x2f;
import org.joml.Matrix3x2fc;

public final class UpgraderVisuals {
    private UpgraderVisuals() {
    }

    public static record UpgraderWheelGeometry(int guiRadius, int guiScale) {
        public UpgraderWheelGeometry {
            if (guiRadius < 1 || guiScale < 1) throw new IllegalArgumentException("Invalid wheel geometry");
        }

        public int physicalPixelStep() { return Math.max(1, (int) Math.ceil(guiScale / 1.5)); }
        public float detailScale() { return guiScale / (float) physicalPixelStep(); }
        public float rasterToGui() { return physicalPixelStep() / (float) guiScale; }
        public int units(double guiUnits) { return (int) Math.round(guiUnits * detailScale()); }

        public int radius() { return units(guiRadius); }
        public int coloredOuter() { return units(guiRadius - 1); }
        public int coloredInner() { return units(Math.max(8, guiRadius - Math.max(6, guiRadius / 6))); }
        public int pointerTip() { return coloredOuter() + 1; }
        public int pointerBase() { return units(guiRadius + 6); }
        public int pointerLineWidth() { return 3; }
        public int pointerHeadHalfWidth() { return Math.max(2, units(1.5)); }
        public int pointerHeadLength() { return Math.max(4, units(4)); }
        public int outerExtent() { return units(guiRadius + 8); }
    }

    public final static class UpgraderSelection {
        private UpgraderSelection() {}

        public static boolean accepts(int button) {
            return button == InputConstants.MOUSE_BUTTON_LEFT || button == InputConstants.MOUSE_BUTTON_RIGHT;
        }

        public static int nextCount(int current, int available, int button, boolean shift) {
            return nextCount(current, available, 0, button, shift);
        }

        public static int nextCount(int current, int available, int otherSelected, int button, boolean shift) {
            return nextCount(current, available, otherSelected, true, button, shift);
        }

        public static boolean canAddType(boolean alreadySelectedType, int selectedTypes) {
            return alreadySelectedType || Math.max(0, selectedTypes) < UpgraderTransaction.MAX_SELECTED_TYPES;
        }

        public static int nextCount(int current, int available, int otherSelected, boolean typeAvailable, int button, boolean shift) {
            int free = Math.max(0, UpgraderTransaction.MAX_SELECTED_ITEMS - Math.max(0, otherSelected));
            int maximum = Math.min(Math.max(0, available), free);
            int selected = Math.clamp(current, 0, maximum);
            if (!accepts(button)) return selected;
            if (shift) {
                if (selected == maximum || !typeAvailable && selected > 0) return 0;
                return typeAvailable ? maximum : selected;
            }
            if (!typeAvailable && button != InputConstants.MOUSE_BUTTON_RIGHT) return selected;
            return Math.clamp(selected + (button == InputConstants.MOUSE_BUTTON_RIGHT ? -1 : 1), 0, maximum);
        }
    }

    public final static class UpgraderWheel {
        private UpgraderWheel() {}

        public static double spinTurns(double progress, double roll, double chance) {
            return spinTurns(progress, roll, chance, 0);
        }

        public static double spinTurns(double progress, double roll, double chance, double startTurn) {
            if (!Double.isFinite(progress)) throw new IllegalArgumentException("Invalid spin progress");
            double start = wrap01(startTurn);
            double end = stoppedTurn(roll, chance);
            double t = Math.clamp(progress, 0.0, 1.0);
            if (t >= 1.0) return end;
            double acceleration = .12;
            double travel = t < acceleration ? t * t / (2 * acceleration) : t - acceleration / 2;
            double phase = travel / (1 - acceleration / 2);
            double eased = 1 - Math.pow(1 - phase, 3);
            double delta = wrap01(end - start);
            return start + (10 + delta) * eased;
        }

        public static double stoppedTurn(double roll, double chance) {
            return UpgraderMath.wheelTurn(roll, chance);
        }

        public static double presentationRoll(double actualRoll, double chance, double bonus, double presentationSample) {
            UpgraderMath.wheelTurn(actualRoll, chance);
            if (UpgraderMath.inBonus(actualRoll, chance, bonus)) return actualRoll;
            if (chance <= 0 || !Double.isFinite(presentationSample)
                    || presentationSample < 0 || presentationSample >= 1) {
                throw new IllegalArgumentException("Invalid presentation sample");
            }
            if (UpgraderMath.wins(actualRoll, chance) || presentationSample >= .20) return actualRoll;
            double gap = .004 + .008 * (presentationSample / .20);
            return 1 - gap;
        }

        public static double wrap01(double turn) {
            if (!Double.isFinite(turn)) throw new IllegalArgumentException("Invalid wheel angle");
            double wrapped = turn - Math.floor(turn);
            return wrapped >= 1 ? 0 : wrapped;
        }
    }

    public static record UpgraderScreenLayout(int windowX, int windowY, int windowWidth, int windowHeight,
                                       int contentX, int contentWidth, int contentTop, int slotSize,
                                       int leftWidth, int rightWidth, int lowerY, int lowerHeight,
                                       int heroHeight) {
        public static UpgraderScreenLayout fit(int screenWidth, int screenHeight) {
            int width = Math.min(Math.min(620, screenWidth - 32), Math.max(288, (int) (screenWidth * .82)));
            int height = Math.min(Math.min(310, screenHeight - 20), Math.max(220, (int) (screenHeight * .84)));
            int windowX = (screenWidth - width) / 2;
            int windowY = (screenHeight - height) / 2;
            int contentWidth = width - 12;
            int contentTop = windowY + 26;
            int slotSize = Math.max(16, Math.min(22,
                Math.min((contentWidth / 2 - 16) / 9, (height - 100) / 9)));
            int lowerHeight = slotSize * 4 + 30;
            int lowerY = windowY + height - 14 - lowerHeight;
            int heroHeight = lowerY - contentTop - 34;
            int leftWidth = Math.max(slotSize * 9 + 8, (contentWidth - 8) / 2);
            int rightWidth = contentWidth - leftWidth - 8;
            return new UpgraderScreenLayout(windowX, windowY, width, height, windowX + 6, contentWidth,
                contentTop, slotSize, leftWidth, rightWidth, lowerY, lowerHeight, heroHeight);
        }

        public int inventoryX() {
            return contentX + (leftWidth - slotSize * 9) / 2;
        }

        public int targetColumns() {
            return Math.max(1, Math.min(9, (rightWidth - 16) / slotSize));
        }

        public int targetX() {
            return contentX + leftWidth + 8 + (rightWidth - targetColumns() * slotSize) / 2;
        }

        public int gridY() { return lowerY + 23; }
        public int actionY() { return contentTop + heroHeight + 4; }
        public int actionHeight() { return 26; }
        public int sideWidth() { return (contentWidth - 16) / 3; }
        public int centerWidth() { return contentWidth - sideWidth() * 2 - 16; }
        public int centerX() { return contentX + sideWidth() + 8; }
        public int targetPanelX() { return centerX() + centerWidth() + 8; }
        public int wheelRadius() { return Math.max(16, Math.min(centerWidth() / 2 - 10, heroHeight / 2 - 9)); }
        public int presetX(int column) { return targetPanelX() + (sideWidth() - 4) * column / 3 + 2 * column; }
        public int presetWidth(int column) {
            return (sideWidth() - 4) * (column + 1) / 3 - (sideWidth() - 4) * column / 3;
        }
    }

    public final static class UpgraderSearch {
        private UpgraderSearch() {}

        public static OptionalLong numericValue(String query) {
            if (query == null) return OptionalLong.empty();
            long number = 0;
            boolean digitFound = false;
            boolean overflow = false;
            for (int index = 0; index < query.length(); index++) {
                char symbol = query.charAt(index);
                if (Character.isWhitespace(symbol) || Character.isSpaceChar(symbol) || symbol == '_') continue;
                if (symbol < '0' || symbol > '9') return OptionalLong.empty();
                digitFound = true;
                int digit = symbol - '0';
                if (number > (Long.MAX_VALUE - digit) / 10) overflow = true;
                if (!overflow) number = number * 10 + digit;
            }
            return digitFound ? OptionalLong.of(overflow ? Long.MAX_VALUE : number) : OptionalLong.empty();
        }

        public static <T> List<T> apply(List<T> items, String query, long stake,
                                        ToLongFunction<T> value, Function<T, String> label,
                                        Function<T, String> id) {
            String needle = normalize(query).strip();
            OptionalLong requested = numericValue(query);
            Comparator<T> order = Comparator.comparingLong(value).thenComparing(id);
            if (requested.isPresent()) {
                long desired = requested.getAsLong();
                order = Comparator.<T>comparingLong(item -> Math.abs(value.applyAsLong(item) - desired))
                    .thenComparingLong(value).thenComparing(id);
            }
            return items.stream()
                .filter(item -> value.applyAsLong(item) > 0)
                .filter(item -> requested.isPresent() || needle.isEmpty()
                    || normalize(label.apply(item)).contains(needle) || normalize(id.apply(item)).contains(needle))
                .sorted(order)
                .toList();
        }

        private static String normalize(String text) {
            return text == null ? "" : text.toLowerCase(Locale.ROOT);
        }
    }

    public final static class UpgraderRingRaster {
        public record Span(int x, int y, int endX, boolean success) {}

        private final int outer;
        private final int inner;
        private final double chance;
        private final List<Span> spans;

        public UpgraderRingRaster(int outer, int inner, double chance) {
            if (inner < 0 || outer <= inner || !Double.isFinite(chance) || chance < 0 || chance > UpgraderMath.MAX_CHANCE)
                throw new IllegalArgumentException("Invalid chance ring");
            this.outer = outer;
            this.inner = inner;
            this.chance = chance;
            List<Span> output = new ArrayList<>();
            long outside = (long) outer * outer;
            long inside = (long) inner * inner;
            for (int y = -outer; y <= outer; y++) {
                int extent = (int) Math.sqrt(outside - (long) y * y);
                int hole = Math.abs(y) < inner ? (int) Math.ceil(Math.sqrt(inside - (long) y * y)) : 0;
                if (hole == 0) append(output, -extent, extent, y, chance);
                else {
                    append(output, -extent, -hole, y, chance);
                    append(output, hole, extent, y, chance);
                }
            }
            spans = List.copyOf(output);
        }

        private static void append(List<Span> output, int start, int end, int y, double chance) {
            if (start > end) return;
            int runStart = start;
            boolean previous = wins(start, y, chance);
            for (int x = start + 1; x <= end; x++) {
                boolean current = wins(x, y, chance);
                if (current != previous) {
                    output.add(new Span(runStart, y, x, previous));
                    runStart = x;
                    previous = current;
                }
            }
            output.add(new Span(runStart, y, end + 1, previous));
        }

        private static boolean wins(int x, int y, double chance) {
            double turn = (Math.atan2(y, x) / (Math.PI * 2) - .25 + 1) % 1;
            return UpgraderMath.isWinningWheelTurn(turn, chance);
        }

        public boolean matches(int outer, int inner, double chance) {
            return this.outer == outer && this.inner == inner && this.chance == chance;
        }

        public List<Span> spans() { return spans; }
    }

    public final static class UpgraderPixelMesh {
        public record Quad(int x0, int y0, int x1, int y1, int colorIndex) {}

        private final List<Quad> quads;
        private final ScreenRectangle localBounds;

        public UpgraderPixelMesh(List<Quad> quads) {
            this.quads = List.copyOf(quads);
            int x0 = Integer.MAX_VALUE, y0 = Integer.MAX_VALUE, x1 = Integer.MIN_VALUE, y1 = Integer.MIN_VALUE;
            for (Quad quad : quads) {
                x0 = Math.min(x0, quad.x0());
                y0 = Math.min(y0, quad.y0());
                x1 = Math.max(x1, quad.x1());
                y1 = Math.max(y1, quad.y1());
            }
            localBounds = quads.isEmpty() ? ScreenRectangle.empty() : new ScreenRectangle(x0, y0, x1 - x0, y1 - y0);
        }

        public void extract(GuiGraphicsExtractor graphics, int color0, int color1) {
            if (quads.isEmpty()) return;
            Matrix3x2f pose = new Matrix3x2f(graphics.pose());
            ScreenRectangle scissor = graphics.scissorStack.peek();
            ScreenRectangle bounds = localBounds.transformMaxBounds(pose);
            if (scissor != null) bounds = bounds.intersection(scissor);
            if (bounds == null) return;
            graphics.guiRenderState.addGuiElement(new State(quads, pose, scissor, bounds, color0, color1));
        }

        public static final class Builder {
            private final List<Quad> quads = new ArrayList<>();

            public void fill(int x0, int y0, int x1, int y1, int colorIndex) {
                if (x1 <= x0 || y1 <= y0) return;
                if (!quads.isEmpty()) {
                    Quad last = quads.getLast();
                    if (last.x0() == x0 && last.x1() == x1 && last.y1() == y0 && last.colorIndex() == colorIndex) {
                        quads.set(quads.size() - 1, new Quad(x0, last.y0(), x1, y1, colorIndex));
                        return;
                    }
                }
                quads.add(new Quad(x0, y0, x1, y1, colorIndex));
            }

            public UpgraderPixelMesh build() { return new UpgraderPixelMesh(quads); }
        }

        private record State(List<Quad> quads, Matrix3x2fc pose, ScreenRectangle scissorArea,
                             ScreenRectangle bounds, int color0, int color1) implements GuiElementRenderState {
            @Override
            public void buildVertices(VertexConsumer consumer) {
                for (Quad quad : quads) {
                    int color = quad.colorIndex() == 0 ? color0 : color1;
                    consumer.addVertexWith2DPose(pose, quad.x0(), quad.y0()).setColor(color);
                    consumer.addVertexWith2DPose(pose, quad.x0(), quad.y1()).setColor(color);
                    consumer.addVertexWith2DPose(pose, quad.x1(), quad.y1()).setColor(color);
                    consumer.addVertexWith2DPose(pose, quad.x1(), quad.y0()).setColor(color);
                }
            }

            @Override
            public RenderPipeline pipeline() { return RenderPipelines.GUI; }

            @Override
            public TextureSetup textureSetup() { return TextureSetup.noTexture(); }
        }
    }

    public final static class UpgraderPalette {
        private static final int YELLOW = 0xFFFFD45C;
        private static final int WHITE = 0xFFFFFFFF;
        private static final int BLACK = 0xFF000000;

        public static int WINDOW, WINDOW_EDGE, PANEL, EDGE, STAKE, STAKE_EDGE, TARGET, TARGET_EDGE;
        public static int GOLD, TEAL, TEXT, MUTED, INK;
        public static int SLOT, SLOT_EDGE, SLOT_HOVER, SELECTED, TARGET_SELECTED;
        public static int BUTTON, BUTTON_EDGE, BUTTON_DISABLED, BUTTON_DISABLED_EDGE;
        public static int PRIMARY, PRIMARY_DISABLED, PRIMARY_DISABLED_EDGE;
        public static int HEADER, DISC, SUCCESS_FILL, FAILURE_FILL, RING_TRACK, RING_EDGE, BACKDROP;
        public static final int GREEN = 0xFF86EFAC;
        public static final int RED = 0xFFF87171;

        private static Theme applied;

        static {
            refresh();
        }

        private UpgraderPalette() {}

        private static int opaque(int rgb) {
            return BLACK | rgb;
        }

        public static void refresh() {
            Theme theme = Theme.current();
            if (theme == applied) return;
            applied = theme;
            int panel = opaque(theme.panel), base = opaque(theme.buttonBase), hover = opaque(theme.buttonHover);
            int accent = opaque(theme.accent), border = opaque(theme.border);
            WINDOW = Ui.lerp(panel, BLACK, .35f);
            WINDOW_EDGE = Ui.lerp(hover, accent, .35f);
            PANEL = Ui.lerp(panel, base, .35f);
            EDGE = Ui.lerp(border, panel, .35f);
            STAKE = Ui.lerp(panel, base, .55f);
            STAKE_EDGE = accent;
            TARGET = Ui.lerp(panel, hover, .22f);
            TARGET_EDGE = Ui.lerp(hover, WHITE, .25f);
            GOLD = accent;
            TEAL = Ui.lerp(accent, WHITE, .55f);
            TEXT = WHITE;
            MUTED = Ui.lerp(Ui.lerp(accent, WHITE, .5f), 0xFF8A8A93, .6f);
            INK = Ui.lerp(panel, BLACK, .7f);
            SLOT = Ui.lerp(base, BLACK, .25f);
            SLOT_EDGE = Ui.lerp(border, BLACK, .3f);
            SLOT_HOVER = Ui.lerp(hover, BLACK, .2f);
            SELECTED = Ui.lerp(accent, BLACK, .55f);
            TARGET_SELECTED = Ui.lerp(hover, BLACK, .35f);
            BUTTON = base;
            BUTTON_EDGE = Ui.lerp(hover, WHITE, .2f);
            BUTTON_DISABLED = Ui.lerp(base, BLACK, .45f);
            BUTTON_DISABLED_EDGE = Ui.lerp(border, BLACK, .45f);
            PRIMARY = hover;
            PRIMARY_DISABLED = Ui.lerp(hover, BLACK, .55f);
            PRIMARY_DISABLED_EDGE = Ui.lerp(hover, BLACK, .3f);
            HEADER = Ui.lerp(panel, base, .7f);
            DISC = Ui.lerp(panel, BLACK, .5f);
            SUCCESS_FILL = 0xFF14532D;
            FAILURE_FILL = 0xFF5F1D1D;
            RING_TRACK = Ui.lerp(base, BLACK, .2f);
            RING_EDGE = Ui.lerp(accent, WHITE, .3f);
            BACKDROP = Ui.argb(theme.bgBottom, 0.55f);
        }

        public static int chanceColor(double chance) {
            if (!Double.isFinite(chance) || chance <= 0) return MUTED;
            double value = Math.clamp(chance, 0, .75);
            return value <= .375 ? blend(RED, YELLOW, value / .375)
                : blend(YELLOW, GREEN, (value - .375) / .375);
        }

        private static int blend(int from, int to, double progress) {
            int red = (int) Math.round((from >> 16 & 255) + ((to >> 16 & 255) - (from >> 16 & 255)) * progress);
            int green = (int) Math.round((from >> 8 & 255) + ((to >> 8 & 255) - (from >> 8 & 255)) * progress);
            int blue = (int) Math.round((from & 255) + ((to & 255) - (from & 255)) * progress);
            return 0xFF000000 | red << 16 | green << 8 | blue;
        }
    }
}
