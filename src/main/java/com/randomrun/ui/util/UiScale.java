package com.randomrun.ui.util;

import com.mojang.blaze3d.platform.Window;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;







public final class UiScale {
    public static final double BASE_WIDTH = 640;
    public static final double BASE_HEIGHT = 360;


    public interface Scaled {}

    private UiScale() {}

    public static boolean applies(Screen screen) {
        return screen instanceof Scaled;
    }

    public static boolean active() {
        Minecraft mc = Minecraft.getInstance();
        return mc != null && mc.gui != null && applies(mc.gui.screen());
    }


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


    public static float factor() {
        Window window = Minecraft.getInstance().getWindow();
        return (float) (unit(window) / Math.max(1, window.getGuiScale()));
    }


    public static double mouseX(Window window, double x) {
        return x * width() / Math.max(1, window.getScreenWidth());
    }

    public static double mouseY(Window window, double y) {
        return y * height() / Math.max(1, window.getScreenHeight());
    }
}
