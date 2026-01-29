package com.randomrun.ui.screen;

import com.randomrun.main.RandomRunMod;
import com.randomrun.main.config.ModConfig;
import com.randomrun.ui.widget.StyledButton2;
import com.randomrun.ui.widget.VolumeSlider;
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
        addDrawableChild(new StyledButton2(
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
        
        // Volume slider (only if sound effects are enabled)
        if (config.isSoundEffectsEnabled()) {
            addDrawableChild(new VolumeSlider(
                centerX - buttonWidth / 2, startY + spacing,
                buttonWidth, buttonHeight,
                Text.translatable("randomrun.settings.volume"),
                config.getSoundVolume(),
                value -> {
                    config.setSoundVolume(value);
                    RandomRunMod.getInstance().saveConfig();
                },
                isRefreshing
            ));
        }
        
        
        // Back button (aligned with main menu)
        addDrawableChild(new StyledButton2(
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
