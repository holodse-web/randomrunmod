package com.randomrun.features.battle.screen;

import com.randomrun.features.battle.manager.BattleManager;
import com.randomrun.features.battle.manager.BattleRoom;
import com.randomrun.features.battle.manager.BattleSupport.RoomJoinResult;
import com.randomrun.features.battle.screen.LobbyRender.UIColors;
import com.randomrun.ui.screen.main.AbstractRandomRunScreen;
import com.randomrun.ui.widget.styled.Buttons.ButtonDefault;
import com.randomrun.ui.widget.styled.TextFieldStyled;

import java.util.concurrent.CompletableFuture;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;

public class PasswordInputScreen extends AbstractRandomRunScreen {
    private static final long CONNECTION_TIMEOUT = 10000;
    private static final int SHAKE_DURATION = 500;
    
    private final Screen parent;
    private final BattleRoom room;
    private TextFieldStyled passwordField;
    private ButtonDefault joinButton;
    private ButtonDefault showPasswordButton;
    
    private boolean joining = false;
    private boolean showPassword = false;
    private String errorMsg = null;
    private boolean neutralMessage;
    private long shakeStartTime = 0;
    private int retryCount = 0;
    private long joinStarted;

    public PasswordInputScreen(Screen parent, BattleRoom room) {
        super(com.randomrun.infrastructure.util.LanguageManager.text("randomrun.battle.enter_password"));
        this.parent = parent;
        this.room = room;
    }

    @Override
    protected void init() {
        String previousPassword = passwordField == null ? "" : passwordField.getValue();
        super.init();
        
        int centerX = width / 2;
        int centerY = height / 2;
        
        passwordField = new TextFieldStyled(font, centerX - 100, centerY - 15, 170, 20, 
            com.randomrun.infrastructure.util.LanguageManager.text("randomrun.battle.password"), 0.05f);
        passwordField.setMaxLength(32);
        passwordField.setMasked(!showPassword);
        passwordField.setCenteredPlaceholder(
            com.randomrun.infrastructure.util.LanguageManager.text("randomrun.battle.enter_password_placeholder"));
        addRenderableWidget(passwordField);
        setInitialFocus(passwordField);
        
        showPasswordButton = new ButtonDefault(
            centerX + 75, centerY - 15, 25, 20,
            Component.literal(showPassword ? "👁" : "🔒"),
            button -> {
                showPassword = !showPassword;
                passwordField.setMasked(!showPassword);
                showPasswordButton.icon(showPassword ? com.randomrun.ui.util.Icons.EYE : com.randomrun.ui.util.Icons.LOCK);
                playSound(SoundEvents.UI_BUTTON_CLICK.value());
            },
            0, 0.05f
        );
        showPasswordButton.icon(showPassword ? com.randomrun.ui.util.Icons.EYE : com.randomrun.ui.util.Icons.LOCK);
        addRenderableWidget(showPasswordButton);
        
        joinButton = new ButtonDefault(
            centerX - 100, centerY + 20, 200, 20,
            com.randomrun.infrastructure.util.LanguageManager.text("randomrun.battle.join_room"),
            button -> joinRoom(),
            0, 0.1f
        );
        addRenderableWidget(joinButton);
        
        addRenderableWidget(new ButtonDefault(
            centerX - 100, height - 30, 200, 20,
            com.randomrun.infrastructure.util.LanguageManager.text("randomrun.button.cancel"),
            button -> onClose(),
            1, 0.15f
        ));
        passwordField.setValue(previousPassword);
        joinButton.active = !joining;
    }

    private void joinRoom() {
        if (joining) return;
        
        joining = true;
        errorMsg = null;
        neutralMessage = false;
        joinButton.active = false;
        joinStarted = System.currentTimeMillis();
        
        String password = passwordField.getValue().trim();
        String playerName = com.randomrun.network.BackendSession.playerName();
        
        CompletableFuture<RoomJoinResult> joinFuture = BattleManager.getInstance()
            .joinRoomDetailed(playerName, room.getRoomCode(), password);
        
        joinFuture.whenComplete((result, throwable) -> minecraft.execute(() -> {
                joining = false;
                joinButton.active = true;
                if (minecraft.gui.screen() != this) return;
                if (throwable != null) {
                    errorMsg = com.randomrun.infrastructure.util.LanguageManager.text(RoomJoinResult.CONNECTION_FAILED.messageKey()).getString();
                    shakeStartTime = System.currentTimeMillis();
                    playSound(SoundEvents.VILLAGER_NO);
                    com.randomrun.infrastructure.RandomRunMod.LOGGER.error("[Password] Ошибка подключения", throwable);
                    return;
                }
                
                if (result != null && result.joined()) {
                        playSound(SoundEvents.PLAYER_LEVELUP);
                        minecraft.gui.setScreen(new RoomWaitingScreen(parent, room.getRoomCode(), false));
                } else {
                    RoomJoinResult reason = result == null ? RoomJoinResult.CONNECTION_FAILED : result;
                    errorMsg = com.randomrun.infrastructure.util.LanguageManager.text(reason.messageKey()).getString();
                    neutralMessage = reason == RoomJoinResult.CANCELLED;
                    if (reason == RoomJoinResult.INVALID_PASSWORD) retryCount++;
                    if (reason != RoomJoinResult.CANCELLED) {
                        shakeStartTime = System.currentTimeMillis();
                        playSound(SoundEvents.VILLAGER_NO);
                    } else shakeStartTime = 0;
                }
            }));
    }

    @Override
    public void onClose() {
        if (!joining) minecraft.gui.setScreen(parent);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        extractBackground(context, mouseX, mouseY, delta);
        
        com.randomrun.ui.util.GuiText.centeredText(context, font, 
            com.randomrun.infrastructure.util.LanguageManager.text("randomrun.battle.enter_password"), 
            width / 2, height / 2 - 50, UIColors.TEXT_WHITE);
        
        super.extractRenderState(context, mouseX, mouseY, delta);
        
        renderError(context);
        renderLoadingIndicator(context);
        renderRetryHint(context);
    }
    
    private void renderError(GuiGraphicsExtractor context) {
        if (errorMsg != null) {
            long elapsed = System.currentTimeMillis() - shakeStartTime;
            int shakeOffset = 0;
            
            if (elapsed < SHAKE_DURATION) {
                shakeOffset = (int)(Math.sin(elapsed / 50.0) * 3);
            }
            
            com.randomrun.ui.util.GuiText.centeredText(context, font, 
                Component.literal(errorMsg), 
                width / 2 + shakeOffset, height / 2 + 5, neutralMessage ? UIColors.TEXT_GRAY : UIColors.TEXT_ERROR);
        }
    }
    
    private void renderLoadingIndicator(GuiGraphicsExtractor context) {
        if (joining) {
            long time = System.currentTimeMillis();
            String dots = ".".repeat((int)((time / 300) % 4));
            com.randomrun.ui.util.GuiText.centeredText(context, font, 
                com.randomrun.infrastructure.util.LanguageManager.text(time - joinStarted >= CONNECTION_TIMEOUT ? "randomrun.join.waiting_reply" : "randomrun.join.connecting").append(dots),
                width / 2, height / 2 + 80, UIColors.TEXT_GRAY);
        }
    }
    
    private void renderRetryHint(GuiGraphicsExtractor context) {
        if (retryCount > 0 && !joining) {
            com.randomrun.ui.util.GuiText.centeredText(context, font, 
                com.randomrun.infrastructure.util.LanguageManager.text("randomrun.join.retry_hint"),
                width / 2, height / 2 + 95, UIColors.TEXT_GRAY);
        }
    }
    
    @Override
    public boolean keyPressed(net.minecraft.client.input.KeyEvent event) {
        int keyCode = event.key();
        int scanCode = event.keycode();
        int modifiers = event.modifiers();
        if ((keyCode == com.mojang.blaze3d.platform.InputConstants.KEY_RETURN
                || keyCode == com.mojang.blaze3d.platform.InputConstants.KEY_NUMPADENTER) && !joining) {
            joinRoom();
            return true;
        }
        return super.keyPressed(new net.minecraft.client.input.KeyEvent(keyCode, scanCode, modifiers));
    }
    
    private void playSound(net.minecraft.sounds.SoundEvent sound) {
        minecraft.getSoundManager().play(
            net.minecraft.client.resources.sounds.SimpleSoundInstance.forUI(sound, 1.0f)
        );
    }
}
