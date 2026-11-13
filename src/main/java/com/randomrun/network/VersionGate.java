package com.randomrun.network;

import com.randomrun.infrastructure.RandomRunMod;
import com.randomrun.infrastructure.util.LanguageManager;
import com.randomrun.ui.overlay.CustomNotification;
import com.randomrun.ui.screen.main.UpdateRequiredScreen;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;

public final class VersionGate {
    public enum State { IDLE, PENDING, OK, UPDATE_REQUIRED, FAILED }

    private static volatile State state = State.IDLE;
    private static volatile boolean announce;
    private static CompletableFuture<Boolean> running;

    private VersionGate() {}

    public static void register() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> tick(client));
    }

    public static State state() {
        return state;
    }

    public static synchronized void start(boolean force) {
        if (state == State.PENDING) return;
        if (!force && (state == State.OK || state == State.UPDATE_REQUIRED)) return;
        state = State.PENDING;
        VersionChecker checker = VersionChecker.getInstance();
        CompletableFuture<Boolean> future;
        try {
            future = force ? checker.forceCheck() : checker.checkVersion();
        } catch (RuntimeException error) {
            fail();
            return;
        }
        running = future;
        future.orTimeout(VersionChecker.CHECK_TIMEOUT_MILLIS + 1000, TimeUnit.MILLISECONDS).whenComplete((required, error) -> {
            synchronized (VersionGate.class) {
                if (running != future) return;
                if (error != null || required == null || !VersionChecker.getInstance().isCheckCompleted()) fail();
                else state = required ? State.UPDATE_REQUIRED : State.OK;
                announce = true;
            }
        });
    }

    private static void fail() {
        state = State.FAILED;
        announce = true;
    }

    public static boolean requireOnline() {
        if (!RandomRunMod.getInstance().getConfig().isOnlineMode()) return true;
        switch (state) {
            case OK -> { return true; }
            case UPDATE_REQUIRED -> Minecraft.getInstance().gui.setScreen(new UpdateRequiredScreen());
            case FAILED -> {
                CustomNotification.showError(LanguageManager.translate("randomrun.version.failed"), LanguageManager.translate("randomrun.version.retrying"));
                start(true);
            }
            default -> {
                CustomNotification.show(LanguageManager.translate("randomrun.version.checking"));
                start(false);
            }
        }
        return false;
    }

    private static void tick(Minecraft client) {
        if (!announce) return;
        announce = false;
        if (!RandomRunMod.getInstance().getConfig().isOnlineMode()) return;
        Screen screen = client.gui.screen();
        boolean modScreen = com.randomrun.infrastructure.util.ModClasses.isOwn(screen);
        if (state == State.UPDATE_REQUIRED && modScreen && !(screen instanceof UpdateRequiredScreen)) client.gui.setScreen(new UpdateRequiredScreen());
        else if (state == State.FAILED && modScreen) CustomNotification.showError(LanguageManager.translate("randomrun.version.failed"), LanguageManager.translate("randomrun.version.failed_hint"));
    }
}
