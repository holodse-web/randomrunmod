package com.randomrun.ui.screen.settings;

import com.randomrun.ui.screen.main.AbstractRandomRunScreen;

import com.randomrun.main.RandomRunMod;
import com.randomrun.main.config.ModConfig;
import com.randomrun.ui.widget.styled.ButtonDefault;
import com.randomrun.ui.widget.styled.SliderDefault;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;

public class SoundSettingsScreen extends AbstractRandomRunScreen {
    private final Screen parent;
    private final ModConfig config;
    private final boolean isRefreshing;
    
    public SoundSettingsScreen(Screen parent) {
        this(parent, false);
    }
    
    public SoundSettingsScreen(Screen parent, boolean isRefreshing) {
        super(Text.translatable("randomrun.screen.sound_settings.title"));
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
        int spacing = 35;
        
        // Sound effects toggle
        String toggleText = config.isSoundEffectsEnabled() 
            ? "§a" + Text.translatable("randomrun.toggle.enabled").getString()
            : "§c" + Text.translatable("randomrun.toggle.disabled").getString();
        addDrawableChild(new ButtonDefault(
            centerX - buttonWidth / 2, startY,
            buttonWidth, buttonHeight,
            Text.translatable("randomrun.settings.sound_effects", toggleText),
            button -> {
                config.setSoundEffectsEnabled(!config.isSoundEffectsEnabled());
                RandomRunMod.getInstance().saveConfig();
                refreshScreen();
            },
            0, 0.1f, isRefreshing
        ));
        
        if (config.isSoundEffectsEnabled()) {
            addDrawableChild(new SliderDefault(
                centerX - buttonWidth / 2, startY + spacing,
                buttonWidth, buttonHeight,
                Text.translatable("randomrun.settings.volume"),
                config.getSoundVolume() / 100.0,
                value -> {
                    config.setSoundVolume((int)(value * 100));
                    RandomRunMod.getInstance().saveConfig();
                },
                0.1f
            ) {
                @Override
                protected void updateMessage() {
                    int percent = (int)(this.value * 100);
                    setMessage(Text.translatable("randomrun.settings.volume").append(": " + percent + "%"));
                }
            });
        }
        
        
        // Back button (aligned with main menu)
        addDrawableChild(new ButtonDefault(
            centerX - 50, height - 30,
            100, buttonHeight,
            Text.translatable("randomrun.button.back"),
            button -> MinecraftClient.getInstance().setScreen(parent),
            1, 0.15f, isRefreshing
        ));
    }
    
    private void refreshScreen() {
        MinecraftClient.getInstance().setScreen(new SoundSettingsScreen(parent, true));
    }
    
    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);
        
        // Title
        context.drawCenteredTextWithShadow(textRenderer, title, width / 2, 20, 0xFFFFFF);
    }
    
}
