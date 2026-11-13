package com.randomrun.ui.screen.account;

import com.randomrun.infrastructure.util.LanguageManager;
import com.randomrun.ui.screen.main.AbstractRandomRunScreen;
import com.randomrun.ui.util.GuiText;
import com.randomrun.ui.util.Previews.Heads;
import com.randomrun.ui.util.Theme;
import com.randomrun.ui.util.Ui;
import com.randomrun.ui.widget.OverlayWidgets.LanguageDropdownWidget;
import com.randomrun.ui.widget.styled.Buttons.ButtonMenu;
import com.randomrun.ui.widget.styled.Buttons.ButtonRainbow;
import com.randomrun.ui.widget.styled.TextFieldStyled;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.sounds.SoundEvents;

/** Password change, laid out like the login screen: one panel, head and name on top, fields below. */
public class ChangePasswordScreen extends AbstractRandomRunScreen {
    private static final int PANEL_WIDTH = 260;
    private static final int PANEL_HEIGHT = 186;

    private final Screen parent;
    private final String playerName;

    private TextFieldStyled currentPasswordField;
    private TextFieldStyled newPasswordField;
    private TextFieldStyled confirmPasswordField;

    private String errorMessage;
    private long errorTime;
    private boolean isChanging;
    private long openTime;

    public ChangePasswordScreen(Screen parent, String playerName) {
        super(LanguageManager.text("randomrun.changepassword.title"));
        this.parent = parent;
        this.playerName = playerName;
    }

    @Override
    protected void init() {
        super.init();
        openTime = System.currentTimeMillis();
        int centerX = width / 2;
        int y = panelTop() + 62;

        currentPasswordField = field(centerX, y, "randomrun.changepassword.current.placeholder", 0f);
        newPasswordField = field(centerX, y + 26, "randomrun.changepassword.new.placeholder", 0.04f);
        confirmPasswordField = field(centerX, y + 52, "randomrun.changepassword.confirm.placeholder", 0.08f);

        ButtonRainbow changeButton = new ButtonRainbow(centerX - 100, y + 82, 200, 20,
            LanguageManager.text("randomrun.changepassword.button.change"), button -> changePassword(), 0, 0.12f);
        changeButton.setAnimationDirection(ButtonRainbow.AnimationDirection.HORIZONTAL_LEFT);
        addRenderableWidget(changeButton);

        addRenderableWidget(new ButtonMenu(centerX - 100, height - 30, 200, 20,
            LanguageManager.text("randomrun.button.back"), button -> onClose(), 1, 0.2f));
        addRenderableWidget(new LanguageDropdownWidget(10, height - 30, this, 0.25f));
        setInitialFocus(currentPasswordField);
    }

    private TextFieldStyled field(int centerX, int y, String placeholderKey, float delay) {
        TextFieldStyled field = new TextFieldStyled(font, centerX - 100, y, 200, 20, LanguageManager.text(placeholderKey), delay) {
            @Override
            public boolean keyPressed(net.minecraft.client.input.KeyEvent event) {
                int key = event.key();
                if (key == com.mojang.blaze3d.platform.InputConstants.KEY_RETURN || key == com.mojang.blaze3d.platform.InputConstants.KEY_NUMPADENTER) {
                    changePassword();
                    return true;
                }
                return super.keyPressed(event);
            }
        };
        field.setMaxLength(64);
        field.setCenteredPlaceholder(LanguageManager.text(placeholderKey));
        field.masked();
        addRenderableWidget(field);
        return field;
    }

    private int panelTop() {
        return height / 2 - PANEL_HEIGHT / 2 - 16;
    }

    private int panelBottom() {
        return panelTop() + PANEL_HEIGHT;
    }

    private void changePassword() {
        if (isChanging) return;
        String currentPassword = currentPasswordField.getValue();
        String nextPassword = newPasswordField.getValue();
        if (!validatePasswords(currentPassword, nextPassword, confirmPasswordField.getValue())) return;
        isChanging = true;
        com.randomrun.network.BackendSession.changePassword(currentPassword, nextPassword)
            .whenComplete((success, error) -> Minecraft.getInstance().execute(() -> {
                if (Minecraft.getInstance().gui.screen() != this) return;
                isChanging = false;
                if (error != null) {
                    showError(LanguageManager.translate("randomrun.password.change_failed"));
                    return;
                }
                Ui.playSound(SoundEvents.PLAYER_LEVELUP, 1.6f, 0.4f);
                Minecraft.getInstance().gui.setScreen(new com.randomrun.ui.screen.main.MainModScreen(null));
            }));
    }

    private boolean validatePasswords(String currentPassword, String newPassword, String confirmPassword) {
        String key = null;
        if (currentPassword == null || currentPassword.isEmpty()) key = "randomrun.changepassword.error.current_empty";
        else if (newPassword == null || newPassword.isEmpty()) key = "randomrun.changepassword.error.new_empty";
        else if (newPassword.length() < 8) key = "randomrun.auth.error.short";
        else if (!newPassword.equals(confirmPassword)) key = "randomrun.auth.error.mismatch";
        else if (currentPassword.equals(newPassword)) key = "randomrun.changepassword.error.same";
        if (key == null) return true;
        showError(LanguageManager.translate(key));
        return false;
    }

    private void showError(String message) {
        errorMessage = message;
        errorTime = System.currentTimeMillis();
        Ui.playSound(SoundEvents.NOTE_BLOCK_BASS.value(), 0.8f, 0.5f);
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        super.extractBackground(context, mouseX, mouseY, delta);
        float fade = Math.min(1f, (System.currentTimeMillis() - openTime) / 300f);
        int left = width / 2 - PANEL_WIDTH / 2, right = width / 2 + PANEL_WIDTH / 2;
        int top = panelTop(), bottom = panelBottom();
        Theme theme = Theme.current();
        Ui.panel(context, left, top, right, bottom, fade);
        Ui.glowBorder(context, left, top, right, bottom, 2, 0xFFFFFF);

        GuiText.centeredText(context, font, LanguageManager.text("randomrun.changepassword.title"), width / 2, top + 10, Ui.argb(0xFFFFFF, fade));
        Ui.divider(context, left + 12, right - 12, top + 23, theme.border, fade);

        int nameWidth = font.width(playerName);
        int headX = width / 2 - (nameWidth + 20) / 2;
        Heads.draw(context, playerName, headX, top + 31, 14, Ui.argb(theme.accent, fade));
        GuiText.text(context, font, playerName, headX + 20, top + 34, Ui.argb(theme.accent, fade), true);
        GuiText.centeredText(context, font, LanguageManager.text("randomrun.changepassword.hint"), width / 2, top + 49, Ui.argb(0x8A8A93, fade));
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        super.extractRenderState(context, mouseX, mouseY, delta);
        int y = panelBottom() + 6;
        if (isChanging) {
            String dots = ".".repeat((int) (System.currentTimeMillis() / 300 % 4));
            GuiText.centeredText(context, font, LanguageManager.translate("randomrun.changepassword.changing") + dots, width / 2, y, 0xFF4ADE80);
        } else if (errorMessage != null && System.currentTimeMillis() - errorTime < 4000) {
            float alpha = Math.min(1f, (4000 - (System.currentTimeMillis() - errorTime)) / 400f);
            GuiText.centeredText(context, font, errorMessage, width / 2, y, Ui.argb(0xF87171, alpha));
        }
    }

    @Override
    public void onClose() {
        minecraft.gui.setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
