package com.randomrun.ui.screen.auth;

import com.randomrun.infrastructure.util.LanguageManager;
import com.randomrun.ui.screen.main.AbstractRandomRunScreen;
import com.randomrun.ui.widget.styled.ButtonMenu;
import com.randomrun.ui.widget.styled.ButtonRainbow;
import com.randomrun.ui.widget.styled.TextFieldStyled;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.util.Mth;

public class ChangePasswordScreen extends AbstractRandomRunScreen {
    private final Screen parent;
    private final String playerName;
    
    private TextFieldStyled currentPasswordField;
    private TextFieldStyled newPasswordField;
    private TextFieldStyled confirmPasswordField;
    
    private String errorMessage = "";
    private int errorTicks = 0;
    
    private boolean isChanging = false;
    
    private float fadeProgress = 0f;
    private long openTime;
    private static final float FADE_SPEED = 0.08f;
    
    private float titleScale = 0.8f;
    private float targetTitleScale = 1.0f;
    
    private static final int PANEL_WIDTH = 300;
    private static final int PANEL_HEIGHT = 220;
    private static final int SHADOW_LAYERS = 10;
    
    public ChangePasswordScreen(Screen parent, String playerName) {
        super(LanguageManager.text("randomrun.changepassword.title"));
        this.parent = parent;
        this.playerName = playerName;
    }
    
    @Override
    protected void init() {
        super.init();
        
        this.openTime = System.currentTimeMillis();
        
        int centerX = width / 2;
        int startY = height / 2 - 50;
        
        currentPasswordField = new TextFieldStyled(
            font,
            centerX - 120,
            startY,
            240,
            20,
            com.randomrun.infrastructure.util.LanguageManager.text("randomrun.changepassword.current"),
            0f
        );
        currentPasswordField.setMaxLength(32);
        currentPasswordField.setCenteredPlaceholder(com.randomrun.infrastructure.util.LanguageManager.text("randomrun.changepassword.current.placeholder"));
        addRenderableWidget(currentPasswordField);
        
        newPasswordField = new TextFieldStyled(
            font,
            centerX - 120,
            startY + 30,
            240,
            20,
            com.randomrun.infrastructure.util.LanguageManager.text("randomrun.changepassword.new"),
            0.05f
        );
        newPasswordField.setMaxLength(32);
        newPasswordField.setCenteredPlaceholder(com.randomrun.infrastructure.util.LanguageManager.text("randomrun.changepassword.new.placeholder"));
        addRenderableWidget(newPasswordField);
        
        confirmPasswordField = new TextFieldStyled(
            font,
            centerX - 120,
            startY + 60,
            240,
            20,
            com.randomrun.infrastructure.util.LanguageManager.text("randomrun.changepassword.confirm"),
            0.1f
        );
        confirmPasswordField.setMaxLength(32);
        confirmPasswordField.setCenteredPlaceholder(com.randomrun.infrastructure.util.LanguageManager.text("randomrun.changepassword.confirm.placeholder"));
        addRenderableWidget(confirmPasswordField);
        
        ButtonRainbow changeButton = new ButtonRainbow(
            centerX - 120,
            startY + 95,
            240,
            20,
            com.randomrun.infrastructure.util.LanguageManager.text("randomrun.changepassword.button.change"),
            button -> changePassword(),
            0,
            0.15f
        );
        changeButton.setAnimationDirection(ButtonRainbow.AnimationDirection.HORIZONTAL_LEFT);
        addRenderableWidget(changeButton);
        
        ButtonMenu cancelButton = new ButtonMenu(
            centerX - 100,
            height - 30,
            200,
            20,
            com.randomrun.infrastructure.util.LanguageManager.text("randomrun.button.back"),
            button -> onClose(),
            1,
            0.2f
        );
        addRenderableWidget(cancelButton);
        
        addRenderableWidget(new com.randomrun.ui.widget.overlay.LanguageDropdownWidget(
            10, height - 30,
            this, 0.25f
        ));
    }
    
    private void changePassword() {
        if (isChanging) return;
        
        String currentPassword = currentPasswordField.getValue().trim();
        String newPassword = newPasswordField.getValue().trim();
        String confirmPassword = confirmPasswordField.getValue().trim();
        
        if (!validatePasswords(currentPassword, newPassword, confirmPassword)) return;
        
        isChanging = true;
        
        java.util.concurrent.CompletableFuture.runAsync(() -> {
            try {
                boolean currentValid = checkCurrentPassword(currentPassword);
                
                if (!currentValid) {
                    Minecraft.getInstance().execute(() -> {
                        showError(com.randomrun.infrastructure.util.LanguageManager.text("randomrun.changepassword.error.wrong").getString());
                        isChanging = false;
                    });
                    return;
                }
                
                String newPasswordHash = com.randomrun.infrastructure.util.PasswordHasher.hash(newPassword);
                boolean changed = updatePassword(newPasswordHash);
                
                Minecraft.getInstance().execute(() -> {
                    if (changed) {
                        com.randomrun.core.profile.PlayerProfile profile = com.randomrun.core.profile.PlayerProfile.get();
                        if (profile != null) {
                            profile.setPasswordHash(newPasswordHash);
                            profile.setNeedsPasswordChange(false);
                            com.randomrun.core.profile.PlayerProfile.save();
                        }
                        
                        Minecraft client = Minecraft.getInstance();
                        client.gui.setScreen(new com.randomrun.ui.screen.main.MainModScreen(null));
                    } else {
                        showError("Не удалось изменить пароль. Попробуйте еще раз.");
                        isChanging = false;
                    }
                });
            } catch (Exception e) {
                Minecraft.getInstance().execute(() -> {
                    showError(com.randomrun.infrastructure.util.LanguageManager.text("randomrun.auth.error.generic", e.getMessage()).getString());
                    isChanging = false;
                });
            }
        });
    }
    
    private boolean validatePasswords(String currentPassword, String newPassword, String confirmPassword) {
        if (currentPassword == null || currentPassword.isEmpty()) {
            showError(com.randomrun.infrastructure.util.LanguageManager.text("randomrun.changepassword.error.current_empty").getString());
            return false;
        }
        
        if (newPassword == null || newPassword.isEmpty()) {
            showError(com.randomrun.infrastructure.util.LanguageManager.text("randomrun.changepassword.error.new_empty").getString());
            return false;
        }
        
        if (newPassword.length() < 4) {
            showError(com.randomrun.infrastructure.util.LanguageManager.text("randomrun.auth.error.short").getString());
            return false;
        }
        
        if (!newPassword.equals(confirmPassword)) {
            showError(com.randomrun.infrastructure.util.LanguageManager.text("randomrun.auth.error.mismatch").getString());
            return false;
        }
        
        if (currentPassword.equals(newPassword)) {
            showError(com.randomrun.infrastructure.util.LanguageManager.text("randomrun.changepassword.error.same").getString());
            return false;
        }
        
        return true;
    }
    
    private boolean checkCurrentPassword(String password) {
        try {
            com.randomrun.core.profile.PlayerProfile profile = com.randomrun.core.profile.PlayerProfile.get();
            if (profile == null) return false;
            
            String storedHash = profile.getPasswordHash();
            if (storedHash == null) return false;
            
            return com.randomrun.infrastructure.util.PasswordHasher.verify(password, storedHash);
        } catch (Exception e) {
            com.randomrun.infrastructure.RandomRunMod.LOGGER.error("[ChangePassword] Ошибка проверки пароля: {}", e.getMessage());
            return false;
        }
    }
    
    private boolean updatePassword(String newPasswordHash) {
        try {
            com.randomrun.core.profile.PlayerProfile profile = com.randomrun.core.profile.PlayerProfile.get();
            if (profile == null) return false;
            
            profile.setPasswordHash(newPasswordHash);
            
            boolean success = com.randomrun.network.HttpClient.getInstance()
                .put("/profiles/" + playerName + "/profile", profile).join();
            
            if (!success) {
                com.randomrun.infrastructure.RandomRunMod.LOGGER.error("[ChangePassword] Не удалось изменить пароль");
            }
            
            return success;
        } catch (Exception e) {
            com.randomrun.infrastructure.RandomRunMod.LOGGER.error("[ChangePassword] Ошибка изменения пароля: {}", e.getMessage());
            return false;
        }
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
        String title = com.randomrun.infrastructure.util.LanguageManager.text("randomrun.changepassword.title").getString();
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
        String subtitle = com.randomrun.infrastructure.util.LanguageManager.text("randomrun.changepassword.player", playerName).getString();
        
        context.pose().pushMatrix();
        context.pose().translate(centerX, panelY + 30);
        context.pose().scale(1.2f, 1.2f);
        context.pose().translate(-centerX, -(panelY + 30));
        com.randomrun.ui.util.GuiText.centeredText(context, font, subtitle, centerX, panelY + 30, 0xFFAAAAAA);
        context.pose().popMatrix();
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
            renderErrorMessage(context, centerX, panelY + PANEL_HEIGHT - 30);
        }
        
        if (isChanging) {
            renderLoadingMessage(context, centerX, panelY + PANEL_HEIGHT - 15);
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
        String changingText = com.randomrun.infrastructure.util.LanguageManager.text("randomrun.changepassword.changing").getString() + dots;
        com.randomrun.ui.util.GuiText.centeredText(context, font, changingText, centerX, loadingY, 0xFF55FF55);
    }
    
    @Override
    public void onClose() {
        minecraft.gui.setScreen(parent);
    }
    
    @Override
    public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent event, boolean doubleClick) {
        double mouseX = event.x();
        double mouseY = event.y();
        int button = event.button();
        if (currentPasswordField != null && currentPasswordField.mouseClicked(new net.minecraft.client.input.MouseButtonEvent(mouseX, mouseY, event.buttonInfo()), doubleClick)) {
            setFocused(currentPasswordField);
            return true;
        }
        
        if (newPasswordField != null && newPasswordField.mouseClicked(new net.minecraft.client.input.MouseButtonEvent(mouseX, mouseY, event.buttonInfo()), doubleClick)) {
            setFocused(newPasswordField);
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
    public boolean shouldCloseOnEsc() {
        return true;
    }
    
    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
