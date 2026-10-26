package com.randomrun.ui.widget.styled;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

public class ButtonDefault extends AbstractStyledButton {
    private static final int COLOR_BASE = 0x302b63;
    private static final int COLOR_HOVER = 0x6930c3;
    private static final int COLOR_BORDER = 0x6930c3;
    private static final int COLOR_CURSOR = 0xFFFFFF;
    private static final int COLOR_OVERLAY = 0xd042ff;
    
    private final float delay;
    private final long creationTime;
    private final boolean skipAnimation;
    
    private int baseColor = COLOR_BASE;
    private int hoverColor = COLOR_HOVER;
    
    public ButtonDefault(int x, int y, int width, int height, Component message, OnPress onPress, 
                        int index, float delay, boolean skipAnimation) {
        super(x, y, width, height, message, onPress);
        this.delay = delay;
        this.skipAnimation = skipAnimation;
        this.creationTime = skipAnimation ? 0 : System.currentTimeMillis();
    }
    
    public ButtonDefault(int x, int y, int width, int height, Component message, OnPress onPress, 
                        int index, float delay) {
        this(x, y, width, height, message, onPress, index, delay, false);
    }
    
    public ButtonDefault(int x, int y, int width, int height, Component message, OnPress onPress) {
        this(x, y, width, height, message, onPress, 0, 0f);
    }
    
    public ButtonDefault setColors(int baseColor, int hoverColor) {
        this.baseColor = baseColor;
        this.hoverColor = hoverColor;
        return this;
    }

    @Override
    public void extractContents(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        float alpha = calculateAppearanceProgress();
        if (alpha <= 0) return;
        
        int animatedY = getY() + (int) ((1f - alpha) * SLIDE_DISTANCE_VERTICAL);
        
        updateAnimations(delta);
        
        boolean hovered = isHovered();
        if (hovered && !wasHovered) {
            playHoverSound();
        }
        wasHovered = hovered;
        
        int bgColor = lerpColor(baseColor, hoverColor, hoverProgress);
        extractBackground(context, getX(), animatedY, bgColor, alpha);
        renderBorderWithCursorEffect(context, getX(), animatedY, getX() + width, animatedY + height, 
                                     COLOR_BORDER, COLOR_CURSOR, alpha);
        renderText(context, getX(), animatedY, alpha);
        renderHoverOverlay(context, getX(), animatedY, COLOR_OVERLAY, alpha);
    }
    
    private float calculateAppearanceProgress() {
        if (skipAnimation) return 1f;
        
        long elapsed = System.currentTimeMillis() - creationTime;
        float delayMs = delay * 1000;
        
        if (elapsed < delayMs) return 0f;
        
        float animElapsed = elapsed - delayMs;
        float progress = Math.min(1f, animElapsed / ANIMATION_DURATION);
        return 1f - (float) Math.pow(1 - progress, 3);
    }
}
