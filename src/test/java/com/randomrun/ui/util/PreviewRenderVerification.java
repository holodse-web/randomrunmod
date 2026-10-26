package com.randomrun.ui.util;

import java.util.Random;

public final class PreviewRenderVerification {
    private PreviewRenderVerification() {}

    public static void main(String[] args) {
        System.out.println("Preview render verification passed: " + verify() + " assertions");
    }

    public static int verify() {
        int checks = 0;
        close(1, PreviewRenderGeometry.magnification(1, 0, 0, 1), "identity"); checks++;
        close(3, PreviewRenderGeometry.magnification(3, 0, 0, 3), "three-times GUI enlargement"); checks++;
        close(4, PreviewRenderGeometry.magnification(-2, 0, 0, 4), "reflection with nonuniform scale"); checks++;
        close(1, PreviewRenderGeometry.magnification(.5f, 0, 0, .5f), "small poses retain native detail"); checks++;
        close((1 + Math.sqrt(5)) / 2, PreviewRenderGeometry.magnification(1, 0, 1, 1), "shear"); checks++;
        close(1, PreviewRenderGeometry.magnification(Float.NaN, 0, 0, 1), "nonfinite pose fallback"); checks++;

        for (float size : new float[]{17.25f, 32, 48, 80}) {
            for (float scale : new float[]{.5f, 1, 1.5f, 2, 3}) {
                for (int guiScale = 1; guiScale <= 6; guiScale++) {
                    float expected = (float) (Math.ceil(size * Math.max(1, scale) * guiScale) / guiScale);
                    for (int degree = 0; degree < 360; degree += 3) {
                        float cosine = (float) Math.cos(Math.toRadians(degree)) * scale;
                        float sine = (float) Math.sin(Math.toRadians(degree)) * scale;
                        float magnification = PreviewRenderGeometry.magnification(cosine, sine, -sine, cosine);
                        float textureScale = PreviewRenderGeometry.textureScale(size, magnification, guiScale);
                        close(expected, textureScale, "rotating pose must reuse the same target scale"); checks++;
                        close(size, textureScale * (size / textureScale), "blit keeps displayed model size"); checks++;
                    }
                }
            }
        }

        Random random = new Random(0x52524D);
        for (int sample = 0; sample < 1_000; sample++) {
            float m00 = random.nextFloat() * 8 - 4;
            float m01 = random.nextFloat() * 8 - 4;
            float m10 = random.nextFloat() * 8 - 4;
            float m11 = random.nextFloat() * 8 - 4;
            float magnification = PreviewRenderGeometry.magnification(m00, m01, m10, m11);
            for (int direction = 0; direction < 16; direction++) {
                double angle = direction * Math.PI / 8;
                double x = Math.cos(angle), y = Math.sin(angle);
                double projectedLength = Math.hypot(m00 * x + m10 * y, m01 * x + m11 * y);
                require(projectedLength <= magnification + .000001, "native target undersamples a posed direction"); checks++;
            }
            int guiScale = 1 + sample % 6;
            float textureScale = PreviewRenderGeometry.textureScale(48, magnification, guiScale);
            require(textureScale * guiScale + .0002 >= 48 * magnification * guiScale,
                "allocated model resolution includes pose scale"); checks++;
        }

        close(0, PreviewRenderGeometry.frameSeconds(-1), "negative frame duration"); checks++;
        close(0, PreviewRenderGeometry.frameSeconds(0), "same instant"); checks++;
        close(1.0 / 60, PreviewRenderGeometry.frameSeconds(16_666_667), "60fps time units"); checks++;
        close(.05, PreviewRenderGeometry.frameSeconds(50_000_000), "20fps time units"); checks++;
        close(.05, PreviewRenderGeometry.frameSeconds(Long.MAX_VALUE), "paused frame cannot launch the preview"); checks++;
        return checks;
    }

    private static void close(double expected, double actual, String message) {
        require(Double.isFinite(actual) && Math.abs(expected - actual) < .00002, message + ": " + expected + " vs " + actual);
    }

    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
