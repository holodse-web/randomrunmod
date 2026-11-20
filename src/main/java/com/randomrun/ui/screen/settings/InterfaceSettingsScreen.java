package com.randomrun.ui.screen.settings;

import com.randomrun.infrastructure.ModConfig;
import com.randomrun.infrastructure.RandomRunMod;
import com.randomrun.infrastructure.util.LanguageManager;
import com.randomrun.ui.util.Theme;
import com.randomrun.ui.widget.styled.Buttons.ButtonDefault;
import com.randomrun.ui.widget.styled.OptionRow;

import java.util.function.IntConsumer;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public class InterfaceSettingsScreen extends AnimatedSettingsScreen {
    private static final ModConfig.HudPosition[] PRESETS = {
        ModConfig.HudPosition.TOP_LEFT, ModConfig.HudPosition.TOP_CENTER, ModConfig.HudPosition.TOP_RIGHT
    };
    private final ModConfig config = RandomRunMod.getInstance().getConfig();

    public InterfaceSettingsScreen(Screen parent) {
        super(parent, false, LanguageManager.text("randomrun.screen.interface_settings.title"));
    }

    @Override
    protected void initContent() {
        int editWidth = 24;
        cycler(row(0, rowWidth() - editWidth - 4, "randomrun.settings.hud_position.label",
            () -> Component.literal(positionText(config.getHudPosition())).withStyle(s -> s.withColor(0xFACC15)), 0.05f), this::stepPosition);
        ButtonDefault editButton = addRenderableWidget(new ButtonDefault(rowX() + rowWidth() - editWidth, rowY(0), editWidth, 20, Component.literal("✎"),
            button -> minecraft.gui.setScreen(new HudPositionEditorScreen(this)), 0, 0.05f));
        editButton.icon(com.randomrun.ui.util.Icons.PENCIL);
        editButton.setTooltip(Tooltip.create(LanguageManager.text("randomrun.settings.hud_position.edit")));

        cycler(row(1, rowWidth(), "randomrun.settings.hud_style.label",
            () -> LanguageManager.text(config.getHudStyle() == ModConfig.HudStyle.PROFESSIONAL ? "randomrun.settings.hud_style.professional" : "randomrun.settings.hud_style.pleasant")
                .withStyle(s -> s.withColor(config.getHudStyle() == ModConfig.HudStyle.PROFESSIONAL ? 0x67E8F9 : 0x4ADE80)), 0.08f),
            step -> config.setHudStyle(next(ModConfig.HudStyle.values(), config.getHudStyle(), step)));

        cycler(row(2, rowWidth(), "randomrun.settings.hud_size.label",
            () -> LanguageManager.text("randomrun.settings.hud_size." + config.getHudSize().name().toLowerCase(java.util.Locale.ROOT))
                .withStyle(style -> style.withColor(0xFACC15)), 0.1f),
            step -> config.setHudSize(next(ModConfig.HudSize.values(), config.getHudSize(), step)));

        OptionRow animations = cycler(row(3, rowWidth(), "randomrun.settings.ui_style.label",
            () -> LanguageManager.text(config.getUIStyle() == ModConfig.UIStyle.DETAILED ? "randomrun.settings.ui_style.detailed" : "randomrun.settings.ui_style.simplified")
                .withStyle(s -> s.withColor(config.getUIStyle() == ModConfig.UIStyle.DETAILED ? 0x4ADE80 : 0xFACC15)), 0.11f),
            step -> config.setUIStyle(config.getUIStyle() == ModConfig.UIStyle.DETAILED ? ModConfig.UIStyle.SIMPLIFIED : ModConfig.UIStyle.DETAILED));
        animations.setTooltip(Tooltip.create(LanguageManager.text("randomrun.settings.ui_style.tooltip")));

        cycler(row(4, rowWidth(), "randomrun.settings.theme.label",
            () -> LanguageManager.text(Theme.current().nameKey()).withStyle(s -> s.withColor(Theme.current().accent)), 0.14f),
            step -> config.setTheme((step > 0 ? Theme.current().next() : Theme.current().previous()).id));

        cycler(row(5, rowWidth(), "randomrun.settings.language.label",
            () -> Component.literal(LanguageManager.getCurrentLanguageEnum().getDisplayName()), 0.17f), LanguageManager::cycleLanguage);

        addRenderableWidget(new OptionRow(rowX(), rowY(6), rowWidth(), LanguageManager.text("randomrun.settings.interface_style.label"),
            () -> LanguageManager.text("randomrun.interface_style." + config.getInterfaceStyle()).withStyle(s -> s.withColor(Theme.current().accent)),
            button -> minecraft.gui.setScreen(new com.randomrun.ui.screen.InterfaceStyleScreen(this, false)), 0.2f).noArrows());
        addBackButton(7);
    }

    private record Row(int index, int width, String label, java.util.function.Supplier<Component> value, float delay) {}

    private Row row(int index, int width, String label, java.util.function.Supplier<Component> value, float delay) {
        return new Row(index, width, label, value, delay);
    }

    private OptionRow cycler(Row row, IntConsumer step) {
        IntConsumer apply = direction -> {
            step.accept(direction);
            RandomRunMod.getInstance().saveConfig();
        };
        return addRenderableWidget(new OptionRow(rowX(), rowY(row.index()), row.width(), LanguageManager.text(row.label()), row.value(),
            button -> apply.accept(1), row.delay()).previous(() -> apply.accept(-1)));
    }

    private void stepPosition(int step) {
        int index = java.util.Arrays.asList(PRESETS).indexOf(config.getHudPosition());
        if (index < 0) index = step > 0 ? -1 : 0;
        config.setHudPosition(PRESETS[Math.floorMod(index + step, PRESETS.length)]);
    }

    private static <T extends Enum<T>> T next(T[] values, T current, int step) {
        return values[Math.floorMod(current.ordinal() + step, values.length)];
    }

    @Override
    protected int widgetHeight() { return Math.min(220, height - 50); }

    private String positionText(ModConfig.HudPosition position) {
        return LanguageManager.translate(switch (position) {
            case TOP_LEFT -> "randomrun.settings.hud_position.top_left";
            case TOP_RIGHT -> "randomrun.settings.hud_position.top_right";
            case TOP_CENTER -> "randomrun.settings.hud_position.top_center";
            case CUSTOM -> "randomrun.settings.hud_position.custom";
        });
    }
}
