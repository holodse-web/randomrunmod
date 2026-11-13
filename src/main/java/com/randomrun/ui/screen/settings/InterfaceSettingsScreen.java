package com.randomrun.ui.screen.settings;

import com.randomrun.infrastructure.ModConfig;
import com.randomrun.infrastructure.RandomRunMod;
import com.randomrun.infrastructure.util.LanguageManager;
import com.randomrun.ui.util.Theme;
import com.randomrun.ui.widget.styled.Buttons.ButtonDefault;
import com.randomrun.ui.widget.styled.OptionRow;

import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public class InterfaceSettingsScreen extends AnimatedSettingsScreen {
    private final ModConfig config = RandomRunMod.getInstance().getConfig();
    private ButtonDefault editButton;

    public InterfaceSettingsScreen(Screen parent) {
        super(parent, false, LanguageManager.text("randomrun.screen.interface_settings.title"));
    }

    @Override
    protected void initContent() {
        int editWidth = 24;
        addRenderableWidget(new OptionRow(rowX(), rowY(0), rowWidth() - editWidth - 4, LanguageManager.text("randomrun.settings.hud_position.label"),
            () -> Component.literal(positionText(config.getHudPosition())).withStyle(s -> s.withColor(0xFACC15)), button -> {
                ModConfig.HudPosition[] values = ModConfig.HudPosition.values();
                config.setHudPosition(values[(config.getHudPosition().ordinal() + 1) % values.length]);
                RandomRunMod.getInstance().saveConfig();
                editButton.active = config.getHudPosition() == ModConfig.HudPosition.CUSTOM;
            }, 0.05f));
        editButton = addRenderableWidget(new ButtonDefault(rowX() + rowWidth() - editWidth, rowY(0), editWidth, 20, Component.literal("✎"),
            button -> minecraft.gui.setScreen(new HudPositionEditorScreen(this)), 0, 0.05f));
        editButton.icon(com.randomrun.ui.util.Icons.PENCIL);
        editButton.setTooltip(Tooltip.create(LanguageManager.text("randomrun.settings.hud_position.edit")));
        editButton.active = config.getHudPosition() == ModConfig.HudPosition.CUSTOM;

        addRenderableWidget(new OptionRow(rowX(), rowY(1), rowWidth(), LanguageManager.text("randomrun.settings.hud_style.label"),
            () -> LanguageManager.text(config.getHudStyle() == ModConfig.HudStyle.PROFESSIONAL ? "randomrun.settings.hud_style.professional" : "randomrun.settings.hud_style.pleasant")
                .withStyle(s -> s.withColor(config.getHudStyle() == ModConfig.HudStyle.PROFESSIONAL ? 0x67E8F9 : 0x4ADE80)),
            button -> {
                ModConfig.HudStyle[] values = ModConfig.HudStyle.values();
                config.setHudStyle(values[(config.getHudStyle().ordinal() + 1) % values.length]);
                RandomRunMod.getInstance().saveConfig();
            }, 0.08f));

        OptionRow animations = addRenderableWidget(new OptionRow(rowX(), rowY(2), rowWidth(), LanguageManager.text("randomrun.settings.ui_style.label"),
            () -> LanguageManager.text(config.getUIStyle() == ModConfig.UIStyle.DETAILED ? "randomrun.settings.ui_style.detailed" : "randomrun.settings.ui_style.simplified")
                .withStyle(s -> s.withColor(config.getUIStyle() == ModConfig.UIStyle.DETAILED ? 0x4ADE80 : 0xFACC15)),
            button -> {
                config.setUIStyle(config.getUIStyle() == ModConfig.UIStyle.DETAILED ? ModConfig.UIStyle.SIMPLIFIED : ModConfig.UIStyle.DETAILED);
                RandomRunMod.getInstance().saveConfig();
            }, 0.11f));
        animations.setTooltip(Tooltip.create(LanguageManager.text("randomrun.settings.ui_style.tooltip")));

        addRenderableWidget(new OptionRow(rowX(), rowY(3), rowWidth(), LanguageManager.text("randomrun.settings.theme.label"),
            () -> LanguageManager.text(Theme.current().nameKey()).withStyle(s -> s.withColor(Theme.current().accent)),
            button -> {
                config.setTheme(Theme.current().next().id);
                RandomRunMod.getInstance().saveConfig();
            }, 0.14f));

        addRenderableWidget(new OptionRow(rowX(), rowY(4), rowWidth(), LanguageManager.text("randomrun.settings.language.label"),
            () -> Component.literal(LanguageManager.getCurrentLanguageEnum().getDisplayName()), button -> LanguageManager.cycleLanguage(), 0.17f));
        addBackButton(6);
    }

    private String positionText(ModConfig.HudPosition position) {
        return LanguageManager.translate(switch (position) {
            case TOP_LEFT -> "randomrun.settings.hud_position.top_left";
            case TOP_RIGHT -> "randomrun.settings.hud_position.top_right";
            case TOP_CENTER -> "randomrun.settings.hud_position.top_center";
            case CUSTOM -> "randomrun.settings.hud_position.custom";
        });
    }
}
