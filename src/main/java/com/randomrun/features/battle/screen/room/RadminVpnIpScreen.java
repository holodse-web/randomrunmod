package com.randomrun.features.battle.screen.room;

import com.randomrun.features.battle.screen.room.RoomWaitingScreen;
import com.randomrun.features.battle.manager.BattleManager;
import com.randomrun.features.battle.manager.BattleRoom;
import com.randomrun.features.battle.screen.room.util.validation.InputValidator;
import com.randomrun.features.battle.screen.room.util.manager.RecentDataManager;
import com.randomrun.features.battle.screen.room.util.common.UIConstants;
import com.randomrun.infrastructure.RandomRunMod;
import com.randomrun.ui.screen.main.AbstractRandomRunScreen;
import com.randomrun.ui.widget.styled.ButtonDefault;
import com.randomrun.ui.widget.styled.TextFieldStyled;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;

public class RadminVpnIpScreen extends AbstractRandomRunScreen {
    private static final int BOX_WIDTH = 260;
    private static final int BOX_HEIGHT = 100;
    private static final int BOX_OFFSET_Y = 45;
    private static final int ERROR_OFFSET_Y = 40;
    
    private final Screen parent;
    private final String roomCode;
    private TextFieldStyled ipField;
    private String errorMessage;
    private ButtonDefault saveIpButton;
    
    private boolean showSuccessIndicator = false;
    private long successIndicatorTime = 0;
    private List<String> recentIps;
    private boolean showRecentDropdown = false;

    public RadminVpnIpScreen(Screen parent, String roomCode) {
        super(com.randomrun.infrastructure.util.LanguageManager.text("randomrun.radmin.ip_input.title"));
        this.parent = parent;
        this.roomCode = roomCode;
        this.recentIps = RecentDataManager.getIpInstance().getRecent();
    }

    @Override
    protected void init() {
        String previousIp = ipField == null ? null : ipField.getValue();
        super.init();
        int centerX = width / 2;
        int centerY = height / 2;

        ipField = new TextFieldStyled(font, centerX - 120, centerY - 35, 240, 20,
                com.randomrun.infrastructure.util.LanguageManager.text("randomrun.radmin.ip_input.placeholder"), 0.06f);
        ipField.setMaxLength(64);
        ipField.setCenteredPlaceholder(com.randomrun.infrastructure.util.LanguageManager.text("randomrun.radmin.enter_ip"));
        ipField.setResponder(text -> {
            errorMessage = null;
            showSuccessIndicator = false;
            updateSaveButton();
            updateRecentDropdown(text);
        });
        
        String savedIp = previousIp == null ? com.randomrun.infrastructure.util.RadminIpStorage.loadIp() : previousIp;
        if (savedIp != null && !savedIp.isEmpty()) {
            ipField.setValue(savedIp);
        }
        
        addRenderableWidget(ipField);

        saveIpButton = new ButtonDefault(
                centerX - 120, centerY - 5, 240, 20,
                com.randomrun.infrastructure.util.LanguageManager.text("randomrun.radmin.save_ip"),
                button -> saveIp(), 0, 0.1f
        );
        addRenderableWidget(saveIpButton);
        updateSaveButton();

        addRenderableWidget(new ButtonDefault(
                centerX - 120, centerY + 25, 240, 20,
                com.randomrun.infrastructure.util.LanguageManager.text("randomrun.battle.start_game"),
                button -> startGame(), 1, 0.15f
        ));

        addRenderableWidget(new ButtonDefault(
                centerX - 120, height - 30, 240, 20,
                com.randomrun.infrastructure.util.LanguageManager.text("randomrun.button.back"),
                button -> onClose(), 2, 0.2f
        ));
    }
    
    private void updateSaveButton() {
        if (saveIpButton == null) return;
        String savedIp = com.randomrun.infrastructure.util.RadminIpStorage.loadIp();
        boolean hasSaved = savedIp != null && !savedIp.isEmpty();
        String key = hasSaved ? "randomrun.radmin.update_ip" : "randomrun.radmin.save_ip";
        saveIpButton.setMessage(com.randomrun.infrastructure.util.LanguageManager.text(key));
        saveIpButton.active = true;
    }
    
    private void saveIp() {
        String ip = ipField.getValue().trim();
        if (!ip.isEmpty() && InputValidator.isValidIp(ip)) {
            com.randomrun.infrastructure.util.RadminIpStorage.saveIp(ip);
            RecentDataManager.getIpInstance().add(ip);
            
            showSuccessIndicator = true;
            successIndicatorTime = System.currentTimeMillis();
            errorMessage = null;
            
            updateSaveButton();
        }
    }
    
    private void updateRecentDropdown(String text) {
        if (text.isEmpty()) {
            showRecentDropdown = false;
            return;
        }
        
        String lowerText = text.toLowerCase();
        showRecentDropdown = recentIps.stream()
            .anyMatch(ip -> ip.toLowerCase().startsWith(lowerText));
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        extractBackground(context, mouseX, mouseY, delta);
        
        int centerX = width / 2;
        int centerY = height / 2;
        int boxX = centerX - BOX_WIDTH / 2;
        int boxY = centerY - BOX_OFFSET_Y;
        
        context.fill(boxX, boxY, boxX + BOX_WIDTH, boxY + BOX_HEIGHT, UIConstants.BG_SEMI_TRANSPARENT);
        context.outline(boxX, boxY, BOX_WIDTH, BOX_HEIGHT, UIConstants.BORDER_WHITE);
        
        super.extractRenderState(context, mouseX, mouseY, delta);

        if (showSuccessIndicator) {
            long elapsed = System.currentTimeMillis() - successIndicatorTime;
            if (elapsed < UIConstants.SUCCESS_INDICATOR_DURATION) {
                float alpha = 1.0f - (elapsed / (float) UIConstants.SUCCESS_INDICATOR_DURATION);
                int color = (int) (alpha * 255) << 24 | (UIConstants.TEXT_SUCCESS & 0x00FFFFFF);
                com.randomrun.ui.util.GuiText.centeredText(context, font, "✓ " + 
                    com.randomrun.infrastructure.util.LanguageManager.text("randomrun.radmin.saved").getString(), 
                    centerX, centerY - 50, color);
            } else {
                showSuccessIndicator = false;
            }
        }

        if (errorMessage != null) {
            com.randomrun.ui.util.GuiText.centeredText(context, font, errorMessage, 
                centerX, centerY + ERROR_OFFSET_Y, UIConstants.TEXT_ERROR);
        }
        
        if (showRecentDropdown && !recentIps.isEmpty()) {
            renderRecentDropdown(context, centerX, centerY, mouseX, mouseY);
        }
    }
    
    private void renderRecentDropdown(GuiGraphicsExtractor context, int centerX, int centerY, int mouseX, int mouseY) {
        String currentText = ipField.getValue().toLowerCase();
        List<String> matching = recentIps.stream()
            .filter(ip -> ip.toLowerCase().startsWith(currentText))
            .limit(3)
            .toList();
        
        if (matching.isEmpty()) return;
        
        int dropdownX = centerX - 120;
        int dropdownY = centerY - 35 + 22;
        int dropdownWidth = 240;
        int itemHeight = 18;
        int dropdownHeight = matching.size() * itemHeight;
        
        context.fill(dropdownX, dropdownY, dropdownX + dropdownWidth, 
                    dropdownY + dropdownHeight, UIConstants.BG_SEMI_TRANSPARENT);
        context.outline(dropdownX, dropdownY, dropdownWidth, dropdownHeight, UIConstants.BORDER_WHITE);
        
        for (int i = 0; i < matching.size(); i++) {
            int itemY = dropdownY + i * itemHeight;
            boolean hovered = mouseX >= dropdownX && mouseX < dropdownX + dropdownWidth &&
                             mouseY >= itemY && mouseY < itemY + itemHeight;
            
            if (hovered) {
                context.fill(dropdownX, itemY, dropdownX + dropdownWidth, 
                           itemY + itemHeight, UIConstants.BG_HOVERED);
            }
            
            com.randomrun.ui.util.GuiText.text(context, font, matching.get(i), dropdownX + 5, itemY + 5, 
                           UIConstants.TEXT_WHITE, false);
        }
    }

    private void startGame() {
        String ip = ipField.getValue().trim();
        
        if (!InputValidator.isValidIp(ip)) {
            errorMessage = com.randomrun.infrastructure.util.LanguageManager.text("randomrun.radmin.invalid_ip").getString();
            RandomRunMod.LOGGER.warn("[Radmin] Невалидный IP: {}", ip);
            return;
        }

        BattleManager.getInstance().setManualServerAddress(ip);

        BattleRoom room = BattleManager.getInstance().getCurrentRoom();
        if (room != null) {
            int playerCount = room.getPlayers() != null ? room.getPlayers().size() : 1;
            int maxPlayers = room.getMaxPlayers();
            if (playerCount >= maxPlayers) {
                BattleManager.getInstance().setStatusLoading();
            }
        }

        RecentDataManager.getIpInstance().add(ip);
        Minecraft.getInstance().gui.setScreen(parent instanceof RoomWaitingScreen ? parent
            : new RoomWaitingScreen(parent, roomCode, true, true));
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().gui.setScreen(parent);
    }
    
    @Override
    public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent event, boolean doubleClick) {
        double mouseX = event.x();
        double mouseY = event.y();
        int button = event.button();
        if (showRecentDropdown && !recentIps.isEmpty()) {
            String currentText = ipField.getValue().toLowerCase();
            List<String> matching = recentIps.stream()
                .filter(ip -> ip.toLowerCase().startsWith(currentText))
                .limit(3)
                .toList();
            
            if (!matching.isEmpty()) {
                int centerX = width / 2;
                int centerY = height / 2;
                int dropdownX = centerX - 120;
                int dropdownY = centerY - 35 + 22;
                int dropdownWidth = 240;
                int itemHeight = 18;
                
                for (int i = 0; i < matching.size(); i++) {
                    int itemY = dropdownY + i * itemHeight;
                    if (mouseX >= dropdownX && mouseX < dropdownX + dropdownWidth &&
                        mouseY >= itemY && mouseY < itemY + itemHeight) {
                        ipField.setValue(matching.get(i));
                        showRecentDropdown = false;
                        return true;
                    }
                }
            }
        }
        
        return super.mouseClicked(new net.minecraft.client.input.MouseButtonEvent(mouseX, mouseY, event.buttonInfo()), doubleClick);
    }
    
    @Override
    public boolean keyPressed(net.minecraft.client.input.KeyEvent event) {
        int keyCode = event.key();
        int scanCode = event.keycode();
        int modifiers = event.modifiers();
        if (keyCode == com.mojang.blaze3d.platform.InputConstants.KEY_ESCAPE && showRecentDropdown) {
            showRecentDropdown = false;
            return true;
        }
        
        return super.keyPressed(new net.minecraft.client.input.KeyEvent(keyCode, scanCode, modifiers));
    }
}
