package com.randomrun.ui.widget.overlay;

import com.randomrun.infrastructure.util.LanguageManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

public class LanguageDropdownWidget extends Button {
    private static final int BUTTON_SIZE = 20;
    private static final int SLIDE_DISTANCE = 50;
    private static final int ANIMATION_DURATION = 300;
    private static final int BORDER_THICKNESS = 2;
    private static final int CURSOR_RADIUS = 30;
    
    private static final float HOVER_SPEED = 0.15f;
    private static final float BORDER_FADE_SPEED = 0.1f;
    private static final float BORDER_ANIMATION_SPEED = 0.02f;
    
    private static final int COLOR_BASE = 0x302b63;
    private static final int COLOR_HOVER = 0x6930c3;
    private static final int COLOR_BORDER = 0x6930c3;
    private static final int COLOR_CURSOR = 0xFFFFFF;
    
    private static final int ALPHA_BACKGROUND = 224;
    private static final int ALPHA_HOVER_OVERLAY = 40;
    
    private float hoverProgress = 0f;
    private float borderAnimation = 0f;
    private float borderAnimationAlpha = 0f;
    
    private final float delay;
    private final long creationTime;
    
    public LanguageDropdownWidget(int x, int y, Screen parentScreen, float delay) {
        super(x, y, BUTTON_SIZE, BUTTON_SIZE, Component.literal(LanguageManager.getCurrentLanguageCode().toUpperCase()), 
            button -> {}, DEFAULT_NARRATION);
        this.delay = delay;
        this.creationTime = System.currentTimeMillis();
    }
    
    public LanguageDropdownWidget(int x, int y, Screen parentScreen) {
        this(x, y, parentScreen, 0f);
    }
    
    @Override
    public void extractContents(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        float alpha = calculateAnimationProgress();
        if (alpha <= 0) return;
        
        int animatedX = calculateAnimatedX();

        updateAnimations(delta);
        renderButton(context, animatedX, mouseX, mouseY, alpha);
    }
    
    private void updateAnimations(float delta) {
        boolean hovered = this.isHovered();
        float targetHover = hovered ? 1f : 0f;
        hoverProgress = Mth.lerp(HOVER_SPEED, hoverProgress, targetHover);
        
        float targetBorderAlpha = hovered ? 1f : 0f;
        borderAnimationAlpha = Mth.lerp(BORDER_FADE_SPEED, borderAnimationAlpha, targetBorderAlpha);
        
        if (hovered) {
            borderAnimation += delta * BORDER_ANIMATION_SPEED;
            if (borderAnimation > 1.0f) borderAnimation -= 1.0f;
        }
    }
    
    private void renderButton(GuiGraphicsExtractor context, int x, int mouseX, int mouseY, float alpha) {
        int bgColor = lerpColor(COLOR_BASE, COLOR_HOVER, hoverProgress);
        int bgAlpha = (int) (alpha * ALPHA_BACKGROUND);
        int finalBgColor = (bgAlpha << 24) | (bgColor & 0x00FFFFFF);
        context.fill(x, getY(), x + BUTTON_SIZE, getY() + BUTTON_SIZE, finalBgColor);
        
        renderBorderWithCursorEffect(context, x, getY(), x + BUTTON_SIZE, getY() + BUTTON_SIZE, 
                                     COLOR_BORDER, COLOR_CURSOR, alpha, mouseX, mouseY);
        
        if (hoverProgress > 0.01f) {
            int whiteAlpha = (int) (hoverProgress * ALPHA_HOVER_OVERLAY * alpha);
            int whiteOverlay = (whiteAlpha << 24) | 0xFFFFFF;
            context.fill(x, getY(), x + BUTTON_SIZE, getY() + BUTTON_SIZE, whiteOverlay);
        }
        
        int textAlpha = (int) (alpha * 255);
        int textColor = (textAlpha << 24) | 0xFFFFFF;
        
        com.randomrun.ui.util.GuiText.centeredText(context, 
            Minecraft.getInstance().font,
            getMessage(),
            x + BUTTON_SIZE / 2,
            getY() + (BUTTON_SIZE - 8) / 2,
            textColor
        );
    }
    
    private void renderBorderWithCursorEffect(GuiGraphicsExtractor context, int left, int top, int right, int bottom, 
                                              int baseColor, int cursorColor, float alpha, int mouseX, int mouseY) {
        renderBorderSegment(context, left, top, right, top + BORDER_THICKNESS, 
                           mouseX, mouseY, baseColor, cursorColor, true, alpha);
        renderBorderSegment(context, left, bottom - BORDER_THICKNESS, right, bottom, 
                           mouseX, mouseY, baseColor, cursorColor, true, alpha);
        renderBorderSegment(context, left, top, left + BORDER_THICKNESS, bottom, 
                           mouseX, mouseY, baseColor, cursorColor, false, alpha);
        renderBorderSegment(context, right - BORDER_THICKNESS, top, right, bottom, 
                           mouseX, mouseY, baseColor, cursorColor, false, alpha);
    }
    
    private void renderBorderSegment(GuiGraphicsExtractor context, int x1, int y1, int x2, int y2, 
                                     int mouseX, int mouseY, int baseColor, int cursorColor, 
                                     boolean horizontal, float alpha) {
        if (horizontal) {
            for (int px = x1; px < x2; px++) {
                int centerY = (y1 + y2) / 2;
                float distance = (float) Math.sqrt(Math.pow(px - mouseX, 2) + Math.pow(centerY - mouseY, 2));
                int color = blendColorsByDistance(baseColor, cursorColor, distance);
                color = applyAlphaToColor(color, (int)(alpha * 255));
                context.fill(px, y1, px + 1, y2, color);
            }
        } else {
            for (int py = y1; py < y2; py++) {
                int centerX = (x1 + x2) / 2;
                float distance = (float) Math.sqrt(Math.pow(centerX - mouseX, 2) + Math.pow(py - mouseY, 2));
                int color = blendColorsByDistance(baseColor, cursorColor, distance);
                color = applyAlphaToColor(color, (int)(alpha * 255));
                context.fill(x1, py, x2, py + 1, color);
            }
        }
    }
    
    private int blendColorsByDistance(int color1, int color2, float distance) {
        if (distance > CURSOR_RADIUS) return color1;
        
        float blend = Mth.clamp(distance / CURSOR_RADIUS, 0.0f, 1.0f);
        blend = blend * blend;
        
        int r1 = (color1 >> 16) & 0xFF;
        int g1 = (color1 >> 8) & 0xFF;
        int b1 = color1 & 0xFF;
        
        int r2 = (color2 >> 16) & 0xFF;
        int g2 = (color2 >> 8) & 0xFF;
        int b2 = color2 & 0xFF;
        
        int r = (int) (r1 * blend + r2 * (1 - blend));
        int g = (int) (g1 * blend + g2 * (1 - blend));
        int b = (int) (b1 * blend + b2 * (1 - blend));
        
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }
    
    private int applyAlphaToColor(int color, int alpha) {
        int r = (color >> 16) & 0xFF;
        int g = (color >> 8) & 0xFF;
        int b = color & 0xFF;
        return (alpha << 24) | (r << 16) | (g << 8) | b;
    }
    
    private float calculateAnimationProgress() {
        long elapsed = System.currentTimeMillis() - creationTime;
        float delayMs = delay * 1000;
        
        if (elapsed < delayMs) return 0f;
        
        float animElapsed = elapsed - delayMs;
        float progress = Math.min(1f, animElapsed / ANIMATION_DURATION);
        return 1f - (float) Math.pow(1 - progress, 3);
    }
    
    private int calculateAnimatedX() {
        float progress = calculateAnimationProgress();
        return getX() - (int) ((1f - progress) * SLIDE_DISTANCE);
    }
    
    private int lerpColor(int color1, int color2, float t) {
        int r1 = (color1 >> 16) & 0xFF;
        int g1 = (color1 >> 8) & 0xFF;
        int b1 = color1 & 0xFF;
        
        int r2 = (color2 >> 16) & 0xFF;
        int g2 = (color2 >> 8) & 0xFF;
        int b2 = color2 & 0xFF;
        
        int r = (int) Mth.lerpInt(t, r1, r2);
        int g = (int) Mth.lerpInt(t, g1, g2);
        int b = (int) Mth.lerpInt(t, b1, b2);
        
        return (r << 16) | (g << 8) | b;
    }
    
    @Override
    public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent event, boolean doubleClick) {
        double mouseX = event.x();
        double mouseY = event.y();
        int button = event.button();
        if (!active || !visible) return false;
        
        int animatedX = calculateAnimatedX();
        
        if (mouseX >= animatedX && mouseX <= animatedX + BUTTON_SIZE && 
            mouseY >= getY() && mouseY <= getY() + BUTTON_SIZE) {
            this.playDownSound(Minecraft.getInstance().getSoundManager());
            
            LanguageManager.cycleLanguage();
            
            this.setMessage(Component.literal(LanguageManager.getCurrentLanguageCode().toUpperCase()));
            
            return true;
        }
        
        return false;
    }
    
    @Override
    protected boolean isValidClickButton(net.minecraft.client.input.MouseButtonInfo input) {
        int button = input.button();
        return button == com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_LEFT;
    }
}
