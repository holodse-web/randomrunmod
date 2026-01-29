/*
 * Copyright (c) 2026 Stanislav Kholod.
 * All rights reserved.
 */
package com.randomrun.challenges.advancement.hud;

import com.randomrun.main.RandomRunMod;
import com.randomrun.main.config.ModConfig;
import com.randomrun.main.data.RunDataManager;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.util.InputUtil;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;

public class AchievementHudRenderer implements HudRenderCallback {
    
    private float slideAnimation = 0f;
    private boolean wasActive = false;
    
    @Override
    public void onHudRender(DrawContext context, float tickDelta) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || client.world == null) return;
        
        RunDataManager runManager = RandomRunMod.getInstance().getRunDataManager();
        RunDataManager.RunStatus status = runManager.getStatus();
        
        // Only render if run is active AND target is ADVANCEMENT
        boolean isActive = status != RunDataManager.RunStatus.INACTIVE && 
                          runManager.getTargetType() == RunDataManager.TargetType.ADVANCEMENT;
        
        if (!isActive) {
            wasActive = false;
            slideAnimation = 0f;
            return;
        }
        
        // Slide-in animation
        if (!wasActive) {
            slideAnimation = 0f;
            wasActive = true;
        }
        
        if (slideAnimation < 1.0f) {
            slideAnimation = Math.min(1.0f, slideAnimation + tickDelta * 0.05f);
        }
        
        renderToastHud(context, client, runManager, status);
    }
    
    private void renderToastHud(DrawContext context, MinecraftClient client, RunDataManager runManager, RunDataManager.RunStatus status) {
        int width = 160;
        int height = 32;
        
        // Calculate position with slide animation - TOP LEFT
        int targetX = 5;
        int startX = -width;
        int x = (int)(startX + (targetX - startX) * easeOutCubic(slideAnimation));
        int y = 10;
        
        // Draw rounded corners effect
        // Outer border (light gray)
        context.fill(x + 2, y, x + width - 2, y + 1, 0xFFC6C6C6);
        context.fill(x + 2, y + height - 1, x + width - 2, y + height, 0xFFC6C6C6);
        context.fill(x, y + 2, x + 1, y + height - 2, 0xFFC6C6C6);
        context.fill(x + width - 1, y + 2, x + width, y + height - 2, 0xFFC6C6C6);
        context.fill(x + 1, y + 1, x + 2, y + 2, 0xFFC6C6C6);
        context.fill(x + width - 2, y + 1, x + width - 1, y + 2, 0xFFC6C6C6);
        context.fill(x + 1, y + height - 2, x + 2, y + height - 1, 0xFFC6C6C6);
        context.fill(x + width - 2, y + height - 2, x + width - 1, y + height - 1, 0xFFC6C6C6);
        
        // Main background (dark gray)
        context.fill(x + 1, y + 2, x + width - 1, y + height - 2, 0xFF3F3F3F);
        context.fill(x + 2, y + 1, x + width - 2, y + height - 1, 0xFF3F3F3F);
        
        // Get achievement data from cached info in RunDataManager
        ItemStack icon = new ItemStack(Items.BOOK);
        String achievementName = "Неизвестное достижение";
        
        if (runManager.getTargetAdvancementId() != null) {
            // Use cached display info that was saved when challenge started
            String cachedName = runManager.getTargetAdvancementName();
            Item cachedIcon = runManager.getTargetAdvancementIcon();
            
            if (cachedName != null && cachedIcon != null) {
                achievementName = cachedName;
                icon = new ItemStack(cachedIcon);
            } else {
                // Fallback: try to get from advancement manager (if available)
                if (client.getNetworkHandler() != null) {
                    var manager = client.getNetworkHandler().getAdvancementHandler();
                    var entry = manager.get(runManager.getTargetAdvancementId());
                    
                    if (entry != null && entry.value().display().isPresent()) {
                        var display = entry.value().display().get();
                        icon = display.getIcon().copy();
                        achievementName = display.getTitle().getString();
                    }
                }
            }
        }
        
        // Draw icon
        context.getMatrices().push();
        context.getMatrices().translate(x + 18, y + 16, 0); // Moved slightly right (was 16)
        context.getMatrices().scale(2.0f, 2.0f, 1f); // Increased from 1.5f
        context.getMatrices().translate(-8, -8, 0);
        context.drawItem(icon, 0, 0);
        context.getMatrices().pop();
        
        // Draw achievement name in yellow
        String displayName = achievementName;
        int maxWidth = width - 40;
        if (client.textRenderer.getWidth(displayName) > maxWidth) {
            displayName = client.textRenderer.trimToWidth(displayName, maxWidth - 10) + "...";
        }
        context.drawText(client.textRenderer, displayName, x + 40, y + 7, 0xFFFFFF00, false); // Moved text to right (was 35)
        
        // Draw timer below in white
        String timeStr;
        
        if (status == RunDataManager.RunStatus.FROZEN) {
            ModConfig config = RandomRunMod.getInstance().getConfig();
            if (config.getStartMethod() == ModConfig.StartMethod.KEYBIND) {
                String keyName = InputUtil.fromKeyCode(config.getStartKey(), 0).getLocalizedText().getString();
                timeStr = keyName.toUpperCase();
            } else {
                timeStr = "/GO";
            }
        } else {
            long timeLimit = runManager.getTimeLimit();
            if (timeLimit > 0) {
                long remainingTime = runManager.getRemainingTime();
                timeStr = RunDataManager.formatTime(remainingTime);
            } else {
                long currentTime = runManager.getCurrentTime();
                timeStr = RunDataManager.formatTime(currentTime);
            }
        }
        context.drawText(client.textRenderer, timeStr, x + 35, y + 18, 0xFFFFFFFF, false);
    }
    
    private float easeOutCubic(float t) {
        return 1 - (float)Math.pow(1 - t, 3);
    }
}