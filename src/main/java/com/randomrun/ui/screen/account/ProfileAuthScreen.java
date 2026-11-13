package com.randomrun.ui.screen.account;

import com.randomrun.core.data.DataSyncService.CoreInitializer;
import com.randomrun.core.data.PlayerProfile;
import com.randomrun.infrastructure.RandomRunMod;
import com.randomrun.infrastructure.util.LanguageManager;
import com.randomrun.network.BackendSession;
import com.randomrun.network.HttpClient;
import com.randomrun.ui.screen.main.AbstractRandomRunScreen;
import com.randomrun.ui.screen.main.MainModScreen;
import com.randomrun.ui.util.GuiText;
import com.randomrun.ui.util.Previews.Heads;
import com.randomrun.ui.util.Theme;
import com.randomrun.ui.util.Ui;
import com.randomrun.ui.widget.OverlayWidgets.LanguageDropdownWidget;
import com.randomrun.ui.widget.OverlayWidgets.ThemeSwitchWidget;
import com.randomrun.ui.widget.styled.Buttons.ButtonMenu;
import com.randomrun.ui.widget.styled.Buttons.ButtonRainbow;
import com.randomrun.ui.widget.styled.TextFieldStyled;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.sounds.SoundEvents;

public class ProfileAuthScreen extends AbstractRandomRunScreen {
    private enum Mode { CHECKING, LOGIN, CREATE, UNAVAILABLE }

    private static final int PANEL_WIDTH = 260;

    private final Screen parent;
    private final String playerName;
    private final MainModScreen mainModScreen;
    private Mode mode = Mode.CHECKING;
    private TextFieldStyled passwordField;
    private TextFieldStyled confirmField;
    private String errorMessage;
    private long errorTime;
    private boolean authenticating;
    private long openTime;
    private int checkGeneration;

    public ProfileAuthScreen(Screen parent, String playerName, MainModScreen mainModScreen) {
        super(LanguageManager.text("randomrun.auth.title.check"));
        this.parent = parent;
        this.playerName = playerName;
        this.mainModScreen = mainModScreen;
    }

    @Override
    protected void init() {
        super.init();
        openTime = System.currentTimeMillis();
        passwordField = null;
        confirmField = null;
        if (mode == Mode.CHECKING) checkProfile();
        int centerX = width / 2;
        int panelTop = panelTop();
        int y = panelTop + 58;

        if (mode == Mode.LOGIN || mode == Mode.CREATE) {
            passwordField = field(centerX, y, "randomrun.auth.password.placeholder", 0f);
            y += 26;
            if (mode == Mode.CREATE) {
                confirmField = field(centerX, y, "randomrun.auth.password.confirm.placeholder", 0.05f);
                y += 26;
            }
            ButtonRainbow submit = new ButtonRainbow(centerX - 100, y + 4, 200, 20,
                LanguageManager.text(mode == Mode.LOGIN ? "randomrun.auth.button.login" : "randomrun.auth.button.create"),
                button -> authenticate(), 0, 0.1f);
            submit.setAnimationDirection(ButtonRainbow.AnimationDirection.HORIZONTAL_LEFT);
            submit.setTooltip(Tooltip.create(LanguageManager.text(mode == Mode.LOGIN ? "randomrun.auth.button.login.tooltip" : "randomrun.auth.button.create.tooltip")));
            addRenderableWidget(submit);
            setInitialFocus(passwordField);
        } else if (mode == Mode.UNAVAILABLE) {
            addRenderableWidget(new ButtonMenu(centerX - 100, y + 30, 200, 20, LanguageManager.text("randomrun.button.retry"), button -> {
                mode = Mode.CHECKING;
                rebuildWidgets();
            }, 0, 0.05f));
        }

        ButtonMenu offline = new ButtonMenu(centerX - 100, panelBottom() + 8, 200, 20,
            LanguageManager.text("randomrun.auth.button.play_offline"), button -> playOffline(), 1, 0.15f);
        offline.setTooltip(Tooltip.create(LanguageManager.text("randomrun.auth.button.play_offline.tooltip")));
        addRenderableWidget(offline);
        addRenderableWidget(new ButtonMenu(centerX - 100, height - 30, 200, 20,
            LanguageManager.text("randomrun.button.back"), button -> onClose(), 2, 0.2f));
        addRenderableWidget(new LanguageDropdownWidget(10, height - 30, this, 0.25f));
        addRenderableWidget(new ThemeSwitchWidget(34, height - 30, 0.3f));
    }

    private TextFieldStyled field(int centerX, int y, String placeholderKey, float delay) {
        TextFieldStyled field = new TextFieldStyled(font, centerX - 100, y, 200, 20, LanguageManager.text(placeholderKey), delay) {
            @Override
            public boolean keyPressed(net.minecraft.client.input.KeyEvent event) {
                if (event.key() == com.mojang.blaze3d.platform.InputConstants.KEY_RETURN || event.key() == com.mojang.blaze3d.platform.InputConstants.KEY_NUMPADENTER) {
                    authenticate();
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

    private int panelHeight() {
        return switch (mode) {
            case CHECKING -> 90;
            case LOGIN -> 128;
            case CREATE -> 154;
            case UNAVAILABLE -> 120;
        };
    }

    private int panelTop() {
        return height / 2 - panelHeight() / 2 - 20;
    }

    private int panelBottom() {
        return panelTop() + panelHeight();
    }

    private void checkProfile() {
        int generation = ++checkGeneration;
        HttpClient.getInstance().request("GET", "/auth/exists/" + playerName, null, false).whenComplete((value, error) -> minecraft.execute(() -> {
            if (generation != checkGeneration || minecraft.gui.screen() != this) return;
            if (error != null || value == null || !value.isJsonObject() || !value.getAsJsonObject().has("exists")) {
                mode = Mode.UNAVAILABLE;
            } else {
                mode = value.getAsJsonObject().get("exists").getAsBoolean() ? Mode.LOGIN : Mode.CREATE;
            }
            rebuildWidgets();
        }));
    }

    private void authenticate() {
        if (!com.randomrun.network.VersionGate.requireOnline()) return;
        if (authenticating || passwordField == null || (mode != Mode.LOGIN && mode != Mode.CREATE)) return;
        String password = passwordField.getValue();
        if (password.isEmpty()) { showError(LanguageManager.translate("randomrun.auth.error.empty")); return; }
        if (password.length() < 8) { showError(LanguageManager.translate("randomrun.auth.error.short")); return; }
        if (mode == Mode.CREATE && (confirmField == null || !password.equals(confirmField.getValue()))) {
            showError(LanguageManager.translate("randomrun.auth.error.mismatch"));
            return;
        }
        authenticating = true;
        boolean register = mode == Mode.CREATE;
        BackendSession.authenticate(playerName, password, register)
            .thenCompose(profile -> PlayerProfile.load(playerName))
            .whenComplete((success, error) -> minecraft.execute(() -> {
                authenticating = false;
                if (minecraft.gui.screen() != this) return;
                if (error != null || !Boolean.TRUE.equals(success)) {
                    handleFailure(error);
                    return;
                }
                CoreInitializer.reset();
                CoreInitializer.initialize(playerName);
                Ui.playSound(SoundEvents.PLAYER_LEVELUP, 1.6f, 0.4f);
                minecraft.gui.setScreen(mainModScreen);
            }));
    }

    private void handleFailure(Throwable error) {
        Throwable cause = error;
        while (cause != null && !(cause instanceof HttpClient.ApiException) && cause.getCause() != null && cause.getCause() != cause) cause = cause.getCause();
        String code = cause instanceof HttpClient.ApiException api ? api.getMessage() : null;
        if ("USERNAME_TAKEN".equals(code)) {
            mode = Mode.LOGIN;
            rebuildWidgets();
            showError(LanguageManager.translate("randomrun.auth.error.taken"));
            return;
        }
        if ("ACCOUNT_BANNED".equals(code) || "MOD_MODIFIED".equals(code)) {
            authenticating = false;
            minecraft.gui.setScreen(new com.randomrun.ui.screen.account.BanScreen(parent, playerName,
                "ACCOUNT_BANNED".equals(code) ? com.randomrun.ui.screen.account.BanScreen.Kind.BANNED : com.randomrun.ui.screen.account.BanScreen.Kind.MOD_MODIFIED, mainModScreen));
            return;
        }
        String key = code == null ? "randomrun.auth.error.connection" : switch (code) {
            case "INVALID_CREDENTIALS" -> "randomrun.auth.error.wrong";
            case "RATE_LIMITED" -> "randomrun.auth.error.rate";
            case "ACCOUNT_BANNED" -> "randomrun.auth.error.banned";
            case "INVALID_PASSWORD" -> "randomrun.auth.error.short";
            case "INVALID_USERNAME" -> "randomrun.auth.error.username";
            default -> "randomrun.auth.error.connection";
        };
        showError(LanguageManager.translate(key));
        if (passwordField != null) passwordField.setValue("");
        if (confirmField != null) confirmField.setValue("");
        setFocused(passwordField);
    }

    private void playOffline() {
        BackendSession.clear();
        RandomRunMod.getInstance().getConfig().setOnlineMode(false);
        RandomRunMod.getInstance().saveConfig();
        minecraft.execute(() -> minecraft.gui.setScreen(mainModScreen));
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
        Ui.panel(context, left, top, right, bottom, fade);
        Ui.glowBorder(context, left, top, right, bottom, 2, 0xFFFFFF);
        Theme theme = Theme.current();

        String titleKey = switch (mode) {
            case CHECKING -> "randomrun.auth.title.check";
            case LOGIN -> "randomrun.auth.title.login";
            case CREATE -> "randomrun.auth.title.create";
            case UNAVAILABLE -> "randomrun.auth.title.unavailable";
        };
        GuiText.centeredText(context, font, LanguageManager.text(titleKey), width / 2, top + 10, Ui.argb(0xFFFFFF, fade));
        Ui.divider(context, left + 12, right - 12, top + 23, theme.border, fade);

        int nameWidth = font.width(playerName);
        int headX = width / 2 - (nameWidth + 20) / 2;
        Heads.draw(context, playerName, headX, top + 31, 14, Ui.argb(theme.accent, fade));
        GuiText.text(context, font, playerName, headX + 20, top + 34, Ui.argb(theme.accent, fade), true);

        int infoY = top + 50;
        switch (mode) {
            case CHECKING -> {
                Ui.spinner(context, width / 2, infoY + 14, 6, theme.accent);
                GuiText.centeredText(context, font, LanguageManager.text("randomrun.auth.checking"), width / 2, infoY + 26, Ui.argb(0xA1A1AA, fade));
            }
            case UNAVAILABLE -> {
                int y = infoY;
                for (var line : font.split(LanguageManager.text("randomrun.auth.unavailable"), PANEL_WIDTH - 30)) {
                    GuiText.centeredText(context, font, line, width / 2, y, Ui.argb(0xF87171, fade));
                    y += 10;
                }
            }
            case CREATE -> GuiText.centeredText(context, font, LanguageManager.text("randomrun.auth.create_hint"), width / 2, top + 47, Ui.argb(0x8A8A93, fade));
            case LOGIN -> GuiText.centeredText(context, font, LanguageManager.text("randomrun.auth.login_hint"), width / 2, top + 47, Ui.argb(0x8A8A93, fade));
        }
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        super.extractRenderState(context, mouseX, mouseY, delta);
        int bottom = panelBottom();
        if (authenticating) {
            String dots = ".".repeat((int) (System.currentTimeMillis() / 300 % 4));
            GuiText.centeredText(context, font, LanguageManager.translate("randomrun.auth.connecting") + dots, width / 2, bottom - 14, 0xFF4ADE80);
        } else if (errorMessage != null && System.currentTimeMillis() - errorTime < 4000) {
            float alpha = Math.min(1f, (4000 - (System.currentTimeMillis() - errorTime)) / 400f);
            GuiText.centeredText(context, font, errorMessage, width / 2, bottom - 14, Ui.argb(0xF87171, alpha));
        }
        if (mode == Mode.LOGIN) {
            GuiText.centeredText(context, font, LanguageManager.text("randomrun.auth.forgot_password_line1"), width / 2, bottom + 34, 0xFF888888);
            GuiText.centeredText(context, font, LanguageManager.text("randomrun.auth.forgot_password_line2"), width / 2, bottom + 45, 0xFF888888);
        }
    }

    @Override
    public void onClose() {
        checkGeneration++;
        minecraft.gui.setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
