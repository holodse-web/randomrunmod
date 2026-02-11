package com.randomrun.ui.widget;

import com.randomrun.main.RandomRunMod;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;

public class ToggleSwitchWidget extends ClickableWidget {
    private boolean enabled;
    private final Runnable onPress;
    private float animProgress = 0f;
    private float hoverProgress = 0f;
    private boolean wasHovered = false;
    
    // Анимация появления
    private float appearanceProgress = 0f;
    private final long creationTime;
    private final float delay;
    private static final float APPEARANCE_DURATION = 400f; // ms
    
    // Цвета
    private static final int BG_OFF = 0xFF802020;    // Темно-красный
    private static final int BG_ON = 0xFF208020;     // Темно-зеленый
    private static final int BORDER_COLOR = 0xFFAAAAAA; // Нейтральный серый
    private static final int KNOB_COLOR = 0xFFFFFFFF;   // Белый
    
    // Размеры
    private static final int SWITCH_WIDTH = 40;
    private static final int SWITCH_HEIGHT = 20;
    
    public ToggleSwitchWidget(int x, int y, boolean initialState, Runnable onPress, float delay) {
        super(x, y, SWITCH_WIDTH, SWITCH_HEIGHT, Text.empty());
        this.enabled = initialState;
        this.onPress = onPress;
        this.animProgress = enabled ? 1.0f : 0.0f;
        this.creationTime = System.currentTimeMillis();
        this.delay = delay;
    }
    
    public ToggleSwitchWidget(int x, int y, boolean initialState, Runnable onPress) {
        this(x, y, initialState, onPress, 0f);
    }

    @Override
    public void renderWidget(DrawContext context, int mouseX, int mouseY, float delta) {
        // 0. Анимация появления (как в ButtonMenu)
        long elapsed = System.currentTimeMillis() - creationTime;
        float delayMs = delay * 1000;
        
        if (elapsed < delayMs) {
            appearanceProgress = 0f;
        } else {
            float animElapsed = elapsed - delayMs;
            appearanceProgress = Math.min(1f, animElapsed / APPEARANCE_DURATION);
        }
        
        // Easing function (ease out cubic)
        float easedProgress = 1f - (float) Math.pow(1 - appearanceProgress, 3);
        
        // Calculate slide offset (slide from left like ButtonMenu)
        int slideOffset = (int) ((1f - easedProgress) * 50);
        float alpha = easedProgress;
        
        if (alpha <= 0) return;
        
        // Смещаем X на время рендеринга
        int originalX = getX();
        int animatedX = originalX - slideOffset;
        setX(animatedX);
        
        // Анимация переключения
        float target = enabled ? 1.0f : 0.0f;
        animProgress += (target - animProgress) * 0.15f * delta * 5f;
        if (Math.abs(target - animProgress) < 0.001f) animProgress = target;
        
        // Логика наведения
        boolean hovered = isHovered();
        float targetHover = hovered ? 1.0f : 0.0f;
        hoverProgress += (targetHover - hoverProgress) * 0.2f * delta * 5f;
        if (Math.abs(targetHover - hoverProgress) < 0.001f) hoverProgress = targetHover;
        
        // Звук наведения
        if (hovered && !wasHovered && appearanceProgress >= 1f) {
             if (RandomRunMod.getInstance().getConfig().isSoundEffectsEnabled()) {
                float volume = RandomRunMod.getInstance().getConfig().getSoundVolume() / 100f;
                MinecraftClient.getInstance().getSoundManager().play(
                    PositionedSoundInstance.master(SoundEvents.BLOCK_NOTE_BLOCK_BELL.value(), 1.8f, volume * 0.25f)
                );
            }
        }
        wasHovered = hovered;
        
        int x = getX();
        int y = getY();
        int w = width;
        int h = height;
        
        // 1. Текстовая метка (Анимированный вылет) - Рисуется первой (сзади)
        if (hoverProgress > 0.01f) {
            MinecraftClient client = MinecraftClient.getInstance();
            Text labelText = enabled 
                ? Text.translatable("randomrun.toggle.online") 
                : Text.translatable("randomrun.toggle.offline");
            
            int labelColor = enabled ? 0x55FF55 : 0xFF5555; 
            
            // Затухание альфы
            int labelAlpha = (int)(hoverProgress * 255 * alpha);
            int finalColor = (labelAlpha << 24) | (labelColor & 0x00FFFFFF);
            
            int textWidth = client.textRenderer.getWidth(labelText);
            float scale = 0.75f;
            
            // Анимация скольжения
            // Начинается с середины кнопки (скрыто) и опускается ниже кнопки
            float startY = y + h / 2.0f - (client.textRenderer.fontHeight * scale) / 2.0f;
            float endY = y + h + 2;
            float currentY = startY + (endY - startY) * hoverProgress;
            
            context.getMatrices().push();
            context.getMatrices().translate(x + w / 2.0f, currentY, 0);
            context.getMatrices().scale(scale, scale, 1.0f);
            
            // Рисование по центру горизонтально
            context.drawTextWithShadow(client.textRenderer, labelText, -textWidth / 2, 0, finalColor);
            
            context.getMatrices().pop();
        }
        
        // 2. Тело кнопки (Спереди) - Рендерится на Z=1.0, чтобы перекрывать текст
        context.getMatrices().push();
        context.getMatrices().translate(0, 0, 1.0f);
        
        // Рамка (с учетом прозрачности появления)
        int borderAlpha = (int)(alpha * 255);
        int finalBorderColor = (borderAlpha << 24) | (BORDER_COLOR & 0x00FFFFFF);
        context.fill(x, y, x + w, y + h, finalBorderColor);
        
        // Фон (Отступ 1 пиксель)
        int bgColor = lerpColor(BG_OFF, BG_ON, animProgress);
        int bgAlpha = (int)(alpha * 255); // Непрозрачный фон переключателя (или можно тоже альфу)
        // Но lerpColor уже содержит 0xFF в старших битах. Надо заменить.
        int finalBgColor = (bgAlpha << 24) | (bgColor & 0x00FFFFFF);
        
        context.fill(x + 1, y + 1, x + w - 1, y + h - 1, finalBgColor);
        
        // Ползунок (Белый)
        int padding = 2;
        int knobSize = h - (padding * 2);
        
        int knobXStart = x + padding;
        int knobXEnd = x + w - padding - knobSize;
        int knobX = (int) (knobXStart + (knobXEnd - knobXStart) * animProgress);
        int knobY = y + padding;
        
        int knobAlpha = (int)(alpha * 255);
        int finalKnobColor = (knobAlpha << 24) | (KNOB_COLOR & 0x00FFFFFF);
        
        context.fill(knobX, knobY, knobX + knobSize, knobY + knobSize, finalKnobColor);
        
        context.getMatrices().pop();
        
        // Возвращаем оригинальный X для корректной обработки кликов в следующем кадре
        setX(originalX);
    }
    
    private int lerpColor(int c1, int c2, float p) {
        int r1 = (c1 >> 16) & 0xFF;
        int g1 = (c1 >> 8) & 0xFF;
        int b1 = (c1) & 0xFF;
        
        int r2 = (c2 >> 16) & 0xFF;
        int g2 = (c2 >> 8) & 0xFF;
        int b2 = (c2) & 0xFF;
        
        int r = (int) (r1 + (r2 - r1) * p);
        int g = (int) (g1 + (g2 - g1) * p);
        int b = (int) (b1 + (b2 - b1) * p);
        
        return (0xFF << 24) | (r << 16) | (g << 8) | b;
    }

    @Override
    protected void appendClickableNarrations(net.minecraft.client.gui.screen.narration.NarrationMessageBuilder builder) {
        // Без озвучки
    }

    @Override
    public void onClick(double mouseX, double mouseY) {
        this.enabled = !this.enabled;
        
        MinecraftClient.getInstance().getSoundManager().play(PositionedSoundInstance.master(SoundEvents.UI_BUTTON_CLICK.value(), 1.0f));
        
        if (this.enabled) {
            GlobalParticleSystem.getInstance().addExplosion(getX() + width / 2, getY() + height / 2, 20, 0x55FF55);
        }
        
        if (onPress != null) {
            onPress.run();
        }
    }
}
