/*
 * Copyright (c) 2026 Stanislav Kholod.
 * All rights reserved.
 */
package com.randomrun.challenges.advancement.hud;

import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.item.ItemStack;
import net.minecraft.registry.Registries;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.util.ArrayDeque;
import java.util.Queue;

public class OpponentAchievementHud implements HudRenderCallback {
    
    private static class Notification {
        String title;
        ItemStack icon;
        long startTime;
        
        Notification(String title, String iconId) {
            this.title = title;
            try {
                this.icon = new ItemStack(Registries.ITEM.get(new Identifier(iconId)));
            } catch (Exception e) {
                this.icon = ItemStack.EMPTY;
            }
            this.startTime = System.currentTimeMillis();
        }
    }
    
    private static final Queue<Notification> notificationQueue = new ArrayDeque<>();
    private static Notification currentNotification = null;
    private static float slideAnimation = 0f;
    private static final long DISPLAY_DURATION = 4000;
    
    public static void show(String title, String iconId) {
        notificationQueue.add(new Notification(title, iconId));
    }
    
    @Override
    public void onHudRender(DrawContext context, float tickDelta) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || client.world == null) return;
        
        long now = System.currentTimeMillis();
        
        if (currentNotification == null) {
            if (!notificationQueue.isEmpty()) {
                currentNotification = notificationQueue.poll();
                currentNotification.startTime = now;
                slideAnimation = 0f;
            } else {
                return;
            }
        }
        
        long elapsed = now - currentNotification.startTime;
        
        if (elapsed > DISPLAY_DURATION) {
            currentNotification = null;
            return;
        }
        
        // Animation logic
        // 0-500ms: Slide in
        // 500ms - (Duration-500ms): Stay
        // (Duration-500ms) - Duration: Slide out
        
        if (elapsed < 500) {
            slideAnimation = elapsed / 500f;
        } else if (elapsed > DISPLAY_DURATION - 500) {
            slideAnimation = (DISPLAY_DURATION - elapsed) / 500f;
        } else {
            slideAnimation = 1f;
        }
        
        renderNotification(context, client);
    }
    
    private void renderNotification(DrawContext context, MinecraftClient client) {
        int width = 160;
        int height = 32;
        
        // Position: Top Right, slightly lower than own achievements (which are Top Left usually, but user said Top Right?)
        // Wait, AchievementHudRenderer uses Top Left.
        // User said: "Show it top right but slightly lower so it doesn't overlap own achievements"
        // If own achievements are Top Left, then Top Right is free.
        // If vanilla achievements are Top Right, we should be careful.
        // Vanilla toast is Top Right.
        // Let's put it Top Right, but offset Y by typical toast height (32) + some padding.
        
        int screenWidth = client.getWindow().getScaledWidth();
        int targetX = screenWidth - width - 5;
        int startX = screenWidth + 5;
        
        int x = (int)(startX + (targetX - startX) * easeOutCubic(slideAnimation));
        int y = 40; // Lower than vanilla toasts (usually at y=0)
        
        // Draw background (Purple theme for opponent)
        // Outer border
        int borderColor = 0xFF8B5CF6; // Light purple
        context.fill(x + 2, y, x + width - 2, y + 1, borderColor);
        context.fill(x + 2, y + height - 1, x + width - 2, y + height, borderColor);
        context.fill(x, y + 2, x + 1, y + height - 2, borderColor);
        context.fill(x + width - 1, y + 2, x + width, y + height - 2, borderColor);
        context.fill(x + 1, y + 1, x + 2, y + 2, borderColor);
        context.fill(x + width - 2, y + 1, x + width - 1, y + 2, borderColor);
        context.fill(x + 1, y + height - 2, x + 2, y + height - 1, borderColor);
        context.fill(x + width - 2, y + height - 2, x + width - 1, y + height - 1, borderColor);
        
        // Inner background
        int bgColor = 0xFF2E1065; // Dark purple
        context.fill(x + 1, y + 2, x + width - 1, y + height - 2, bgColor);
        context.fill(x + 2, y + 1, x + width - 2, y + height - 1, bgColor);
        
        // Icon
        if (currentNotification.icon != null && !currentNotification.icon.isEmpty()) {
            context.drawItem(currentNotification.icon, x + 8, y + 8);
        }
        
        // Text
        context.drawText(client.textRenderer, Text.literal("Соперник получил:"), x + 30, y + 6, 0xFFA78BFA, false);
        
        String title = currentNotification.title;
        int maxWidth = width - 35;
        if (client.textRenderer.getWidth(title) > maxWidth) {
            title = client.textRenderer.trimToWidth(title, maxWidth - 10) + "...";
        }
        context.drawText(client.textRenderer, title, x + 30, y + 16, 0xFFFFFFFF, false);
    }
    
    private float easeOutCubic(float t) {
        return 1 - (float)Math.pow(1 - t, 3);
    }
}
