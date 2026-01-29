package com.randomrun.ui.widget;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.Drawable;
import net.minecraft.client.gui.Element;
import net.minecraft.util.Util;

public class CreditWidget implements Drawable, Element {
    private final int x;
    private final int y;
    private final String text;
    private final String url;
    private boolean hovered = false;
    private float hoverProgress = 0f;
    
    public CreditWidget(int x, int y, String text, String url) {
        this.x = x;
        this.y = y;
        this.text = text;
        this.url = url;
    }
    
    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        MinecraftClient client = MinecraftClient.getInstance();
        
        // Update hover state
        hovered = isMouseOver(mouseX, mouseY);
        
        // Smooth hover animation
        if (hovered) {
            hoverProgress = Math.min(1.0f, hoverProgress + delta * 0.1f);
        } else {
            hoverProgress = Math.max(0.0f, hoverProgress - delta * 0.1f);
        }
        
        // Interpolate color from gray to cyan
        int grayColor = 0x808080;
        int cyanColor = 0x00FFFF;
        
        int r1 = (grayColor >> 16) & 0xFF;
        int g1 = (grayColor >> 8) & 0xFF;
        int b1 = grayColor & 0xFF;
        
        int r2 = (cyanColor >> 16) & 0xFF;
        int g2 = (cyanColor >> 8) & 0xFF;
        int b2 = cyanColor & 0xFF;
        
        int r = (int)(r1 + (r2 - r1) * hoverProgress);
        int g = (int)(g1 + (g2 - g1) * hoverProgress);
        int b = (int)(b1 + (b2 - b1) * hoverProgress);
        
        int color = 0xFF000000 | (r << 16) | (g << 8) | b;
        
        // Draw text
        context.drawText(client.textRenderer, text, x, y, color, false);
    }
    
    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0 && isMouseOver((int)mouseX, (int)mouseY)) {
            Util.getOperatingSystem().open(url);
            return true;
        }
        return false;
    }
    
    private boolean isMouseOver(int mouseX, int mouseY) {
        MinecraftClient client = MinecraftClient.getInstance();
        int width = client.textRenderer.getWidth(text);
        int height = client.textRenderer.fontHeight;
        return mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + height;
    }
    
    @Override
    public void setFocused(boolean focused) {
    }
    
    @Override
    public boolean isFocused() {
        return false;
    }
}
