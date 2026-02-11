package com.randomrun.ui.widget.styled;

import com.randomrun.main.RandomRunMod;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.math.MathHelper;

public class ButtonGreen extends ButtonWidget {
    private float hoverProgress = 0f;
    private float borderAnimation = 0f;
    private float borderAnimationAlpha = 0f;
    private boolean wasHovered = false;
    
    private static final float HOVER_SPEED = 0.15f;
    private static final float BORDER_FADE_SPEED = 0.1f;
    
    public ButtonGreen(int x, int y, int width, int height, Text message, PressAction onPress) {
        super(x, y, width, height, message, onPress, DEFAULT_NARRATION_SUPPLIER);
    }
    
    @Override
    public void renderWidget(DrawContext context, int mouseX, int mouseY, float delta) {
        // Update hover progress
        boolean hovered = isHovered();
        float targetHover = hovered ? 1f : 0f;
        hoverProgress = MathHelper.lerp(HOVER_SPEED, hoverProgress, targetHover);
        
        // Update border animation alpha (fade in on hover)
        float targetBorderAlpha = hovered ? 1f : 0f;
        borderAnimationAlpha = MathHelper.lerp(BORDER_FADE_SPEED, borderAnimationAlpha, targetBorderAlpha);
        
        // Update border animation position
        if (hovered) {
            borderAnimation += delta * 0.05f;
            if (borderAnimation > 1.0f) borderAnimation -= 1.0f;
        }
        
        // Play hover sound when mouse enters button
        if (hovered && !wasHovered) {
            if (RandomRunMod.getInstance().getConfig().isSoundEffectsEnabled()) {
                float volume = RandomRunMod.getInstance().getConfig().getSoundVolume() / 100f;
                MinecraftClient.getInstance().getSoundManager().play(
                    PositionedSoundInstance.master(SoundEvents.BLOCK_NOTE_BLOCK_BELL.value(), 1.8f, volume * 0.25f)
                );
            }
        }
        wasHovered = hovered;
        
        // Background color with hover effect - GREEN THEME
        int baseColor = 0x145514;  // Dark green
        int hoverColor = 0x30c930;  // Bright green
        int bgColor = lerpColor(baseColor, hoverColor, hoverProgress);
        int bgAlpha = 224;
        int finalBgColor = (bgAlpha << 24) | (bgColor & 0x00FFFFFF);
        
        // Draw background
        context.fill(getX(), getY(), getX() + width, getY() + height, finalBgColor);
        
        // Draw animated border (1.5px)
        renderAnimatedBorder(context, getX(), getY(), getX() + width, getY() + height);
        
        // Draw text
        MinecraftClient client = MinecraftClient.getInstance();
        int textColor = 0xFFFFFFFF;
        
        context.drawCenteredTextWithShadow(
            client.textRenderer,
            getMessage(),
            getX() + width / 2,
            getY() + (height - 8) / 2,
            textColor
        );
        
        // White transparency overlay on hover
        if (hoverProgress > 0.01f) {
            int whiteAlpha = (int) (hoverProgress * 40);
            int whiteOverlay = (whiteAlpha << 24) | 0xFFFFFF;
            context.fill(getX(), getY(), getX() + width, getY() + height, whiteOverlay);
        }
    }
    
    private void renderAnimatedBorder(DrawContext context, int left, int top, int right, int bottom) {
        int borderWidth = 2;
        int perimeter = 2 * (right - left) + 2 * (bottom - top);
        
        // Draw border segments with animated colors - GREEN THEME
        for (int i = 0; i < perimeter; i++) {
            float progress = (borderAnimation + (float) i / perimeter) % 1.0f;
            int color = lerpBorderColor(progress);
            
            int x, y;
            if (i < (right - left)) {
                x = left + i;
                y = top;
            } else if (i < (right - left) + (bottom - top)) {
                x = right;
                y = top + (i - (right - left));
            } else if (i < 2 * (right - left) + (bottom - top)) {
                x = right - (i - (right - left) - (bottom - top));
                y = bottom;
            } else {
                x = left;
                y = bottom - (i - 2 * (right - left) - (bottom - top));
            }
            
            context.fill(x, y, x + borderWidth, y + borderWidth, color);
        }
    }
    
    private int lerpColor(int color1, int color2, float t) {
        int r1 = (color1 >> 16) & 0xFF;
        int g1 = (color1 >> 8) & 0xFF;
        int b1 = color1 & 0xFF;
        
        int r2 = (color2 >> 16) & 0xFF;
        int g2 = (color2 >> 8) & 0xFF;
        int b2 = color2 & 0xFF;
        
        int r = (int) (r1 + (r2 - r1) * t);
        int g = (int) (g1 + (g2 - g1) * t);
        int b = (int) (b1 + (b2 - b1) * t);
        
        return (r << 16) | (g << 8) | b;
    }
    
    private int lerpBorderColor(float t) {
        // GREEN THEME BORDER
        int green = 0x55FF55;
        int white = 0xFFFFFF;
        
        float factor = (float) Math.sin(t * Math.PI * 2) * 0.5f + 0.5f;
        
        int r1 = (green >> 16) & 0xFF;
        int g1 = (green >> 8) & 0xFF;
        int b1 = green & 0xFF;
        
        int r2 = (white >> 16) & 0xFF;
        int g2 = (white >> 8) & 0xFF;
        int b2 = white & 0xFF;
        
        float animatedFactor = factor * borderAnimationAlpha;
        
        int r = (int) (r1 + (r2 - r1) * animatedFactor);
        int g = (int) (g1 + (g2 - g1) * animatedFactor);
        int b = (int) (b1 + (b2 - b1) * animatedFactor);
        
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }
}
