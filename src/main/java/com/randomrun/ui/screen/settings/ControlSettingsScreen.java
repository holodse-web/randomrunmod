package com.randomrun.ui.screen.settings;

import com.randomrun.infrastructure.ModConfig;
import com.randomrun.infrastructure.RandomRunMod;
import com.randomrun.infrastructure.util.LanguageManager;
import com.randomrun.infrastructure.util.LegacyKeyCodes;
import com.randomrun.ui.widget.styled.Buttons.ButtonDefault;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;

public class ControlSettingsScreen extends AnimatedSettingsScreen {
    private static final int BUTTON_WIDTH = 300;
    private static final int BUTTON_HEIGHT = 20;
    private static final int RESET_WIDTH = 65;

    private enum Binding { UPGRADER }

    private final ModConfig config;
    private Binding binding;
    private ButtonDefault upgraderKeyButton;
    private ButtonDefault upgraderResetButton;
    private Component conflict = Component.empty();

    public ControlSettingsScreen(Screen parent) { this(parent, false); }

    public ControlSettingsScreen(Screen parent, boolean isRefreshing) {
        super(parent, isRefreshing, LanguageManager.text("randomrun.screen.control_settings.title"));
        config = RandomRunMod.getInstance().getConfig();
    }

    @Override
    protected void initContent() {
        int centerX = width / 2;
        int top = getWidgetY();
        upgraderKeyButton = addBindingButton(Binding.UPGRADER, top + 45, 0);
        upgraderResetButton = addResetButton(Binding.UPGRADER, top + 45, 1);
        addRenderableWidget(new ButtonDefault(centerX - BUTTON_WIDTH / 2, height - 30,
            BUTTON_WIDTH, BUTTON_HEIGHT, LanguageManager.text("randomrun.button.back"),
            button -> startClosing(), 2, .2f, isRefreshing));
        updateBindings();
    }

    private ButtonDefault addBindingButton(Binding action, int y, int order) {
        ButtonDefault button = new ButtonDefault(width / 2 - BUTTON_WIDTH / 2, y,
            BUTTON_WIDTH - RESET_WIDTH - 5, BUTTON_HEIGHT, Component.empty(),
            clicked -> {
                binding = binding == action ? null : action;
                conflict = Component.empty();
                updateBindings();
            }, order, .15f, isRefreshing);
        button.setTooltip(Tooltip.create(LanguageManager.text("randomrun.control.binding_help")));
        return addRenderableWidget(button);
    }

    private ButtonDefault addResetButton(Binding action, int y, int order) {
        return addRenderableWidget(new ButtonDefault(width / 2 + BUTTON_WIDTH / 2 - RESET_WIDTH, y,
            RESET_WIDTH, BUTTON_HEIGHT, LanguageManager.text("randomrun.button.reset"),
            button -> {
                int key = ModConfig.DEFAULT_UPGRADER_KEY;
                KeyEvent event = new KeyEvent(LegacyKeyCodes.toInputKey(key), 0, 0);
                if (checkConflict(action, event)) return;
                saveBinding(action, key);
            }, order, .15f, isRefreshing));
    }

    private void updateBindings() {
        upgraderKeyButton.setMessage(bindingText(Binding.UPGRADER));
        upgraderResetButton.active = config.getUpgraderKey() != ModConfig.DEFAULT_UPGRADER_KEY;
    }

    private Component bindingText(Binding action) {
        Component key;
        if (binding == action) key = LanguageManager.text("randomrun.settings.press_key");
        else {
            int saved = config.getUpgraderKey();
            int input = LegacyKeyCodes.toInputKey(saved);
            key = input > 0 ? InputConstants.Type.KEYBOARD.getOrCreate(input).getDisplayName()
                : LanguageManager.text("randomrun.control.unbound");
        }
        return LanguageManager.text("randomrun.control.upgrader_key", key.getString());
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        if (binding == null) return super.keyPressed(event);
        if (event.key() == InputConstants.KEY_ESCAPE) {
            binding = null;
            conflict = Component.empty();
            updateBindings();
            return true;
        }
        if (event.key() == InputConstants.KEY_BACKSPACE || event.key() == InputConstants.KEY_DELETE) {
            saveBinding(binding, -1);
            return true;
        }
        if (event.key() > 0 && !checkConflict(binding, event)) {
            saveBinding(binding, LegacyKeyCodes.toLegacyKey(event.key()));
        }
        return true;
    }

    private boolean checkConflict(Binding action, KeyEvent event) {
        Component name = null;
        for (KeyMapping mapping : minecraft.options.keyMappings) {
            if (mapping.matches(event)) {
                name = Component.translatable(mapping.getName());
                break;
            }
        }
        if (name == null) return false;
        conflict = LanguageManager.text("randomrun.control.key_conflict", name.getString());
        return true;
    }

    private void saveBinding(Binding action, int key) {
        config.setUpgraderKey(key);
        RandomRunMod.getInstance().saveConfig();
        binding = null;
        conflict = Component.empty();
        updateBindings();
    }

    @Override
    protected void renderWidgetContent(GuiGraphicsExtractor context, int widgetX, int widgetY, float fadeProgress) {
        Component status = !conflict.getString().isEmpty() ? conflict
            : LanguageManager.text("randomrun.control.binding_help");
        int color = !conflict.getString().isEmpty() ? 0xFF8888 : binding != null ? 0xFFE182 : 0xC6BDCF;
        int argb = ((int) (255 * fadeProgress) << 24) | color;
        int y = widgetY + 80;
        for (var line : font.split(status, BUTTON_WIDTH)) {
            com.randomrun.ui.util.GuiText.centeredText(context, font, line, width / 2, y, argb);
            y += font.lineHeight + 2;
        }
    }
}
