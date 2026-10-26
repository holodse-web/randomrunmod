package com.randomrun.features.battle.screen.lobby.util.render;

import com.randomrun.features.battle.manager.BattleRoom;
import com.randomrun.features.battle.screen.lobby.util.helper.UIColors;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

public class RoomListRenderer {
    private static final int ICON_OFFSET = 8;
    private static final int TEXT_X = 50;
    private static final int TEXT_Y_TITLE = 10;
    private static final int TEXT_Y_INFO = 26;
    private static final int LOCK_OFFSET = 20;
    
    public static void renderRoomEntry(GuiGraphicsExtractor context, Font textRenderer,
                                      BattleRoom room, ItemStack iconStack, String modeText,
                                      int left, int top, int width, int height, boolean hovered) {
        renderIcon(context, iconStack, left, top);
        
        renderInfo(context, textRenderer, room, modeText, left, top, width);
        
        if (room.isPrivate()) {
            com.randomrun.ui.util.GuiText.text(context, textRenderer, Component.literal("🔒"), 
                left + width - LOCK_OFFSET, top + TEXT_Y_INFO, UIColors.TEXT_WARNING);
        }
    }
    
    private static void drawBorder(GuiGraphicsExtractor context, int left, int top, int width, int height, int color) {
        context.fill(left - 2, top - 2, left + width + 2, top, color);
        context.fill(left - 2, top + height, left + width + 2, top + height + 2, color);
        context.fill(left - 2, top - 2, left, top + height + 2, color);
        context.fill(left + width, top - 2, left + width + 2, top + height + 2, color);
    }
    
    private static void renderIcon(GuiGraphicsExtractor context, ItemStack iconStack, int left, int top) {
        if (!iconStack.isEmpty()) {
            context.pose().pushMatrix();
            context.pose().translate(left + ICON_OFFSET, top + ICON_OFFSET);
            context.pose().scale(2.0f, 2.0f);
            context.item(iconStack, 0, 0);
            context.pose().popMatrix();
        }
    }
    
    private static void renderInfo(GuiGraphicsExtractor context, Font textRenderer,
                                   BattleRoom room, String modeText, int left, int top, int width) {
        String host = room.getHost() != null && !room.getHost().isEmpty() ? room.getHost() : "Unknown";
        com.randomrun.ui.util.GuiText.text(context, textRenderer, Component.literal(host + "'s Room"), 
            left + TEXT_X, top + TEXT_Y_TITLE, UIColors.TEXT_WHITE);
        
        if (room.getStatus() != BattleRoom.RoomStatus.WAITING) {
            String statusText = getRuleIndicators(room) + "§c⚠ " + com.randomrun.infrastructure.util.LanguageManager
                .text("randomrun.battle.status.in_game").getString();
            com.randomrun.ui.util.GuiText.text(context, textRenderer, Component.literal(statusText), 
                left + TEXT_X, top + TEXT_Y_INFO, UIColors.TEXT_WARNING);
        } else {
            int playerCount = room.getPlayers() != null ? room.getPlayers().size() : 0;
            
            String minigameIndicator = getRuleIndicators(room);
            
            String info = minigameIndicator + modeText + " | " + playerCount + "/" + room.getMaxPlayers();
            com.randomrun.ui.util.GuiText.text(context, textRenderer, Component.literal(info), 
                left + TEXT_X, top + TEXT_Y_INFO, UIColors.TEXT_GRAY);
        }
    }

    public static String getRuleIndicators(BattleRoom room) {
        return (room.isUpgrader() ? "§b[Upgrader] §r" : "")
            + (room.isRandomDrop() ? "§6[RD] §r" : "");
    }
    
    public static String getRoomModeText(BattleRoom room) {
        String cm = room.getCreationMode();
        
        if (cm != null) {
            if ("radmin".equals(cm)) {
                return com.randomrun.infrastructure.util.LanguageManager
                    .text("randomrun.battle.shared_world_radmin").getString();
            } else if ("e4".equals(cm)) {
                return com.randomrun.infrastructure.util.LanguageManager
                    .text("randomrun.battle.shared_world").getString();
            } else {
                return com.randomrun.infrastructure.util.LanguageManager
                    .text("randomrun.battle.separate_worlds").getString();
            }
        } else if (room.isSharedWorld()) {
            String addr = room.getServerAddress();
            if (addr != null && (addr.startsWith("26.") || addr.startsWith("192.168.") || 
                addr.startsWith("127.") || addr.equals("localhost"))) {
                return com.randomrun.infrastructure.util.LanguageManager
                    .text("randomrun.battle.shared_world_radmin").getString();
            } else {
                return com.randomrun.infrastructure.util.LanguageManager
                    .text("randomrun.battle.shared_world").getString();
            }
        } else {
            return com.randomrun.infrastructure.util.LanguageManager
                .text("randomrun.battle.separate_worlds").getString();
        }
    }
    
    public static ItemStack createRoomIcon(String itemId, com.randomrun.features.battle.util.ItemGlitchEffect glitchEffect) {
        if (itemId == null || itemId.isEmpty()) {
            itemId = glitchEffect.getCurrentItemId();
        }
        Identifier id = Identifier.tryParse(itemId);
        if (id == null || !BuiltInRegistries.ITEM.containsKey(id)) return ItemStack.EMPTY;
        Item item = BuiltInRegistries.ITEM.getValue(id);
        return item != null ? com.randomrun.ui.util.PreviewItems.stack(item) : ItemStack.EMPTY;
    }
}
