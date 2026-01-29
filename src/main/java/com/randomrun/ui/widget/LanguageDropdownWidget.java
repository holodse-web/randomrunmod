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
    
    public LanguageDropdownWidget(int x, int y, Screen parentScreen) {
        super(x, y, BUTTON_SIZE, BUTTON_SIZE, Text.literal(LanguageManager.getCurrentLanguageCode().toUpperCase()), 
            button -> {}, DEFAULT_NARRATION_SUPPLIER);
    }
    
    @Override
    public void renderWidget(DrawContext context, int mouseX, int mouseY, float delta) {
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
        int bgAlpha = 224;
        int finalBgColor = (bgAlpha << 24) | (bgColor & 0x00FFFFFF);
        
        context.fill(getX(), getY(), getX() + BUTTON_SIZE, getY() + BUTTON_SIZE, finalBgColor);
        
        // Animated border like StyledButton
        renderAnimatedBorder(context, getX(), getY(), getX() + BUTTON_SIZE, getY() + BUTTON_SIZE);
        
        // White overlay on hover
        if (hoverProgress > 0.01f) {
            int whiteAlpha = (int) (hoverProgress * 40);
            int whiteOverlay = (whiteAlpha << 24) | 0xFFFFFF;
            context.fill(getX(), getY(), getX() + BUTTON_SIZE, getY() + BUTTON_SIZE, whiteOverlay);
        }
        
        context.drawCenteredTextWithShadow(
            MinecraftClient.getInstance().textRenderer,
            getMessage(),
            getX() + BUTTON_SIZE / 2,
            getY() + (BUTTON_SIZE - 8) / 2,
            0xFFFFFF
        );
        
        // Render dropdown sliding to the right
        if (expandProgress > 0f) {
            String currentLang = LanguageManager.getCurrentLanguageCode().toUpperCase();
            int dropdownX = getX() + BUTTON_SIZE + 2;
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
                int itemBgAlpha = 224;
                int itemFinalBgColor = (itemBgAlpha << 24) | (itemBgColor & 0x00FFFFFF);
                
                context.fill(itemX, getY(), itemX + itemWidth, getY() + BUTTON_SIZE, itemFinalBgColor);
                
                renderAnimatedBorder(context, itemX, getY(), itemX + itemWidth, getY() + BUTTON_SIZE);
                
                if (expandProgress > 0.8f) {
                    context.drawCenteredTextWithShadow(
                        MinecraftClient.getInstance().textRenderer,
                        Text.literal(LANGUAGE_CODES[i]),
                        itemX + itemWidth / 2,
                        getY() + (BUTTON_SIZE - 8) / 2,
                        0xFFFFFF
                    );
                }
                
                itemIndex++;
            }
        }
    }
    
    private void renderAnimatedBorder(DrawContext context, int left, int top, int right, int bottom) {
        int borderWidth = 2;
        int perimeter = 2 * (right - left) + 2 * (bottom - top);
        
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
        
        return 0xFF000000 | (r << 16) | (g << 8) | b;
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
        
        if (this.clicked(mouseX, mouseY)) {
            this.playDownSound(MinecraftClient.getInstance().getSoundManager());
            expanded = !expanded;
            return true;
        }
        
        if (expanded) {
            String currentLang = LanguageManager.getCurrentLanguageCode().toUpperCase();
            int dropdownX = getX() + BUTTON_SIZE + 2;
            int itemIndex = 0;
            
            for (int i = 0; i < LANGUAGE_CODES.length; i++) {
                if (LANGUAGE_CODES[i].equals(currentLang)) continue;
                
                int itemX = dropdownX + (itemIndex * BUTTON_SIZE);
                
                if (mouseX >= itemX && mouseX <= itemX + BUTTON_SIZE &&
                    mouseY >= getY() && mouseY <= getY() + BUTTON_SIZE) {
                    
                    this.playDownSound(MinecraftClient.getInstance().getSoundManager());
                    LanguageManager.setLanguage(LANGUAGES[i]);
                    expanded = false;
                    return true;
                }
                
                itemIndex++;
            }
        }
        
        if (expanded && (mouseX < getX() || mouseX > getX() + 200 || mouseY < getY() || mouseY > getY() + BUTTON_SIZE)) {
             expanded = false;
        }
        
        return false;
    }
    
    @Override
    protected boolean isValidClickButton(int button) {
        return button == 0;
    }
}
