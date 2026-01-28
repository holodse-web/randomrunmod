package com.randomrun.gui.screen;

import com.randomrun.RandomRunMod;
import com.randomrun.config.ModConfig;
import com.randomrun.gui.widget.StyledButton2;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.item.ItemStack;
import net.minecraft.item.Items;
import net.minecraft.text.Text;

public class HudPositionEditorScreen extends AbstractRandomRunScreen {
    private final Screen parent;
    private final ModConfig config;
    
    private int hudX;
    private int hudY;
    private boolean dragging = false;
    private float borderAnimation = 0f;
    
    private static final int HUD_SIZE = 48;
    private static final int SNAP_DISTANCE = 10;
    private static final int BORDER_WIDTH = 2;
    
    public HudPositionEditorScreen(Screen parent) {
        super(Text.translatable("randomrun.screen.hud_editor.title"));
        this.parent = parent;
        this.config = RandomRunMod.getInstance().getConfig();
        this.hudX = config.getCustomHudX();
        this.hudY = config.getCustomHudY();
    }
    
    @Override
    protected void init() {
        super.init();
        int centerX = width / 2;
        
       
        addDrawableChild(new StyledButton2(
            centerX - 105, height - 30,
            100, 20,
            Text.translatable("randomrun.button.save"),
            button -> {
                config.setCustomHudX(hudX);
                config.setCustomHudY(hudY);
                RandomRunMod.getInstance().saveConfig();
                MinecraftClient.getInstance().setScreen(parent);
            },
            0, 0.1f
        ));
        
        // Cancel button
        addDrawableChild(new StyledButton2(
            centerX + 5, height - 30,
            100, 20,
            Text.translatable("randomrun.button.cancel"),
            button -> MinecraftClient.getInstance().setScreen(parent),
            1, 0.15f
        ));
    }
    
    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);
        
       
        borderAnimation += delta * 0.01f;
        if (borderAnimation > 1.0f) borderAnimation -= 1.0f;
        
       
        renderGuidelines(context);
        
        
        renderHudPreview(context, mouseX, mouseY);
        
       
        context.drawCenteredTextWithShadow(textRenderer, title, width / 2, 10, 0xFFFFFF);
        context.drawCenteredTextWithShadow(textRenderer,
            Text.translatable("randomrun.hud_editor.instructions"),
            width / 2, 30, 0xAAAAAA);
        
        context.drawCenteredTextWithShadow(textRenderer,
            Text.literal("X: " + hudX + " Y: " + hudY),
            width / 2, height - 50, 0x888888);
    }
    
    private void renderGuidelines(DrawContext context) {
        int centerX = width / 2;
        int centerY = height / 2;
        int lineAlpha = 0x66;
        int color = (lineAlpha << 24) | 0x55FF55;
        
        
        context.fill(centerX - 1, 0, centerX + 1, height, color);
       
        context.fill(0, centerY - 1, width, centerY + 1, color);
    }
    
    private void renderHudPreview(DrawContext context, int mouseX, int mouseY) {
        boolean hovered = mouseX >= hudX && mouseX <= hudX + HUD_SIZE &&
                         mouseY >= hudY && mouseY <= hudY + HUD_SIZE;
        
      
        context.fill(hudX, hudY, hudX + HUD_SIZE, hudY + HUD_SIZE, 0xC0000000);
        
       
        renderAnimatedBorder(context, hudX, hudY);
        
       
        ItemStack diamondStack = new ItemStack(Items.DIAMOND);
        context.getMatrices().push();
        int itemX = hudX + HUD_SIZE / 2;
        int itemY = hudY + HUD_SIZE / 2 - 4;
        context.getMatrices().translate(itemX, itemY, 0);
        context.getMatrices().scale(2.0f, 2.0f, 1.0f);
        context.drawItem(diamondStack, -8, -8);
        context.getMatrices().pop();
        
      
        String timeStr = "00:00.000";
        context.getMatrices().push();
        context.getMatrices().scale(0.7f, 0.7f, 1.0f);
        int scaledTimeWidth = (int)(textRenderer.getWidth(timeStr) * 0.7f);
        int timeX = (int)((hudX + (HUD_SIZE - scaledTimeWidth) / 2) / 0.7f);
        int timeY = (int)((hudY + HUD_SIZE - 10) / 0.7f);
        context.drawTextWithShadow(textRenderer, "§e" + timeStr, timeX, timeY, 0xFFFFFF);
        context.getMatrices().pop();
        
      
        if (hovered || dragging) {
            context.fill(hudX, hudY, hudX + HUD_SIZE, hudY + 1, 0x40FFFFFF);
            context.fill(hudX, hudY, hudX + 1, hudY + HUD_SIZE, 0x40FFFFFF);
        }
        
       
        int centerX = width / 2;
        int centerY = height / 2;
        int hudCenterX = hudX + HUD_SIZE / 2;
        int hudCenterY = hudY + HUD_SIZE / 2;
        
        if (Math.abs(hudCenterX - centerX) < SNAP_DISTANCE * 2) {
            context.fill(centerX - 2, hudY, centerX + 2, hudY + HUD_SIZE, 0xFF00FF00);
        }
        if (Math.abs(hudCenterY - centerY) < SNAP_DISTANCE * 2) {
            context.fill(hudX, centerY - 2, hudX + HUD_SIZE, centerY + 2, 0xFF00FF00);
        }
    }
    
    private void renderAnimatedBorder(DrawContext context, int x, int y) {
        int color = getRainbowColor(borderAnimation);
        int t = BORDER_WIDTH;
        
        context.fill(x, y, x + HUD_SIZE, y + t, color);
        context.fill(x, y + HUD_SIZE - t, x + HUD_SIZE, y + HUD_SIZE, color);
        context.fill(x, y + t, x + t, y + HUD_SIZE - t, color);
        context.fill(x + HUD_SIZE - t, y + t, x + HUD_SIZE, y + HUD_SIZE - t, color);
    }
    
    private int getRainbowColor(float t) {
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
    
    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            if (mouseX >= hudX && mouseX <= hudX + HUD_SIZE &&
                mouseY >= hudY && mouseY <= hudY + HUD_SIZE) {
                dragging = true;
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }
    
    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (button == 0) {
            dragging = false;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }
    
    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double deltaX, double deltaY) {
        if (dragging) {
            hudX = (int) (mouseX - HUD_SIZE / 2);
            hudY = (int) (mouseY - HUD_SIZE / 2);
            
            
            int centerX = width / 2;
            int centerY = height / 2;
            int hudCenterX = hudX + HUD_SIZE / 2;
            int hudCenterY = hudY + HUD_SIZE / 2;
            
            if (Math.abs(hudCenterX - centerX) < SNAP_DISTANCE) {
                hudX = centerX - HUD_SIZE / 2;
            }
            if (Math.abs(hudCenterY - centerY) < SNAP_DISTANCE) {
                hudY = centerY - HUD_SIZE / 2;
            }
            
          
            hudX = Math.max(0, Math.min(width - HUD_SIZE, hudX));
            hudY = Math.max(0, Math.min(height - HUD_SIZE, hudY));
            
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, deltaX, deltaY);
    }
}