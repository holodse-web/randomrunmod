package com.randomrun.ui.util;

import com.randomrun.infrastructure.RandomRunMod;

public enum InterfaceStyle {
    CLASSIC("classic"), MODERN("modern");

    public final String id;
    private static final ThreadLocal<InterfaceStyle> PREVIEW = new ThreadLocal<>();

    InterfaceStyle(String id) { this.id = id; }

    public static InterfaceStyle fromId(String id) {
        return "classic".equals(id) ? CLASSIC : MODERN;
    }

    public static InterfaceStyle current() {
        InterfaceStyle preview = PREVIEW.get();
        if (preview != null) return preview;
        RandomRunMod mod = RandomRunMod.getInstance();
        return mod == null || mod.getConfig() == null ? MODERN : fromId(mod.getConfig().getInterfaceStyle());
    }

    public static boolean isClassic() { return current() == CLASSIC; }

    public static void withStyle(InterfaceStyle style, Runnable draw) {
        InterfaceStyle previous = PREVIEW.get();
        PREVIEW.set(style);
        try { draw.run(); }
        finally {
            if (previous == null) PREVIEW.remove();
            else PREVIEW.set(previous);
        }
    }
}
