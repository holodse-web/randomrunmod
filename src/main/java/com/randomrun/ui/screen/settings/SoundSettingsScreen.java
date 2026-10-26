package com.randomrun.ui.screen.settings;

import com.randomrun.infrastructure.RandomRunMod;
import com.randomrun.infrastructure.config.ModConfig;
import com.randomrun.ui.widget.styled.ButtonDefault;
import com.randomrun.ui.widget.styled.SliderDefault;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;

public class SoundSettingsScreen extends AnimatedSettingsScreen {
    private static final int BUTTON_WIDTH = 300;
    private static final int BUTTON_HEIGHT = 20;
    private static final int SPACING = 35;
    
    private final ModConfig config;
    
    public SoundSettingsScreen(Screen parent) {
        this(parent, false);
    }
    
    public SoundSettingsScreen(Screen parent, boolean isRefreshing) {
        super(parent, isRefreshing, com.randomrun.infrastructure.util.LanguageManager.text("randomrun.screen.sound_settings.title"));
        this.config = RandomRunMod.getInstance().getConfig();
    }
    
    @Override
    protected void initContent() {
        int centerX = width / 2;
        int widgetY = getWidgetY();
        int startY = widgetY + 50;
        
        addRenderableWidget(new ButtonDefault(
            centerX - BUTTON_WIDTH / 2, startY,
            BUTTON_WIDTH, BUTTON_HEIGHT,
            getSoundEffectsToggleText(),
            button -> {
                config.setSoundEffectsEnabled(!config.isSoundEffectsEnabled());
                RandomRunMod.getInstance().saveConfig();
                refreshScreen();
            },
            0, 0.1f, isRefreshing
        ));
        
        if (config.isSoundEffectsEnabled()) {
            addRenderableWidget(new SliderDefault(
                centerX - BUTTON_WIDTH / 2, startY + SPACING,
                BUTTON_WIDTH, BUTTON_HEIGHT,
                com.randomrun.infrastructure.util.LanguageManager.text("randomrun.settings.volume"),
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
                    setMessage(com.randomrun.infrastructure.util.LanguageManager.text("randomrun.settings.volume").append(": " + percent + "%"));
                }
            });
        }
        
        addRenderableWidget(new ButtonDefault(
            centerX - BUTTON_WIDTH / 2, widgetY + WIDGET_HEIGHT - 40,
            BUTTON_WIDTH, BUTTON_HEIGHT,
            com.randomrun.infrastructure.util.LanguageManager.text("randomrun.button.back"),
            button -> startClosing(),
            1, 0.15f, isRefreshing
        ));
    }
    
    private net.minecraft.network.chat.Component getSoundEffectsToggleText() {
        String status = config.isSoundEffectsEnabled() 
            ? "§a" + com.randomrun.infrastructure.util.LanguageManager.text("randomrun.toggle.enabled").getString()
            : "§c" + com.randomrun.infrastructure.util.LanguageManager.text("randomrun.toggle.disabled").getString();
        return com.randomrun.infrastructure.util.LanguageManager.text("randomrun.settings.sound_effects", status);
    }
    
    private void refreshScreen() {
        Minecraft.getInstance().gui.setScreen(new SoundSettingsScreen(parent, true));
    }
}
