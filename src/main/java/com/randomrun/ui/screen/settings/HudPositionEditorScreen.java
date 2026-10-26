package com.randomrun.ui.screen.settings;

import com.randomrun.infrastructure.RandomRunMod;
import com.randomrun.infrastructure.config.ModConfig;
import com.randomrun.infrastructure.util.LanguageManager;
import com.randomrun.ui.screen.main.AbstractRandomRunScreen;
import com.randomrun.ui.widget.styled.ButtonDefault;
import java.awt.Color;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Renderable;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public class HudPositionEditorScreen extends AbstractRandomRunScreen {
    private static final int HUD_SIZE = 48;
    private static final int SNAP_DISTANCE = 10;
    private static final int BORDER_WIDTH = 2;
    private static final float TIME_SCALE = 0.7f;
    private static final float ITEM_SCALE = 2.0f;
    
    private static final int COLOR_OVERLAY = 0x60000000;
    private static final ItemStack PREVIEW_ITEM = com.randomrun.ui.util.PreviewItems.stack(Items.DIAMOND);
    private static final int COLOR_HUD_BG = 0xC0000000;
    private static final int COLOR_GUIDELINE = 0x66666655;
    private static final int COLOR_SNAP = 0xFF00FF00;
    private static final int COLOR_HIGHLIGHT = 0x4000FF00;
    private static final int COLOR_TEXT_GRAY = 0x888888;
    private static final int COLOR_TEXT_LIGHT = 0xAAAAAA;
    private static final int COLOR_TEXT_WHITE = 0xFFFFFF;
    
    private static final Identifier BACKGROUND_TEXTURE = Identifier.fromNamespaceAndPath("randomrun", "textures/gui/hud_editor_background.png");
    
    private final Screen parent;
    private final ModConfig config;
    private int hudX;
    private int hudY;
    private boolean dragging = false;
    private float borderAnimation = 0.0f;
    
    public HudPositionEditorScreen(Screen parent) {
        super(LanguageManager.text("randomrun.screen.hud_editor.title"));
        this.parent = parent;
        this.config = RandomRunMod.getInstance().getConfig();
        this.hudX = config.getCustomHudX();
        this.hudY = config.getCustomHudY();
    }
    
    @Override
    protected void init() {
        super.init();
        int centerX = width / 2;
        
        addRenderableWidget(new ButtonDefault(
            centerX - 105, height - 30,
            100, 20,
            LanguageManager.text("randomrun.button.save"),
            button -> {
                config.setCustomHudX(hudX);
                config.setCustomHudY(hudY);
                RandomRunMod.getInstance().saveConfig();
                Minecraft.getInstance().gui.setScreen(parent);
            },
            0, 0.1f
        ));
        
        addRenderableWidget(new ButtonDefault(
            centerX + 5, height - 30,
            100, 20,
            LanguageManager.text("randomrun.button.cancel"),
            button -> Minecraft.getInstance().gui.setScreen(parent),
            1, 0.15f
        ));
    }
    
    @Override
    public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        renderBackgroundTexture(context);
        context.fill(0, 0, width, height, COLOR_OVERLAY);
        
        borderAnimation += delta * 0.01f;
        if (borderAnimation > 1.0f) borderAnimation -= 1.0f;
        
        renderGuidelines(context);
        renderHudPreview(context, mouseX, mouseY);
        
        for (var element : children()) {
            if (element instanceof Renderable drawable) {
                drawable.extractRenderState(context, mouseX, mouseY, delta);
            }
        }
        
        com.randomrun.ui.util.GuiText.centeredText(context, font, title, width / 2, 10, COLOR_TEXT_WHITE);
        com.randomrun.ui.util.GuiText.centeredText(context, font, 
            LanguageManager.text("randomrun.hud_editor.instructions"), 
            width / 2, 30, COLOR_TEXT_LIGHT);
        
        com.randomrun.ui.util.GuiText.centeredText(context, font, 
            Component.literal("X: " + hudX + " Y: " + hudY), 
            width / 2, height - 50, COLOR_TEXT_GRAY);
    }
    
    private void renderBackgroundTexture(GuiGraphicsExtractor context) {
        try {
            context.blit(net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED, BACKGROUND_TEXTURE, 
                0, 0, 0.0f, 0.0f, width, height, width, height);
        } catch (Exception e) {
            context.fillGradient(0, 0, width, height, 0xFF1A0033, 0xFF0D001A);
        }
    }
    
    private void renderGuidelines(GuiGraphicsExtractor context) {
        int centerX = width / 2;
        int centerY = height / 2;
        context.fill(centerX - 1, 0, centerX + 1, height, COLOR_GUIDELINE);
        context.fill(0, centerY - 1, width, centerY + 1, COLOR_GUIDELINE);
    }
    
    private void renderHudPreview(GuiGraphicsExtractor context, int mouseX, int mouseY) {
        boolean hovered = isHovered(mouseX, mouseY);
        
        ModConfig.HudStyle hudStyle = config.getHudStyle();
        
        if (hudStyle == ModConfig.HudStyle.PROFESSIONAL) {
            renderProfessionalPreview(context);
        } else {
            context.fill(hudX, hudY, hudX + HUD_SIZE, hudY + HUD_SIZE, COLOR_HUD_BG);
            renderAnimatedBorder(context, hudX, hudY);
            renderHudItem(context);
            renderHudTime(context);
        }
        
        if (hovered || dragging) {
            renderHudHighlight(context);
        }
        
        renderSnapIndicators(context);
    }
    
    private void renderProfessionalPreview(GuiGraphicsExtractor context) {
        String itemName = "Diamond";
        String rtaStr = "RTA: 00:00.000";
        String igtStr = "IGT: 00:00.000";
        
        int textWidth = Math.max(font.width(itemName),
            Math.max(font.width(rtaStr), font.width(igtStr))) + 6;
        int totalHeight = 35;
        
        context.fill(hudX, hudY, hudX + textWidth, hudY + totalHeight, COLOR_HUD_BG);
        renderAnimatedBorder(context, hudX, hudY);
        
        com.randomrun.ui.util.GuiText.text(context, font, itemName, hudX + 3, hudY + 2, COLOR_TEXT_WHITE, false);
        com.randomrun.ui.util.GuiText.text(context, font, "§7RTA: §e00:00.000", hudX + 3, hudY + 13, COLOR_TEXT_WHITE, false);
        com.randomrun.ui.util.GuiText.text(context, font, "§7IGT: §a00:00.000", hudX + 3, hudY + 24, COLOR_TEXT_WHITE, false);
    }
    
    private boolean isHovered(int mouseX, int mouseY) {
        return mouseX >= hudX && mouseX <= hudX + HUD_SIZE && 
               mouseY >= hudY && mouseY <= hudY + HUD_SIZE;
    }
    
    private void renderHudItem(GuiGraphicsExtractor context) {
        context.pose().pushMatrix();
        int itemX = hudX + HUD_SIZE / 2;
        int itemY = hudY + HUD_SIZE / 2 - 4;
        context.pose().translate(itemX, itemY);
        context.pose().scale(ITEM_SCALE, ITEM_SCALE);
        context.item(PREVIEW_ITEM, -8, -8);
        context.pose().popMatrix();
    }
    
    private void renderHudTime(GuiGraphicsExtractor context) {
        String timeStr = "00:00.000";
        context.pose().pushMatrix();
        context.pose().scale(TIME_SCALE, TIME_SCALE);
        int scaledTimeWidth = (int)(font.width(timeStr) * TIME_SCALE);
        int timeX = (int)((hudX + (HUD_SIZE - scaledTimeWidth) / 2) / TIME_SCALE);
        int timeY = (int)((hudY + HUD_SIZE - 10) / TIME_SCALE);
        com.randomrun.ui.util.GuiText.text(context, font, "§e" + timeStr, timeX, timeY, COLOR_TEXT_WHITE, false);
        context.pose().popMatrix();
    }
    
    private void renderHudHighlight(GuiGraphicsExtractor context) {
        context.fill(hudX, hudY, hudX + HUD_SIZE, hudY + 1, COLOR_HIGHLIGHT);
        context.fill(hudX, hudY, hudX + 1, hudY + HUD_SIZE, COLOR_HIGHLIGHT);
    }
    
    private void renderSnapIndicators(GuiGraphicsExtractor context) {
        int centerX = width / 2;
        int centerY = height / 2;
        int hudCenterX = hudX + HUD_SIZE / 2;
        int hudCenterY = hudY + HUD_SIZE / 2;
        
        if (Math.abs(hudCenterX - centerX) < 20) {
            context.fill(centerX - 2, hudY, centerX + 2, hudY + HUD_SIZE, COLOR_SNAP);
        }
        if (Math.abs(hudCenterY - centerY) < 20) {
            context.fill(hudX, centerY - 2, hudX + HUD_SIZE, centerY + 2, COLOR_SNAP);
        }
    }
    
    private void renderAnimatedBorder(GuiGraphicsExtractor context, int x, int y) {
        int color = getRainbowColor(borderAnimation);
        int t = BORDER_WIDTH;
        
        context.fill(x, y, x + HUD_SIZE, y + t, color);
        context.fill(x, y + HUD_SIZE - t, x + HUD_SIZE, y + HUD_SIZE, color);
        context.fill(x, y + t, x + t, y + HUD_SIZE - t, color);
        context.fill(x + HUD_SIZE - t, y + t, x + HUD_SIZE, y + HUD_SIZE - t, color);
    }
    
    private int getRainbowColor(float t) {
        float hue = t * 360.0f;
        int rgb = Color.HSBtoRGB(hue / 360.0f, 1.0f, 1.0f);
        return 0xFF000000 | (rgb & 0x00FFFFFF);
    }
    
    @Override
    public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent event, boolean doubleClick) {
        double mouseX = event.x();
        double mouseY = event.y();
        int button = event.button();
        if (button == com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_LEFT && isHovered((int)mouseX, (int)mouseY)) {
            dragging = true;
            return true;
        }
        return super.mouseClicked(new net.minecraft.client.input.MouseButtonEvent(mouseX, mouseY, event.buttonInfo()), doubleClick);
    }
    
    @Override
    public boolean mouseReleased(net.minecraft.client.input.MouseButtonEvent event) {
        double mouseX = event.x();
        double mouseY = event.y();
        int button = event.button();
        if (button == com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_LEFT) {
            dragging = false;
        }
        return super.mouseReleased(new net.minecraft.client.input.MouseButtonEvent(mouseX, mouseY, event.buttonInfo()));
    }
    
    @Override
    public boolean mouseDragged(net.minecraft.client.input.MouseButtonEvent event, double deltaX, double deltaY) {
        double mouseX = event.x();
        double mouseY = event.y();
        int button = event.button();
        if (dragging) {
            hudX = (int)(mouseX - HUD_SIZE / 2);
            hudY = (int)(mouseY - HUD_SIZE / 2);
            
            applySnapping();
            clampPosition();
            
            return true;
        }
        return super.mouseDragged(new net.minecraft.client.input.MouseButtonEvent(mouseX, mouseY, event.buttonInfo()), deltaX, deltaY);
    }
    
    private void applySnapping() {
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
    }
    
    private void clampPosition() {
        hudX = Math.max(0, Math.min(width - HUD_SIZE, hudX));
        hudY = Math.max(0, Math.min(height - HUD_SIZE, hudY));
    }
}
