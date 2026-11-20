package com.randomrun.ui.overlay;

import com.randomrun.ui.util.InterfaceStyle;
import com.randomrun.ui.util.Ui;
import com.randomrun.ui.util.Previews.ItemPreviewRenderer;

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
    
    private static final int COLOR_TEXT = 0xFFFFFFFF;
    
    private static class Notification {
        String title;
        String description;
        ItemStack icon;
        long startTime;
        boolean error;
        
        Notification(String title, String description, String iconId) {
            this.title = title;
            this.description = description;
            try {
                this.icon = iconId == null ? ItemStack.EMPTY : com.randomrun.ui.util.Previews.PreviewItems.stack(BuiltInRegistries.ITEM.getValue(Identifier.parse(iconId)));
            } catch (Exception e) {
                this.icon = ItemStack.EMPTY;
            }
            this.startTime = System.currentTimeMillis();
        }
    }
    
    private static final java.util.List<Notification> activeNotifications = new java.util.ArrayList<>();
    
    public static void show(String title) {
        show(title, "", null);
    }

    public static void show(String title, String description, String iconId) {
        activeNotifications.add(new Notification(title, description, iconId));
    }

    public static void showError(String title, String description) {
        activeNotifications.removeIf(n -> n.error && n.title.equals(title));
        Notification notification = new Notification(title, description, null);
        notification.error = true;
        activeNotifications.add(notification);
    }
    
    public static void renderOverlay(GuiGraphicsExtractor context) {
        if (activeNotifications.isEmpty()) return;
        
        Minecraft client = Minecraft.getInstance();
        long now = System.currentTimeMillis();
        
        activeNotifications.removeIf(n -> now - n.startTime > DISPLAY_DURATION);
        if (activeNotifications.isEmpty()) return;
        
        int y = TOP_OFFSET;
        for (Notification notification : activeNotifications) y += INSTANCE.renderNotification(context, client, notification, y, now) + NOTIFICATION_SPACING;
    }
    
    private static final CustomNotification INSTANCE = new CustomNotification();
    
    @Override
    public void extractRenderState(GuiGraphicsExtractor context, DeltaTracker tickCounter) {
        if (activeNotifications.isEmpty()) return;
        
        Minecraft client = Minecraft.getInstance();
        
        long now = System.currentTimeMillis();
        
        activeNotifications.removeIf(n -> now - n.startTime > DISPLAY_DURATION);
        
        if (activeNotifications.isEmpty()) return;
        
        int y = TOP_OFFSET;
        for (Notification notification : activeNotifications) y += renderNotification(context, client, notification, y, now) + NOTIFICATION_SPACING;
    }
    
    private int renderNotification(GuiGraphicsExtractor context, Minecraft client, Notification notification, int y, long now) {
        long elapsed = now - notification.startTime;
        float slideAnimation = calculateSlideAnimation(elapsed);
        
        int screenWidth = client.getWindow().getGuiScaledWidth();
        int targetX = screenWidth - NOTIFICATION_WIDTH - SCREEN_MARGIN;
        int startX = screenWidth + SCREEN_MARGIN;
        
        int x = (int)(startX + (targetX - startX) * easeOutCubic(slideAnimation));
        com.randomrun.ui.util.Theme theme = com.randomrun.ui.util.Theme.current();
        boolean compact = notification.description == null || notification.description.isEmpty();
        int textX = notification.icon != null && !notification.icon.isEmpty() ? x + 30 : x + 8;
        int maxWidth = NOTIFICATION_WIDTH - (textX - x) - 8;
        var lines = compact ? java.util.List.<net.minecraft.util.FormattedCharSequence>of()
            : client.font.split(Component.literal(notification.description), maxWidth);
        if (lines.size() > 4) lines = lines.subList(0, 4);
        int height = compact ? 22 : Math.max(NOTIFICATION_HEIGHT, 24 + lines.size() * 10);
        if (InterfaceStyle.isClassic()) {
            Ui.bevel(context, x, y, x + NOTIFICATION_WIDTH, y + height, theme.panel, 1f, false);
            int accent = notification.error ? Ui.DANGER : theme.accent;
            context.fill(x + 2, y + 2, x + 4, y + height - 2, Ui.argb(accent, 1f));
        } else {
            drawRoundedBorder(context, x, y, NOTIFICATION_WIDTH, height, notification.error ? 0xFFDC2626 : 0xFF000000 | theme.buttonHover);
            drawRoundedBackground(context, x, y, NOTIFICATION_WIDTH, height, 0xF0000000 | theme.panel);
        }
        
        if (notification.icon != null && !notification.icon.isEmpty()) {
            int iconTop = y + (height - 20) / 2;
            if (InterfaceStyle.isClassic()) Ui.slot(context, x + 6, iconTop, x + 26, iconTop + 20, 1f);
            ItemPreviewRenderer.extractInSlot(context, notification.icon, x + 6, iconTop, x + 26, iconTop + 20, 3);
        }
        
        if (compact) {
            com.randomrun.ui.util.GuiText.text(context, client.font, com.randomrun.ui.util.Ui.ellipsize(client.font, notification.title, NOTIFICATION_WIDTH - (textX - x) - 6), textX, y + 7, COLOR_TEXT, false);
            return height;
        }
        com.randomrun.ui.util.GuiText.text(context, client.font, Ui.ellipsize(client.font, notification.title, maxWidth), textX, y + 7, notification.error ? 0xFFF87171 : 0xFF000000 | theme.accent, false);
        int lineY = y + 20;
        for (var line : lines) {
            com.randomrun.ui.util.GuiText.text(context, client.font, line, textX, lineY, COLOR_TEXT, false);
            lineY += 10;
        }
        return height;
    }
    
    private float calculateSlideAnimation(long elapsed) {
        long duration = InterfaceStyle.isClassic() ? 200 : ANIMATION_DURATION;
        if (elapsed < duration) {
            return elapsed / (float) duration;
        } else if (elapsed > DISPLAY_DURATION - duration) {
            return (DISPLAY_DURATION - elapsed) / (float) duration;
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
