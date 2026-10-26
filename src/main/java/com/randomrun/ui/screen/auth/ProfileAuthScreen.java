package com.randomrun.ui.screen.auth;

import com.randomrun.infrastructure.util.CompactLogger;
import com.randomrun.ui.screen.main.AbstractRandomRunScreen;
import com.randomrun.ui.widget.styled.ButtonMenu;
import com.randomrun.ui.widget.styled.TextFieldStyled;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

public class ProfileAuthScreen extends AbstractRandomRunScreen {
    private final Screen parent;
    private final String playerName;
    private final boolean profileExists;
    private final com.randomrun.ui.screen.main.MainModScreen mainModScreen;
    
    private TextFieldStyled passwordField;
    private TextFieldStyled confirmPasswordField;
    
    private String errorMessage = "";
    private int errorTicks = 0;
    
    private boolean isAuthenticating = false;
    
    private float fadeProgress = 0f;
    private long openTime;
    private static final float FADE_SPEED = 0.08f;
    
    private float titleScale = 0.8f;
    private float targetTitleScale = 1.0f;
    
    private static final int PANEL_WIDTH = 280;
    private static final int PANEL_HEIGHT = 200;
    private static final int SHADOW_LAYERS = 10;
    
    public ProfileAuthScreen(Screen parent, String playerName, boolean profileExists, com.randomrun.ui.screen.main.MainModScreen mainModScreen) {
        super(Component.literal("Profile Authentication"));
        this.parent = parent;
        this.playerName = playerName;
        this.profileExists = profileExists;
        this.mainModScreen = mainModScreen;
    }
    
    @Override
    protected void init() {
        super.init();
        
        this.openTime = System.currentTimeMillis();
        
        int centerX = width / 2;
        int startY = height / 2 - 40;
        
        passwordField = new TextFieldStyled(
            font,
            centerX - 100,
            startY,
            200,
            20,
            com.randomrun.infrastructure.util.LanguageManager.text("randomrun.auth.password"),
            0f
        );
        passwordField.setMaxLength(32);
        passwordField.setCenteredPlaceholder(com.randomrun.infrastructure.util.LanguageManager.text("randomrun.auth.password.placeholder"));
        addRenderableWidget(passwordField);
        
        if (!profileExists) {
            confirmPasswordField = new TextFieldStyled(
                font,
                centerX - 100,
                startY + 30,
                200,
                20,
                com.randomrun.infrastructure.util.LanguageManager.text("randomrun.auth.password.confirm"),
                0.05f
            );
            confirmPasswordField.setMaxLength(32);
            confirmPasswordField.setCenteredPlaceholder(com.randomrun.infrastructure.util.LanguageManager.text("randomrun.auth.password.confirm.placeholder"));
            addRenderableWidget(confirmPasswordField);
        }
        
        int buttonY = profileExists ? startY + 40 : startY + 70;
        com.randomrun.ui.widget.styled.ButtonRainbow loginButton = new com.randomrun.ui.widget.styled.ButtonRainbow(
            centerX - 100,
            buttonY,
            200,
            20,
            com.randomrun.infrastructure.util.LanguageManager.text(profileExists ? "randomrun.auth.button.login" : "randomrun.auth.button.create"),
            button -> authenticate(),
            0,
            0.1f
        );
        loginButton.setAnimationDirection(com.randomrun.ui.widget.styled.ButtonRainbow.AnimationDirection.HORIZONTAL_LEFT);
        loginButton.setTooltip(net.minecraft.client.gui.components.Tooltip.create(
            com.randomrun.infrastructure.util.LanguageManager.text(profileExists ? "randomrun.auth.button.login.tooltip" : "randomrun.auth.button.create.tooltip")
        ));
        addRenderableWidget(loginButton);
        
        com.randomrun.ui.widget.styled.ButtonMenu playOfflineButton = new com.randomrun.ui.widget.styled.ButtonMenu(
            centerX - 100,
            buttonY + 30,
            200,
            20,
            Component.literal("Играть оффлайн"),
            button -> playOffline(),
            1,
            0.18f
        );
        playOfflineButton.setTooltip(net.minecraft.client.gui.components.Tooltip.create(
            Component.literal("Играть в одиночном режиме (БЕЗ онлайна)")
        ));
        addRenderableWidget(playOfflineButton);
        
        ButtonMenu cancelButton = new ButtonMenu(
            centerX - 100,
            height - 30,
            200,
            20,
            com.randomrun.infrastructure.util.LanguageManager.text("randomrun.auth.button.cancel"),
            button -> onClose(),
            2,
            0.22f
        );
        cancelButton.setTooltip(net.minecraft.client.gui.components.Tooltip.create(
            com.randomrun.infrastructure.util.LanguageManager.text("randomrun.auth.button.cancel.tooltip")
        ));
        addRenderableWidget(cancelButton);
        
        addRenderableWidget(new com.randomrun.ui.widget.overlay.LanguageDropdownWidget(
            10, height - 30,
            this, 0.25f
        ));
    }
    
    private void authenticate() {
        if (isAuthenticating) return;
        
        String passwordInput = passwordField.getValue();
        
        if (passwordInput == null) {
            CompactLogger.authError("Password field returned null");
            showError("Ошибка: не удалось прочитать пароль");
            return;
        }
        
        final String password = passwordInput.trim();
        
        if (!validatePassword(password)) return;
        
        isAuthenticating = true;
        
        java.util.concurrent.CompletableFuture.runAsync(() -> {
            try {
                if (profileExists) {
                    boolean valid = checkPassword(password);
                    
                    Minecraft.getInstance().execute(() -> {
                        if (valid) {
                            loadProfile();
                        } else {
                            showError(com.randomrun.infrastructure.util.LanguageManager.text("randomrun.auth.error.wrong").getString());
                            isAuthenticating = false;
                        }
                    });
                } else {
                    String passwordHash = com.randomrun.infrastructure.util.PasswordHasher.hash(password);
                    boolean created = createProfile(passwordHash);
                    
                    Minecraft.getInstance().execute(() -> {
                        if (created) {
                            loadProfile();
                        } else {
                            showError("Не удалось создать профиль. Попробуйте еще раз.");
                            isAuthenticating = false;
                        }
                    });
                }
            } catch (Exception e) {
                Minecraft.getInstance().execute(() -> {
                    String errorMsg = com.randomrun.infrastructure.util.LanguageManager.text("randomrun.auth.error.generic", e.getMessage()).getString();
                    if (e.getMessage() != null && (e.getMessage().contains("timeout") || 
                        e.getMessage().contains("connect") || 
                        e.getMessage().contains("Connection") ||
                        e.getMessage().contains("UnknownHost"))) {
                        errorMsg += "\n" + com.randomrun.infrastructure.util.LanguageManager.text("randomrun.auth.error.vpn_hint").getString();
                    }
                    showError(errorMsg);
                    isAuthenticating = false;
                });
            }
        });
    }
    
    private boolean validatePassword(String password) {
        if (password == null || password.isEmpty()) {
            showError(com.randomrun.infrastructure.util.LanguageManager.text("randomrun.auth.error.empty").getString());
            return false;
        }
        
        if (password.length() < 4) {
            showError(com.randomrun.infrastructure.util.LanguageManager.text("randomrun.auth.error.short").getString());
            return false;
        }
        
        if (!profileExists) {
            if (confirmPasswordField == null) {
                CompactLogger.authError("Confirm field is null");
                showError("Ошибка: поле подтверждения не инициализировано");
                return false;
            }
            
            String confirmPassword = confirmPasswordField.getValue().trim();
            
            if (!password.equals(confirmPassword)) {
                showError(com.randomrun.infrastructure.util.LanguageManager.text("randomrun.auth.error.mismatch").getString());
                return false;
            }
        }
        
        return true;
    }
    
    private boolean checkPassword(String password) {
        try {
            com.google.gson.JsonObject profile = fetchProfile();
            if (profile == null) return false;
            
            String storedHash = extractPasswordHash(profile);
            if (storedHash == null) return false;
            
            return com.randomrun.infrastructure.util.PasswordHasher.verify(password, storedHash);
        } catch (Exception e) {
            CompactLogger.authError("Password check failed: {}", e.getMessage());
            return false;
        }
    }
    
    private com.google.gson.JsonObject fetchProfile() {
        com.google.gson.JsonElement result = com.randomrun.network.HttpClient.getInstance()
            .get("/profiles/" + playerName + "/profile").join();
        
        if (result == null || !result.isJsonObject()) {
            result = com.randomrun.network.HttpClient.getInstance()
                .get("/profiles/" + playerName).join();
        }
        
        return (result != null && result.isJsonObject()) ? result.getAsJsonObject() : null;
    }
    
    private String extractPasswordHash(com.google.gson.JsonObject profile) {
        if (profile.has("sec") && profile.getAsJsonObject("sec").has("pwd")) {
            return profile.getAsJsonObject("sec").get("pwd").getAsString();
        }
        return null;
    }
    
    private boolean createProfile(String passwordHash) {
        try {
            com.randomrun.core.profile.PlayerProfile profile = new com.randomrun.core.profile.PlayerProfile(playerName);
            profile.setPasswordHash(passwordHash);
            
            boolean success = com.randomrun.network.HttpClient.getInstance()
                .put("/profiles/" + playerName + "/profile", profile).join();
            
            if (!success) {
                CompactLogger.authError("Profile creation failed");
            }
            
            return success;
        } catch (Exception e) {
            CompactLogger.authError("Profile creation error: {}", e.getMessage());
            return false;
        }
    }
    
    private void loadProfile() {
        com.randomrun.core.profile.PlayerProfile.load(playerName).thenAccept(success -> {
            Minecraft.getInstance().execute(() -> {
                if (!success) {
                    String errorMsg = com.randomrun.infrastructure.util.LanguageManager.text("randomrun.auth.error.generic", "Timeout during profile load").getString();
                    errorMsg += "\n" + com.randomrun.infrastructure.util.LanguageManager.text("randomrun.auth.error.vpn_hint").getString();
                    showError(errorMsg);
                    isAuthenticating = false;
                    return;
                }
                com.randomrun.core.profile.PlayerProfile profile = com.randomrun.core.profile.PlayerProfile.get();
                
                if (profile != null && profile.getPasswordHash() != null) {
                    if (com.randomrun.infrastructure.util.PasswordHasher.isLegacyHash(profile.getPasswordHash())) {
                        profile.setNeedsPasswordChange(true);
                        com.randomrun.core.profile.PlayerProfile.save();
                        CompactLogger.authWarn("Legacy password detected");
                    }
                }
                
                com.randomrun.ui.screen.main.MainModScreen.onAuthenticationSuccess();
                minecraft.gui.setScreen(mainModScreen);
            });
        });
    }
    
    private void playOffline() {
        com.randomrun.infrastructure.RandomRunMod.getInstance().getConfig().setOnlineMode(false);
        com.randomrun.infrastructure.RandomRunMod.getInstance().saveConfig();
        com.randomrun.ui.screen.main.MainModScreen.onAuthenticationSuccess();
        
        Minecraft.getInstance().execute(() -> {
            minecraft.gui.setScreen(mainModScreen);
        });
    }
    
    private void showError(String message) {
        this.errorMessage = message;
        this.errorTicks = 60;
    }
    
    @Override
    public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        updateAnimations(delta);
        extractBackground(context, mouseX, mouseY, delta);
        renderTitle(context);
        renderPanel(context);
        renderWidgets(context, mouseX, mouseY, delta);
        renderStatusMessages(context);
    }
    
    private void renderTitle(GuiGraphicsExtractor context) {
        String title = com.randomrun.infrastructure.util.LanguageManager.text(profileExists ? "randomrun.auth.title.login" : "randomrun.auth.title.create").getString();
        com.randomrun.ui.util.GuiText.centeredText(context, font, title, width / 2, 20, 0xFFFFFFFF);
    }
    
    private void updateAnimations(float delta) {
        long elapsed = System.currentTimeMillis() - openTime;
        fadeProgress = Math.min(1.0f, elapsed / 1000.0f * (1.0f / FADE_SPEED));
        titleScale = Mth.lerp(delta * 0.1f, titleScale, targetTitleScale);
    }
    
    private void renderPanel(GuiGraphicsExtractor context) {
        int centerX = width / 2;
        int panelX = centerX - PANEL_WIDTH / 2;
        int panelY = height / 2 - PANEL_HEIGHT / 2;
        
        renderPanelShadow(context, panelX, panelY);
        renderPanelBackground(context, panelX, panelY);
        renderPanelBorders(context, panelX, panelY);
        renderPanelText(context, centerX, panelY);
    }
    
    private void renderPanelShadow(GuiGraphicsExtractor context, int panelX, int panelY) {
        for (int i = 0; i < SHADOW_LAYERS; i++) {
            int shadowAlpha = (int) ((SHADOW_LAYERS - i) * 2.5f * fadeProgress);
            context.fill(panelX - i, panelY - i, panelX + PANEL_WIDTH + i, panelY + PANEL_HEIGHT + i, (shadowAlpha << 24));
        }
    }
    
    private void renderPanelBackground(GuiGraphicsExtractor context, int panelX, int panelY) {
        int panelAlpha = (int) (0xD0 * fadeProgress);
        int panelColor = (panelAlpha << 24) | 0x150030;
        context.fill(panelX, panelY, panelX + PANEL_WIDTH, panelY + PANEL_HEIGHT, panelColor);
    }
    
    private void renderPanelBorders(GuiGraphicsExtractor context, int panelX, int panelY) {
        com.randomrun.ui.screen.main.MainModScreen.renderStaticBorder(context, panelX, panelY, panelX + PANEL_WIDTH, panelY + PANEL_HEIGHT, 2);
        com.randomrun.ui.screen.main.MainModScreen.renderAnimatedBorder(context, panelX, panelY, panelX + PANEL_WIDTH, panelY + PANEL_HEIGHT, 2);
    }
    
    private void renderPanelText(GuiGraphicsExtractor context, int centerX, int panelY) {
        context.pose().pushMatrix();
        context.pose().translate(centerX, panelY + 30);
        context.pose().scale(titleScale, titleScale);
        context.pose().translate(-centerX, -(panelY + 30));
        
        String subtitle = com.randomrun.infrastructure.util.LanguageManager.text("randomrun.auth.player", playerName).getString();
        com.randomrun.ui.util.GuiText.centeredText(context, font, subtitle, centerX, panelY + 30, 0xFFAAAAAA);
        context.pose().popMatrix();
        
        if (profileExists) {
            int panelBottom = panelY + PANEL_HEIGHT;
            String forgotLine1 = com.randomrun.infrastructure.util.LanguageManager.text("randomrun.auth.forgot_password_line1").getString();
            String forgotLine2 = com.randomrun.infrastructure.util.LanguageManager.text("randomrun.auth.forgot_password_line2").getString();
            
            com.randomrun.ui.util.GuiText.centeredText(context, font, forgotLine1, centerX, panelBottom + 15, 0xFF888888);
            com.randomrun.ui.util.GuiText.centeredText(context, font, forgotLine2, centerX, panelBottom + 26, 0xFF888888);
        }
    }
    
    private void renderWidgets(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        for (net.minecraft.client.gui.components.events.GuiEventListener element : this.children()) {
            if (element instanceof net.minecraft.client.gui.components.Renderable drawable) {
                drawable.extractRenderState(context, mouseX, mouseY, delta);
            }
        }
    }
    
    private void renderStatusMessages(GuiGraphicsExtractor context) {
        int centerX = width / 2;
        int panelY = height / 2 - PANEL_HEIGHT / 2;
        
        if (errorTicks > 0) {
            renderErrorMessage(context, centerX, panelY + PANEL_HEIGHT - 45);
        }
        
        if (isAuthenticating) {
            renderLoadingMessage(context, centerX, panelY + PANEL_HEIGHT - 25);
        }
    }
    
    private void renderErrorMessage(GuiGraphicsExtractor context, int centerX, int errorY) {
        errorTicks--;
        float errorAlpha = (float) Math.sin(errorTicks * 0.2f) * 0.3f + 0.7f;
        int errorColor = (int) (errorAlpha * 255) << 24 | 0xFF5555;
        com.randomrun.ui.util.GuiText.centeredText(context, font, errorMessage, centerX, errorY, errorColor);
    }
    
    private void renderLoadingMessage(GuiGraphicsExtractor context, int centerX, int loadingY) {
        String dots = ".".repeat((int) ((System.currentTimeMillis() / 300) % 4));
        String connectingText = com.randomrun.infrastructure.util.LanguageManager.text("randomrun.auth.connecting").getString() + dots;
        com.randomrun.ui.util.GuiText.centeredText(context, font, connectingText, centerX, loadingY, 0xFF55FF55);
    }
    
    @Override
    public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent event, boolean doubleClick) {
        double mouseX = event.x();
        double mouseY = event.y();
        int button = event.button();
        if (passwordField != null && passwordField.mouseClicked(new net.minecraft.client.input.MouseButtonEvent(mouseX, mouseY, event.buttonInfo()), doubleClick)) {
            setFocused(passwordField);
            return true;
        }
        
        if (confirmPasswordField != null && confirmPasswordField.mouseClicked(new net.minecraft.client.input.MouseButtonEvent(mouseX, mouseY, event.buttonInfo()), doubleClick)) {
            setFocused(confirmPasswordField);
            return true;
        }
        
        boolean handled = super.mouseClicked(new net.minecraft.client.input.MouseButtonEvent(mouseX, mouseY, event.buttonInfo()), doubleClick);
        
        if (!handled && button == com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_LEFT) {
            setFocused(null);
        }
        
        return handled;
    }
    
    @Override
    public void onClose() {
        if (!profileExists && !isAuthenticating) {
            com.randomrun.infrastructure.RandomRunMod.getInstance().getConfig().setOnlineMode(false);
            com.randomrun.infrastructure.RandomRunMod.getInstance().saveConfig();
        }
        minecraft.gui.setScreen(parent);
    }
    
    @Override
    public boolean shouldCloseOnEsc() {
        return true;
    }
    
    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
