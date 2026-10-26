package com.randomrun.features.battle.screen.room;

import com.randomrun.features.battle.screen.room.RoomWaitingScreen;
import com.randomrun.features.battle.manager.BattleManager;
import com.randomrun.features.battle.manager.RoomJoinResult;
import com.randomrun.features.battle.screen.room.util.common.CodeFormatter;
import com.randomrun.features.battle.screen.room.util.common.SoundHelper;
import com.randomrun.features.battle.screen.room.util.common.UIConstants;
import com.randomrun.features.battle.screen.room.util.manager.RecentDataManager;
import com.randomrun.infrastructure.RandomRunMod;
import com.randomrun.ui.widget.styled.ButtonDefault;
import com.randomrun.ui.screen.main.AbstractRandomRunScreen;
import java.util.List;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public class RoomJoinScreen extends AbstractRandomRunScreen {
    private static final int CODE_LENGTH = 5;
    
    private final Screen parent;
    private EditBox codeField;
    private EditBox passwordField;
    private String errorMessage = null;
    private boolean joinCancelled;
    private long startTime;
    private float fadeIn = 0f;
    
    private List<String> recentRooms;
    private boolean showRecentDropdown = false;
    private boolean joining;
    private ButtonDefault joinButton;
    
    public RoomJoinScreen(Screen parent) {
        super(com.randomrun.infrastructure.util.LanguageManager.text("randomrun.screen.join.title"));
        this.parent = parent;
        this.startTime = System.currentTimeMillis();
        this.recentRooms = RecentDataManager.getRoomInstance().getRecent();
    }
    
    @Override
    protected void init() {
        String previousCode = codeField == null ? "" : codeField.getValue();
        String previousPassword = passwordField == null ? "" : passwordField.getValue();
        super.init();
        
        int centerX = width / 2;
        int centerY = height / 2;
        
        codeField = new EditBox(font, centerX - 100, centerY - 30, 200, 20, 
            com.randomrun.infrastructure.util.LanguageManager.text("randomrun.screen.join.code_field_label"));
        codeField.setMaxLength(9);
        codeField.setBordered(false);
        codeField.setHint(Component.literal("\u00A771-2-3-4-5"));
        codeField.setResponder(text -> {
            errorMessage = null;
            String formatted = CodeFormatter.format(text);
            if (!formatted.equals(text)) {
                codeField.setValue(formatted);
            }
            updateRecentDropdown(CodeFormatter.unformat(text));
        });
        addRenderableWidget(codeField);
        setInitialFocus(codeField);
        
        passwordField = new EditBox(font, centerX - 100, centerY + 2, 200, 20, 
            com.randomrun.infrastructure.util.LanguageManager.text("randomrun.battle.password"));
        passwordField.setMaxLength(32);
        passwordField.setBordered(false);
        passwordField.setHint(Component.literal("\u00A771234"));
        addRenderableWidget(passwordField);
        
        joinButton = addRenderableWidget(new ButtonDefault(
            centerX - 100, height - 55, 200, 20,
            com.randomrun.infrastructure.util.LanguageManager.text("randomrun.battle.join_room"),
            button -> joinRoom(), 0, 0.1f
        ));
        
        addRenderableWidget(new ButtonDefault(
            centerX - 100, height - 30, 200, 20,
            com.randomrun.infrastructure.util.LanguageManager.text("randomrun.button.back"),
            button -> onClose(), 1, 0.15f
        ));
        codeField.setValue(previousCode);
        passwordField.setValue(previousPassword);
        joinButton.active = !joining;
    }
    
    private void updateRecentDropdown(String text) {
        if (text.isEmpty()) {
            showRecentDropdown = false;
            return;
        }
        
        showRecentDropdown = recentRooms.stream()
            .anyMatch(code -> code.startsWith(text));
    }
    
    private void joinRoom() {
        if (joining) return;
        String code = CodeFormatter.unformat(codeField.getValue().trim());
        String password = passwordField.getValue().trim();
        joinCancelled = false;
        
        if (code.length() != CODE_LENGTH) {
            errorMessage = com.randomrun.infrastructure.util.LanguageManager.text(
                "randomrun.screen.join.error.invalid_code").getString();
            SoundHelper.playError();
            return;
        }
        
        String playerName = Minecraft.getInstance().getUser().getName();
        errorMessage = com.randomrun.infrastructure.util.LanguageManager.text(
            "randomrun.screen.join.status.connecting").getString();
        
        joining = true;
        joinButton.active = false;
        
        BattleManager.getInstance().joinRoomDetailed(playerName, code, password).whenComplete((result, error) ->
            Minecraft.getInstance().execute(() -> {
            joining = false;
            joinButton.active = true;
            if (Minecraft.getInstance().gui.screen() != this) return;
            if (error != null) {
                RandomRunMod.LOGGER.error("[Room] Ошибка подключения: код={}", code, error);
                errorMessage = com.randomrun.infrastructure.util.LanguageManager.text(RoomJoinResult.CONNECTION_FAILED.messageKey()).getString();
                SoundHelper.playError();
            } else if (result != null && result.joined()) {
                RecentDataManager.getRoomInstance().add(code);
                SoundHelper.playJoin();
                    Minecraft.getInstance().gui.setScreen(new RoomWaitingScreen(parent, code, false));
            } else {
                RoomJoinResult reason = result == null ? RoomJoinResult.CONNECTION_FAILED : result;
                joinCancelled = reason == RoomJoinResult.CANCELLED;
                if (reason != RoomJoinResult.CANCELLED) {
                    RandomRunMod.LOGGER.warn("[Room] Не удалось войти: код={}, причина={}", code, reason);
                    SoundHelper.playError();
                }
                errorMessage = com.randomrun.infrastructure.util.LanguageManager.text(reason.messageKey()).getString();
            }
        }));
    }

    @Override
    public void onClose() {
        if (!joining) Minecraft.getInstance().gui.setScreen(parent);
    }
    
    @Override
    public void extractBackground(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        super.extractBackground(context, mouseX, mouseY, delta);
        
        int centerX = width / 2;
        int centerY = height / 2;
        
        int contentLeft = centerX - 120;
        int contentTop = centerY - 70;
        int contentRight = centerX + 120;
        int contentBottom = centerY + 50;
        
        context.fill(contentLeft, contentTop, contentRight, contentBottom, 0xCC1a0b2e);
        
        com.randomrun.ui.screen.main.MainModScreen.renderAnimatedBorder(context, 
            contentLeft, contentTop, contentRight, contentBottom, 2);
        
        context.fill(centerX - 100, contentTop + 25, centerX + 100, contentTop + 26, UIConstants.BORDER_WHITE);
    }
    
    @Override
    public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        fadeIn = Math.min(1.0f, fadeIn + delta * 2);
        
        renderTextField(context, codeField, mouseX, mouseY);
        renderTextField(context, passwordField, mouseX, mouseY);
        
        super.extractRenderState(context, mouseX, mouseY, delta);
        
        int centerX = width / 2;
        int centerY = height / 2;
        int contentTop = centerY - 70;
        
        float time = (System.currentTimeMillis() - startTime) / 1000.0f;
        float hue = (time * 0.5f) % 1.0f;
        int rainbowColor = java.awt.Color.HSBtoRGB(hue, 0.8f, 1.0f);
        
        String title = com.randomrun.infrastructure.util.LanguageManager.text("randomrun.screen.join.title").getString();
        com.randomrun.ui.util.GuiText.centeredText(context, font, Component.literal(title), 
            centerX, contentTop + 10, rainbowColor);
        
        com.randomrun.ui.util.GuiText.text(context, font,
            com.randomrun.infrastructure.util.LanguageManager.text("randomrun.screen.join.label.enter_code"),
            centerX - 100, centerY - 42, UIConstants.TEXT_GRAY);
        
        com.randomrun.ui.util.GuiText.text(context, font,
            com.randomrun.infrastructure.util.LanguageManager.text("randomrun.battle.password_optional"),
            centerX - 100, centerY - 12, UIConstants.TEXT_GRAY);
        
        if (errorMessage != null) {
            int errorColor = !joining && !joinCancelled ? UIConstants.TEXT_ERROR : UIConstants.TEXT_GRAY;
            com.randomrun.ui.util.GuiText.centeredText(context, font, Component.literal(errorMessage),
                centerX, centerY + 30, errorColor);
        }
        
        if (showRecentDropdown && !recentRooms.isEmpty()) {
            renderRecentDropdown(context, centerX, centerY, mouseX, mouseY);
        }
    }
    
    private void renderTextField(GuiGraphicsExtractor context, EditBox field, int mouseX, int mouseY) {
        if (field == null) return;
        
        int x = field.getX();
        int y = field.getY();
        int w = field.getWidth();
        int h = field.getHeight();
        
        boolean hovered = field.isMouseOver(mouseX, mouseY) || field.isFocused();
        
        int bgColor = hovered ? UIConstants.BG_HOVERED : UIConstants.BG_NORMAL;
        context.fill(x, y, x + w, y + h, (0xE0 << 24) | (bgColor & 0x00FFFFFF));
        
        int borderColor = hovered ? UIConstants.BORDER_PURPLE_LIGHT : UIConstants.BORDER_PURPLE;
        context.fill(x - 1, y - 1, x + w + 1, y, borderColor);
        context.fill(x - 1, y + h, x + w + 1, y + h + 1, borderColor);
        context.fill(x - 1, y, x, y + h, borderColor);
        context.fill(x + w, y, x + w + 1, y + h, borderColor);
        
        field.extractRenderState(context, mouseX, mouseY, 0);
    }
    
    private void renderRecentDropdown(GuiGraphicsExtractor context, int centerX, int centerY, int mouseX, int mouseY) {
        String currentText = CodeFormatter.unformat(codeField.getValue());
        List<String> matching = recentRooms.stream()
            .filter(code -> code.startsWith(currentText))
            .limit(3)
            .toList();
        
        if (matching.isEmpty()) return;
        
        int dropdownX = centerX - 100;
        int dropdownY = centerY - 30 + 22;
        int dropdownWidth = 200;
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
            
            String formatted = CodeFormatter.format(matching.get(i));
            com.randomrun.ui.util.GuiText.text(context, font, formatted, dropdownX + 5, itemY + 5, 
                           UIConstants.TEXT_WHITE, false);
        }
    }
    
    @Override
    public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent event, boolean doubleClick) {
        double mouseX = event.x();
        double mouseY = event.y();
        int button = event.button();
        if (showRecentDropdown && !recentRooms.isEmpty()) {
            String currentText = CodeFormatter.unformat(codeField.getValue());
            List<String> matching = recentRooms.stream()
                .filter(code -> code.startsWith(currentText))
                .limit(3)
                .toList();
            
            if (!matching.isEmpty()) {
                int centerX = width / 2;
                int centerY = height / 2;
                int dropdownX = centerX - 100;
                int dropdownY = centerY - 30 + 22;
                int dropdownWidth = 200;
                int itemHeight = 18;
                
                for (int i = 0; i < matching.size(); i++) {
                    int itemY = dropdownY + i * itemHeight;
                    if (mouseX >= dropdownX && mouseX < dropdownX + dropdownWidth &&
                        mouseY >= itemY && mouseY < itemY + itemHeight) {
                        codeField.setValue(CodeFormatter.format(matching.get(i)));
                        showRecentDropdown = false;
                        SoundHelper.playClick();
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
        if (keyCode == com.mojang.blaze3d.platform.InputConstants.KEY_RETURN || keyCode == com.mojang.blaze3d.platform.InputConstants.KEY_NUMPADENTER) {
            joinRoom();
            return true;
        }
        
        if (keyCode == com.mojang.blaze3d.platform.InputConstants.KEY_ESCAPE && showRecentDropdown) {
            showRecentDropdown = false;
            return true;
        }
        
        if (keyCode == com.mojang.blaze3d.platform.InputConstants.KEY_TAB) {
            if (codeField.isFocused()) {
                setFocused(passwordField);
            } else {
                setFocused(codeField);
            }
            return true;
        }
        
        return super.keyPressed(new net.minecraft.client.input.KeyEvent(keyCode, scanCode, modifiers));
    }
}
