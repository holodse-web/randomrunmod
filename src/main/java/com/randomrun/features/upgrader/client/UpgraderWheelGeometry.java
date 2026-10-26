package com.randomrun.features.upgrader.client;

public record UpgraderWheelGeometry(int guiRadius, int guiScale) {
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
