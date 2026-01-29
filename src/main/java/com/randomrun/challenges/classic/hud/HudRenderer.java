/*
 * Copyright (c) 2026 Stanislav Kholod.
 * All rights reserved.
 */
package com.randomrun.challenges.classic.hud;

import com.randomrun.main.RandomRunMod;
import com.randomrun.main.config.ModConfig;
import com.randomrun.main.data.RunDataManager;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.util.InputUtil;
import net.minecraft.item.ItemStack;

public class HudRenderer implements HudRenderCallback {
    
    private static final int HUD_SIZE = 48; // Square HUD (уменьшен)
    private static final int BORDER_WIDTH = 2; // Увеличена толщина обводки
    private float borderAnimation = 0f;
    private float blinkAnimation = 0f;
    
    @Override
    public void onHudRender(DrawContext context, float tickDelta) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || client.world == null) return;
        
        RunDataManager runManager = RandomRunMod.getInstance().getRunDataManager();
        RunDataManager.RunStatus status = runManager.getStatus();
        
        // Only render if run is active AND target is ITEM
        if (status == RunDataManager.RunStatus.INACTIVE || runManager.getTargetType() != RunDataManager.TargetType.ITEM) return;
        
        ModConfig config = RandomRunMod.getInstance().getConfig();
        
        // Calculate position
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
        borderAnimation += tickDelta * 0.02f;
        if (borderAnimation > 1.0f) borderAnimation -= 1.0f;
        
        blinkAnimation += tickDelta * 0.05f;
        if (blinkAnimation > 1.0f) blinkAnimation -= 1.0f;
        
        // Render HUD background (semi-transparent black)
        context.fill(x, y, x + HUD_SIZE, y + HUD_SIZE, 0xC0000000);
        
        // Render animated border (2px)
        renderAnimatedBorder(context, x, y, status);
        
        // Render target item (centered)
        if (runManager.getTargetItem() != null) {
            ItemStack stack = new ItemStack(runManager.getTargetItem());
            context.getMatrices().push();
            int itemX = x + HUD_SIZE / 2;
            int itemY = y + HUD_SIZE / 2 - 4; // Немного ниже (было -6)
            context.getMatrices().translate(itemX, itemY, 0);
            context.getMatrices().scale(2.0f, 2.0f, 1.0f); // Уменьшен обратно до 2.0
            context.drawItem(stack, -8, -8);
            context.getMatrices().pop();
        }
        
        // Render timer or '/go' text (ниже и меньше)
        if (status == RunDataManager.RunStatus.FROZEN) {
            // Show blinking '/go' text (yellow-orange)
            float blinkValue = (float) Math.sin(blinkAnimation * Math.PI * 2) * 0.5f + 0.5f;
            int red = 255;
            int green = (int) (165 + blinkValue * 90); // 165-255 (orange to yellow)
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
            int goY = (int)((y + HUD_SIZE - 12) / 0.8f);
            context.drawTextWithShadow(client.textRenderer, goText, goX, goY, blinkColor);
            context.getMatrices().pop();
        } else {
            // Render time (running or completed/failed)
            long timeLimit = runManager.getTimeLimit();
            String timeStr;
            int timeColor = 0xFFFFFF; // White default
            
            if (timeLimit > 0) {
                long remainingTime = runManager.getRemainingTime();
                timeStr = RunDataManager.formatTime(remainingTime);
                if (remainingTime < 10000) { // < 10 seconds
                    timeColor = 0xFF5555; // Red
                }
            } else {
                long currentTime = runManager.getCurrentTime();
                timeStr = RunDataManager.formatTime(currentTime);
            }
            
            // Draw time centered below item
            context.getMatrices().push();
            context.getMatrices().scale(0.7f, 0.7f, 1.0f);
            int scaledTimeWidth = (int)(client.textRenderer.getWidth(timeStr) * 0.7f);
            int timeX = (int)((x + (HUD_SIZE - scaledTimeWidth) / 2) / 0.7f);
            int timeY = (int)((y + HUD_SIZE - 10) / 0.7f);
            context.drawTextWithShadow(client.textRenderer, "§e" + timeStr, timeX, timeY, timeColor);
            context.getMatrices().pop();
        }
    }
    
    private void renderAnimatedBorder(DrawContext context, int x, int y, RunDataManager.RunStatus status) {
        int perimeter = (HUD_SIZE - 1) * 4;
        
        for (int i = 0; i < perimeter; i += 1) {
            float progress = (borderAnimation + (float) i / perimeter) % 1.0f;
            
            // Color: rainbow if FROZEN, purple if RUNNING/COMPLETED/FAILED
            int color;
            if (status == RunDataManager.RunStatus.FROZEN) {
                color = getRainbowColor(progress);
            } else {
                color = 0xFF6930c3; // Purple
            }
            
            // Calculate position on border (fixed to prevent overlap)
            int bx, by;
            if (i < HUD_SIZE - 1) {
                // Top edge
                bx = x + i;
                by = y;
            } else if (i < (HUD_SIZE - 1) * 2) {
                // Right edge
                bx = x + HUD_SIZE - 1;
                by = y + (i - (HUD_SIZE - 1));
            } else if (i < (HUD_SIZE - 1) * 3) {
                // Bottom edge
                bx = x + HUD_SIZE - 1 - (i - (HUD_SIZE - 1) * 2);
                by = y + HUD_SIZE - 1;
            } else {
                // Left edge
                bx = x;
                by = y + HUD_SIZE - 1 - (i - (HUD_SIZE - 1) * 3);
            }
            
            // Draw 1px border pixel
            context.fill(bx, by, bx + BORDER_WIDTH, by + BORDER_WIDTH, color);
        }
    }
    
    private int getRainbowColor(float t) {
        // Smooth rainbow transition: Red -> Orange -> Yellow -> Green -> Cyan -> Blue -> Purple -> Red
        int red, green, blue;
        
        if (t < 0.166f) {
            // Red to Orange
            red = 255;
            green = (int) (t / 0.166f * 165);
            blue = 0;
        } else if (t < 0.333f) {
            // Orange to Yellow
            red = 255;
            green = (int) (165 + (t - 0.166f) / 0.167f * 90);
            blue = 0;
        } else if (t < 0.5f) {
            // Yellow to Green
            red = (int) (255 - (t - 0.333f) / 0.167f * 255);
            green = 255;
            blue = 0;
        } else if (t < 0.666f) {
            // Green to Cyan
            red = 0;
            green = 255;
            blue = (int) ((t - 0.5f) / 0.166f * 255);
        } else if (t < 0.833f) {
            // Cyan to Blue
            red = 0;
            green = (int) (255 - (t - 0.666f) / 0.167f * 255);
            blue = 255;
        } else {
            // Blue to Purple to Red
            red = (int) ((t - 0.833f) / 0.167f * 255);
            green = 0;
            blue = (int) (255 - (t - 0.833f) / 0.167f * 128);
        }
        
        return 0xFF000000 | (red << 16) | (green << 8) | blue;
    }
}
