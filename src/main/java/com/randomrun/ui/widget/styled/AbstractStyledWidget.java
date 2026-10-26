package com.randomrun.ui.widget.styled;

import com.randomrun.infrastructure.RandomRunMod;
import com.randomrun.ui.util.GuiQuadBatch;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.Mth;

public abstract class AbstractStyledWidget {
    protected static final int SLIDE_DISTANCE = 50;
    protected static final int SLIDE_DISTANCE_VERTICAL = 30;
    protected static final int BORDER_THICKNESS = 2;
    protected static final int CURSOR_RADIUS = 30;
    
    protected static final float ANIMATION_DURATION = 400f;
    protected static final float HOVER_SPEED = 0.15f;
    protected static final float BORDER_FADE_SPEED = 0.1f;
    protected static final float BORDER_ANIMATION_SPEED = 0.02f;
    
    protected static final int ALPHA_BACKGROUND = 224;
    protected static final int ALPHA_OVERLAY = 40;
    
    protected static final float SOUND_HOVER_PITCH = 1.8f;
    protected static final float SOUND_HOVER_VOLUME = 0.25f;
    
    protected static final int COLOR_BASE_DEFAULT = 0x302b63;
    protected static final int COLOR_HOVER_DEFAULT = 0x6930c3;
    
    protected float hoverProgress = 0f;
    protected float borderAnimation = 0f;
    protected float borderAnimationAlpha = 0f;
    protected boolean wasHovered = false;
    
    protected void updateAnimations(float delta, boolean hovered) {
        float targetHover = hovered ? 1f : 0f;
        hoverProgress = Mth.lerp(HOVER_SPEED, hoverProgress, targetHover);
        
        float targetBorderAlpha = hovered ? 1f : 0f;
        borderAnimationAlpha = Mth.lerp(BORDER_FADE_SPEED, borderAnimationAlpha, targetBorderAlpha);
        
        if (hovered) {
            borderAnimation += delta * BORDER_ANIMATION_SPEED;
            if (borderAnimation > 1.0f) borderAnimation -= 1.0f;
        }
    }
    
    protected void playHoverSound() {
        if (!RandomRunMod.getInstance().getConfig().isSoundEffectsEnabled()) return;
        
        float volume = RandomRunMod.getInstance().getConfig().getSoundVolume() / 100f;
        Minecraft.getInstance().getSoundManager().play(
            SimpleSoundInstance.forUI(
                SoundEvents.NOTE_BLOCK_BELL.value(), 
                SOUND_HOVER_PITCH, 
                volume * SOUND_HOVER_VOLUME
            )
        );
    }
    
    protected void renderBorderWithCursorEffect(GuiGraphicsExtractor context, int left, int top, int right, int bottom, 
                                                int baseColor, int cursorColor, float alpha) {
        Minecraft mc = Minecraft.getInstance();
        double mouseX = mc.mouseHandler.xpos() * mc.getWindow().getGuiScaledWidth() / mc.getWindow().getScreenWidth();
        double mouseY = mc.mouseHandler.ypos() * mc.getWindow().getGuiScaledHeight() / mc.getWindow().getScreenHeight();
        GuiQuadBatch.Builder border = new GuiQuadBatch.Builder(2 * (right - left + bottom - top));
        
        renderBorderSegment(border, left, top, right, top + BORDER_THICKNESS,
                           (int)mouseX, (int)mouseY, baseColor, cursorColor, true, alpha);
        renderBorderSegment(border, left, bottom - BORDER_THICKNESS, right, bottom,
                           (int)mouseX, (int)mouseY, baseColor, cursorColor, true, alpha);
        renderBorderSegment(border, left, top, left + BORDER_THICKNESS, bottom,
                           (int)mouseX, (int)mouseY, baseColor, cursorColor, false, alpha);
        renderBorderSegment(border, right - BORDER_THICKNESS, top, right, bottom,
                           (int)mouseX, (int)mouseY, baseColor, cursorColor, false, alpha);
        border.build().extract(context);
    }
    
    private void renderBorderSegment(GuiQuadBatch.Builder border, int x1, int y1, int x2, int y2,
                                     int mouseX, int mouseY, int baseColor, int cursorColor, 
                                     boolean horizontal, float alpha) {
        if (horizontal) {
            for (int px = x1; px < x2; px++) {
                int centerY = (y1 + y2) / 2;
                double dx = (double) px - mouseX;
                double dy = (double) centerY - mouseY;
                float distance = (float) Math.sqrt(dx * dx + dy * dy);
                int color = blendColorsByDistance(baseColor, cursorColor, distance);
                color = applyAlphaToColor(color, (int)(alpha * 255));
                border.fill(px, y1, px + 1, y2, color);
            }
        } else {
            for (int py = y1; py < y2; py++) {
                int centerX = (x1 + x2) / 2;
                double dx = (double) centerX - mouseX;
                double dy = (double) py - mouseY;
                float distance = (float) Math.sqrt(dx * dx + dy * dy);
                int color = blendColorsByDistance(baseColor, cursorColor, distance);
                color = applyAlphaToColor(color, (int)(alpha * 255));
                border.fill(x1, py, x2, py + 1, color);
            }
        }
    }
    
    protected int blendColorsByDistance(int color1, int color2, float distance) {
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
    
    protected int applyAlphaToColor(int color, int alpha) {
        int r = (color >> 16) & 0xFF;
        int g = (color >> 8) & 0xFF;
        int b = color & 0xFF;
        return (alpha << 24) | (r << 16) | (g << 8) | b;
    }
    
    protected int lerpColor(int color1, int color2, float t) {
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
    
    protected float calculateAppearanceProgress(long creationTime, float delay) {
        long elapsed = System.currentTimeMillis() - creationTime;
        float delayMs = delay * 1000;
        
        if (elapsed < delayMs) return 0f;
        
        float animElapsed = elapsed - delayMs;
        float progress = Math.min(1f, animElapsed / ANIMATION_DURATION);
        return 1f - (float) Math.pow(1 - progress, 3);
    }
}
