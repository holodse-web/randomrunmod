package com.randomrun.ui.screen.settings;

import com.randomrun.infrastructure.ModConfig;
import com.randomrun.infrastructure.RandomRunMod;
import com.randomrun.infrastructure.util.LanguageManager;
import com.randomrun.features.speedrun.HudRenderer;
import com.randomrun.ui.screen.main.AbstractRandomRunScreen;
import com.randomrun.ui.util.GuiText;
import com.randomrun.ui.util.Theme;
import com.randomrun.ui.util.Ui;
import com.randomrun.ui.widget.styled.Buttons.ButtonDefault;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public class HudPositionEditorScreen extends AbstractRandomRunScreen {
    private final Screen parent;
    private final ModConfig config = RandomRunMod.getInstance().getConfig();
    private final HudRenderer preview = new HudRenderer();
    private final ItemStack item = com.randomrun.ui.util.Previews.PreviewItems.stack(Items.DIAMOND);
    private int hudX, hudY;
    private double grabX, grabY;
    private boolean dragging;
    private boolean placed;
    private ButtonDefault saveButton, resetButton, cancelButton;

    public HudPositionEditorScreen(Screen parent) {
        super(LanguageManager.text("randomrun.screen.hud_editor.title"));
        this.parent = parent;
        hudX = config.getCustomHudX();
        hudY = config.getCustomHudY();
    }

    @Override
    public float transition() { return 1f; }

    private int hudWidth() {
        int base = config.getHudStyle() == ModConfig.HudStyle.PROFESSIONAL
            ? HudRenderer.professionalWidth(font, item.getHoverName().getString(), "00:00.000", "00:00.000") : 48;
        return (int) Math.ceil(base * config.getHudSize().scale);
    }

    private int hudHeight() {
        return (int) Math.ceil((config.getHudStyle() == ModConfig.HudStyle.PROFESSIONAL ? 35 : 48) * config.getHudSize().scale);
    }

    @Override
    protected void init() {
        super.init();
        if (!placed && config.getHudPosition() != ModConfig.HudPosition.CUSTOM) {
            hudX = switch (config.getHudPosition()) {
                case TOP_RIGHT -> width - hudWidth() - 10;
                case TOP_CENTER -> (width - hudWidth()) / 2;
                default -> 10;
            };
            hudY = 10;
        }
        placed = true;
        clampPosition();
        int x = width / 2 - 158;
        saveButton = addRenderableWidget(new ButtonDefault(x, toolbarY() + 60, 100, 20, LanguageManager.text("randomrun.button.save"), button -> {
            clampPosition();
            config.setCustomHudX(hudX);
            config.setCustomHudY(hudY);
            config.setHudPosition(ModConfig.HudPosition.CUSTOM);
            RandomRunMod.getInstance().saveConfig();
            minecraft.gui.setScreen(parent);
        }, 0, 0f));
        resetButton = addRenderableWidget(new ButtonDefault(x + 108, toolbarY() + 60, 100, 20, LanguageManager.text("randomrun.hud_editor.reset"), button -> {
            hudX = 10;
            hudY = 10;
            clampPosition();
        }, 1, 0f));
        cancelButton = addRenderableWidget(new ButtonDefault(x + 216, toolbarY() + 60, 100, 20, LanguageManager.text("randomrun.button.cancel"), button -> onClose(), 2, 0f));
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        int accent = Theme.current().accent;
        if (dragging) {
            context.fill(width / 2, 0, width / 2 + 1, height, 0x447F7F8A);
            context.fill(0, height / 2, width, height / 2 + 1, 0x447F7F8A);
            if (Math.abs(hudX + hudWidth() / 2 - width / 2) <= 1)
                context.fill(width / 2, 0, width / 2 + 1, height, 0xFF000000 | accent);
            if (Math.abs(hudY + hudHeight() / 2 - height / 2) <= 1)
                context.fill(0, height / 2, width, height / 2 + 1, 0xFF000000 | accent);
        }
        context.pose().pushMatrix();
        context.pose().translate(hudX, hudY);
        context.pose().scale(config.getHudSize().scale, config.getHudSize().scale);
        preview.renderPreview(context, config, item);
        context.pose().popMatrix();
        if (dragging || hovered(mouseX, mouseY))
            Ui.border(context, hudX - 2, hudY - 2, hudX + hudWidth() + 2, hudY + hudHeight() + 2, 1, 0xFF000000 | accent);

        int left = width / 2 - 170, right = width / 2 + 170, top = toolbarY();
        Ui.panel(context, left, top, right, top + 86, 1f);
        GuiText.centeredText(context, font, title, width / 2, top + 10, 0xFFFFFFFF);
        GuiText.centeredText(context, font, LanguageManager.text("randomrun.hud_editor.instructions"), width / 2, top + 25, 0xFFD4D4D8);
        Ui.divider(context, left + 12, right - 12, top + 39, Theme.current().border, 1f);
        String size = LanguageManager.translate("randomrun.settings.hud_size." + config.getHudSize().name().toLowerCase(java.util.Locale.ROOT));
        GuiText.centeredText(context, font, Component.literal("X: " + hudX + "   Y: " + hudY + "   ·   " + size), width / 2, top + 46, 0xFFD4D4D8);
        updateToolbar();
        super.extractRenderState(context, mouseX, mouseY, delta);
    }

    private boolean hovered(double x, double y) {
        return Ui.inside(x, y, hudX, hudY, hudWidth(), hudHeight());
    }

    private int toolbarY() {
        return hudY + hudHeight() / 2 > height / 2 ? 8 : height - 94;
    }

    private void updateToolbar() {
        if (saveButton == null) return;
        int y = toolbarY() + 60;
        saveButton.setY(y);
        resetButton.setY(y);
        cancelButton.setY(y);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        updateToolbar();
        if (super.mouseClicked(event, doubleClick)) return true;
        if (event.button() == com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_LEFT && hovered(event.x(), event.y())) {
            dragging = true;
            grabX = event.x() - hudX;
            grabY = event.y() - hudY;
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double deltaX, double deltaY) {
        if (!dragging || event.button() != com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_LEFT) return super.mouseDragged(event, deltaX, deltaY);
        hudX = (int) Math.round(event.x() - grabX);
        hudY = (int) Math.round(event.y() - grabY);
        hudX = snap(hudX, hudWidth(), width);
        hudY = snap(hudY, hudHeight(), height);
        clampPosition();
        return true;
    }

    private int snap(int value, int size, int area) {
        for (int target : new int[]{10, (area - size) / 2, area - size - 10}) {
            if (Math.abs(value - target) <= 6) return target;
        }
        return value;
    }

    private void clampPosition() {
        hudX = Math.max(0, Math.min(Math.max(0, width - hudWidth()), hudX));
        hudY = Math.max(0, Math.min(Math.max(0, height - hudHeight()), hudY));
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        if (event.button() == com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_LEFT && dragging) {
            dragging = false;
            return true;
        }
        return super.mouseReleased(event);
    }

    @Override
    public void onClose() { minecraft.gui.setScreen(parent); }
}
