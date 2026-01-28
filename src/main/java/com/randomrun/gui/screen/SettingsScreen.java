package com.randomrun.gui.screen;

import com.randomrun.gui.widget.StyledButton;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;

public class SettingsScreen extends AbstractRandomRunScreen {
    private final Screen parent;
    
    public SettingsScreen(Screen parent) {
        super(Text.translatable("randomrun.screen.settings.title"));
        this.parent = parent;
    }
    
    @Override
    protected void init() {
        super.init();
        int centerX = width / 2;
        int buttonWidth = 200;
        int buttonHeight = 20;
        int startY = 60;
        int spacing = 30;
        
        // Speedrun settings
        addDrawableChild(new StyledButton(
            centerX - buttonWidth / 2, startY,
            buttonWidth, buttonHeight,
            Text.translatable("randomrun.settings.speedrun"),
            button -> MinecraftClient.getInstance().setScreen(new SpeedrunSettingsScreen(this)),
            0, 0.1f
        ));
        
        // Interface settings (HUD editor)
        addDrawableChild(new StyledButton(
            centerX - buttonWidth / 2, startY + spacing,
            buttonWidth, buttonHeight,
            Text.translatable("randomrun.settings.interface"),
            button -> MinecraftClient.getInstance().setScreen(new InterfaceSettingsScreen(this)),
            1, 0.15f
        ));
        
        // Sound settings
        addDrawableChild(new StyledButton(
            centerX - buttonWidth / 2, startY + spacing * 2,
            buttonWidth, buttonHeight,
            Text.translatable("randomrun.settings.sound"),
            button -> MinecraftClient.getInstance().setScreen(new SoundSettingsScreen(this)),
            2, 0.2f
        ));
        
        // Time challenge settings
        addDrawableChild(new StyledButton(
            centerX - buttonWidth / 2, startY + spacing * 3,
            buttonWidth, buttonHeight,
            Text.translatable("randomrun.settings.time_challenge"),
            button -> MinecraftClient.getInstance().setScreen(new TimeChallengeSettingsScreen(this)),
            3, 0.25f
        ));
        
        // Back button
        addDrawableChild(new StyledButton(
            centerX - buttonWidth / 2, height - 30,
            buttonWidth, buttonHeight,
            Text.translatable("randomrun.button.back"),
            button -> MinecraftClient.getInstance().setScreen(parent),
            4, 0.3f
        ));
    }
    
    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);
        
        // Title
        context.drawCenteredTextWithShadow(textRenderer, title, width / 2, 20, 0xFFFFFF);
    }
    
}
