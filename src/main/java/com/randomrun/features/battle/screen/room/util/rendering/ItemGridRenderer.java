package com.randomrun.features.battle.screen.room.util.rendering;

import com.randomrun.features.battle.screen.lobby.util.helper.UIColors;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.world.item.Item;

public class ItemGridRenderer {
    private static final int ITEM_SIZE = 20;
    private static final int GRID_PADDING = 2;
    
    private static final int COLOR_SLOT_BG = 0xFF3a3a5c;
    private static final int COLOR_SLOT_HOVER = 0xFF6930c3;
    private static final int COLOR_SLOT_SELECTED = 0xFFd042ff;
    
    public static void item(GuiGraphicsExtractor context, Font textRenderer,
                                 Item item, int x, int y, boolean isSelected, 
                                 boolean isHovered, int mouseX, int mouseY) {
        int bgColor = isSelected ? COLOR_SLOT_SELECTED : 
                     (isHovered ? COLOR_SLOT_HOVER : COLOR_SLOT_BG);
        context.fill(x, y, x + ITEM_SIZE, y + ITEM_SIZE, bgColor);
        
        int borderColor = isSelected ? 0xFFFFFF00 : 
                         (isHovered ? UIColors.BORDER_HOVERED : UIColors.BORDER_DISABLED);
        drawBorder(context, x, y, ITEM_SIZE, ITEM_SIZE, borderColor);
        
        context.item(com.randomrun.ui.util.PreviewItems.stack(item), x + 2, y + 2);
        
        if (isHovered) {
            context.setTooltipForNextFrame(textRenderer, net.minecraft.network.chat.Component.translatable(item.getDescriptionId()), mouseX, mouseY);
        }
    }
    
    private static void drawBorder(GuiGraphicsExtractor context, int x, int y, int width, int height, int color) {
        context.fill(x - 1, y - 1, x + width + 1, y, color);
        context.fill(x - 1, y + height, x + width + 1, y + height + 1, color);
        context.fill(x - 1, y - 1, x, y + height + 1, color);
        context.fill(x + width, y - 1, x + width + 1, y + height + 1, color);
    }
    
    public static int getItemSize() {
        return ITEM_SIZE;
    }
    
    public static int getGridPadding() {
        return GRID_PADDING;
    }
}
