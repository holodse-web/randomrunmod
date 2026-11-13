package com.randomrun.ui.util;

import com.mojang.blaze3d.platform.Window;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;

/**
 * Makes the mod's interface look the same at any resolution, window size and
 * GUI scale: everything is laid out on a virtual canvas that is 640×360 on a
 * 16:9 screen (what 1920×1080 at GUI scale 3 gives) and stretched to fill the
 * window. Other aspect ratios keep the short side at 360/640 units.
 */
public final class UiScale {
    public static final double BASE_WIDTH = 640;
    public static final double BASE_HEIGHT = 360;

    /** Marker for screens drawn on the virtual canvas. */
    public interface Scaled {}

    private UiScale() {}

    public static boolean applies(Screen screen) {
        return screen instanceof Scaled;
    }

    public static boolean active() {
        Minecraft mc = Minecraft.getInstance();
        return mc != null && mc.gui != null && applies(mc.gui.screen());
    }

    /** Framebuffer pixels per virtual unit. */
    private static double unit(Window window) {
        return Math.max(0.25, Math.min(window.getWidth() / BASE_WIDTH, window.getHeight() / BASE_HEIGHT));
    }

    public static int width() {
        Window window = Minecraft.getInstance().getWindow();
        return Math.max(1, (int) Math.floor(window.getWidth() / unit(window)));
    }

    public static int height() {
        Window window = Minecraft.getInstance().getWindow();
        return Math.max(1, (int) Math.floor(window.getHeight() / unit(window)));
    }

    /** Pose scale from GUI-scaled coordinates to the virtual canvas. */
    public static float factor() {
        Window window = Minecraft.getInstance().getWindow();
        return (float) (unit(window) / Math.max(1, window.getGuiScale()));
    }

    /** Mouse position in virtual units, from window coordinates. */
    public static double mouseX(Window window, double x) {
        return x * width() / Math.max(1, window.getScreenWidth());
    }

    public static double mouseY(Window window, double y) {
        return y * height() / Math.max(1, window.getScreenHeight());
    }
}
