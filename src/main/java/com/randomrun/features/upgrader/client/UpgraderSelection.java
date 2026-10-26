package com.randomrun.features.upgrader.client;

import com.mojang.blaze3d.platform.InputConstants;
import com.randomrun.features.upgrader.UpgraderTransaction;

public final class UpgraderSelection {
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
