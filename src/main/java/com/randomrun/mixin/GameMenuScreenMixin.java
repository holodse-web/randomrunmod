package com.randomrun.mixin;

import com.randomrun.features.battle.manager.BattleManager;
import com.randomrun.features.battle.manager.BattleRoom;
import com.randomrun.infrastructure.RandomRunMod;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PauseScreen.class)
public abstract class GameMenuScreenMixin extends Screen {
    private static final int SEED_CHANGE_COOLDOWN_MS = 5000;
    private Button changeSeedButton = null;

    protected GameMenuScreenMixin(Component title) {
        super(title);
    }

    @Inject(method = "createPauseMenu", at = @At("TAIL"))
    private void onInitWidgets(CallbackInfo ci) {
        changeSeedButton = null;
        // Mod settings right from the pause menu, so they can be changed mid-run.
        Screen pause = this;
        int bx = width / 2 - 102, by = height / 4 + 120, bw = 204;
        for (var child : children()) {
            // Right under the lowest full-width button ("Save and Quit").
            if (child instanceof Button b && b.getWidth() >= 150 && b.getY() + 24 > by) {
                bx = b.getX();
                bw = b.getWidth();
                by = b.getY() + 24;
            }
        }
        addRenderableWidget(Button.builder(com.randomrun.infrastructure.util.LanguageManager.text("randomrun.pause.settings"),
                pressed -> minecraft.gui.setScreen(new com.randomrun.ui.screen.settings.SettingsScreen(pause)))
            .bounds(bx, by, bw, 20)
            .tooltip(net.minecraft.client.gui.components.Tooltip.create(com.randomrun.infrastructure.util.LanguageManager.text("randomrun.pause.settings.tooltip")))
            .build());
        var sessions = com.randomrun.core.run.RunSessionManager.getInstance();
        if (BattleManager.getInstance().isInBattle() || !sessions.hasActiveRun() || minecraft.getSingleplayerServer() == null) return;
        for (var child : new java.util.ArrayList<>(children())) {
            if (child instanceof Button button && button.getMessage().getContents() instanceof net.minecraft.network.chat.contents.TranslatableContents contents
                    && contents.getKey().equals("options.worldOptions.button")) {
                changeSeedButton = Button.builder(com.randomrun.infrastructure.util.LanguageManager.text("randomrun.pause.change_seed"), pressed -> {
                    if (!isButtonLocked()) onChangeSeedClick();
                }).bounds(button.getX(), button.getY(), button.getWidth(), button.getHeight()).build();
                changeSeedButton.active = !isButtonLocked();
                removeWidget(button);
                addRenderableWidget(changeSeedButton);
                return;
            }
        }
    }

    @Inject(method = "extractRenderState", at = @At("TAIL"))
    private void onRender(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        if (changeSeedButton != null) {
            com.randomrun.core.run.RunSessionManager sessionManager =
                com.randomrun.core.run.RunSessionManager.getInstance();

            if (sessionManager.hasActiveRun()) {
                com.randomrun.core.run.RunSession session = sessionManager.getCurrentSession();
                long timeSinceLoad = session != null ? session.getTimeSinceWorldLoad() : 0;
                long remainingMs = SEED_CHANGE_COOLDOWN_MS - timeSinceLoad;
                boolean isLocked = remainingMs > 0;

                if (isLocked) {
                    int remainingSec = (int) Math.ceil(remainingMs / 1000.0);
                    changeSeedButton.setMessage(com.randomrun.infrastructure.util.LanguageManager.text("randomrun.pause.change_seed_wait", remainingSec));
                    changeSeedButton.active = false;
                } else {
                    changeSeedButton.setMessage(com.randomrun.infrastructure.util.LanguageManager.text("randomrun.pause.change_seed"));
                    changeSeedButton.active = true;
                }
            }

            if (changeSeedButton.isMouseOver(mouseX, mouseY)) {
                context.setTooltipForNextFrame(
                    this.font,
                    com.randomrun.infrastructure.util.LanguageManager.text("randomrun.pause.change_seed.tooltip"),
                    mouseX,
                    mouseY
                );
            }
        }
    }

    private boolean isButtonLocked() {
        com.randomrun.core.run.RunSessionManager sessionManager =
            com.randomrun.core.run.RunSessionManager.getInstance();

        if (!sessionManager.hasActiveRun()) {
            return false;
        }

        com.randomrun.core.run.RunSession session = sessionManager.getCurrentSession();
        if (session == null) {
            return false;
        }

        long timeSinceLoad = session.getTimeSinceWorldLoad();
        return timeSinceLoad < SEED_CHANGE_COOLDOWN_MS;
    }

    private void onChangeSeedClick() {
        if (com.randomrun.features.speedrun.WorldCreator.SeedChanger.changeSeed()) changeSeedButton.active = false;
    }

    @Mixin(Minecraft.class)
    public static class Disconnect {
    @Inject(method = "disconnectFromWorld", at = @At("HEAD"), cancellable = true)
    private void onDisconnect(Component reason, CallbackInfo ci) {
        if (!(Minecraft.getInstance().gui.screen() instanceof PauseScreen)) return;
        BattleManager battleManager = BattleManager.getInstance();
        BattleRoom room = battleManager.getCurrentRoom();

        if (!battleManager.isInBattle()) {
            com.randomrun.core.run.RunSessionManager sessionManager =
                com.randomrun.core.run.RunSessionManager.getInstance();

            if (sessionManager.hasActiveRun() && sessionManager.isRunning()) {
                com.randomrun.core.run.RunCompletionService.getInstance()
                    .completeWithGaveUp();
            } else {
                RandomRunMod.getInstance().getRunDataManager().cancelRun();
                sessionManager.cancelRun();
            }
        }

        if (room != null && room.isSharedWorld() && battleManager.isHost()) {
            if (room.getStatus() == BattleRoom.RoomStatus.STARTED && room.getWinner() == null) {
                Minecraft client = Minecraft.getInstance();
                if (client.player != null) {
                    com.randomrun.infrastructure.util.CompactLogger.ClientMessages.send(client.player, com.randomrun.infrastructure.util.LanguageManager.text("randomrun.pause.host_leave_warning"), false);
                    com.randomrun.infrastructure.util.CompactLogger.ClientMessages.send(client.player, com.randomrun.infrastructure.util.LanguageManager.text("randomrun.pause.host_leave_confirm"), false);
                }

                if (!battleManager.isDisconnectConfirmed()) {
                    battleManager.setDisconnectConfirmed(true);
                    ci.cancel();
                    Minecraft.getInstance().gui.setScreen(null);
                }
            }
        }
    }
    }
}
