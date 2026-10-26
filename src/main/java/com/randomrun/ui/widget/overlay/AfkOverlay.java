package com.randomrun.ui.widget.overlay;

import com.randomrun.infrastructure.util.LanguageManager;
import com.randomrun.ui.widget.styled.ButtonRed;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public class AfkOverlay {
    private static final int PANEL_WIDTH = 400;
    private static final int PANEL_HEIGHT = 180;
    private static final int BUTTON_WIDTH = 200;
    private static final int BUTTON_HEIGHT = 20;
    
    private static final int COLOR_PANEL_BG = 0xF0200000;
    private static final int COLOR_PANEL_BORDER = 0xFFFF0000;
    private static final int COLOR_TEXT = 0xFFFFFF;
    
    private float fadeProgress = 0f;
    private ButtonRed returnButton;
    
    public AfkOverlay(Screen parentScreen) {
    }
    
    public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        Minecraft client = Minecraft.getInstance();
        int screenWidth = client.getWindow().getGuiScaledWidth();
        int screenHeight = client.getWindow().getGuiScaledHeight();
        
        fadeProgress = Math.min(1f, fadeProgress + delta * 0.05f);
        
        int blurAlpha = (int) (224 * fadeProgress);
        context.fill(0, 0, screenWidth, screenHeight, (blurAlpha << 24));
        
        int panelX = (screenWidth - PANEL_WIDTH) / 2;
        int panelY = (screenHeight - PANEL_HEIGHT) / 2;
        
        int panelAlpha = (int) (240 * fadeProgress);
        int panelColor = (panelAlpha << 24) | (COLOR_PANEL_BG & 0x00FFFFFF);
        context.fill(panelX, panelY, panelX + PANEL_WIDTH, panelY + PANEL_HEIGHT, panelColor);
        
        int borderAlpha = (int) (255 * fadeProgress);
        int borderColor = (borderAlpha << 24) | (COLOR_PANEL_BORDER & 0x00FFFFFF);
        context.outline(panelX, panelY, PANEL_WIDTH, PANEL_HEIGHT, borderColor);
        
        int titleY = panelY + 40;
        Component titleText = LanguageManager.text("randomrun.afk.title");
        com.randomrun.ui.util.GuiText.centeredText(context, client.font, titleText, 
            screenWidth / 2, titleY, COLOR_TEXT);
        
        int messageY = panelY + 70;
        Component messageText = LanguageManager.text("randomrun.afk.message");
        com.randomrun.ui.util.GuiText.centeredText(context, client.font, messageText, 
            screenWidth / 2, messageY, COLOR_TEXT);
        
        int buttonX = (screenWidth - BUTTON_WIDTH) / 2;
        int buttonY = panelY + PANEL_HEIGHT - BUTTON_HEIGHT - 30;
        
        if (returnButton == null) {
            returnButton = new ButtonRed(
                buttonX, buttonY, BUTTON_WIDTH, BUTTON_HEIGHT,
                LanguageManager.text("randomrun.afk.button"),
                btn -> {}
            );
        }
        
        returnButton.setX(buttonX);
        returnButton.setY(buttonY);
        returnButton.extractRenderState(context, mouseX, mouseY, delta);
    }
    
    public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent event, boolean doubleClick) {
        double mouseX = event.x();
        double mouseY = event.y();
        int button = event.button();
        if (button != com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_LEFT) return false;
        
        if (returnButton != null && returnButton.isMouseOver(mouseX, mouseY)) {
            return true;
        }
        
        return false;
    }
}
