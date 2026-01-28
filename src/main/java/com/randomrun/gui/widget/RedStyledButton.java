package com.randomrun.gui.widget;

import com.randomrun.RandomRunMod;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.math.MathHelper;

public class RedStyledButton extends ButtonWidget {
    private float hoverProgress = 0f;
    private float borderAnimation = 0f;
    private float borderAnimationAlpha = 0f;
    private boolean wasHovered = false;
    
    private static final float HOVER_SPEED = 0.15f;
    private static final float BORDER_FADE_SPEED = 0.1f;
    
    public RedStyledButton(int x, int y, int width, int height, Text message, PressAction onPress) {
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
        
        // Background color with hover effect - RED THEME
        int baseColor = 0x8c1414;  // Dark red
        int hoverColor = 0xc93030;  // Bright red
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
        
        // Draw border segments with animated colors - RED THEME
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
    
    private int lerpBorderColor(float t) {
        // Red color theme
        int red = 0xFF5555;
        int white = 0xFFFFFF;
        
        // Use sine wave for smooth transition
        float factor = (float) Math.sin(t * Math.PI * 2) * 0.5f + 0.5f;
        
        int r1 = (red >> 16) & 0xFF;
        int g1 = (red >> 8) & 0xFF;
        int b1 = red & 0xFF;
        
        int r2 = (white >> 16) & 0xFF;
        int g2 = (white >> 8) & 0xFF;
        int b2 = white & 0xFF;
        
        // Apply border animation alpha to the white component
        float animatedFactor = factor * borderAnimationAlpha;
        
        int r = (int) MathHelper.lerp(animatedFactor, r1, r2);
        int g = (int) MathHelper.lerp(animatedFactor, g1, g2);
        int b = (int) MathHelper.lerp(animatedFactor, b1, b2);
        
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }
    
    private int lerpColor(int color1, int color2, float t) {
        int r1 = (color1 >> 16) & 0xFF;
        int g1 = (color1 >> 8) & 0xFF;
        int b1 = color1 & 0xFF;
        
        int r2 = (color2 >> 16) & 0xFF;
        int g2 = (color2 >> 8) & 0xFF;
        int b2 = color2 & 0xFF;
        
        int r = (int) MathHelper.lerp(t, r1, r2);
        int g = (int) MathHelper.lerp(t, g1, g2);
        int b = (int) MathHelper.lerp(t, b1, b2);
        
        return (r << 16) | (g << 8) | b;
    }
    
    @Override
    public void playDownSound(net.minecraft.client.sound.SoundManager soundManager) {
        if (RandomRunMod.getInstance().getConfig().isSoundEffectsEnabled()) {
            float volume = RandomRunMod.getInstance().getConfig().getSoundVolume() / 100f;
            soundManager.play(
                PositionedSoundInstance.master(SoundEvents.BLOCK_NOTE_BLOCK_CHIME.value(), 1.2f, volume * 0.8f)
            );
        }
    }
}
