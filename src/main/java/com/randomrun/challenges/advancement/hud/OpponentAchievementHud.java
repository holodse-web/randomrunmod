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

import net.minecraft.client.render.RenderTickCounter;

public class OpponentAchievementHud implements HudRenderCallback {
    
    private static class Notification {
        String title;
        String playerName;
        ItemStack icon;
        long startTime;
        
        Notification(String playerName, String title, String iconId) {
            this.playerName = playerName;
            this.title = title;
            try {
                this.icon = new ItemStack(Registries.ITEM.get(Identifier.of(iconId)));
            } catch (Exception e) {
                this.icon = ItemStack.EMPTY;
            }
            this.startTime = System.currentTimeMillis();
        }
    }
    
    private static final java.util.List<Notification> activeNotifications = new java.util.ArrayList<>();
    private static final long DISPLAY_DURATION = 4000;
    
    public static void show(String playerName, String title, String iconId) {
        activeNotifications.add(new Notification(playerName, title, iconId));
    }
    
    @Override
    public void onHudRender(DrawContext context, RenderTickCounter tickCounter) {
        if (activeNotifications.isEmpty()) return;
        
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || client.world == null) return;
        
        long now = System.currentTimeMillis();
        
        activeNotifications.removeIf(n -> now - n.startTime > DISPLAY_DURATION);
        
        if (activeNotifications.isEmpty()) return;
        
        for (int i = 0; i < activeNotifications.size(); i++) {
            renderNotification(context, client, activeNotifications.get(i), i, now);
        }
    }
    
    private void renderNotification(DrawContext context, MinecraftClient client, Notification notification, int index, long now) {
        int width = 160;
        int height = 32;
        int spacing = 5;
        
        long elapsed = now - notification.startTime;
        float slideAnimation;
        
        if (elapsed < 500) {
            slideAnimation = elapsed / 500f;
        } else if (elapsed > DISPLAY_DURATION - 500) {
            slideAnimation = (DISPLAY_DURATION - elapsed) / 500f;
        } else {
            slideAnimation = 1f;
        }
        
        int screenWidth = client.getWindow().getScaledWidth();
        int targetX = screenWidth - width - 5;
        int startX = screenWidth + 5;
        
        int x = (int)(startX + (targetX - startX) * easeOutCubic(slideAnimation));
        int y = 40 + (index * (height + spacing)); 
        
        // Draw background (Purple theme for opponent)
        // Рисование фона (Фиолетовая тема для противника)
        // Outer border
        // Внешняя рамка
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
        // Внутренний фон
        int bgColor = 0xFF2E1065; // Dark purple
        context.fill(x + 1, y + 2, x + width - 1, y + height - 2, bgColor);
        context.fill(x + 2, y + 1, x + width - 2, y + height - 1, bgColor);
        
        // Icon
        // Иконка
        if (notification.icon != null && !notification.icon.isEmpty()) {
            context.drawItem(notification.icon, x + 8, y + 8);
        }
        
        // Text
        // Текст
        context.drawText(client.textRenderer, Text.literal(notification.playerName + " выполнил:"), x + 30, y + 6, 0xFFA78BFA, false);
        
        String title = notification.title;
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
