package com.randomrun.ui.screen.main;

import com.randomrun.infrastructure.RandomRunMod;
import com.randomrun.infrastructure.util.LanguageManager;
import com.randomrun.infrastructure.version.VersionChecker;
import com.randomrun.ui.util.GuiText;
import com.randomrun.ui.widget.styled.ButtonDefault;
import com.randomrun.ui.widget.styled.ButtonYellow;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;

import java.util.concurrent.CompletableFuture;

public class VersionCheckScreen extends AbstractRandomRunScreen {
    private final Screen parent;
    private final Runnable onVerified;
    private boolean started;
    private boolean failed;
    private boolean handled;
    private boolean closed;
    private long checkStartedNanos;
    private CompletableFuture<Boolean> verification;
    private ButtonDefault retryButton;

    public VersionCheckScreen(Screen parent, Runnable onVerified) {
        super(LanguageManager.text("randomrun.screen.update.checking"));
        this.parent = parent;
        this.onVerified = onVerified;
    }

    @Override
    protected void init() {
        super.init();
        int x = (width - 200) / 2;
        int y = height / 2 + 10;
        retryButton = addRenderableWidget(new ButtonDefault(x, y, 200, 20,
            LanguageManager.text("randomrun.screen.update.retry"), button -> checkVersion()));
        retryButton.active = failed;
        addRenderableWidget(new ButtonYellow(x, y + 25, 200, 20,
            LanguageManager.text("randomrun.screen.update.play_offline"), button -> playOffline()));
        addRenderableWidget(new ButtonDefault(x, y + 50, 200, 20,
            LanguageManager.text("randomrun.button.back"), button -> onClose()));
        if (!started && !closed) {
            checkVersion();
        }
    }

    private void checkVersion() {
        started = true;
        failed = false;
        handled = false;
        checkStartedNanos = System.nanoTime();
        retryButton.active = false;
        try {
            verification = VersionChecker.getInstance().forceCheck();
        } catch (RuntimeException error) {
            showFailure();
        }
    }

    @Override
    public void tick() {
        super.tick();
        if (closed || !started || failed || handled || minecraft.gui.screen() != this) return;
        if (verification == null) {
            showFailure();
        } else if (verification.isDone()) {
            try {
                Boolean required = verification.join();
                if (required == null || !VersionChecker.getInstance().isCheckCompleted()) {
                    showFailure();
                    return;
                }
                handled = true;
                if (required) minecraft.gui.setScreen(new UpdateRequiredScreen());
                else onVerified.run();
            } catch (RuntimeException error) {
                if (minecraft.gui.screen() == this) showFailure();
            }
        } else if (System.nanoTime() - checkStartedNanos >=
            (VersionChecker.CHECK_TIMEOUT_MILLIS + 1_000) * 1_000_000L) {
            showFailure();
        }
    }

    private void showFailure() {
        failed = true;
        handled = true;
        if (retryButton != null) retryButton.active = true;
    }

    @Override
    public void added() {
        super.added();
        closed = false;
    }

    @Override
    public void removed() {
        closed = true;
        started = false;
        handled = false;
        verification = null;
        super.removed();
    }

    private void playOffline() {
        RandomRunMod.getInstance().getConfig().setOnlineMode(false);
        RandomRunMod.getInstance().saveConfig();
        minecraft.gui.setScreen(new MainModScreen(parent));
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        super.extractRenderState(context, mouseX, mouseY, delta);
        GuiText.centeredText(context, font,
            LanguageManager.text(failed ? "randomrun.screen.update.check_failed" : "randomrun.screen.update.checking"),
            width / 2, height / 2 - 35, failed ? 0xFFFF5555 : 0xFFFFFFFF);
    }

    @Override
    public void onClose() {
        minecraft.gui.setScreen(parent);
    }
}
