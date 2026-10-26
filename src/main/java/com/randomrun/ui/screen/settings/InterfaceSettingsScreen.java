package com.randomrun.ui.screen.settings;

import com.randomrun.infrastructure.RandomRunMod;
import com.randomrun.infrastructure.config.ModConfig;
import com.randomrun.ui.widget.styled.ButtonDefault;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public class InterfaceSettingsScreen extends AnimatedSettingsScreen {
    private static final int BUTTON_WIDTH = 300;
    private static final int BUTTON_HEIGHT = 20;
    private static final int BUTTON_SPACING = 25;
    private static final int PENCIL_BUTTON_WIDTH = 25;
    
    private final ModConfig config;
    
    public InterfaceSettingsScreen(Screen parent) {
        this(parent, false);
    }
    
    public InterfaceSettingsScreen(Screen parent, boolean isRefreshing) {
        super(parent, isRefreshing, 
            com.randomrun.infrastructure.util.LanguageManager.text("randomrun.screen.interface_settings.title"));
        this.config = RandomRunMod.getInstance().getConfig();
    }
    
    @Override
    protected void initContent() {
        int widgetY = getWidgetY();
        int centerX = width / 2;
        int startY = widgetY + 50;
        int buttonIndex = 0;
        
        boolean isCustom = config.getHudPosition() == ModConfig.HudPosition.CUSTOM;
        int positionButtonWidth = isCustom ? BUTTON_WIDTH - PENCIL_BUTTON_WIDTH - 5 : BUTTON_WIDTH;
        
        addRenderableWidget(new ButtonDefault(
            centerX - BUTTON_WIDTH / 2, startY,
            positionButtonWidth, BUTTON_HEIGHT,
            com.randomrun.infrastructure.util.LanguageManager.text("randomrun.settings.hud_position", 
                formatPositionText(config.getHudPosition())),
            button -> {
                cycleHudPosition();
                refreshScreen();
            },
            buttonIndex++, 0.15f, isRefreshing
        ));
        
        if (isCustom) {
            addRenderableWidget(new ButtonDefault(
                centerX + BUTTON_WIDTH / 2 - PENCIL_BUTTON_WIDTH, startY,
                PENCIL_BUTTON_WIDTH, BUTTON_HEIGHT,
                Component.literal("✏️"),
                button -> Minecraft.getInstance().gui.setScreen(new HudPositionEditorScreen(this)),
                buttonIndex++, 0.15f, isRefreshing
            ));
        }
        
        addRenderableWidget(new ButtonDefault(
            centerX - BUTTON_WIDTH / 2, startY + BUTTON_SPACING,
            BUTTON_WIDTH, BUTTON_HEIGHT,
            Component.literal("Стиль HUD: " + formatHudStyleText(config.getHudStyle())),
            button -> {
                cycleHudStyle();
                refreshScreen();
            },
            buttonIndex++, 0.18f, isRefreshing
        ));
        
        addRenderableWidget(new ButtonDefault(
            centerX - BUTTON_WIDTH / 2, startY + BUTTON_SPACING * 2,
            BUTTON_WIDTH, BUTTON_HEIGHT,
            com.randomrun.infrastructure.util.LanguageManager.text("randomrun.settings.ui_style", 
                formatUIStyleText(config.getUIStyle())),
            button -> {
                toggleUIStyle();
                refreshScreen();
            },
            buttonIndex++, 0.2f, isRefreshing
        ));

        addRenderableWidget(new ButtonDefault(
            centerX - BUTTON_WIDTH / 2, widgetY + WIDGET_HEIGHT - 40,
            BUTTON_WIDTH, BUTTON_HEIGHT,
            com.randomrun.infrastructure.util.LanguageManager.text("randomrun.button.back"),
            button -> startClosing(),
            buttonIndex, 0.3f, isRefreshing
        ));
    }
    
    private void cycleHudPosition() {
        ModConfig.HudPosition[] positions = ModConfig.HudPosition.values();
        int currentIndex = config.getHudPosition().ordinal();
        int nextIndex = (currentIndex + 1) % positions.length;
        config.setHudPosition(positions[nextIndex]);
        RandomRunMod.getInstance().saveConfig();
    }
    
    private void cycleHudStyle() {
        ModConfig.HudStyle[] styles = ModConfig.HudStyle.values();
        int currentIndex = config.getHudStyle().ordinal();
        int nextIndex = (currentIndex + 1) % styles.length;
        config.setHudStyle(styles[nextIndex]);
        RandomRunMod.getInstance().saveConfig();
    }
    
    private void toggleUIStyle() {
        ModConfig.UIStyle newStyle = config.getUIStyle() == ModConfig.UIStyle.DETAILED 
            ? ModConfig.UIStyle.SIMPLIFIED 
            : ModConfig.UIStyle.DETAILED;
        config.setUIStyle(newStyle);
        RandomRunMod.getInstance().saveConfig();
    }
    
    private String formatPositionText(ModConfig.HudPosition position) {
        String text = switch (position) {
            case TOP_LEFT -> com.randomrun.infrastructure.util.LanguageManager.text("randomrun.settings.hud_position.top_left").getString();
            case TOP_RIGHT -> com.randomrun.infrastructure.util.LanguageManager.text("randomrun.settings.hud_position.top_right").getString();
            case TOP_CENTER -> com.randomrun.infrastructure.util.LanguageManager.text("randomrun.settings.hud_position.top_center").getString();
            case CUSTOM -> com.randomrun.infrastructure.util.LanguageManager.text("randomrun.settings.hud_position.custom").getString();
        };
        return "§e" + text;
    }
    
    private String formatUIStyleText(ModConfig.UIStyle style) {
        String text = style == ModConfig.UIStyle.DETAILED
            ? com.randomrun.infrastructure.util.LanguageManager.text("randomrun.settings.ui_style.detailed").getString()
            : com.randomrun.infrastructure.util.LanguageManager.text("randomrun.settings.ui_style.simplified").getString();
        String color = style == ModConfig.UIStyle.DETAILED ? "§a" : "§e";
        return color + text;
    }
    
    private String formatHudStyleText(ModConfig.HudStyle style) {
        return switch (style) {
            case PROFESSIONAL -> "§bПрофессиональный";
            case PLEASANT -> "§aПриятный";
        };
    }
    
    private void refreshScreen() {
        Minecraft.getInstance().gui.setScreen(new InterfaceSettingsScreen(parent, true));
    }
}
