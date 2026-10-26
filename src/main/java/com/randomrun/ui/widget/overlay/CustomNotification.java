package com.randomrun.ui.widget.overlay;

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;

public class CustomNotification implements HudElement {
    private static final int NOTIFICATION_WIDTH = 220;
    private static final int NOTIFICATION_HEIGHT = 38;
    private static final int NOTIFICATION_SPACING = 5;
    private static final int SCREEN_MARGIN = 5;
    private static final int TOP_OFFSET = 40;
    
    private static final long DISPLAY_DURATION = 4000;
    private static final long ANIMATION_DURATION = 500;
    
    private static final int COLOR_BORDER = 0xFF8B5CF6;
    private static final int COLOR_BACKGROUND = 0xFF2E1065;
    private static final int COLOR_TITLE = 0xFFA78BFA;
    private static final int COLOR_TEXT = 0xFFFFFFFF;
    
    private static class Notification {
        String title;
        String description;
        ItemStack icon;
        long startTime;
        
        Notification(String title, String description, String iconId) {
            this.title = title;
            this.description = description;
            try {
                this.icon = com.randomrun.ui.util.PreviewItems.stack(BuiltInRegistries.ITEM.getValue(Identifier.parse(iconId)));
            } catch (Exception e) {
                this.icon = ItemStack.EMPTY;
            }
            this.startTime = System.currentTimeMillis();
        }
    }
    
    private static final java.util.List<Notification> activeNotifications = new java.util.ArrayList<>();
    
    public static void show(String title, String description, String iconId) {
        activeNotifications.add(new Notification(title, description, iconId));
    }
    
    public static void renderOverlay(GuiGraphicsExtractor context) {
        if (activeNotifications.isEmpty()) return;
        
        Minecraft client = Minecraft.getInstance();
        long now = System.currentTimeMillis();
        
        activeNotifications.removeIf(n -> now - n.startTime > DISPLAY_DURATION);
        if (activeNotifications.isEmpty()) return;
        
        CustomNotification renderer = INSTANCE;
        for (int i = 0; i < activeNotifications.size(); i++) {
            renderer.renderNotification(context, client, activeNotifications.get(i), i, now);
        }
    }
    
    private static final CustomNotification INSTANCE = new CustomNotification();
    
    @Override
    public void extractRenderState(GuiGraphicsExtractor context, DeltaTracker tickCounter) {
        if (activeNotifications.isEmpty()) return;
        
        Minecraft client = Minecraft.getInstance();
        
        long now = System.currentTimeMillis();
        
        activeNotifications.removeIf(n -> now - n.startTime > DISPLAY_DURATION);
        
        if (activeNotifications.isEmpty()) return;
        
        for (int i = 0; i < activeNotifications.size(); i++) {
            renderNotification(context, client, activeNotifications.get(i), i, now);
        }
    }
    
    private void renderNotification(GuiGraphicsExtractor context, Minecraft client, Notification notification, int index, long now) {
        long elapsed = now - notification.startTime;
        float slideAnimation = calculateSlideAnimation(elapsed);
        
        int screenWidth = client.getWindow().getGuiScaledWidth();
        int targetX = screenWidth - NOTIFICATION_WIDTH - SCREEN_MARGIN;
        int startX = screenWidth + SCREEN_MARGIN;
        
        int x = (int)(startX + (targetX - startX) * easeOutCubic(slideAnimation));
        int y = TOP_OFFSET + (index * (NOTIFICATION_HEIGHT + NOTIFICATION_SPACING));
        
        drawRoundedBorder(context, x, y, NOTIFICATION_WIDTH, NOTIFICATION_HEIGHT, COLOR_BORDER);
        drawRoundedBackground(context, x, y, NOTIFICATION_WIDTH, NOTIFICATION_HEIGHT, COLOR_BACKGROUND);
        
        if (notification.icon != null && !notification.icon.isEmpty()) {
            context.item(notification.icon, x + 8, y + 8);
        }
        
        com.randomrun.ui.util.GuiText.text(context, client.font, Component.literal(notification.title), x + 30, y + 6, COLOR_TITLE, false);
        
        String desc = notification.description;
        int maxWidth = NOTIFICATION_WIDTH - 35;
        if (client.font.width(desc) > maxWidth) {
            desc = client.font.plainSubstrByWidth(desc, maxWidth - 10) + "...";
        }
        com.randomrun.ui.util.GuiText.text(context, client.font, desc, x + 30, y + 16, COLOR_TEXT, false);
    }
    
    private float calculateSlideAnimation(long elapsed) {
        if (elapsed < ANIMATION_DURATION) {
            return elapsed / (float) ANIMATION_DURATION;
        } else if (elapsed > DISPLAY_DURATION - ANIMATION_DURATION) {
            return (DISPLAY_DURATION - elapsed) / (float) ANIMATION_DURATION;
        }
        return 1f;
    }
    
    private void drawRoundedBorder(GuiGraphicsExtractor context, int x, int y, int width, int height, int color) {
        context.fill(x + 2, y, x + width - 2, y + 1, color);
        context.fill(x + 2, y + height - 1, x + width - 2, y + height, color);
        context.fill(x, y + 2, x + 1, y + height - 2, color);
        context.fill(x + width - 1, y + 2, x + width, y + height - 2, color);
        context.fill(x + 1, y + 1, x + 2, y + 2, color);
        context.fill(x + width - 2, y + 1, x + width - 1, y + 2, color);
        context.fill(x + 1, y + height - 2, x + 2, y + height - 1, color);
        context.fill(x + width - 2, y + height - 2, x + width - 1, y + height - 1, color);
    }
    
    private void drawRoundedBackground(GuiGraphicsExtractor context, int x, int y, int width, int height, int color) {
        context.fill(x + 1, y + 2, x + width - 1, y + height - 2, color);
        context.fill(x + 2, y + 1, x + width - 2, y + height - 1, color);
    }
    
    private float easeOutCubic(float t) {
        return 1 - (float)Math.pow(1 - t, 3);
    }
}
