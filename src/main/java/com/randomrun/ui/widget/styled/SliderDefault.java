package com.randomrun.ui.widget.styled;

import com.randomrun.main.RandomRunMod;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.SliderWidget;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.math.MathHelper;

import java.util.function.Consumer;

public class SliderDefault extends SliderWidget {
    private final Consumer<Double> onApply;
    private final float delay;
    private float hoverProgress = 0f;
    private float borderAnimation = 0f;
    private float borderAnimationAlpha = 0f;
    private boolean wasHovered = false;
    private float animationProgress = 0f;
    private final long creationTime;
    
    private static final float HOVER_SPEED = 0.15f;
    private static final float BORDER_FADE_SPEED = 0.1f;
    private static final float ANIMATION_DURATION = 400f;
    
    private int baseColor = 0x302b63;
    private int activeColor = 0x6930c3; // Color for the active part/handle
    
    public SliderDefault(int x, int y, int width, int height, Text text, double value, Consumer<Double> onApply, float delay) {
        super(x, y, width, height, text, value);
        this.onApply = onApply;
        this.delay = delay;
        this.creationTime = System.currentTimeMillis();
    }
    
    @Override
    protected void updateMessage() {
        // Message is updated externally or by override
    }
    
    @Override
    protected void applyValue() {
        if (onApply != null) {
            onApply.accept(this.value);
        }
    }
    
    @Override
    public void renderWidget(DrawContext context, int mouseX, int mouseY, float delta) {
        // Animation logic similar to StyledButton2
        long elapsed = System.currentTimeMillis() - creationTime;
        float delayMs = delay * 1000;
        
        if (elapsed < delayMs) {
            animationProgress = 0f;
        } else {
            float animElapsed = elapsed - delayMs;
            animationProgress = Math.min(1f, animElapsed / ANIMATION_DURATION);
        }
        
        float easedProgress = 1f - (float) Math.pow(1 - animationProgress, 3);
        int slideOffset = (int) ((1f - easedProgress) * 30);
        float alpha = easedProgress;
        
        if (alpha <= 0) return;
        
        boolean hovered = isHovered();
        float targetHover = hovered ? 1f : 0f;
        hoverProgress = MathHelper.lerp(HOVER_SPEED, hoverProgress, targetHover);
        
        float targetBorderAlpha = hovered ? 1f : 0f;
        borderAnimationAlpha = MathHelper.lerp(BORDER_FADE_SPEED, borderAnimationAlpha, targetBorderAlpha);
        
        if (hovered) {
            borderAnimation += delta * 0.05f;
            if (borderAnimation > 1.0f) borderAnimation -= 1.0f;
        }
        
        if (hovered && !wasHovered) {
            if (RandomRunMod.getInstance().getConfig().isSoundEffectsEnabled()) {
                float volume = RandomRunMod.getInstance().getConfig().getSoundVolume() / 100f;
                MinecraftClient.getInstance().getSoundManager().play(
                    PositionedSoundInstance.master(SoundEvents.BLOCK_NOTE_BLOCK_BELL.value(), 1.8f, volume * 0.25f)
                );
            }
        }
        wasHovered = hovered;
        
        int animatedY = getY() + slideOffset;
        
        // Render Background (Track)
        int bgAlpha = (int) (alpha * 224);
        int finalBgColor = (bgAlpha << 24) | (baseColor & 0x00FFFFFF);
        context.fill(getX(), animatedY, getX() + width, animatedY + height, finalBgColor);
        
        // Render Active Part (Filled from left to handle)
        int handleWidth = 8;
        int handleX = getX() + (int)(this.value * (double)(this.width - handleWidth));
        int activeWidth = handleX - getX() + handleWidth / 2;
        
        int activeAlpha = (int) (alpha * 255);
        int finalActiveColor = (activeAlpha << 24) | (activeColor & 0x00FFFFFF);
        
        // Draw filled part
        context.fill(getX(), animatedY, getX() + activeWidth, animatedY + height, finalActiveColor);
        
        // Draw Handle Highlight
        int handleColor = 0xFFFFFFFF;
        int handleAlpha = (int) (alpha * 255);
        int finalHandleColor = (handleAlpha << 24) | (handleColor & 0x00FFFFFF);
        context.fill(handleX, animatedY, handleX + handleWidth, animatedY + height, finalHandleColor);
        
        // Render Animated Border
        renderAnimatedBorder(context, getX(), animatedY, getX() + width, animatedY + height, alpha);
        
        // Render Text
        MinecraftClient client = MinecraftClient.getInstance();
        int textAlpha = (int) (alpha * 255);
        int textColor = (textAlpha << 24) | 0xFFFFFF;
        
        context.drawCenteredTextWithShadow(
            client.textRenderer,
            getMessage(),
            getX() + width / 2,
            animatedY + (height - 8) / 2,
            textColor
        );
        
        // Hover Overlay
        if (hoverProgress > 0.01f) {
            int whiteAlpha = (int) (hoverProgress * alpha * 40);
            int whiteOverlay = (whiteAlpha << 24) | 0xFFFFFF;
            context.fill(getX(), animatedY, getX() + width, animatedY + height, whiteOverlay);
        }
    }
    
    private void renderAnimatedBorder(DrawContext context, int left, int top, int right, int bottom, float alpha) {
        int borderWidth = 2;
        int perimeter = 2 * (right - left) + 2 * (bottom - top);
        
        for (int i = 0; i < perimeter; i++) {
            float progress = (borderAnimation + (float) i / perimeter) % 1.0f;
            int color = lerpBorderColor(progress, alpha);
            
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
    
    private int lerpBorderColor(float t, float alpha) {
        int purple = activeColor;
        int white = 0xFFFFFF;
        
        float factor = (float) Math.sin(t * Math.PI * 2) * 0.5f + 0.5f;
        
        int r1 = (purple >> 16) & 0xFF;
        int g1 = (purple >> 8) & 0xFF;
        int b1 = purple & 0xFF;
        
        int r2 = (white >> 16) & 0xFF;
        int g2 = (white >> 8) & 0xFF;
        int b2 = white & 0xFF;
        
        float animatedFactor = factor * borderAnimationAlpha;
        
        int r = (int) MathHelper.lerp(animatedFactor, r1, r2);
        int g = (int) MathHelper.lerp(animatedFactor, g1, g2);
        int b = (int) MathHelper.lerp(animatedFactor, b1, b2);
        
        int colorAlpha = (int) (alpha * 255);
        return (colorAlpha << 24) | (r << 16) | (g << 8) | b;
    }
}
