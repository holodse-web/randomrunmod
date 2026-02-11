package com.randomrun.ui.widget.styled;

import com.randomrun.main.RandomRunMod;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.math.MathHelper;

public class ButtonDefault extends ButtonWidget {
    private final float delay;
    private float hoverProgress = 0f;
    private float borderAnimation = 0f;
    private float borderAnimationAlpha = 0f;
    private boolean wasHovered = false;
    private float animationProgress = 0f;
    private final long creationTime;
    private final boolean skipAnimation;
    
    private static final float HOVER_SPEED = 0.15f;
    private static final float BORDER_FADE_SPEED = 0.1f;
    private static final float ANIMATION_DURATION = 400f;
    
    private int baseColor = 0x302b63;
    private int hoverColor = 0x6930c3;
    
    public ButtonDefault(int x, int y, int width, int height, Text message, PressAction onPress, int index, float delay, boolean skipAnimation) {
        super(x, y, width, height, message, onPress, DEFAULT_NARRATION_SUPPLIER);
        this.delay = delay;
        this.skipAnimation = skipAnimation;
        this.creationTime = skipAnimation ? 0 : System.currentTimeMillis();
    }
    
    public ButtonDefault setColors(int baseColor, int hoverColor) {
        this.baseColor = baseColor;
        this.hoverColor = hoverColor;
        return this;
    }

    public ButtonDefault(int x, int y, int width, int height, Text message, PressAction onPress, int index, float delay) {
        this(x, y, width, height, message, onPress, index, delay, false);
    }
    
    // Constructor without animation for backward compatibility
    // Конструктор без анимации для обратной совместимости
    public ButtonDefault(int x, int y, int width, int height, Text message, PressAction onPress) {
        this(x, y, width, height, message, onPress, 0, 0f);
    }
    
    @Override
    public void renderWidget(DrawContext context, int mouseX, int mouseY, float delta) {
        // Обновление анимации появления (slide-up)
        if (skipAnimation) {
            animationProgress = 1f;
        } else {
            long elapsed = System.currentTimeMillis() - creationTime;
            float delayMs = delay * 1000;
            
            if (elapsed < delayMs) {
                animationProgress = 0f;
            } else {
                float animElapsed = elapsed - delayMs;
                animationProgress = Math.min(1f, animElapsed / ANIMATION_DURATION);
            }
        }
        
        // Плавное появление (cubic ease out)
        float easedProgress = 1f - (float) Math.pow(1 - animationProgress, 3);
        
        // Расчет смещения (появление снизу)
        int slideOffset = (int) ((1f - easedProgress) * 30);
        float alpha = easedProgress;
        
        if (alpha <= 0) return;
        
        // Обновление прогресса наведения
        boolean hovered = isHovered();
        float targetHover = hovered ? 1f : 0f;
        hoverProgress = MathHelper.lerp(HOVER_SPEED, hoverProgress, targetHover);
        
        // Анимация появления обводки при наведении
        float targetBorderAlpha = hovered ? 1f : 0f;
        borderAnimationAlpha = MathHelper.lerp(BORDER_FADE_SPEED, borderAnimationAlpha, targetBorderAlpha);
        
        // Обновление позиции анимации обводки
        if (hovered) {
            borderAnimation += delta * 0.05f;
            if (borderAnimation > 1.0f) borderAnimation -= 1.0f;
        }
        
        // Звук при наведении курсора
        if (hovered && !wasHovered) {
            if (RandomRunMod.getInstance().getConfig().isSoundEffectsEnabled()) {
                float volume = RandomRunMod.getInstance().getConfig().getSoundVolume() / 100f;
                MinecraftClient.getInstance().getSoundManager().play(
                    PositionedSoundInstance.master(SoundEvents.BLOCK_NOTE_BLOCK_BELL.value(), 1.8f, volume * 0.25f)
                );
            }
        }
        wasHovered = hovered;
        
        // Расчет анимированной позиции
        int animatedY = getY() + slideOffset;
        
        // Цвет фона с эффектом наведения
        int bgColor = lerpColor(baseColor, hoverColor, hoverProgress);
        int bgAlpha = (int) (alpha * 224);
        int finalBgColor = (bgAlpha << 24) | (bgColor & 0x00FFFFFF);
        
        // Отрисовка фона
        context.fill(getX(), animatedY, getX() + width, animatedY + height, finalBgColor);
        
        // Отрисовка анимированной обводки (1.5px)
        renderAnimatedBorder(context, getX(), animatedY, getX() + width, animatedY + height, alpha);
        
        // Отрисовка текста
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
        
        // Белое наложение прозрачности при наведении
        if (hoverProgress > 0.01f) {
            int whiteAlpha = (int) (hoverProgress * alpha * 40);
            int whiteOverlay = (whiteAlpha << 24) | 0xFFFFFF;
            context.fill(getX(), animatedY, getX() + width, animatedY + height, whiteOverlay);
        }
    }
    
    private void renderAnimatedBorder(DrawContext context, int left, int top, int right, int bottom, float alpha) {
        int borderWidth = 2; // 1.5px (округлено до 2)
        int perimeter = 2 * (right - left) + 2 * (bottom - top);
        
        // Отрисовка сегментов обводки с анимированными цветами
        for (int i = 0; i < perimeter; i++) {
            float progress = (borderAnimation + (float) i / perimeter) % 1.0f;
            int color = lerpBorderColor(progress, alpha);
            
            int x, y;
            if (i < (right - left)) {
                // Верхняя грань
                x = left + i;
                y = top;
            } else if (i < (right - left) + (bottom - top)) {
                // Правая грань
                x = right;
                y = top + (i - (right - left));
            } else if (i < 2 * (right - left) + (bottom - top)) {
                // Нижняя грань
                x = right - (i - (right - left) - (bottom - top));
                y = bottom;
            } else {
                // Левая грань
                x = left;
                y = bottom - (i - 2 * (right - left) - (bottom - top));
            }
            
            context.fill(x, y, x + borderWidth, y + borderWidth, color);
        }
    }
    
    // Использование установленных цветов также для анимации обводки
    private int lerpBorderColor(float t, float alpha) {
        // Базовый фиолетовый цвет
        int purple = hoverColor;
        int white = 0xFFFFFF;
        
        // Использование синусоиды для плавного перехода
        float factor = (float) Math.sin(t * Math.PI * 2) * 0.5f + 0.5f;
        
        // Интерполяция между фиолетовым и белым на основе позиции анимации и альфа-канала наведения
        int r1 = (purple >> 16) & 0xFF;
        int g1 = (purple >> 8) & 0xFF;
        int b1 = purple & 0xFF;
        
        int r2 = (white >> 16) & 0xFF;
        int g2 = (white >> 8) & 0xFF;
        int b2 = white & 0xFF;
        
        // Применение альфа-канала анимации обводки к белому компоненту
        float animatedFactor = factor * borderAnimationAlpha;
        
        int r = (int) MathHelper.lerp(animatedFactor, r1, r2);
        int g = (int) MathHelper.lerp(animatedFactor, g1, g2);
        int b = (int) MathHelper.lerp(animatedFactor, b1, b2);
        
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
        
        int r = (int) MathHelper.lerp(t, r1, r2);
        int g = (int) MathHelper.lerp(t, g1, g2);
        int b = (int) MathHelper.lerp(t, b1, b2);
        
        return (r << 16) | (g << 8) | b;
    }
    
    @Override
    public void playDownSound(net.minecraft.client.sound.SoundManager soundManager) {
        // Переопределение для предотвращения стандартного звука кнопки
        if (RandomRunMod.getInstance().getConfig().isSoundEffectsEnabled()) {
            float volume = RandomRunMod.getInstance().getConfig().getSoundVolume() / 100f;
            soundManager.play(
                PositionedSoundInstance.master(SoundEvents.BLOCK_NOTE_BLOCK_CHIME.value(), 1.2f, volume * 0.8f)
            );
        }
    }
}
