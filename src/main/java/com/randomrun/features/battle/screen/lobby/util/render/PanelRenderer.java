package com.randomrun.features.battle.screen.lobby.util.render;

import com.randomrun.features.battle.manager.BattleRoom;
import com.randomrun.features.battle.screen.lobby.util.helper.UIColors;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

public class PanelRenderer {
    private static final int BUTTON_WIDTH = 200;
    private static final int INFO_GAP = 8;
    
    public static void renderRoomInfoPanel(GuiGraphicsExtractor context, Font textRenderer, 
                                          BattleRoom room, int centerX, int screenHeight, 
                                          String animatedCode, long elapsed, long animDuration) {
        if (room == null) return;
        
        int buttonLeft = centerX - BUTTON_WIDTH / 2;
        int buttonRight = centerX + BUTTON_WIDTH / 2;
        int infoY = screenHeight - 30 + 6;
        
        float pulse = (float) Math.sin(elapsed / 200.0) * 0.15f + 0.85f;
        int codeColor = elapsed >= animDuration ? 
            (int) (255 * pulse) << 16 | (int) (215 * pulse) << 8 | 0 : 
            UIColors.TEXT_WHITE;
        
        Component codeText = com.randomrun.infrastructure.util.LanguageManager.text("randomrun.battle.room_code_display", animatedCode);
        int codeX = buttonRight + INFO_GAP;
        com.randomrun.ui.util.GuiText.text(context, textRenderer, codeText, codeX, infoY, codeColor);
        
        int codeWidth = textRenderer.width(codeText);
        int copyIconX = codeX + codeWidth + 4;
        com.randomrun.ui.util.GuiText.text(context, textRenderer, Component.literal("📋"), copyIconX, infoY, 0xFFFFFF);
        
        int currentPlayers = room.getPlayers() != null ? room.getPlayers().size() : 1;
        int maxPlayers = room.getMaxPlayers();
        String countText = String.format("%d/%d", currentPlayers, maxPlayers);
        int countWidth = textRenderer.width(countText);
        com.randomrun.ui.util.GuiText.text(context, textRenderer, Component.literal(countText), 
            buttonLeft - INFO_GAP - countWidth, infoY, UIColors.TEXT_WHITE);
        
        com.randomrun.features.battle.manager.GameMode gameMode = room.getGameMode();
        if (room.isUpgrader() || gameMode != com.randomrun.features.battle.manager.GameMode.DEFAULT) {
            String modeText = (room.isUpgrader() ? "§bRRM Upgrader" : "")
                + (gameMode != com.randomrun.features.battle.manager.GameMode.DEFAULT
                    ? (room.isUpgrader() ? " §7+ " : "") + "§e" + gameMode.getDisplayName() : "");
            if (room.isUpgrader() || !gameMode.countsForLeaderboard()) {
                modeText += " §7(не в таблице лидеров)";
            }
            com.randomrun.ui.util.GuiText.centeredText(context, textRenderer, Component.literal(modeText), 
                centerX, 126, UIColors.TEXT_WHITE);
        }
        
        renderServerStatus(context, textRenderer, room, centerX, screenHeight - 12);
    }
    
    public static int[] getRoomCodeBounds(Font textRenderer, int centerX, int screenHeight, String animatedCode) {
        int buttonRight = centerX + BUTTON_WIDTH / 2;
        int infoY = screenHeight - 30 + 6;
        
        Component codeText = com.randomrun.infrastructure.util.LanguageManager.text("randomrun.battle.room_code_display", animatedCode);
        int codeX = buttonRight + INFO_GAP;
        int codeWidth = textRenderer.width(codeText);
        
        int copyIconWidth = textRenderer.width("📋") + 4;
        int totalWidth = codeWidth + copyIconWidth;
        
        return new int[] { codeX, infoY - 2, codeX + totalWidth, infoY + 10 };
    }
    
    private static void renderServerStatus(GuiGraphicsExtractor context, Font textRenderer, 
                                          BattleRoom room, int centerX, int statusY) {
        if (room.isSharedWorld()) {
            String serverAddr = room.getServerAddress();
            boolean isHost = room.getPlayers() != null && !room.getPlayers().isEmpty() 
                           && room.getPlayers().get(0).equals(net.minecraft.client.Minecraft.getInstance().getUser().getName());
            
            if (isHost && serverAddr != null) {
                com.randomrun.ui.util.GuiText.centeredText(context, textRenderer,
                    Component.literal("Сервер готов"),
                    centerX, statusY, UIColors.TEXT_WHITE);
            } else if (!isHost) {
                String status = serverAddr != null ? "Ожидание подключения" : "Подключение к хосту...";
                com.randomrun.ui.util.GuiText.centeredText(context, textRenderer,
                    Component.literal(status),
                    centerX, statusY, UIColors.TEXT_WHITE);
            }
        }
    }
    
    public static void renderPlayerNamePanel(GuiGraphicsExtractor context, Font textRenderer, 
                                            String name, int x, int y) {
        int nameWidth = textRenderer.width(name);
        int panelWidth = nameWidth + 10;
        int panelX = x - panelWidth / 2;
        
        context.fill(panelX, y, panelX + panelWidth, y + 12, UIColors.BG_PANEL);
        com.randomrun.ui.util.GuiText.centeredText(context, textRenderer, name, x, y + (12 - textRenderer.lineHeight) / 2,
            UIColors.TEXT_WHITE);
    }
    
    public static void renderEmptySlot(GuiGraphicsExtractor context, Font textRenderer, 
                                      int x, int slotY, int nameY, int slotSize) {
        context.fill(x - slotSize/2, slotY, x + slotSize/2, slotY + slotSize, UIColors.SLOT_BG);
        context.outline(x - slotSize/2, slotY, slotSize, slotSize, UIColors.SLOT_BORDER);
        com.randomrun.ui.util.GuiText.centeredText(context, textRenderer, "?", x, slotY + (slotSize - textRenderer.lineHeight) / 2, 0xFFFFFF);
        
        renderPlayerNamePanel(context, textRenderer, "Ожидание", x, nameY);
    }
    
    public static void renderPlayerCountText(GuiGraphicsExtractor context, Font textRenderer,
                                            int currentPlayers, int maxPlayers, int x, int y) {
        String countText = String.format("%d/%d игроков", currentPlayers, maxPlayers);
        com.randomrun.ui.util.GuiText.centeredText(context, textRenderer, Component.literal(countText), x, y, UIColors.TEXT_WHITE);
    }
}
