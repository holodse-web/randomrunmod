package com.randomrun.features.upgrader;

import com.mojang.blaze3d.platform.InputConstants;
import com.randomrun.features.upgrader.client.UpgraderSelection;
import com.randomrun.features.upgrader.client.UpgraderScreenLayout;
import com.randomrun.features.upgrader.client.UpgraderPalette;
import com.randomrun.features.upgrader.client.UpgraderWheelGeometry;
import com.randomrun.features.upgrader.client.UpgraderRingRaster;
import org.joml.Matrix3x2f;
import org.joml.Vector2f;

public final class UpgraderUiVerification {
    private static int checks;

    private UpgraderUiVerification() {}

    public static int verify() {
        checks = 0;
        check(UpgraderPalette.chanceColor(0) == UpgraderPalette.MUTED, "empty stake has neutral percentage");
        check(UpgraderPalette.chanceColor(.375) == UpgraderPalette.GOLD, "middle odds have yellow percentage");
        check(UpgraderPalette.chanceColor(.75) == UpgraderPalette.GREEN, "maximum odds have green percentage");
        check(UpgraderPalette.chanceColor(1) == UpgraderPalette.GREEN, "percentage color clamps at the cap");
        check(UpgraderPalette.chanceColor(Double.NaN) == UpgraderPalette.MUTED, "nonfinite percentage has safe neutral color");
        int lowOdds = UpgraderPalette.chanceColor(.1);
        check((lowOdds >> 16 & 255) > (lowOdds >> 8 & 255), "ten percent has a red dominant percentage");
        for (int percent = 1; percent <= 75; percent++) {
            check(contrast(UpgraderPalette.chanceColor(percent / 100d), UpgraderPalette.DISC) >= 4.5,
                "percentage remains readable across the full chance range");
        }
        int left = InputConstants.MOUSE_BUTTON_LEFT;
        int right = InputConstants.MOUSE_BUTTON_RIGHT;
        check(left == 1 && right == 3, "26.3 uses SDL mouse button numbers");
        check(UpgraderSelection.accepts(left), "SDL left click accepted");
        check(UpgraderSelection.accepts(right), "SDL right click accepted");
        check(!UpgraderSelection.accepts(InputConstants.MOUSE_BUTTON_MIDDLE), "middle click ignored");
        check(!UpgraderSelection.accepts(0), "old GLFW left-button number ignored");
        check(!UpgraderSelection.accepts(InputConstants.MOUSE_BUTTON_4), "extra mouse buttons ignored");
        check(UpgraderSelection.nextCount(0, 64, left, false) == 1, "left click adds the first item");
        check(UpgraderSelection.nextCount(1, 64, left, false) == 2, "left click increments selection");
        check(UpgraderSelection.nextCount(10, 64, left, false) == 10, "left click respects total item limit");
        check(UpgraderSelection.nextCount(1, 1, left, false) == 1, "left click respects available item count");
        check(UpgraderSelection.nextCount(2, 64, right, false) == 1, "right click removes one item");
        check(UpgraderSelection.nextCount(0, 64, right, false) == 0, "right click never creates negative selection");
        check(UpgraderSelection.nextCount(0, 64, left, true) == 10, "shift selects at most ten actual items");
        check(UpgraderSelection.nextCount(1, 64, left, true) == 10, "shift completes available stake capacity");
        check(UpgraderSelection.nextCount(10, 64, left, true) == 0, "shift clears a fully selected stake");
        check(UpgraderSelection.nextCount(0, 64, right, true) == 10, "shift-right follows capped selection toggle");
        check(UpgraderSelection.nextCount(2, 64, InputConstants.MOUSE_BUTTON_MIDDLE, false) == 2,
            "middle click preserves selected quantity");
        check(UpgraderSelection.nextCount(2, 64, 0, false) == 2, "unmapped mouse button preserves quantity");
        check(UpgraderSelection.nextCount(0, 0, left, false) == 0, "empty stack stays unselected");
        check(UpgraderTransaction.MAX_SELECTED_ITEMS == 10, "UI and authoritative transaction share ten-item limit");
        check(UpgraderTransaction.MAX_SELECTED_TYPES == 3, "UI and transaction share three distinct item-type limit");
        check(UpgraderSelection.nextCount(0, 64, 9, left, false) == 1, "tenth item may come from another stack");
        check(UpgraderSelection.nextCount(0, 64, 10, left, false) == 0, "eleventh item from another stack is blocked");
        check(UpgraderSelection.nextCount(0, 64, 9, left, true) == 1, "shift fills only shared remaining capacity");
        check(UpgraderSelection.nextCount(0, 64, 10, left, true) == 0, "shift cannot exceed full shared limit");
        check(UpgraderSelection.nextCount(1, 64, 9, right, false) == 0, "right click removes items at full capacity");
        check(UpgraderSelection.nextCount(1, 64, 9, left, true) == 0, "shift clears its slot at full shared capacity");
        check(UpgraderSelection.nextCount(0, 2, 0, left, true) == 2, "shift never invents missing inventory items");
        check(UpgraderSelection.nextCount(64, 64, 0, left, false) == 10, "stale oversized selection is bounded");
        check(UpgraderSelection.canAddType(false, 2), "a third distinct item type is allowed");
        check(!UpgraderSelection.canAddType(false, 3), "a fourth distinct item type is blocked");
        check(UpgraderSelection.canAddType(true, 3), "another slot of an existing type is allowed at the type cap");
        check(UpgraderSelection.nextCount(0, 64, 6, true, left, true) == 4, "3 plus 3 plus shift fills exactly 10 items");
        check(UpgraderSelection.nextCount(0, 64, 3, false, left, false) == 0, "new fourth kind cannot be added despite spare total capacity");
        check(UpgraderSelection.nextCount(0, 64, 3, false, left, true) == 0, "shift cannot add a fourth kind");
        check(UpgraderSelection.nextCount(2, 64, 0, false, right, false) == 1, "removal never depends on an available type slot");
        check(UpgraderSelection.nextCount(2, 64, 0, false, left, true) == 0, "shift can clear even a stale over-limit type");
        for (int first = 0; first <= 10; first++) {
            for (int second = 0; second + first <= 10; second++) {
                int otherItems = first + second;
                int third = UpgraderSelection.nextCount(0, 64, otherItems, left, true);
                check(otherItems + third <= 10, "multiple stacks always share one total capacity");
                check(UpgraderSelection.nextCount(third, 64, otherItems, right, false) <= third,
                    "removal stays available with any cross-stack distribution");
            }
        }
        verifyPalette();
        verifyRingRaster();
        for (int[] size : new int[][] {{320, 240}, {427, 240}, {427, 267}, {640, 360}, {854, 480},
                {960, 540}, {1280, 720}, {1920, 1080}}) {
            verifyLayout(size[0], size[1]);
        }
        return checks;
    }

    private static void verifyLayout(int screenWidth, int screenHeight) {
        UpgraderScreenLayout layout = UpgraderScreenLayout.fit(screenWidth, screenHeight);
        String size = " at " + screenWidth + "x" + screenHeight;
        int windowRight = layout.windowX() + layout.windowWidth();
        int windowBottom = layout.windowY() + layout.windowHeight();
        check(layout.windowX() >= 16 && layout.windowY() >= 10, "visible world margins" + size);
        check(windowRight <= screenWidth - 16 && windowBottom <= screenHeight - 10,
            "window stays on screen" + size);
        check(layout.windowWidth() <= 620 && layout.windowHeight() <= 310, "compact size cap" + size);
        if (screenWidth >= 640 && screenHeight >= 360) {
            check(layout.windowWidth() <= (int) (screenWidth * .82), "proportional horizontal world margin" + size);
            check(layout.windowHeight() <= (int) (screenHeight * .84), "proportional vertical world margin" + size);
        }
        check(layout.contentX() >= layout.windowX()
                && layout.contentX() + layout.contentWidth() <= windowRight, "content fits inside window" + size);
        check(layout.contentTop() >= layout.windowY() + 20, "title space reserved" + size);
        check(layout.slotSize() >= 16 && layout.slotSize() <= 22, "inventory icons remain readable" + size);
        check(layout.leftWidth() >= layout.slotSize() * 9 + 8, "all nine inventory columns fit" + size);
        check(layout.rightWidth() >= layout.slotSize() + 17, "at least one target column fits" + size);
        check(layout.leftWidth() + 8 + layout.rightWidth() == layout.contentWidth(), "lower panels use exact width" + size);
        check(layout.heroHeight() >= 48, "hero row remains usable" + size);
        check(layout.actionHeight() == 26, "uniform action band height" + size);
        check(layout.actionY() == layout.contentTop() + layout.heroHeight() + 4,
            "action band aligns below hero panels" + size);
        check(layout.actionY() + layout.actionHeight() + 4 == layout.lowerY(),
            "action band leaves an exact gap before inventory" + size);
        check(layout.presetX(0) == layout.targetPanelX(), "preset buttons align with target panel" + size);
        for (int column = 0; column < 3; column++) {
            check(layout.presetWidth(column) > 0, "preset buttons have positive widths" + size);
            if (column > 0) {
                check(layout.presetX(column - 1) + layout.presetWidth(column - 1) + 2 == layout.presetX(column),
                    "preset columns use equal gaps" + size);
            }
        }
        check(layout.presetX(2) + layout.presetWidth(2) == layout.targetPanelX() + layout.sideWidth(),
            "preset columns exactly fill their panel width" + size);
        check(layout.gridY() + layout.slotSize() * 4 + 2 <= layout.lowerY() + layout.lowerHeight() - 4,
            "both fourth rows and hotbar gap leave a bottom border" + size);
        check(layout.lowerY() + layout.lowerHeight() <= windowBottom - 14, "footer space reserved" + size);
        int inventoryX = layout.inventoryX();
        check(inventoryX >= layout.contentX() + 4
                && inventoryX + layout.slotSize() * 9 <= layout.contentX() + layout.leftWidth() - 4,
            "inventory grid fits within its panel" + size);
        int inventoryLeftInset = inventoryX - layout.contentX();
        int inventoryRightInset = layout.contentX() + layout.leftWidth() - inventoryX - 9 * layout.slotSize();
        check(Math.abs(inventoryLeftInset - inventoryRightInset) <= 1, "inventory grid is centered" + size);
        int targetColumns = layout.targetColumns();
        int targetPanelX = layout.contentX() + layout.leftWidth() + 8;
        int targetLeftInset = layout.targetX() - targetPanelX;
        int targetRightInset = targetPanelX + layout.rightWidth() - layout.targetX() - targetColumns * layout.slotSize();
        check(targetColumns >= 1 && targetColumns <= 9, "target grid uses one to nine columns" + size);
        check(targetLeftInset >= 8 && targetRightInset >= 8, "target grid leaves room for border and scrollbar" + size);
        check(Math.abs(targetLeftInset - targetRightInset) <= 1, "target grid is centered" + size);
        if (layout.leftWidth() == layout.rightWidth() && targetColumns == 9) {
            check(inventoryLeftInset == targetLeftInset, "equal lower panels use identical grid insets" + size);
        }
        int radius = layout.wheelRadius();
        check(radius + 9 <= layout.heroHeight() / 2, "wheel arrow and ticks fit vertically" + size);
        check(radius + 9 <= layout.centerWidth() / 2, "wheel arrow and ticks fit horizontally" + size);
        verifyPhysicalWheel(layout, size);
    }

    private static void verifyPhysicalWheel(UpgraderScreenLayout layout, String size) {
        int radius = layout.wheelRadius();
        for (int guiScale = 1; guiScale <= 6; guiScale++) {
            UpgraderWheelGeometry wheel = new UpgraderWheelGeometry(radius, guiScale);
            String scale = size + " GUI scale " + guiScale;
            check(wheel.physicalPixelStep() >= 1, "raster uses whole physical pixels" + scale);
            check(wheel.detailScale() >= 1 && wheel.detailScale() <= 1.5f,
                "wheel detail stays within a modest one-to-one-and-a-half increase" + scale);
            check(Math.abs(wheel.rasterToGui() * guiScale - wheel.physicalPixelStep()) <= 0.000001,
                "pose scaling preserves the selected physical pixel grid" + scale);
            check(Math.abs(wheel.detailScale() * wheel.rasterToGui() - 1) <= 0.000001,
                "detail scale and GUI transform are reciprocal" + scale);
            check(Math.abs(wheel.radius() - radius * wheel.detailScale()) <= .50001,
                "rounded wheel radius differs by at most half a raster pixel" + scale);
            if (guiScale >= 3) check(wheel.physicalPixelStep() >= 2,
                "larger GUI scales retain coarse Minecraft pixels" + scale);
            check(wheel.coloredInner() > 0 && wheel.coloredInner() < wheel.coloredOuter(),
                "colored ring has positive thickness" + scale);
            check(wheel.pointerTip() > wheel.coloredOuter(), "needle tip stays outside the colored arc" + scale);
            check(wheel.pointerTip() < wheel.pointerBase() && wheel.pointerBase() < wheel.outerExtent(),
                "needle head, base and mount have ordered fixed radii" + scale);
            check(wheel.pointerLineWidth() >= 3 && (wheel.pointerLineWidth() & 1) == 1,
                "needle shaft has a visible odd raster width with an exact center axis" + scale);
            check(wheel.pointerHeadHalfWidth() * 2 + 1 >= wheel.pointerLineWidth()
                    && wheel.pointerHeadLength() >= 4, "needle head remains broader than its shaft" + scale);
            check(wheel.outerExtent() * wheel.rasterToGui() <= layout.heroHeight() / 2,
                "coarse-grid needle fits the hero panel vertically" + scale);
            check(wheel.outerExtent() * wheel.rasterToGui() <= layout.centerWidth() / 2,
                "coarse-grid needle fits the hero panel horizontally" + scale);

            float centerX = layout.centerX() + layout.centerWidth() / 2;
            float centerY = layout.contentTop() + layout.heroHeight() / 2;
            double expectedRadius = wheel.pointerTip() * (double) wheel.rasterToGui();
            for (double turn : new double[] {0, .125, .25, .375, .5, .625, .75, .875, 10.125}) {
                Matrix3x2f pose = new Matrix3x2f().translate(centerX, centerY)
                    .scale(wheel.rasterToGui(), wheel.rasterToGui()).rotate((float) (turn * Math.PI * 2));
                Vector2f origin = pose.transformPosition(0, 0, new Vector2f());
                Vector2f tip = pose.transformPosition(0, wheel.pointerTip(), new Vector2f());
                check(origin.x == centerX && origin.y == centerY, "needle rotation preserves the exact shared center" + scale);
                double actualRadius = Math.hypot(tip.x - origin.x, tip.y - origin.y);
                check(Math.abs(actualRadius - expectedRadius) <= 0.0002,
                    "native float pose keeps needle at a constant radius" + scale);
                Matrix3x2f centeredPixels = new Matrix3x2f().translate(centerX, centerY)
                    .scale(wheel.rasterToGui(), wheel.rasterToGui()).translate(-.5f, -.5f)
                    .translate(.5f, .5f).rotate((float) (turn * Math.PI * 2)).translate(-.5f, -.5f);
                Vector2f pixelOrigin = centeredPixels.transformPosition(.5f, .5f, new Vector2f());
                Vector2f pixelTip = centeredPixels.transformPosition(.5f, wheel.pointerTip() + .5f, new Vector2f());
                check(pixelOrigin.x == centerX && pixelOrigin.y == centerY,
                    "pixel-center compensation preserves the exact disk axle" + scale);
                double pixelRadius = Math.hypot(pixelTip.x - pixelOrigin.x, pixelTip.y - pixelOrigin.y);
                check(Math.abs(pixelRadius - expectedRadius) <= 0.0002,
                    "centered coarse-grid needle keeps the same radius after rotation" + scale);
                int shaftHalf = wheel.pointerLineWidth() / 2;
                Vector2f shaftLeft = centeredPixels.transformPosition(-shaftHalf, wheel.pointerBase() + .5f, new Vector2f());
                Vector2f shaftRight = centeredPixels.transformPosition(shaftHalf + 1, wheel.pointerBase() + .5f, new Vector2f());
                Vector2f shaftAxis = centeredPixels.transformPosition(.5f, wheel.pointerBase() + .5f, new Vector2f());
                check(Math.abs((shaftLeft.x + shaftRight.x) / 2 - shaftAxis.x) <= .0002
                        && Math.abs((shaftLeft.y + shaftRight.y) / 2 - shaftAxis.y) <= .0002,
                    "thicker shaft remains symmetric around the same exact rotation axis" + scale);
            }
        }
    }

    private static void verifyPalette() {
        int[] surfaces = {UpgraderPalette.WINDOW, UpgraderPalette.PANEL, UpgraderPalette.STAKE,
            UpgraderPalette.TARGET, UpgraderPalette.SLOT, UpgraderPalette.BUTTON,
            UpgraderPalette.BUTTON_DISABLED, UpgraderPalette.PRIMARY_DISABLED,
            UpgraderPalette.SUCCESS_FILL, UpgraderPalette.FAILURE_FILL};
        for (int color : surfaces) {
            check((color >>> 24) == 255, "opaque UI surfaces are independent of blurred world brightness");
        }
        check(contrast(UpgraderPalette.TEXT, UpgraderPalette.PANEL) >= 7,
            "main text has at least 7:1 contrast against panels");
        check(contrast(UpgraderPalette.MUTED, UpgraderPalette.PANEL) >= 4.5,
            "secondary text remains readable against panels");
        check(contrast(UpgraderPalette.GOLD, UpgraderPalette.STAKE) >= 4.5,
            "gold stake text remains readable");
        check(contrast(UpgraderPalette.TEAL, UpgraderPalette.TARGET) >= 4.5,
            "secondary target accent remains readable");
        check(contrast(UpgraderPalette.MUTED, UpgraderPalette.BUTTON_DISABLED) >= 4.5,
            "inactive button labels remain readable");
        check(contrast(UpgraderPalette.GREEN, UpgraderPalette.SUCCESS_FILL) >= 4.5,
            "successful result label remains readable against the green filled disc");
        check(contrast(UpgraderPalette.RED, UpgraderPalette.FAILURE_FILL) >= 4.5,
            "failed result label remains readable against the red filled disc");
    }

    private static void verifyRingRaster() {
        for (int[] radii : new int[][] {{10, 6}, {23, 17}, {80, 65}}) {
            int outer = radii[0];
            int inner = radii[1];
            for (double chance : new double[] {0, 1e-9, .01, .35, .55, .75}) {
                UpgraderRingRaster raster = new UpgraderRingRaster(outer, inner, chance);
                check(raster.matches(outer, inner, chance), "stationary raster recognizes its exact geometry and probability");
                check(!raster.matches(outer + 1, inner, chance), "raster invalidates on physical radius changes");
                check(!raster.matches(outer, inner, Math.nextUp(chance)), "raster invalidates even on tiny probability changes");
                int dimension = outer * 2 + 1;
                int[][] samples = new int[dimension][dimension];
                boolean noOverlap = true;
                boolean spansValid = true;
                for (UpgraderRingRaster.Span span : raster.spans()) {
                    if (span.x() >= span.endX() || span.x() < -outer || span.endX() > outer + 1
                            || Math.abs(span.y()) > outer) {
                        spansValid = false;
                        continue;
                    }
                    for (int x = span.x(); x < span.endX(); x++) {
                        if (samples[span.y() + outer][x + outer] != 0) noOverlap = false;
                        samples[span.y() + outer][x + outer] = span.success() ? 2 : 1;
                    }
                }
                boolean exactCoverage = true;
                boolean exactClassification = true;
                for (int y = -outer; y <= outer; y++) {
                    for (int x = -outer; x <= outer; x++) {
                        long distance = (long) x * x + (long) y * y;
                        boolean inside = distance >= (long) inner * inner && distance <= (long) outer * outer;
                        int sample = samples[y + outer][x + outer];
                        if ((sample != 0) != inside) exactCoverage = false;
                        if (inside) {
                            double turn = (Math.atan2(y, x) / (Math.PI * 2) - .25 + 1) % 1;
                            boolean success = UpgraderMath.isWinningWheelTurn(turn, chance);
                            if ((sample == 2) != success) exactClassification = false;
                        }
                    }
                }
                check(spansValid && noOverlap, "physical raster spans are nonempty, bounded and disjoint");
                check(exactCoverage, "stationary ring raster covers exactly the annulus, including boundary pixels");
                check(exactClassification, "cached ring preserves the exact chance sector without minimum widening");
            }
        }
    }

    private static double contrast(int foreground, int background) {
        double first = luminance(foreground);
        double second = luminance(background);
        return (Math.max(first, second) + 0.05) / (Math.min(first, second) + 0.05);
    }

    private static double luminance(int color) {
        return linear((color >>> 16) & 255) * 0.2126 + linear((color >>> 8) & 255) * 0.7152
            + linear(color & 255) * 0.0722;
    }

    private static double linear(int channel) {
        double value = channel / 255.0;
        return value <= 0.04045 ? value / 12.92 : Math.pow((value + 0.055) / 1.055, 2.4);
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
        checks++;
    }
}
