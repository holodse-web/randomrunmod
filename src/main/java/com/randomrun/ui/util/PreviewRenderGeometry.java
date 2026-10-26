package com.randomrun.ui.util;

public final class PreviewRenderGeometry {
    private PreviewRenderGeometry() {}

    public static float magnification(float m00, float m01, float m10, float m11) {
        double first = (double) m00 * m00 + (double) m01 * m01;
        double second = (double) m10 * m10 + (double) m11 * m11;
        double product = (double) m00 * m10 + (double) m01 * m11;
        double largest = (first + second + Math.hypot(first - second, 2 * product)) / 2;
        return Double.isFinite(largest) ? (float) Math.max(1, Math.sqrt(Math.max(0, largest))) : 1;
    }

    public static float frameSeconds(long elapsedNanos) {
        return Math.clamp(elapsedNanos / 1_000_000_000f, 0, .05f);
    }

    public static float textureScale(float size, float magnification, int guiScale) {
        double pixels = (double) size * magnification * guiScale;
        double nearestPixel = Math.rint(pixels);
        if (Math.abs(pixels - nearestPixel) <= Math.max(.0001, Math.ulp((float) pixels) * 2)) pixels = nearestPixel;
        return (float) (Math.ceil(pixels) / guiScale);
    }
}
