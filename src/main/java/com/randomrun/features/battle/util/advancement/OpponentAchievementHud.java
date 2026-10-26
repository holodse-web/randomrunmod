package com.randomrun.features.battle.util.advancement;

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ItemStack;
import com.randomrun.infrastructure.util.LanguageManager;
import com.randomrun.ui.util.GuiQuadBatch;
import net.minecraft.network.chat.Component;

public class OpponentAchievementHud implements HudElement {
    private static final int BORDER_COLOR = 0xFF8B5CF6;
    private static final int BG_COLOR = 0xFF2E1065;
    private static final int TEXT_COLOR_HEADER = 0xFFA78BFA;
    private static final int TEXT_COLOR_TITLE = 0xFFFFFFFF;
    
    private static final int NOTIFICATION_WIDTH = 160;
    private static final int NOTIFICATION_HEIGHT = 32;
    private static final int NOTIFICATION_SPACING = 5;
    private static final long DISPLAY_DURATION = 4000;
    private static final long ANIMATION_DURATION = 500;
    private static final int MAX_NOTIFICATIONS = 5;
    private static final GuiQuadBatch BACKGROUND = createBackground();
    
    private static class Notification {
        String title;
        String playerName;
        ItemStack icon;
        long startTime;
        Font textFont;
        String language;
        Component header;
        String clippedTitle;
        
        Notification(String playerName, String title, String iconId) {
            this.playerName = playerName;
            this.title = title;
            try {
                this.icon = com.randomrun.ui.util.PreviewItems.stack(BuiltInRegistries.ITEM.getValue(Identifier.parse(iconId)));
            } catch (Exception e) {
                this.icon = ItemStack.EMPTY;
            }
            this.startTime = System.currentTimeMillis();
        }
    }
    
    private static final java.util.List<Notification> activeNotifications = new java.util.ArrayList<>();
    
    public static void show(String playerName, String title, String iconId) {
        if (activeNotifications.size() >= MAX_NOTIFICATIONS) {
            activeNotifications.remove(0);
        }
        
        activeNotifications.add(new Notification(playerName, title, iconId));
        
        Minecraft client = Minecraft.getInstance();
        if (client.player != null) {
            client.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.EXPERIENCE_ORB_PICKUP, 1.2f, 0.6f));
        }
    }
    
    @Override
    public void extractRenderState(GuiGraphicsExtractor context, DeltaTracker tickCounter) {
        if (activeNotifications.isEmpty()) return;
        
        Minecraft client = Minecraft.getInstance();
        if (client.player == null || client.level == null) return;
        
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
        int targetX = screenWidth - NOTIFICATION_WIDTH - 5;
        int startX = screenWidth + 5;
        
        int x = (int)(startX + (targetX - startX) * easeOutCubic(slideAnimation));
        int y = 40 + (index * (NOTIFICATION_HEIGHT + NOTIFICATION_SPACING));
        
        extractBackground(context, x, y);
        renderIcon(context, notification, x, y);
        renderText(context, client, notification, x, y);
    }
    
    private float calculateSlideAnimation(long elapsed) {
        if (elapsed < ANIMATION_DURATION) {
            return elapsed / (float) ANIMATION_DURATION;
        } else if (elapsed > DISPLAY_DURATION - ANIMATION_DURATION) {
            return (DISPLAY_DURATION - elapsed) / (float) ANIMATION_DURATION;
        }
        return 1f;
    }
    
    private static GuiQuadBatch createBackground() {
        GuiQuadBatch.Builder batch = new GuiQuadBatch.Builder(10);
        batch.fill(2, 0, NOTIFICATION_WIDTH - 2, 1, BORDER_COLOR);
        batch.fill(2, NOTIFICATION_HEIGHT - 1, NOTIFICATION_WIDTH - 2, NOTIFICATION_HEIGHT, BORDER_COLOR);
        batch.fill(0, 2, 1, NOTIFICATION_HEIGHT - 2, BORDER_COLOR);
        batch.fill(NOTIFICATION_WIDTH - 1, 2, NOTIFICATION_WIDTH, NOTIFICATION_HEIGHT - 2, BORDER_COLOR);
        batch.fill(1, 1, 2, 2, BORDER_COLOR);
        batch.fill(NOTIFICATION_WIDTH - 2, 1, NOTIFICATION_WIDTH - 1, 2, BORDER_COLOR);
        batch.fill(1, NOTIFICATION_HEIGHT - 2, 2, NOTIFICATION_HEIGHT - 1, BORDER_COLOR);
        batch.fill(NOTIFICATION_WIDTH - 2, NOTIFICATION_HEIGHT - 2, NOTIFICATION_WIDTH - 1, NOTIFICATION_HEIGHT - 1, BORDER_COLOR);
        batch.fill(1, 2, NOTIFICATION_WIDTH - 1, NOTIFICATION_HEIGHT - 2, BG_COLOR);
        batch.fill(2, 1, NOTIFICATION_WIDTH - 2, NOTIFICATION_HEIGHT - 1, BG_COLOR);
        return batch.build();
    }

    private void extractBackground(GuiGraphicsExtractor context, int x, int y) {
        context.pose().pushMatrix();
        context.pose().translate(x, y);
        BACKGROUND.extract(context);
        context.pose().popMatrix();
    }
    
    private void renderIcon(GuiGraphicsExtractor context, Notification notification, int x, int y) {
        if (notification.icon != null && !notification.icon.isEmpty()) {
            context.item(notification.icon, x + 8, y + 8);
        }
    }
    
    private void renderText(GuiGraphicsExtractor context, Minecraft client, Notification notification, int x, int y) {
        String language = LanguageManager.getCurrentLanguage();
        if (notification.textFont != client.font || !language.equals(notification.language)) {
            notification.textFont = client.font;
            notification.language = language;
            notification.header = LanguageManager.text("randomrun.battle.opponent_achievement", notification.playerName);
            int maxWidth = NOTIFICATION_WIDTH - 35;
            notification.clippedTitle = client.font.width(notification.title) > maxWidth
                ? client.font.plainSubstrByWidth(notification.title, maxWidth - 10) + "..." : notification.title;
        }
        com.randomrun.ui.util.GuiText.text(context, client.font, 
            notification.header,
            x + 30, y + 6, TEXT_COLOR_HEADER, false);
        
        com.randomrun.ui.util.GuiText.text(context, client.font, notification.clippedTitle, x + 30, y + 16, TEXT_COLOR_TITLE, false);
    }
    
    private float easeOutCubic(float t) {
        return 1 - (float)Math.pow(1 - t, 3);
    }
}
