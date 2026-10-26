package com.randomrun.ui.widget.overlay;

import com.randomrun.infrastructure.RandomRunMod;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;

public class ModInfoWidget {
    private static final String VERSION = RandomRunMod.MOD_VERSION;
    private static final int MARGIN_RIGHT = 10;
    private static final int MARGIN_BOTTOM = 20;
    private static final int COLOR_TEXT = 0xAAAAAA;
    
    private final int screenWidth;
    private final int screenHeight;
    private final Font textRenderer;
    
    public ModInfoWidget(int screenWidth, int screenHeight, Font textRenderer) {
        this.screenWidth = screenWidth;
        this.screenHeight = screenHeight;
        this.textRenderer = textRenderer;
    }
    
    public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        int versionWidth = textRenderer.width(VERSION);
        int x = screenWidth - versionWidth - MARGIN_RIGHT;
        int y = screenHeight - MARGIN_BOTTOM;
        
        com.randomrun.ui.util.GuiText.text(context, textRenderer, VERSION, x, y, COLOR_TEXT);
    }
    
    public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent event, boolean doubleClick) {
        double mouseX = event.x();
        double mouseY = event.y();
        int button = event.button();
        return false;
    }
}
