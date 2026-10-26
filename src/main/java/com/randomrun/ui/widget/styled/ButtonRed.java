package com.randomrun.ui.widget.styled;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

public class ButtonRed extends AbstractStyledButton {
    private static final int COLOR_BASE = 0x8c1414;
    private static final int COLOR_HOVER = 0xc93030;
    private static final int COLOR_BORDER = 0xFF5555;
    private static final int COLOR_CURSOR = 0xFFFFFF;
    private static final int COLOR_OVERLAY = 0xFFFFFF;
    
    public ButtonRed(int x, int y, int width, int height, Component message, OnPress onPress) {
        super(x, y, width, height, message, onPress);
    }
    
    @Override
    public void extractContents(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        updateAnimations(delta);
        
        boolean hovered = isHovered();
        if (hovered && !wasHovered) {
            playHoverSound();
        }
        wasHovered = hovered;
        
        int bgColor = lerpColor(COLOR_BASE, COLOR_HOVER, hoverProgress);
        extractBackground(context, getX(), getY(), bgColor, 1f);
        renderBorderWithCursorEffect(context, getX(), getY(), getX() + width, getY() + height, 
                                     COLOR_BORDER, COLOR_CURSOR, 1f);
        renderText(context, getX(), getY(), 1f);
        renderHoverOverlay(context, getX(), getY(), COLOR_OVERLAY, 1f);
    }
}
