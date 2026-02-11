/*
 * Copyright (c) 2026 Stanislav Kholod.
 * All rights reserved.
 */
package com.randomrun.challenges.classic.hud;

import com.randomrun.main.RandomRunMod;
import com.randomrun.main.config.ModConfig;
import com.randomrun.main.data.RunDataManager;
import com.randomrun.ui.util.BlurHandler;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.util.InputUtil;
import net.minecraft.item.ItemStack;

import net.minecraft.client.render.RenderTickCounter;

public class HudRenderer implements HudRenderCallback {
    
    private static final int HUD_SIZE = 48; // Квадратный HUD (уменьшен)
    private static final int BORDER_WIDTH = 1; // Увеличена толщина обводки
    private float borderAnimation = 0f;
    private float blinkAnimation = 0f;
    
    private final BlurHandler blurHandler = new BlurHandler();
    
    @Override
    public void onHudRender(DrawContext context, RenderTickCounter tickCounter) {
        float tickDelta = tickCounter.getTickDelta(true);
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || client.world == null) return;
        
        RunDataManager runManager = RandomRunMod.getInstance().getRunDataManager();
        RunDataManager.RunStatus status = runManager.getStatus();
        
        // Only render if run is active AND target is ITEM
        // Рендерить только если забег активен И цель - ПРЕДМЕТ
        if (status == RunDataManager.RunStatus.INACTIVE || runManager.getTargetType() != RunDataManager.TargetType.ITEM) return;
        
        ModConfig config = RandomRunMod.getInstance().getConfig();
        
        // Calculate position
        // Вычисление позиции
        int x, y;
        switch (config.getHudPosition()) {
            case TOP_RIGHT:
                x = client.getWindow().getScaledWidth() - HUD_SIZE - 10;
                y = 10;
                break;
            case TOP_CENTER:
                x = (client.getWindow().getScaledWidth() - HUD_SIZE) / 2;
                y = 10;
                break;
            case CUSTOM:
                x = config.getCustomHudX();
                y = config.getCustomHudY();
                break;
            case TOP_LEFT:
            default:
                x = 10;
                y = 10;
                break;
        }
        
        // Update animations
        // Обновление анимаций
        borderAnimation += tickDelta * 0.02f;
        if (borderAnimation > 1.0f) borderAnimation -= 1.0f;
        
        blinkAnimation += tickDelta * 0.05f;
        if (blinkAnimation > 1.0f) blinkAnimation -= 1.0f;
        
        // Render Blur Background (Gaussian with downsampling) if enabled
        // Рендер размытия фона (Гауссово с уменьшением масштаба), если включено
        if (config.isBlurEnabled()) {
            // FIX: Blur shader logic restored.
            // Using DrawContext's matrices to ensure correct positioning.
            // blurHandler.renderBlur(x, y, HUD_SIZE, HUD_SIZE, 2);
            // Since we don't have a reliable blur implementation yet, we skip it to avoid black screen issues.
            // But user reported "Stopped working", implying it worked before or they want it fixed.
            // If BlurHandler is available, let's try to use it cautiously.
            
            // DISABLED due to rendering issues (HUD disappears when chat hidden, buggy items)
            // User requested to disable/remove it if unstable.
            /*
            try {
                // Assuming BlurHandler exists and works
                blurHandler.renderBlur(x, y, HUD_SIZE, HUD_SIZE, 4); 
            } catch (Exception e) {
                // Ignore blur errors
            }
            */
        }
        
        // Render HUD background (semi-transparent black, lighter if blur enabled)
        // Рендер фона HUD (полупрозрачный черный, светлее если включено размытие)
        int bgAlpha = config.isBlurEnabled() ? 0x60000000 : 0xC0000000;
        context.fill(x, y, x + HUD_SIZE, y + HUD_SIZE, bgAlpha);
        
        // Render animated border (1px)
        // Рендер анимированной рамки (1px)
        renderAnimatedBorder(context, x, y, status);
        
        // Render target item (centered, moved down)
        // Рендер целевого предмета (по центру, смещен вниз)
        if (runManager.getTargetItem() != null) {
            ItemStack stack = new ItemStack(runManager.getTargetItem());
            context.getMatrices().push();
            int itemX = x + HUD_SIZE / 2;
            int itemY = y + HUD_SIZE / 2 - 4; // Смещено вниз с -8 до -4
            context.getMatrices().translate(itemX, itemY, 0);
            context.getMatrices().scale(2.0f, 2.0f, 1.0f);
            context.drawItem(stack, -8, -8, 0);
            context.getMatrices().pop();
        }
        
        // Render timer or '/go' text (both at the same position, lower)
        // Рендер таймера или текста '/go' (оба на одной позиции, ниже)
        if (status == RunDataManager.RunStatus.FROZEN) {
            // Show blinking '/go' text (yellow-orange)
            // Показать мигающий текст '/go' (желто-оранжевый)
            float blinkValue = (float) Math.sin(blinkAnimation * Math.PI * 2) * 0.5f + 0.5f;
            int red = 255;
            int green = (int) (165 + blinkValue * 90); // 165-255 (от оранжевого к желтому)
            int blue = 0;
            int blinkColor = 0xFF000000 | (red << 16) | (green << 8) | blue;
            
            String goText;
            if (config.getStartMethod() == ModConfig.StartMethod.KEYBIND) {
                String keyName = InputUtil.fromKeyCode(config.getStartKey(), 0).getLocalizedText().getString();
                goText = keyName.toUpperCase();
            } else {
                goText = "/go";
            }
            
            context.getMatrices().push();
            context.getMatrices().scale(0.8f, 0.8f, 1.0f);
            int scaledGoWidth = (int)(client.textRenderer.getWidth(goText) * 0.8f);
            int goX = (int)((x + (HUD_SIZE - scaledGoWidth) / 2) / 0.8f);
            int goY = (int)((y + HUD_SIZE - 9) / 0.8f); // Смещено с -5 до -9 (ниже)
            context.drawTextWithShadow(client.textRenderer, goText, goX, goY, blinkColor);
            context.getMatrices().pop();
        } else {
            // Render time (running or completed/failed)
            // Рендер времени (идет или завершено/провалено)
            long timeLimit = runManager.getTimeLimit();
            String timeStr;
            int timeColor = 0xFFFFFF; // Белый по умолчанию
            
            if (timeLimit > 0) {
                long remainingTime = runManager.getRemainingTime();
                timeStr = RunDataManager.formatTime(remainingTime);
                if (remainingTime < 10000) { // < 10 секунд
                    timeColor = 0xFF5555; // Красный
                }
            } else {
                long currentTime = runManager.getCurrentTime();
                timeStr = RunDataManager.formatTime(currentTime);
            }
            
            // Draw time centered below item (at same position as /go text)
            // Отрисовка времени по центру под предметом (на той же позиции, что и текст /go)
            context.getMatrices().push();
            context.getMatrices().scale(0.7f, 0.7f, 1.0f);
            int scaledTimeWidth = (int)(client.textRenderer.getWidth(timeStr) * 0.7f);
            int timeX = (int)((x + (HUD_SIZE - scaledTimeWidth) / 2) / 0.7f);
            int timeY = (int)((y + HUD_SIZE - 9) / 0.7f); // Смещено с -12 до -9 (ниже, так же как /go)
            context.drawTextWithShadow(client.textRenderer, "§e" + timeStr, timeX, timeY, timeColor);
            context.getMatrices().pop();
        }
    }
    
    private void renderAnimatedBorder(DrawContext context, int x, int y, RunDataManager.RunStatus status) {
        int perimeter = (HUD_SIZE - 1) * 4;
        
        for (int i = 0; i < perimeter; i += 1) {
            float progress = (borderAnimation + (float) i / perimeter) % 1.0f;
            
            // Color: always rainbow
            // Цвет: всегда радужный
            int color = getRainbowColor(progress);
            
            // Calculate position on border (fixed to prevent overlap)
            // Вычисление позиции на рамке (исправлено для предотвращения перекрытия)
            int bx, by;
            if (i < HUD_SIZE - 1) {
                // Top edge
                // Верхний край
                bx = x + i;
                by = y;
            } else if (i < (HUD_SIZE - 1) * 2) {
                // Right edge
                // Правый край
                bx = x + HUD_SIZE - 1;
                by = y + (i - (HUD_SIZE - 1));
            } else if (i < (HUD_SIZE - 1) * 3) {
                // Bottom edge
                // Нижний край
                bx = x + HUD_SIZE - 1 - (i - (HUD_SIZE - 1) * 2);
                by = y + HUD_SIZE - 1;
            } else {
                // Left edge
                // Левый край
                bx = x;
                by = y + HUD_SIZE - 1 - (i - (HUD_SIZE - 1) * 3);
            }
            
            // Draw 1px border pixel
            // Рисование пикселя рамки 1px
            context.fill(bx, by, bx + BORDER_WIDTH, by + BORDER_WIDTH, color);
        }
    }
    
    private int getRainbowColor(float t) {
        // Smooth rainbow transition: Red -> Orange -> Yellow -> Green -> Cyan -> Blue -> Purple -> Red
        // Плавный радужный переход: Красный -> Оранжевый -> Желтый -> Зеленый -> Голубой -> Синий -> Фиолетовый -> Красный
        int red, green, blue;
        
        if (t < 0.166f) {
            // Red to Orange
            // От красного к оранжевому
            red = 255;
            green = (int) (t / 0.166f * 165);
            blue = 0;
        } else if (t < 0.333f) {
            // Orange to Yellow
            // От оранжевого к желтому
            red = 255;
            green = (int) (165 + (t - 0.166f) / 0.167f * 90);
            blue = 0;
        } else if (t < 0.5f) {
            // Yellow to Green
            // От желтого к зеленому
            red = (int) (255 - (t - 0.333f) / 0.167f * 255);
            green = 255;
            blue = 0;
        } else if (t < 0.666f) {
            // Green to Cyan
            // От зеленого к голубому
            red = 0;
            green = 255;
            blue = (int) ((t - 0.5f) / 0.166f * 255);
        } else if (t < 0.833f) {
            // Cyan to Blue
            // От голубого к синему
            red = 0;
            green = (int) (255 - (t - 0.666f) / 0.167f * 255);
            blue = 255;
        } else {
            // Blue to Purple to Red
            // От синего к фиолетовому и красному
            red = (int) ((t - 0.833f) / 0.167f * 255);
            green = 0;
            blue = (int) (255 - (t - 0.833f) / 0.167f * 128);
        }
        
        return 0xFF000000 | (red << 16) | (green << 8) | blue;
    }
}
