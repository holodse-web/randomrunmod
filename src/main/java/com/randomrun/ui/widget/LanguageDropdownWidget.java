package com.randomrun.ui.widget;

import com.randomrun.main.util.LanguageManager;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;

public class LanguageDropdownWidget extends ButtonWidget {
    private boolean expanded = false;
    private float expandProgress = 0f;
    private static final String[] LANGUAGE_CODES = {"RU", "UA", "EN"};
    private static final String[] LANGUAGES = {"ru_ru", "uk_ua", "en_us"};
    private static final int BUTTON_SIZE = 20;
    private static final float EXPAND_SPEED = 0.15f;
    private float hoverProgress = 0f;
    private float borderAnimation = 0f;
    private float borderAnimationAlpha = 0f;
    private static final float HOVER_SPEED = 0.15f;
    private static final float BORDER_FADE_SPEED = 0.1f;
    
    // Animation fields
    private final float delay;
    private float animationProgress = 0f;
    private long creationTime;
    private static final int ANIMATION_DURATION = 300; // ms
    
    public LanguageDropdownWidget(int x, int y, Screen parentScreen, float delay) {
        super(x, y, BUTTON_SIZE, BUTTON_SIZE, Text.literal(LanguageManager.getCurrentLanguageCode().toUpperCase()), 
            button -> {}, DEFAULT_NARRATION_SUPPLIER);
        this.delay = delay;
        this.creationTime = System.currentTimeMillis();
    }
    
    public LanguageDropdownWidget(int x, int y, Screen parentScreen) {
        this(x, y, parentScreen, 0f);
    }
    
    @Override
    public void renderWidget(DrawContext context, int mouseX, int mouseY, float delta) {
        // Calculate animation progress
        long elapsed = System.currentTimeMillis() - creationTime;
        float delayMs = delay * 1000;
        
        if (elapsed < delayMs) {
            animationProgress = 0f;
        } else {
            float animElapsed = elapsed - delayMs;
            animationProgress = Math.min(1f, animElapsed / ANIMATION_DURATION);
        }
        
        // Easing function (ease out cubic)
        float easedProgress = 1f - (float) Math.pow(1 - animationProgress, 3);
        
        // Calculate position with slide-in animation
        int animatedX = getX() - (int) ((1f - easedProgress) * 50);
        float alpha = easedProgress;
        
        if (alpha <= 0) return;

        // Update expand animation
        if (expanded && expandProgress < 1f) {
            expandProgress = Math.min(1f, expandProgress + EXPAND_SPEED);
        } else if (!expanded && expandProgress > 0f) {
            expandProgress = Math.max(0f, expandProgress - EXPAND_SPEED);
        }
        
        // Render main button with StyledButton style (purple theme)
        boolean hovered = this.isHovered();
        float targetHover = hovered ? 1f : 0f;
        hoverProgress = hoverProgress + (targetHover - hoverProgress) * HOVER_SPEED;
        
        // Update border animation alpha
        float targetBorderAlpha = hovered ? 1f : 0f;
        borderAnimationAlpha = borderAnimationAlpha + (targetBorderAlpha - borderAnimationAlpha) * BORDER_FADE_SPEED;
        
        // Update border animation position
        if (hovered) {
            borderAnimation += delta * 0.05f;
            if (borderAnimation > 1.0f) borderAnimation -= 1.0f;
        }
        
        int baseColor = 0x302b63;
        int hoverColor = 0x6930c3;
        int bgColor = lerpColor(baseColor, hoverColor, hoverProgress);
        int bgAlpha = (int) (alpha * 224);
        int finalBgColor = (bgAlpha << 24) | (bgColor & 0x00FFFFFF);
        
        context.fill(animatedX, getY(), animatedX + BUTTON_SIZE, getY() + BUTTON_SIZE, finalBgColor);
        
        // Animated border like StyledButton
        renderAnimatedBorder(context, animatedX, getY(), animatedX + BUTTON_SIZE, getY() + BUTTON_SIZE, alpha);
        
        // White overlay on hover
        if (hoverProgress > 0.01f) {
            int whiteAlpha = (int) (hoverProgress * 40 * alpha);
            int whiteOverlay = (whiteAlpha << 24) | 0xFFFFFF;
            context.fill(animatedX, getY(), animatedX + BUTTON_SIZE, getY() + BUTTON_SIZE, whiteOverlay);
        }
        
        int textAlpha = (int) (alpha * 255);
        int textColor = (textAlpha << 24) | 0xFFFFFF;
        
        context.drawCenteredTextWithShadow(
            MinecraftClient.getInstance().textRenderer,
            getMessage(),
            animatedX + BUTTON_SIZE / 2,
            getY() + (BUTTON_SIZE - 8) / 2,
            textColor
        );
        
        // Render dropdown sliding to the right
        if (expandProgress > 0f) {
            String currentLang = LanguageManager.getCurrentLanguageCode().toUpperCase();
            int dropdownX = animatedX + BUTTON_SIZE + 2;
            int itemIndex = 0;
            
            for (int i = 0; i < LANGUAGE_CODES.length; i++) {
                // Skip current language
                if (LANGUAGE_CODES[i].equals(currentLang)) continue;
                
                int itemX = dropdownX + (int) (itemIndex * BUTTON_SIZE * expandProgress);
                int itemWidth = (int) (BUTTON_SIZE * expandProgress);
                
                if (itemWidth <= 0) {
                    itemIndex++;
                    continue;
                }
                
                boolean itemHovered = mouseX >= itemX && mouseX <= itemX + itemWidth &&
                                     mouseY >= getY() && mouseY <= getY() + BUTTON_SIZE;
                
                // StyledButton style colors
                int itemBaseColor = 0x302b63;
                int itemHoverColor = 0x6930c3;
                
                // Calculate hover for this item (simplified)
                int itemBgColor = itemHovered ? itemHoverColor : itemBaseColor;
                int itemBgAlpha = (int) (alpha * 224);
                int itemFinalBgColor = (itemBgAlpha << 24) | (itemBgColor & 0x00FFFFFF);
                
                context.fill(itemX, getY(), itemX + itemWidth, getY() + BUTTON_SIZE, itemFinalBgColor);
                
                renderAnimatedBorder(context, itemX, getY(), itemX + itemWidth, getY() + BUTTON_SIZE, alpha);
                
                if (expandProgress > 0.8f) {
                    context.drawCenteredTextWithShadow(
                        MinecraftClient.getInstance().textRenderer,
                        Text.literal(LANGUAGE_CODES[i]),
                        itemX + itemWidth / 2,
                        getY() + (BUTTON_SIZE - 8) / 2,
                        textColor
                    );
                }
                
                itemIndex++;
            }
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
        int purple = 0x6930c3;
        int white = 0xFFFFFF;
        
        float factor = (float) Math.sin(t * Math.PI * 2) * 0.5f + 0.5f;
        
        int r1 = (purple >> 16) & 0xFF;
        int g1 = (purple >> 8) & 0xFF;
        int b1 = purple & 0xFF;
        
        int r2 = (white >> 16) & 0xFF;
        int g2 = (white >> 8) & 0xFF;
        int b2 = white & 0xFF;
        
        float animatedFactor = factor * borderAnimationAlpha;
        
        int r = (int) (r1 + (r2 - r1) * animatedFactor);
        int g = (int) (g1 + (g2 - g1) * animatedFactor);
        int b = (int) (b1 + (b2 - b1) * animatedFactor);
        
        int colorAlpha = (int) (alpha * 255);
        return (colorAlpha << 24) | (r << 16) | (g << 8) | b;
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
    
    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (!active || !visible) return false;
        
        // Calculate animated X position (same as in renderWidget)
        long elapsed = System.currentTimeMillis() - creationTime;
        float delayMs = delay * 1000;
        float animationProgress = 0f;
        if (elapsed >= delayMs) {
            float animElapsed = elapsed - delayMs;
            animationProgress = Math.min(1f, animElapsed / ANIMATION_DURATION);
        }
        float easedProgress = 1f - (float) Math.pow(1 - animationProgress, 3);
        int animatedX = getX() - (int) ((1f - easedProgress) * 50);
        
        if (mouseX >= animatedX && mouseX <= animatedX + BUTTON_SIZE &&
            mouseY >= getY() && mouseY <= getY() + BUTTON_SIZE) {
            this.playDownSound(MinecraftClient.getInstance().getSoundManager());
            expanded = !expanded;
            return true;
        }
        
        if (expanded) {
            String currentLang = LanguageManager.getCurrentLanguageCode().toUpperCase();
            int dropdownX = animatedX + BUTTON_SIZE + 2;
            int itemIndex = 0;
            
            for (int i = 0; i < LANGUAGE_CODES.length; i++) {
                if (LANGUAGE_CODES[i].equals(currentLang)) continue;
                
                int itemX = dropdownX + (int) (itemIndex * BUTTON_SIZE * expandProgress);
                int itemWidth = (int) (BUTTON_SIZE * expandProgress);
                
                // Ensure clickable area exists if expanded
                if (itemWidth < 1 && expandProgress > 0.1f) itemWidth = 1;
                
                if (mouseX >= itemX && mouseX <= itemX + itemWidth &&
                    mouseY >= getY() && mouseY <= getY() + BUTTON_SIZE) {
                    
                    this.playDownSound(MinecraftClient.getInstance().getSoundManager());
                    // Log selection for debugging
                    System.out.println("Selecting language: " + LANGUAGES[i]);
                    LanguageManager.setLanguage(LANGUAGES[i]);
                    
                    expanded = false;
                    return true;
                }
                
                itemIndex++;
            }
            
            // Check if click is outside
            // Если клик был не по кнопкам языка, закрываем меню
            // (Проверка выше вернет true, если клик был по кнопке)
            if (mouseX < animatedX || mouseX > animatedX + 200 || mouseY < getY() || mouseY > getY() + BUTTON_SIZE) {
                 expanded = false;
            }
        }
        
        return false;
    }
    
    @Override
    protected boolean isValidClickButton(int button) {
        return button == 0;
    }
}
