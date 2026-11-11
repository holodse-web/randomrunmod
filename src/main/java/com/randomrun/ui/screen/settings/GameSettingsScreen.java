package com.randomrun.ui.screen.settings;

import com.randomrun.infrastructure.RandomRunMod;
import com.randomrun.infrastructure.config.ModConfig;
import com.randomrun.infrastructure.util.LanguageManager;
import com.randomrun.ui.widget.styled.OptionRow;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public class GameSettingsScreen extends AnimatedSettingsScreen {
    private final ModConfig config = RandomRunMod.getInstance().getConfig();

    public GameSettingsScreen(Screen parent) {
        super(parent, false, LanguageManager.text("randomrun.screen.speedrun_settings.title"));
    }

    @Override
    protected void initContent() {
        OptionRow victory = addRenderableWidget(new OptionRow(rowX(), rowY(0), rowWidth(), LanguageManager.text("randomrun.settings.victory_screen.label"),
            () -> switch (config.getVictoryScreenMode()) {
                case SHOW -> LanguageManager.text("randomrun.victory_screen.show").withStyle(s -> s.withColor(0x4ADE80));
                case HIDE -> LanguageManager.text("randomrun.victory_screen.hide").withStyle(s -> s.withColor(0xF87171));
                case SHOW_AFTER_10_SECONDS -> LanguageManager.text("randomrun.victory_screen.show_after_10").withStyle(s -> s.withColor(0xFACC15));
            },
            button -> {
                config.setVictoryScreenMode(switch (config.getVictoryScreenMode()) {
                    case SHOW -> ModConfig.VictoryScreenMode.HIDE;
                    case HIDE -> ModConfig.VictoryScreenMode.SHOW_AFTER_10_SECONDS;
                    case SHOW_AFTER_10_SECONDS -> ModConfig.VictoryScreenMode.SHOW;
                });
                RandomRunMod.getInstance().saveConfig();
            }, 0.05f));
        victory.setTooltip(Tooltip.create(LanguageManager.text("randomrun.settings.victory_screen.tooltip")));
        addRenderableWidget(toggle(1, "randomrun.settings.unobtainable_items.label", config::isAllowUnobtainableItems,
            () -> config.setAllowUnobtainableItems(!config.isAllowUnobtainableItems()), "randomrun.settings.unobtainable_items.tooltip"));
        addRenderableWidget(toggle(2, "randomrun.settings.delete_worlds.label", config::isDeleteWorldsAfterSpeedrun,
            () -> config.setDeleteWorldsAfterSpeedrun(!config.isDeleteWorldsAfterSpeedrun()), "randomrun.settings.delete_worlds.tooltip"));
        addRenderableWidget(toggle(3, "randomrun.settings.hardcore_mode.label", config::isHardcoreModeEnabled,
            () -> config.setHardcoreModeEnabled(!config.isHardcoreModeEnabled()), "randomrun.settings.hardcore_mode.tooltip"));
        addBackButton(5);
    }

    private OptionRow toggle(int index, String label, java.util.function.BooleanSupplier state, Runnable flip, String tooltip) {
        OptionRow row = new OptionRow(rowX(), rowY(index), rowWidth(), LanguageManager.text(label), () -> onOff(state.getAsBoolean()), button -> {
            flip.run();
            RandomRunMod.getInstance().saveConfig();
        }, 0.05f + index * 0.03f);
        row.setTooltip(Tooltip.create(LanguageManager.text(tooltip)));
        return row;
    }

    static Component onOff(boolean value) {
        return LanguageManager.text(value ? "randomrun.toggle.enabled" : "randomrun.toggle.disabled").withStyle(s -> s.withColor(value ? 0x4ADE80 : 0xF87171));
    }
}
