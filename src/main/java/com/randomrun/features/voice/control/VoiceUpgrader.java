package com.randomrun.features.voice.control;

import com.randomrun.features.upgrader.client.UpgraderClient;
import com.randomrun.features.upgrader.client.UpgraderScreen;

import java.util.function.BiConsumer;
import net.minecraft.client.Minecraft;

/** Voice control of the RRM Upgrader: open it, put items on the stake, pick odds, roll. */
final class VoiceUpgrader {
    private VoiceUpgrader() {}

    /** Opens the upgrader if needed, waits for it to lay out, then returns it. */
    private static UpgraderScreen ensureOpen(VoiceController c, Minecraft mc, int[] wait) {
        if (mc.gui.screen() instanceof UpgraderScreen screen) {
            return wait[0]-- <= 0 ? screen : null;
        }
        if (!UpgraderClient.available()) {
            c.status("upgrader_off", VoiceTasks.BAD);
            wait[0] = -100;
            return null;
        }
        if (mc.gui.screen() != null) mc.player.closeContainer();
        mc.gui.setScreen(new UpgraderScreen());
        wait[0] = 2;
        return null;
    }

    static final class OpenTask implements VoiceTasks.Task {
        private final boolean open;

        OpenTask(boolean open) { this.open = open; }

        @Override
        public boolean tick(VoiceController c, Minecraft mc) {
            if (open) {
                if (mc.gui.screen() instanceof UpgraderScreen) return true;
                if (!UpgraderClient.available()) {
                    c.status("upgrader_off", VoiceTasks.BAD);
                    return true;
                }
                if (mc.gui.screen() != null) mc.player.closeContainer();
                mc.gui.setScreen(new UpgraderScreen());
            } else if (mc.gui.screen() instanceof UpgraderScreen screen) {
                screen.onClose();
            }
            return true;
        }

        @Override
        public String label() {
            return VoiceTasks.text(open ? "upgrader_open" : "upgrader_close");
        }
    }

    static final class ActionTask implements VoiceTasks.Task {
        private final String label;
        private final BiConsumer<VoiceController, UpgraderScreen> action;
        private final int[] wait = {0};
        private int ticks;

        ActionTask(String label, BiConsumer<VoiceController, UpgraderScreen> action) {
            this.label = label;
            this.action = action;
        }

        @Override
        public boolean tick(VoiceController c, Minecraft mc) {
            if (++ticks > 40) return true;
            UpgraderScreen screen = ensureOpen(c, mc, wait);
            if (wait[0] < -50) return true;
            if (screen == null) return false;
            action.accept(c, screen);
            return true;
        }

        @Override
        public String label() {
            return label;
        }
    }
}
