package com.randomrun.ui.screen.settings;

import com.randomrun.infrastructure.RandomRunMod;
import com.randomrun.infrastructure.config.ModConfig;
import com.randomrun.infrastructure.util.LanguageManager;
import com.randomrun.ui.util.Ui;
import com.randomrun.ui.widget.styled.OptionRow;
import com.randomrun.ui.widget.styled.SliderDefault;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.sounds.SoundEvents;

public class SoundSettingsScreen extends AnimatedSettingsScreen {
    private final ModConfig config = RandomRunMod.getInstance().getConfig();
    private SliderDefault volume;

    public SoundSettingsScreen(Screen parent) {
        super(parent, false, LanguageManager.text("randomrun.screen.sound_settings.title"));
    }

    @Override
    protected void initContent() {
        addRenderableWidget(new OptionRow(rowX(), rowY(0), rowWidth(), LanguageManager.text("randomrun.settings.sound_effects.label"),
            () -> GameSettingsScreen.onOff(config.isSoundEffectsEnabled()), button -> {
                config.setSoundEffectsEnabled(!config.isSoundEffectsEnabled());
                RandomRunMod.getInstance().saveConfig();
                volume.active = config.isSoundEffectsEnabled();
            }, 0.05f));
        volume = addRenderableWidget(new SliderDefault(rowX(), rowY(1), rowWidth(), 20,
            LanguageManager.text("randomrun.settings.volume"), config.getSoundVolume() / 100.0, value -> {
                config.setSoundVolume((int) Math.round(value * 100));
                RandomRunMod.getInstance().saveConfig();
            }, 0.08f) {
            {
                updateMessage();
            }

            @Override
            protected void updateMessage() {
                setMessage(LanguageManager.text("randomrun.settings.volume").append(": " + (int) Math.round(this.value * 100) + "%"));
            }

            @Override
            public void onRelease(net.minecraft.client.input.MouseButtonEvent event) {
                super.onRelease(event);
                Ui.playSound(SoundEvents.NOTE_BLOCK_PLING.value(), 1.2f, 0.8f);
            }
        });
        volume.active = config.isSoundEffectsEnabled();
        addBackButton(3);
    }
}
