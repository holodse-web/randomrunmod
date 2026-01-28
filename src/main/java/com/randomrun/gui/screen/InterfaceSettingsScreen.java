package com.randomrun.gui.screen;

import com.randomrun.RandomRunMod;
import com.randomrun.config.ModConfig;
import com.randomrun.gui.widget.StyledButton2;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;

public class InterfaceSettingsScreen extends AbstractRandomRunScreen {
    private final Screen parent;
    private final ModConfig config;
    private final boolean isRefreshing;
    
    public InterfaceSettingsScreen(Screen parent) {
        this(parent, false);
    }
    
    public InterfaceSettingsScreen(Screen parent, boolean isRefreshing) {
        super(Text.translatable("randomrun.screen.interface_settings.title"));
        this.parent = parent;
        this.config = RandomRunMod.getInstance().getConfig();
        this.isRefreshing = isRefreshing;
    }
    
    @Override
    protected void init() {
        super.init();
        int centerX = width / 2;
        int buttonWidth = 250;
        int buttonHeight = 20;
        int startY = 60;
        int spacing = 30;
        int buttonIndex = 0;
        
      
        boolean isCustom = config.getHudPosition() == ModConfig.HudPosition.CUSTOM;
        int positionButtonWidth = isCustom ? buttonWidth - 30 : buttonWidth;
        
        String positionText = "§e" + getPositionText(config.getHudPosition());
        addDrawableChild(new StyledButton2(
            centerX - buttonWidth / 2, startY,
            positionButtonWidth, buttonHeight,
            Text.translatable("randomrun.settings.hud_position", positionText),
            button -> {
                ModConfig.HudPosition[] positions = ModConfig.HudPosition.values();
                int currentIndex = config.getHudPosition().ordinal();
                int nextIndex = (currentIndex + 1) % positions.length;
                config.setHudPosition(positions[nextIndex]);
                RandomRunMod.getInstance().saveConfig();
                refreshScreen();
            },
            buttonIndex++, 0.15f, isRefreshing
        ));
        
        
        if (isCustom) {
            addDrawableChild(new StyledButton2(
                centerX + buttonWidth / 2 - 25, startY,
                25, buttonHeight,
                Text.literal("✏️"),
                button -> MinecraftClient.getInstance().setScreen(new HudPositionEditorScreen(this)),
                buttonIndex++, 0.15f, isRefreshing
            ));
        }
        
        
        addDrawableChild(new StyledButton2(
            centerX - 50, height - 30,
            100, buttonHeight,
            Text.translatable("randomrun.button.back"),
            button -> MinecraftClient.getInstance().setScreen(parent),
            buttonIndex++, 0.2f, isRefreshing
        ));
    }
    
    private String getPositionText(ModConfig.HudPosition position) {
        return switch (position) {
            case TOP_LEFT -> Text.translatable("randomrun.settings.hud_position.top_left").getString();
            case TOP_RIGHT -> Text.translatable("randomrun.settings.hud_position.top_right").getString();
            case TOP_CENTER -> Text.translatable("randomrun.settings.hud_position.top_center").getString();
            case CUSTOM -> Text.translatable("randomrun.settings.hud_position.custom").getString();
        };
    }
    
    private void refreshScreen() {
        MinecraftClient.getInstance().setScreen(new InterfaceSettingsScreen(parent, true));
    }
    
    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);
        
        // Title
        context.drawCenteredTextWithShadow(textRenderer, title, width / 2, 20, 0xFFFFFF);
    }
    
}
