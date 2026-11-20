package com.randomrun.features.speedrun;

import com.randomrun.ui.util.InterfaceStyle;

import com.randomrun.core.run.RunDataManager;
import com.randomrun.infrastructure.ModConfig;
import com.randomrun.infrastructure.RandomRunMod;
import com.randomrun.ui.util.GuiQuadBatch;
import com.randomrun.ui.util.Ui;
import com.randomrun.ui.util.Previews.ItemPreviewRenderer;

import net.fabricmc.fabric.api.client.rendering.v1.hud.HudElement;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

public class HudRenderer implements HudElement {
    private static final int HUD_SIZE = 48;
    private static final int BORDER_WIDTH = 1;
    private static final int ITEM_SCALE = 2;
    private static final float TEXT_SCALE = 0.7f;
    private static final float GO_TEXT_SCALE = 0.8f;

    private static final long RAINBOW_CYCLE_MS = 2000;
    private static final long BLINK_CYCLE_MS = 800;

    private static final int BG_COLOR = 0xC0000000;

    private static final int TIME_NORMAL_COLOR = 0xFFFFFF;

    private static final int[] RAINBOW_COLORS = new int[360];

    static {
        initRainbowColors();
    }

    private float borderAnimation = 0f;
    private float blinkAnimation = 0f;

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
        if (client.gui.screen() instanceof com.randomrun.features.upgrader.client.UpgraderScreen
                || client.gui.screen() instanceof com.randomrun.ui.screen.settings.HudPositionEditorScreen) return;

        com.randomrun.core.run.RunSessionManager sessionManager = com.randomrun.core.run.RunSessionManager.getInstance();

        if (!sessionManager.hasActiveRun()) {
            return;
        }

        com.randomrun.core.run.RunSession.RunStatus status = sessionManager.getRunStatus();
        ModConfig config = RandomRunMod.getInstance().getConfig();

        float scaleFactor = com.randomrun.ui.util.UiScale.factor() * config.getHudSize().scale;

        context.pose().pushMatrix();
        context.pose().scale(scaleFactor, scaleFactor);

        try {
            int scaledWidth = (int)(client.getWindow().getGuiScaledWidth() / scaleFactor);
            int hudWidth = HUD_SIZE;
            int hudHeight = config.getHudStyle() == ModConfig.HudStyle.PROFESSIONAL ? 35 : HUD_SIZE;
            if (config.getHudStyle() == ModConfig.HudStyle.PROFESSIONAL) {
                String value = status == com.randomrun.core.run.RunSession.RunStatus.FROZEN
                    ? AutoStart.hudLabel()
                    : RunDataManager.formatTime(sessionManager.getRtaTime());
                String igt = status == com.randomrun.core.run.RunSession.RunStatus.FROZEN ? value : RunDataManager.formatTime(sessionManager.getElapsedTime());
                hudWidth = professionalWidth(client.font, getTargetName(sessionManager), value, igt);
            }
            int x = calculateHudX(config, scaledWidth, hudWidth);
            int y = Math.max(0, Math.min((int) (client.getWindow().getGuiScaledHeight() / scaleFactor) - hudHeight, calculateHudY(config)));

            float tickDelta = tickCounter.getGameTimeDeltaPartialTick(true);
            updateAnimations(tickDelta);

            ModConfig.HudStyle hudStyle = config.getHudStyle();

            switch (hudStyle) {
                case PROFESSIONAL -> renderProfessionalHud(context, client, sessionManager, config, status, x, y, scaledWidth, scaleFactor);
                default -> renderPleasantHud(context, client, sessionManager, config, status, x, y, scaledWidth, scaleFactor);
            }

            if (sessionManager.getCurrentSession().isUpgrader() && !config.hasSeenUpgraderHint()) {
                String hint = com.randomrun.features.upgrader.client.UpgraderClient.keyName().getString()
                    + " · " + com.randomrun.infrastructure.util.LanguageManager.translate("randomrun.mode.upgrader.name");
                int hintWidth = client.font.width(hint) + 8;
                int hintX = Math.max(2, Math.min(x, scaledWidth - hintWidth - 2));
                int hintY = y + (hudStyle == ModConfig.HudStyle.PROFESSIONAL ? 38 : 65);
                if (InterfaceStyle.isClassic()) {
                    Ui.bevel(context, hintX, hintY, hintX + hintWidth, hintY + 14, 0x383833, 1f, false);
                } else {
                    context.fill(hintX, hintY, hintX + hintWidth, hintY + 14, 0xCC171A20);
                }
                context.fill(hintX, hintY, hintX + 2, hintY + 14, 0xFFFFCF52);
                com.randomrun.ui.util.GuiText.text(context, client.font, hint, hintX + 4, hintY + 3, 0xFFFFCF52);
            }

            if (status == com.randomrun.core.run.RunSession.RunStatus.FROZEN) {
                String key = RunEligibility.statusKey(sessionManager.getCurrentSession());
                if (key != null) {
                    boolean ok = key.endsWith(".ok");
                    String text = com.randomrun.infrastructure.util.LanguageManager.translate(key);
                    int textWidth = client.font.width(text) + 8;
                    int statusX = Math.max(2, Math.min(x, scaledWidth - textWidth - 2));
                    int statusY = y + (hudStyle == ModConfig.HudStyle.PROFESSIONAL ? 38 : 65)
                        + (sessionManager.getCurrentSession().isUpgrader() && !config.hasSeenUpgraderHint() ? 17 : 0);
                    int accent = ok ? 0xFF4ADE80 : 0xFFF87171;
                    if (InterfaceStyle.isClassic()) {
                        Ui.bevel(context, statusX, statusY, statusX + textWidth, statusY + 14, 0x383833, 1f, false);
                    } else {
                        context.fill(statusX, statusY, statusX + textWidth, statusY + 14, 0xCC171A20);
                    }
                    context.fill(statusX, statusY, statusX + 2, statusY + 14, accent);
                    com.randomrun.ui.util.GuiText.text(context, client.font, text, statusX + 4, statusY + 3, accent);
                }
            }

        } finally {
            context.pose().popMatrix();
        }
    }

    public static int professionalWidth(net.minecraft.client.gui.Font font, String name, String rta, String igt) {
        return Math.max(font.width(name == null ? "" : name), Math.max(font.width("RTA: " + rta), font.width("IGT: " + igt))) + 6;
    }

    public void renderPreview(GuiGraphicsExtractor context, ModConfig config, ItemStack item) {
        var font = Minecraft.getInstance().font;
        updateAnimations(0);
        if (config.getHudStyle() == ModConfig.HudStyle.PROFESSIONAL) {
            String name = item.getHoverName().getString();
            int w = professionalWidth(font, name, "00:00.000", "00:00.000");
            if (InterfaceStyle.isClassic()) {
                Ui.bevel(context, 0, 0, w, 35, 0x343432, .94f, false);
            } else {
                context.fill(0, 0, w, 35, BG_COLOR);
            }
            com.randomrun.ui.util.GuiText.text(context, font, name, 3, 2, 0xFFFFFFFF);
            com.randomrun.ui.util.GuiText.text(context, font, "§7RTA: §e00:00.000", 3, 13, 0xFFFFFFFF);
            com.randomrun.ui.util.GuiText.text(context, font, "§7IGT: §a00:00.000", 3, 24, 0xFFFFFFFF);
        } else {
            extractBackground(context, 0, 0);
            renderBorder(context, 0, 0);
            ItemPreviewRenderer.extractInSlot(context, item, 3, 3, HUD_SIZE - 3, HUD_SIZE - 12, itemPadding());
            context.pose().pushMatrix();
            context.pose().scale(TEXT_SCALE, TEXT_SCALE);
            com.randomrun.ui.util.GuiText.text(context, font, "§e00:00.000", (int) ((48 - font.width("00:00.000") * TEXT_SCALE) / 2 / TEXT_SCALE), (int) (39 / TEXT_SCALE), 0xFFFFFFFF);
            context.pose().popMatrix();
        }
    }

    private int calculateHudX(ModConfig config, int scaledWidth, int hudWidth) {
        int margin = Math.round(10 / config.getHudSize().scale);
        int x = switch (config.getHudPosition()) {
            case TOP_RIGHT -> scaledWidth - hudWidth - margin;
            case TOP_CENTER -> (scaledWidth - hudWidth) / 2;
            case CUSTOM -> Math.round(config.getCustomHudX() / config.getHudSize().scale);
            default -> margin;
        };
        return Math.max(0, Math.min(scaledWidth - hudWidth, x));
    }

    private int calculateHudY(ModConfig config) {
        return Math.round((config.getHudPosition() == ModConfig.HudPosition.CUSTOM ? config.getCustomHudY() : 10) / config.getHudSize().scale);
    }

    private void renderPleasantHud(GuiGraphicsExtractor context, Minecraft client, 
            com.randomrun.core.run.RunSessionManager sessionManager, ModConfig config,
            com.randomrun.core.run.RunSession.RunStatus status, int x, int y, int scaledWidth, float scaleFactor) {
        extractBackground(context, x, y);
        renderBorder(context, x, y);

        renderTargetItem(context, sessionManager, x, y);
        renderItemTooltip(context, client, sessionManager, x, y, scaleFactor);

        if (status == com.randomrun.core.run.RunSession.RunStatus.FROZEN) {
            renderGoText(context, client, config, x, y);
        } else {
            renderTimer(context, client, sessionManager, x, y);
        }
    }

    private void renderProfessionalHud(GuiGraphicsExtractor context, Minecraft client,
            com.randomrun.core.run.RunSessionManager sessionManager, ModConfig config,
            com.randomrun.core.run.RunSession.RunStatus status, int x, int y, int scaledWidth, float scaleFactor) {
        String targetName = getTargetName(sessionManager);
        if (targetName == null) return;

        String rtaStr;
        String igtStr;
        int rtaColor = TIME_NORMAL_COLOR;
        if (status == com.randomrun.core.run.RunSession.RunStatus.FROZEN) {
            String goText = AutoStart.hudLabel();
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

        if (InterfaceStyle.isClassic()) {
            Ui.bevel(context, x, y, x + totalWidth, y + totalHeight, 0x343432, .94f, false);
        } else {
            context.fill(x, y, x + totalWidth, y + totalHeight, BG_COLOR);
        }

        com.randomrun.ui.util.GuiText.text(context, client.font, targetName, x + 3, y + 2, 0xFFFFFF);
        com.randomrun.ui.util.GuiText.text(context, client.font, "§7RTA: §e" + rtaStr, x + 3, y + 13, rtaColor);
        com.randomrun.ui.util.GuiText.text(context, client.font, "§7IGT: §a" + igtStr, x + 3, y + 24, TIME_NORMAL_COLOR);
    }

    private String getTargetName(com.randomrun.core.run.RunSessionManager sessions) {
        return sessions.getTargetItem() == null ? null : net.minecraft.network.chat.Component.translatable(sessions.getTargetItem().getDescriptionId()).getString();
    }



    private void updateAnimations(float tickDelta) {
        long time = System.currentTimeMillis();
        borderAnimation = ((time % RAINBOW_CYCLE_MS) / (float) RAINBOW_CYCLE_MS);
        blinkAnimation = ((time % BLINK_CYCLE_MS) / (float) BLINK_CYCLE_MS);
    }

    private void extractBackground(GuiGraphicsExtractor context, int x, int y) {
        if (InterfaceStyle.isClassic()) {
            Ui.bevel(context, x, y, x + HUD_SIZE, y + HUD_SIZE, 0x686861, .96f, false);
            Ui.slot(context, x + 3, y + 3, x + HUD_SIZE - 3, y + HUD_SIZE - 12, 1f);
            context.fill(x + 3, y + HUD_SIZE - 11, x + HUD_SIZE - 3, y + HUD_SIZE - 3, 0xFF282824);
        } else {
            context.fill(x, y, x + HUD_SIZE, y + HUD_SIZE, BG_COLOR);
        }
    }

    private void renderBorder(GuiGraphicsExtractor context, int x, int y) {
        if (InterfaceStyle.isClassic()) {
            Ui.border(context, x, y, x + HUD_SIZE, y + HUD_SIZE, 1, 0xFF171713);
            return;
        }
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

    private void renderTargetItem(GuiGraphicsExtractor context, com.randomrun.core.run.RunSessionManager sessions, int x, int y) {
        if (sessions.getTargetItem() == null) return;
        ItemPreviewRenderer.extractInSlot(context, displayStack(sessions.getTargetItem()),
            x + 3, y + 3, x + HUD_SIZE - 3, y + HUD_SIZE - 12, itemPadding());
    }

    private static int itemPadding() { return InterfaceStyle.isClassic() ? 3 : 1; }

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
            com.randomrun.core.run.RunSessionManager sessionManager, int x, int y, float scaleFactor) {
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

            if (InterfaceStyle.isClassic()) {
                Ui.bevel(context, tooltipX - 3, tooltipY - 2, tooltipX + textWidth + 3, tooltipY + 11, 0x272724, .98f, false);
            } else {
                context.fill(tooltipX - 3, tooltipY - 2, tooltipX + textWidth + 3, tooltipY + 11, 0xCC000000);
            }
            com.randomrun.ui.util.GuiText.text(context, client.font, targetName, tooltipX, tooltipY, 0xFFFFFF);
        }
    }

    private void renderGoText(GuiGraphicsExtractor context, Minecraft client, ModConfig config, int x, int y) {
        float blinkValue = (float) Math.sin(blinkAnimation * Math.PI * 2) * 0.5f + 0.5f;
        int red = 255;
        int green = (int) (165 + blinkValue * 90);
        int blinkColor = 0xFF000000 | (red << 16) | (green << 8);

        String goText = AutoStart.hudLabel();

        context.pose().pushMatrix();
        context.pose().scale(GO_TEXT_SCALE, GO_TEXT_SCALE);

        int scaledGoWidth = (int)(client.font.width(goText) * GO_TEXT_SCALE);
        int goX = (int)((x + (HUD_SIZE - scaledGoWidth) / 2) / GO_TEXT_SCALE);
        int goY = (int)((y + HUD_SIZE - 9) / GO_TEXT_SCALE);

        com.randomrun.ui.util.GuiText.text(context, client.font, goText, goX, goY, blinkColor);
        context.pose().popMatrix();
    }

    private void renderTimer(GuiGraphicsExtractor context, Minecraft client, com.randomrun.core.run.RunSessionManager sessionManager, int x, int y) {
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
