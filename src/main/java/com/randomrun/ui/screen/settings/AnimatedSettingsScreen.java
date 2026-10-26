package com.randomrun.ui.screen.settings;

import com.randomrun.infrastructure.RandomRunMod;
import com.randomrun.ui.screen.main.AbstractRandomRunScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public abstract class AnimatedSettingsScreen extends AbstractRandomRunScreen {
    protected static final int WIDGET_WIDTH = 400;
    protected static final int WIDGET_HEIGHT = 250;
    protected static final long FADE_DURATION = 300;
    protected static final long ANIMATION_CYCLE = 5000;
    
    protected static final int SHADOW_LAYERS = 5;
    protected static final int SHADOW_SIZE = 8;
    protected static final int BORDER_THICKNESS = 2;
    protected static final int GRADIENT_LENGTH = 30;
    
    protected static final int COLOR_PANEL = 0x150030;
    protected static final int COLOR_BORDER = 0x6A0DAD;
    protected static final int COLOR_GLOW = 0xFFFFFF;
    protected static final int COLOR_SHADOW = 0x000000;
    protected static final int COLOR_OVERLAY = 0x000000;
    
    protected final Screen parent;
    protected final boolean isRefreshing;
    
    protected long screenOpenTime = 0;
    protected boolean closing = false;
    protected long closeStartTime = 0;
    
    protected AnimatedSettingsScreen(Screen parent, boolean isRefreshing, Component title) {
        super(title);
        this.parent = parent;
        this.isRefreshing = isRefreshing;
    }
    
    @Override
    protected void init() {
        super.init();
        screenOpenTime = System.currentTimeMillis();
        initContent();
    }
    
    protected abstract void initContent();
    
    protected void startClosing() {
        closing = true;
        closeStartTime = System.currentTimeMillis();
    }
    
    protected int getWidgetX() {
        return (width - WIDGET_WIDTH) / 2;
    }
    
    protected int getWidgetY() {
        return (height - WIDGET_HEIGHT) / 2;
    }
    
    @Override
    public void extractBackground(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        super.extractBackground(context, mouseX, mouseY, delta);
        
        float fadeProgress = getFadeProgress();
        if (closing && fadeProgress <= 0) {
            Minecraft.getInstance().gui.setScreen(parent);
            return;
        }
        
        int blurAlpha = (int) (180 * fadeProgress);
        context.fill(0, 0, width, height, (blurAlpha << 24) | COLOR_OVERLAY);
        
        extractWidgetRenderState(context, fadeProgress, delta);
    }
    
    protected float getFadeProgress() {
        if (closing) {
            long elapsed = System.currentTimeMillis() - closeStartTime;
            return 1.0f - Math.min(1.0f, elapsed / (float) FADE_DURATION);
        } else {
            long elapsed = System.currentTimeMillis() - screenOpenTime;
            return Math.min(1.0f, elapsed / (float) FADE_DURATION);
        }
    }
    
    protected void extractWidgetRenderState(GuiGraphicsExtractor context, float fadeProgress, float delta) {
        int widgetX = getWidgetX();
        int widgetY = getWidgetY();
        
        int panelAlpha = (int) (0xD0 * fadeProgress);
        int panelColor = (panelAlpha << 24) | COLOR_PANEL;
        
        renderShadow(context, widgetX, widgetY, fadeProgress);
        context.fill(widgetX, widgetY, widgetX + WIDGET_WIDTH, widgetY + WIDGET_HEIGHT, panelColor);
        renderStaticBorder(context, widgetX, widgetY, fadeProgress);
        renderAnimatedBorder(context, widgetX, widgetY, delta, fadeProgress);
        
        int titleAlpha = (int) (255 * fadeProgress);
        int titleColor = (titleAlpha << 24) | COLOR_GLOW;
        com.randomrun.ui.util.GuiText.centeredText(context, font, title, width / 2, widgetY + 10, titleColor);
        
        renderWidgetContent(context, widgetX, widgetY, fadeProgress);
    }
    
    protected void renderWidgetContent(GuiGraphicsExtractor context, int widgetX, int widgetY, float fadeProgress) {
    }
    
    protected void renderShadow(GuiGraphicsExtractor context, int x, int y, float fadeProgress) {
        for (int i = 0; i < SHADOW_LAYERS; i++) {
            float progress = (float) i / SHADOW_LAYERS;
            int offset = (int) (SHADOW_SIZE * progress);
            int alpha = (int) (40 * (1.0f - progress * progress) * fadeProgress);
            int shadowColor = (alpha << 24) | COLOR_SHADOW;
            
            context.fill(x - offset, y - offset, 
                x + WIDGET_WIDTH + offset, y + WIDGET_HEIGHT + offset, shadowColor);
        }
    }
    
    protected void renderStaticBorder(GuiGraphicsExtractor context, int x, int y, float fadeProgress) {
        int borderAlpha = (int) (255 * fadeProgress);
        int borderColor = (borderAlpha << 24) | COLOR_BORDER;
        
        context.fill(x, y, x + WIDGET_WIDTH, y + BORDER_THICKNESS, borderColor);
        context.fill(x, y + WIDGET_HEIGHT - BORDER_THICKNESS, x + WIDGET_WIDTH, y + WIDGET_HEIGHT, borderColor);
        context.fill(x, y, x + BORDER_THICKNESS, y + WIDGET_HEIGHT, borderColor);
        context.fill(x + WIDGET_WIDTH - BORDER_THICKNESS, y, x + WIDGET_WIDTH, y + WIDGET_HEIGHT, borderColor);
    }
    
    protected void renderAnimatedBorder(GuiGraphicsExtractor context, int x, int y, float delta, float fadeProgress) {
        if (!RandomRunMod.getInstance().getConfig().isAnimationsEnabled()) return;
        
        long time = System.currentTimeMillis();
        float animationProgress = ((time % ANIMATION_CYCLE) / (float) ANIMATION_CYCLE);
        
        int perimeter = 2 * (WIDGET_WIDTH + WIDGET_HEIGHT);
        float currentPos = animationProgress * perimeter;
        
        drawBorderSegment(context, x, y, 0, WIDGET_WIDTH, true, currentPos, perimeter, fadeProgress);
        drawBorderSegment(context, x + WIDGET_WIDTH - BORDER_THICKNESS, y, WIDGET_WIDTH, WIDGET_HEIGHT, false, currentPos, perimeter, fadeProgress);
        drawBorderSegment(context, x, y + WIDGET_HEIGHT - BORDER_THICKNESS, WIDGET_WIDTH + WIDGET_HEIGHT, WIDGET_WIDTH, true, currentPos, perimeter, fadeProgress);
        drawBorderSegment(context, x, y, 2 * WIDGET_WIDTH + WIDGET_HEIGHT, WIDGET_HEIGHT, false, currentPos, perimeter, fadeProgress);
    }
    
    protected void drawBorderSegment(GuiGraphicsExtractor context, int x, int y, int offset, int length, 
                                     boolean horizontal, float currentPos, int perimeter, float fadeProgress) {
        for (int i = 0; i < length; i++) {
            float dist = getCircularDistance(offset + i, currentPos, perimeter);
            if (dist < GRADIENT_LENGTH) {
                float intensity = (1.0f - (dist / GRADIENT_LENGTH)) * fadeProgress;
                int alpha = (int) (255 * intensity);
                int color = (alpha << 24) | COLOR_GLOW;
                
                if (horizontal) {
                    context.fill(x + i, y, x + i + 1, y + BORDER_THICKNESS, color);
                } else {
                    context.fill(x, y + i, x + BORDER_THICKNESS, y + i + 1, color);
                }
            }
        }
    }
    
    protected float getCircularDistance(int pos, float currentPos, int perimeter) {
        float dist = Math.abs(pos - currentPos);
        if (dist > perimeter / 2) {
            dist = perimeter - dist;
        }
        return dist;
    }
}
