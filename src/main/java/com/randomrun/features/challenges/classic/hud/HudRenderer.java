package com.randomrun.features.challenges.classic.hud;

import com.randomrun.infrastructure.data.RunDataManager;
import com.mojang.blaze3d.platform.InputConstants;
import com.randomrun.infrastructure.RandomRunMod;
import com.randomrun.infrastructure.config.ModConfig;
import com.randomrun.ui.util.GuiQuadBatch;
import com.randomrun.ui.util.ItemPreviewRenderer;
import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Item;

public class HudRenderer implements HudElement {
    private static final int HUD_SIZE = 48;
    private static final int BORDER_WIDTH = 1;
    private static final int ITEM_SCALE = 2;
    private static final float TEXT_SCALE = 0.7f;
    private static final float GO_TEXT_SCALE = 0.8f;
    
    private static final long RAINBOW_CYCLE_MS = 2000;
    private static final long BLINK_CYCLE_MS = 800;
    
    private static final int BG_COLOR = 0xC0000000;
    private static final int TIME_WARNING_COLOR = 0xFF5555;
    private static final int TIME_NORMAL_COLOR = 0xFFFFFF;
    private static final int PROGRESS_COLOR_START = 0xFF00FF00;
    private static final int PROGRESS_COLOR_MID = 0xFFFFFF00;
    private static final int PROGRESS_COLOR_END = 0xFFFF0000;
    
    private static final long TIME_WARNING_10S = 10000;
    private static final long TIME_WARNING_5S = 5000;
    private static final long TIME_WARNING_3S = 3000;
    
    private static final int[] RAINBOW_COLORS = new int[360];
    
    static {
        initRainbowColors();
    }
    
    private float borderAnimation = 0f;
    private float blinkAnimation = 0f;
    
    private boolean sound10Played = false;
    private boolean sound5Played = false;
    private boolean sound3Played = false;
    private long lastRemainingTime = -1;
    private Item displayedItem;
    private ItemStack displayedStack = ItemStack.EMPTY;
    private net.minecraft.client.multiplayer.ClientLevel displayedLevel;
    
    private static void initRainbowColors() {
        for (int i = 0; i < 360; i++) {
            float t = i / 360.0f;
            RAINBOW_COLORS[i] = calculateRainbowColor(t);
        }
    }
    
    @Override
    public void extractRenderState(GuiGraphicsExtractor context, DeltaTracker tickCounter) {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null || client.level == null) return;
        if (client.gui.screen() instanceof com.randomrun.features.upgrader.client.UpgraderScreen) return;
        
        com.randomrun.core.session.RunSessionManager sessionManager = com.randomrun.core.session.RunSessionManager.getInstance();
        
        if (!sessionManager.hasActiveRun()) {
            resetSoundFlags();
            return;
        }
        
        com.randomrun.core.session.RunStatus status = sessionManager.getRunStatus();
        ModConfig config = RandomRunMod.getInstance().getConfig();
        
        int currentGuiScale = (int) client.getWindow().getGuiScale();
        float scaleFactor = currentGuiScale > 0 ? 3.0f / currentGuiScale : 1.0f;
        
        context.pose().pushMatrix();
        context.pose().scale(scaleFactor, scaleFactor);
        
        try {
            int scaledWidth = (int)(client.getWindow().getGuiScaledWidth() / scaleFactor);
            int x = calculateHudX(config, scaledWidth);
            int y = calculateHudY(config);
            
            float tickDelta = tickCounter.getGameTimeDeltaPartialTick(true);
            updateAnimations(tickDelta);
            
            ModConfig.HudStyle hudStyle = config.getHudStyle();
            
            switch (hudStyle) {
                case PROFESSIONAL -> renderProfessionalHud(context, client, sessionManager, config, status, x, y, scaledWidth, scaleFactor);
                default -> renderPleasantHud(context, client, sessionManager, config, status, x, y, scaledWidth, scaleFactor);
            }

            if (sessionManager.getCurrentSession().isUpgrader() && !config.hasSeenUpgraderHint()) {
                String hint = com.randomrun.features.upgrader.client.UpgraderClient.keyName().getString()
                    + " · RRM Upgrader";
                int hintWidth = client.font.width(hint) + 8;
                int hintX = Math.max(2, Math.min(x, scaledWidth - hintWidth - 2));
                int hintY = y + (hudStyle == ModConfig.HudStyle.PROFESSIONAL ? 38 : 65);
                context.fill(hintX, hintY, hintX + hintWidth, hintY + 14, 0xCC171A20);
                context.fill(hintX, hintY, hintX + 2, hintY + 14, 0xFFFFCF52);
                com.randomrun.ui.util.GuiText.text(context, client.font, hint, hintX + 4, hintY + 3, 0xFFFFCF52);
            }
            
            if (status != com.randomrun.core.session.RunStatus.FROZEN) {
                checkAndPlayTimerSounds(client, sessionManager);
            } else {
                resetSoundFlags();
            }
        } finally {
            context.pose().popMatrix();
        }
    }
    
    private int calculateHudX(ModConfig config, int scaledWidth) {
        return switch (config.getHudPosition()) {
            case TOP_RIGHT -> scaledWidth - HUD_SIZE - 10;
            case TOP_CENTER -> (scaledWidth - HUD_SIZE) / 2;
            case CUSTOM -> config.getCustomHudX();
            default -> 10;
        };
    }
    
    private int calculateHudY(ModConfig config) {
        return config.getHudPosition() == ModConfig.HudPosition.CUSTOM ? config.getCustomHudY() : 10;
    }
    
    private void renderPleasantHud(GuiGraphicsExtractor context, Minecraft client, 
            com.randomrun.core.session.RunSessionManager sessionManager, ModConfig config,
            com.randomrun.core.session.RunStatus status, int x, int y, int scaledWidth, float scaleFactor) {
        extractBackground(context, x, y);
        renderBorder(context, x, y);
        
        renderTargetItem(context, sessionManager, x, y);
        renderItemTooltip(context, client, sessionManager, x, y, scaleFactor);
        
        if (status == com.randomrun.core.session.RunStatus.FROZEN) {
            renderGoText(context, client, config, x, y);
        } else {
            renderTimer(context, client, sessionManager, x, y);
        }
    }
    
    private void renderProfessionalHud(GuiGraphicsExtractor context, Minecraft client,
            com.randomrun.core.session.RunSessionManager sessionManager, ModConfig config,
            com.randomrun.core.session.RunStatus status, int x, int y, int scaledWidth, float scaleFactor) {
        String targetName = getTargetName(sessionManager);
        if (targetName == null) return;
        
        String rtaStr;
        String igtStr;
        int rtaColor = TIME_NORMAL_COLOR;
        if (status == com.randomrun.core.session.RunStatus.FROZEN) {
            String goText = config.getStartMethod() == ModConfig.StartMethod.KEYBIND
                ? com.mojang.blaze3d.platform.InputConstants.Type.KEYBOARD.getOrCreate(com.randomrun.infrastructure.util.LegacyKeyCodes.toInputKey(config.getStartKey())).getDisplayName().getString().toUpperCase()
                : "/go";
            rtaStr = goText;
            igtStr = goText;
            rtaColor = 0xFFAA00;
        } else {
            rtaStr = RunDataManager.formatTime(sessionManager.getRtaTime());
            igtStr = RunDataManager.formatTime(sessionManager.getElapsedTime());
        }
        
        int nameWidth = client.font.width(targetName);
        int rtaLabelWidth = client.font.width("RTA: " + rtaStr);
        int igtLabelWidth = client.font.width("IGT: " + igtStr);
        int totalWidth = Math.max(nameWidth, Math.max(rtaLabelWidth, igtLabelWidth)) + 6;
        int totalHeight = 35;
        
        context.fill(x, y, x + totalWidth, y + totalHeight, BG_COLOR);
        
        com.randomrun.ui.util.GuiText.text(context, client.font, targetName, x + 3, y + 2, 0xFFFFFF);
        com.randomrun.ui.util.GuiText.text(context, client.font, "§7RTA: §e" + rtaStr, x + 3, y + 13, rtaColor);
        com.randomrun.ui.util.GuiText.text(context, client.font, "§7IGT: §a" + igtStr, x + 3, y + 24, TIME_NORMAL_COLOR);
    }
    
    private void renderItemNameAboveHud(GuiGraphicsExtractor context, Minecraft client,
            com.randomrun.core.session.RunSessionManager sessionManager, ModConfig config, 
            int x, int y, int scaledWidth) {
        String targetName = getTargetName(sessionManager);
        if (targetName == null) return;
        
        int textWidth = client.font.width(targetName);
        
        int textX;
        int textY = Math.max(2, y - 10);
        
        if (config.getHudPosition() == ModConfig.HudPosition.CUSTOM) {
            textX = scaledWidth - textWidth - 10;
            textY = 2;
        } else {
            textX = x + HUD_SIZE / 2 - textWidth / 2;
        }
        
        com.randomrun.ui.util.GuiText.text(context, client.font, targetName, textX, textY, 0xFFFFFF);
    }
    
    private String getTargetName(com.randomrun.core.session.RunSessionManager sessionManager) {
        if (sessionManager.getTargetType() == com.randomrun.core.session.TargetType.ITEM) {
            return sessionManager.getTargetItem() != null ? net.minecraft.network.chat.Component.translatable(sessionManager.getTargetItem().getDescriptionId()).getString() : null;
        } else if (sessionManager.getTargetType() == com.randomrun.core.session.TargetType.MOB) {
            if (sessionManager.getTargetMob() != null) {
                String mobName = sessionManager.getTargetMob().getDescription().getString();
                String variant = sessionManager.getMobVariant();
                if (variant != null) {
                    return capitalizeFirst(variant) + " " + mobName;
                }
                return mobName;
            }
        }
        return null;
    }
    
    private String capitalizeFirst(String str) {
        if (str == null || str.isEmpty()) return str;
        return str.substring(0, 1).toUpperCase() + str.substring(1);
    }
    
    private void updateAnimations(float tickDelta) {
        long time = System.currentTimeMillis();
        borderAnimation = ((time % RAINBOW_CYCLE_MS) / (float) RAINBOW_CYCLE_MS);
        blinkAnimation = ((time % BLINK_CYCLE_MS) / (float) BLINK_CYCLE_MS);
    }
    
    private void extractBackground(GuiGraphicsExtractor context, int x, int y) {
        context.fill(x, y, x + HUD_SIZE, y + HUD_SIZE, BG_COLOR);
    }
    
    private void renderBorder(GuiGraphicsExtractor context, int x, int y) {
        int perimeter = (HUD_SIZE - 1) * 4;
        GuiQuadBatch.Builder border = new GuiQuadBatch.Builder(perimeter);
        
        for (int i = 0; i < perimeter; i++) {
            float progress = (borderAnimation + (float) i / perimeter) % 1.0f;
            int colorIndex = (int) (progress * 360) % 360;
            int color = RAINBOW_COLORS[colorIndex];
            
            int bx = calculateBorderX(x, i);
            int by = calculateBorderY(y, i);
            
            border.fill(bx, by, bx + BORDER_WIDTH, by + BORDER_WIDTH, color);
        }
        border.build().extract(context);
    }
    
    private int calculateBorderX(int x, int i) {
        int side = HUD_SIZE - 1;
        if (i < side) return x + i;
        if (i < side * 2) return x + HUD_SIZE - 1;
        if (i < side * 3) return x + HUD_SIZE - 1 - (i - side * 2);
        return x;
    }
    
    private int calculateBorderY(int y, int i) {
        int side = HUD_SIZE - 1;
        if (i < side) return y;
        if (i < side * 2) return y + (i - side);
        if (i < side * 3) return y + HUD_SIZE - 1;
        return y + HUD_SIZE - 1 - (i - side * 3);
    }
    
    private void renderTargetItem(GuiGraphicsExtractor context, com.randomrun.core.session.RunSessionManager sessionManager, int x, int y) {
        int itemX = x + HUD_SIZE / 2;
        int itemY = y + HUD_SIZE / 2 - 4;
        
        if (sessionManager.getTargetType() == com.randomrun.core.session.TargetType.ITEM) {
            if (sessionManager.getTargetItem() == null) return;
            
            ItemStack stack = displayStack(sessionManager.getTargetItem());
            ItemPreviewRenderer.extractLargeGui(context, stack, itemX, itemY, 16 * ITEM_SCALE);
        } else if (sessionManager.getTargetType() == com.randomrun.core.session.TargetType.MOB) {
            if (sessionManager.getTargetMob() == null) return;
            
            net.minecraft.world.item.Item spawnEgg = net.minecraft.world.item.SpawnEggItem.byId(sessionManager.getTargetMob()).map(net.minecraft.core.Holder::value).orElse(null);
            if (spawnEgg != null) {
                ItemStack eggStack = displayStack(spawnEgg);
                ItemPreviewRenderer.extractLargeGui(context, eggStack, itemX, itemY, 16 * ITEM_SCALE);
            }
        }
    }

    private ItemStack displayStack(Item item) {
        var level = Minecraft.getInstance().level;
        if (displayedItem != item || displayedLevel != level) {
            displayedItem = item;
            displayedLevel = level;
            displayedStack = item == null ? ItemStack.EMPTY : new ItemStack(item);
        }
        return displayedStack;
    }
    
    private void renderItemTooltip(GuiGraphicsExtractor context, Minecraft client, 
            com.randomrun.core.session.RunSessionManager sessionManager, int x, int y, float scaleFactor) {
        if (client.gui.screen() == null) return;
        
        String targetName = getTargetName(sessionManager);
        if (targetName == null) return;
        
        double guiScale = client.getWindow().getGuiScale();
        double mouseX = client.mouseHandler.xpos() / guiScale / scaleFactor;
        double mouseY = client.mouseHandler.ypos() / guiScale / scaleFactor;
        
        int margin = 5;
        if (mouseX >= x - margin && mouseX <= x + HUD_SIZE + margin && 
            mouseY >= y - margin && mouseY <= y + HUD_SIZE + margin) {
            int textWidth = client.font.width(targetName);
            int tooltipX = x + HUD_SIZE / 2 - textWidth / 2;
            int tooltipY = y + HUD_SIZE + 4;
            
            context.fill(tooltipX - 3, tooltipY - 2, tooltipX + textWidth + 3, tooltipY + 11, 0xCC000000);
            com.randomrun.ui.util.GuiText.text(context, client.font, targetName, tooltipX, tooltipY, 0xFFFFFF);
        }
    }
    
    private void renderGoText(GuiGraphicsExtractor context, Minecraft client, ModConfig config, int x, int y) {
        float blinkValue = (float) Math.sin(blinkAnimation * Math.PI * 2) * 0.5f + 0.5f;
        int red = 255;
        int green = (int) (165 + blinkValue * 90);
        int blinkColor = 0xFF000000 | (red << 16) | (green << 8);
        
        String goText = config.getStartMethod() == ModConfig.StartMethod.KEYBIND
            ? InputConstants.Type.KEYBOARD.getOrCreate(com.randomrun.infrastructure.util.LegacyKeyCodes.toInputKey(config.getStartKey())).getDisplayName().getString().toUpperCase()
            : "/go";
        
        context.pose().pushMatrix();
        context.pose().scale(GO_TEXT_SCALE, GO_TEXT_SCALE);
        
        int scaledGoWidth = (int)(client.font.width(goText) * GO_TEXT_SCALE);
        int goX = (int)((x + (HUD_SIZE - scaledGoWidth) / 2) / GO_TEXT_SCALE);
        int goY = (int)((y + HUD_SIZE - 9) / GO_TEXT_SCALE);
        
        com.randomrun.ui.util.GuiText.text(context, client.font, goText, goX, goY, blinkColor);
        context.pose().popMatrix();
    }
    
    private void renderTimer(GuiGraphicsExtractor context, Minecraft client, com.randomrun.core.session.RunSessionManager sessionManager, int x, int y) {
        String timeStr = RunDataManager.formatTime(sessionManager.getElapsedTime());
        int timeColor = TIME_NORMAL_COLOR;
        
        context.pose().pushMatrix();
        context.pose().scale(TEXT_SCALE, TEXT_SCALE);
        
        int scaledTimeWidth = (int)(client.font.width(timeStr) * TEXT_SCALE);
        int timeX = (int)((x + (HUD_SIZE - scaledTimeWidth) / 2) / TEXT_SCALE);
        int timeY = (int)((y + HUD_SIZE - 9) / TEXT_SCALE);
        
        com.randomrun.ui.util.GuiText.text(context, client.font, "§e" + timeStr, timeX, timeY, timeColor);
        context.pose().popMatrix();
    }
    
    private void checkAndPlayTimerSounds(Minecraft client, com.randomrun.core.session.RunSessionManager sessionManager) {
    }
    
    private void playWarningSound(Minecraft client, float pitch) {
        client.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.NOTE_BLOCK_PLING.value(), pitch, 0.5f));
    }
    
    private void playTickSound(Minecraft client) {
        client.getSoundManager().play(SimpleSoundInstance.forUI(SoundEvents.NOTE_BLOCK_HAT.value(), 1.5f, 0.3f));
    }
    
    private void resetSoundFlags() {
        sound10Played = false;
        sound5Played = false;
        sound3Played = false;
        lastRemainingTime = -1;
    }
    
    private static int calculateRainbowColor(float t) {
        int red, green, blue;
        
        if (t < 0.166f) {
            red = 255;
            green = (int) (t / 0.166f * 165);
            blue = 0;
        } else if (t < 0.333f) {
            red = 255;
            green = (int) (165 + (t - 0.166f) / 0.167f * 90);
            blue = 0;
        } else if (t < 0.5f) {
            red = (int) (255 - (t - 0.333f) / 0.167f * 255);
            green = 255;
            blue = 0;
        } else if (t < 0.666f) {
            red = 0;
            green = 255;
            blue = (int) ((t - 0.5f) / 0.166f * 255);
        } else if (t < 0.833f) {
            red = 0;
            green = (int) (255 - (t - 0.666f) / 0.167f * 255);
            blue = 255;
        } else {
            red = (int) ((t - 0.833f) / 0.167f * 255);
            green = 0;
            blue = (int) (255 - (t - 0.833f) / 0.167f * 128);
        }
        
        return 0xFF000000 | (red << 16) | (green << 8) | blue;
    }
}
