package com.randomrun.ui.widget.styled;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

public class ButtonMenu extends AbstractStyledButton {
    private static final int COLOR_BASE = 0x302b63;
    private static final int COLOR_HOVER = 0x6930c3;
    private static final int COLOR_BORDER = 0x6930c3;
    private static final int COLOR_CURSOR = 0xFFFFFF;
    private static final int COLOR_OVERLAY = 0xd042ff;
    
    private final float delay;
    private final long creationTime;
    private float animationProgress = 0f;
    
    public ButtonMenu(int x, int y, int width, int height, Component message, OnPress onPress, 
                     int index, float delay) {
        super(x, y, width, height, message, onPress);
        this.delay = delay;
        this.creationTime = System.currentTimeMillis();
    }
    
    public ButtonMenu(int x, int y, int width, int height, Component message, OnPress onPress) {
        this(x, y, width, height, message, onPress, 0, 0f);
    }
    
    @Override
    public void extractContents(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        float alpha = calculateAppearanceProgress();
        if (alpha <= 0) return;
        
        int animatedX = getX() - (int) ((1f - alpha) * SLIDE_DISTANCE);
        
        updateAnimations(delta);
        
        boolean hovered = isHovered();
        if (hovered && !wasHovered && animationProgress >= 1f) {
            playHoverSound();
        }
        wasHovered = hovered;
        
        int bgColor = lerpColor(COLOR_BASE, COLOR_HOVER, hoverProgress);
        extractBackground(context, animatedX, getY(), bgColor, alpha);
        renderBorderWithCursorEffect(context, animatedX, getY(), animatedX + width, getY() + height, 
                                     COLOR_BORDER, COLOR_CURSOR, alpha);
        renderText(context, animatedX, getY(), alpha);
        renderHoverOverlay(context, animatedX, getY(), COLOR_OVERLAY, alpha);
    }
    
    private float calculateAppearanceProgress() {
        long elapsed = System.currentTimeMillis() - creationTime;
        float delayMs = delay * 1000;
        
        if (elapsed < delayMs) {
            animationProgress = 0f;
            return 0f;
        }
        
        float animElapsed = elapsed - delayMs;
        animationProgress = Math.min(1f, animElapsed / ANIMATION_DURATION);
        return 1f - (float) Math.pow(1 - animationProgress, 3);
    }
}
