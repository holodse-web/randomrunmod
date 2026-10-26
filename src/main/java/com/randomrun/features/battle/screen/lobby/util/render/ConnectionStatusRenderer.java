package com.randomrun.features.battle.screen.lobby.util.render;

import com.randomrun.features.battle.screen.lobby.util.helper.LobbyPollingManager;
import com.randomrun.features.battle.screen.lobby.util.helper.UIColors;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

public class ConnectionStatusRenderer {
    public static void extractRenderState(GuiGraphicsExtractor context, Font textRenderer, 
                            LobbyPollingManager.ConnectionStatus status, 
                            int x, int y) {
        String statusText = getStatusText(status);
        int color = getStatusColor(status);
        String icon = getStatusIcon(status);
        
        String fullText = icon + " " + statusText;
        com.randomrun.ui.util.GuiText.text(context, textRenderer, Component.literal(fullText), x, y, color);
    }
    
    private static String getStatusText(LobbyPollingManager.ConnectionStatus status) {
        switch (status) {
            case CONNECTED:
                return "Подключено";
            case CONNECTING:
                return "Подключение...";
            case DISCONNECTED:
                return "Отключено";
            case ERROR:
                return "Ошибка";
            default:
                return "Неизвестно";
        }
    }
    
    private static int getStatusColor(LobbyPollingManager.ConnectionStatus status) {
        switch (status) {
            case CONNECTED:
                return UIColors.TEXT_SUCCESS;
            case CONNECTING:
                return UIColors.TEXT_YELLOW;
            case DISCONNECTED:
                return UIColors.TEXT_GRAY;
            case ERROR:
                return UIColors.TEXT_ERROR;
            default:
                return UIColors.TEXT_GRAY;
        }
    }
    
    private static String getStatusIcon(LobbyPollingManager.ConnectionStatus status) {
        switch (status) {
            case CONNECTED:
                return "●";
            case CONNECTING:
                return "◐";
            case DISCONNECTED:
                return "○";
            case ERROR:
                return "✖";
            default:
                return "?";
        }
    }
}
