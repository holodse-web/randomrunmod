package com.randomrun.ui.background;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;

public class BackgroundRenderer {
    private static BackgroundRenderer instance;
    
    private static final int COLOR_TOP = 0xFF000000;
    private static final int COLOR_MID = 0xFF4B0082;
    private static final int COLOR_BOTTOM = 0xFF3A0066;
    
    private BackgroundRenderer() {
    }
    
    public static BackgroundRenderer getInstance() {
        if (instance == null) {
            instance = new BackgroundRenderer();
        }
        return instance;
    }
    
    public void extractBackground(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        Minecraft mc = Minecraft.getInstance();
        int screenWidth = mc.getWindow().getGuiScaledWidth();
        int screenHeight = mc.getWindow().getGuiScaledHeight();
        
        renderGradient(context, screenWidth, screenHeight);
        renderTopGradient(context, screenWidth, screenHeight);
    }
    
    public void renderBackgroundOnly(GuiGraphicsExtractor context) {
        Minecraft mc = Minecraft.getInstance();
        int screenWidth = mc.getWindow().getGuiScaledWidth();
        int screenHeight = mc.getWindow().getGuiScaledHeight();
        
        renderGradient(context, screenWidth, screenHeight);
        renderTopGradient(context, screenWidth, screenHeight);
    }
    
    private void renderTopGradient(GuiGraphicsExtractor context, int screenWidth, int screenHeight) {
        context.fillGradient(0, 0, screenWidth, Math.min(60, screenHeight), 0xFF000000, 0x00000000);
    }
    
    private void renderGradient(GuiGraphicsExtractor context, int screenWidth, int screenHeight) {
        int middle = screenHeight / 2;
        context.fillGradient(0, 0, screenWidth, middle, COLOR_TOP, COLOR_MID);
        context.fillGradient(0, middle, screenWidth, screenHeight, COLOR_MID, COLOR_BOTTOM);
    }
}
