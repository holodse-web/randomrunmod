package com.randomrun.ui.screen;

import com.randomrun.infrastructure.ModConfig;
import com.randomrun.infrastructure.RandomRunMod;
import com.randomrun.infrastructure.util.LanguageManager;
import com.randomrun.ui.screen.main.AbstractRandomRunScreen;
import com.randomrun.ui.screen.main.MainModScreen;
import com.randomrun.ui.util.GuiQuadBatch;
import com.randomrun.ui.util.GuiText;
import com.randomrun.ui.util.InterfaceStyle;
import com.randomrun.ui.util.Logo;
import com.randomrun.ui.util.Theme;
import com.randomrun.ui.util.Ui;
import com.randomrun.ui.widget.styled.Buttons.ButtonDefault;
import com.randomrun.ui.widget.styled.Buttons.ButtonMenu;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

public final class InterfaceStyleScreen extends AbstractRandomRunScreen {
    private static final Identifier DIRT = Identifier.withDefaultNamespace("textures/block/dirt.png");
    private static final String[] PREVIEW_BUTTONS = {"randomrun.button.speedrun", "randomrun.button.settings", "randomrun.button.back"};
    private final Screen parent;
    private final boolean firstEntry;
    private final Map<InterfaceStyle, List<AbstractWidget>> previewButtons = new EnumMap<>(InterfaceStyle.class);
    private final Map<InterfaceStyle, int[]> previewAreas = new EnumMap<>(InterfaceStyle.class);
    private int cardsTop, cardsBottom, cardWidth;
    private GuiQuadBatch previewBackground;
    private Theme previewTheme;
    private int previewWidth, previewHeight;

    public InterfaceStyleScreen(Screen parent, boolean firstEntry) {
        super(LanguageManager.text("randomrun.interface_choice.title"));
        this.parent = parent;
        this.firstEntry = firstEntry;
    }

    @Override
    protected void init() {
        super.init();
        previewButtons.clear();
        previewAreas.clear();
        int gap = 14;
        cardWidth = Math.min(254, (width - 34 - gap) / 2);
        int cardHeight = Math.min(214, height - 110);
        cardsTop = Math.max(56, (height - cardHeight) / 2 - 6);
        cardsBottom = cardsTop + cardHeight;
        int left = width / 2 - cardWidth - gap / 2;
        layoutCard(InterfaceStyle.CLASSIC, left);
        layoutCard(InterfaceStyle.MODERN, left + cardWidth + gap);
        addRenderableWidget(chromeButton(width / 2 - 100, height - 28, 200,
            LanguageManager.text("randomrun.button.back"), this::onClose, true));
    }

    private void layoutCard(InterfaceStyle style, int left) {
        int previewLeft = left + 9, previewTop = cardsTop + 9;
        int previewW = cardWidth - 18, previewH = cardsBottom - cardsTop - 70;
        previewAreas.put(style, new int[]{previewLeft, previewTop, previewW, previewH});
        List<AbstractWidget> buttons = new ArrayList<>();
        int buttonWidth = Math.min(150, previewW - 36);
        int logoBottom = previewTop + Math.max(4, (previewH - 122) / 2) + 44;
        for (int i = 0; i < PREVIEW_BUTTONS.length; i++) {
            PreviewButton button = new PreviewButton(previewLeft + (previewW - buttonWidth) / 2, logoBottom + i * 24, buttonWidth,
                LanguageManager.text(PREVIEW_BUTTONS[i]), style, i);
            buttons.add(addWidget(button));
        }
        previewButtons.put(style, buttons);
        boolean current = !firstEntry && style.id.equals(RandomRunMod.getInstance().getConfig().getInterfaceStyle());
        AbstractWidget choose = addRenderableWidget(chromeButton(left + 12, cardsBottom - 28, cardWidth - 24,
            LanguageManager.text(current ? "randomrun.interface_choice.current" : "randomrun.interface_choice.select"), () -> choose(style), false));
        choose.active = !current;
    }

    private AbstractWidget chromeButton(int x, int y, int w, Component label, Runnable action, boolean back) {
        if (firstEntry) return Button.builder(label, pressed -> action.run()).bounds(x, y, w, 20).build();
        return new ButtonDefault(x, y, w, 20, label, pressed -> action.run(), back ? 1 : 0, 0f, true);
    }

    private void choose(InterfaceStyle style) {
        ModConfig config = RandomRunMod.getInstance().getConfig();
        config.setInterfaceStyle(style.id);
        config.setInterfaceStyleChosen(true);
        RandomRunMod.getInstance().saveConfig();
        minecraft.gui.setScreen(firstEntry ? new MainModScreen(parent) : parent);
    }

    @Override
    public float transition() {
        return firstEntry ? 1f : super.transition();
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        if (!firstEntry) {
            super.extractBackground(g, mouseX, mouseY, delta);
            return;
        }
        for (int y = 0; y < height; y += 32)
            for (int x = 0; x < width; x += 32)
                g.blit(RenderPipelines.GUI_TEXTURED, DIRT, x, y, 0f, 0f, 32, 32, 16, 16, 16, 16);
        g.fill(0, 0, width, height, 0xC0000000);
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
        int muted = firstEntry ? 0xFFA0A0A0 : InterfaceStyle.isClassic() ? 0xFFC0BDB2 : 0xFFA1A1AA;
        GuiText.centeredText(g, font, title, width / 2, cardsTop - 32, 0xFFFFFFFF);
        GuiText.centeredText(g, font, LanguageManager.text("randomrun.interface_choice.subtitle"), width / 2, cardsTop - 18, muted);
        for (InterfaceStyle style : InterfaceStyle.values()) drawCard(g, style, mouseX, mouseY, delta, muted);
        super.extractRenderState(g, mouseX, mouseY, delta);
        GuiText.centeredText(g, font, LanguageManager.text("randomrun.interface_choice.change_later"), width / 2,
            Math.min(cardsBottom + 9, height - 42), muted);
    }

    private void drawCard(GuiGraphicsExtractor g, InterfaceStyle style, int mouseX, int mouseY, float delta, int muted) {
        int[] area = previewAreas.get(style);
        int left = area[0] - 9, top = cardsTop, right = left + cardWidth, bottom = cardsBottom;
        if (firstEntry) {
            g.fill(left, top, right, bottom, 0x90000000);
            Ui.border(g, left, top, right, bottom, 1, 0xFF6F6F6F);
            Ui.border(g, area[0] - 1, area[1] - 1, area[0] + area[2] + 1, area[1] + area[3] + 1, 1, 0xFF000000);
        } else if (InterfaceStyle.isClassic()) {
            Ui.bevel(g, left, top, right, bottom, 0x363731, 1f, false);
            Ui.slot(g, area[0] - 2, area[1] - 2, area[0] + area[2] + 2, area[1] + area[3] + 2, 1f);
        } else {
            Ui.panel(g, left, top, right, bottom, 1f);
        }
        InterfaceStyle.withStyle(style, () -> drawPreview(g, style, area, mouseX, mouseY, delta));
        GuiText.centeredText(g, font, LanguageManager.text("randomrun.interface_style." + style.id), (left + right) / 2, bottom - 56, 0xFFFFFFFF);
        GuiText.centeredText(g, font, LanguageManager.text("randomrun.interface_choice." + style.id + ".short"), (left + right) / 2, bottom - 43, muted);
    }

    private void drawPreview(GuiGraphicsExtractor g, InterfaceStyle style, int[] area, int mouseX, int mouseY, float delta) {
        int x = area[0], y = area[1], w = area[2], h = area[3];
        Theme theme = Theme.current();
        g.enableScissor(x, y, x + w, y + h);
        drawPreviewBackground(g, x, y, w, h, theme);
        List<AbstractWidget> buttons = previewButtons.get(style);
        int firstY = buttons.getFirst().getY(), panelHalf = buttons.getFirst().getWidth() / 2 + 14;
        int cx = x + w / 2;
        Ui.panel(g, cx - panelHalf, firstY - 42, cx + panelHalf, buttons.getLast().getY() + 30, 1f);
        Logo.draw(g, cx - 47, firstY - 39, 94, 35, 1f);
        boolean inside = mouseX >= x && mouseX < x + w && mouseY >= y && mouseY < y + h;
        for (AbstractWidget button : buttons) button.extractRenderState(g, inside ? mouseX : -1, inside ? mouseY : -1, delta);
        g.disableScissor();
    }

    private void drawPreviewBackground(GuiGraphicsExtractor g, int x, int y, int w, int h, Theme theme) {
        if (!InterfaceStyle.isClassic()) {
            g.fillGradient(x, y, x + w, y + h / 2, 0xFF000000 | theme.bgTop, 0xFF000000 | theme.bgMid);
            g.fillGradient(x, y + h / 2, x + w, y + h, 0xFF000000 | theme.bgMid, 0xFF000000 | theme.bgBottom);
            return;
        }
        if (previewBackground == null || previewTheme != theme || previewWidth != w || previewHeight != h) {
            GuiQuadBatch.Builder batch = new GuiQuadBatch.Builder(w * h / 16 + 1);
            for (int py = 0; py < h; py += 4) {
                for (int px = 0; px < w; px += 4) {
                    int seed = ((px / 4) & 7) * 73428767 ^ ((py / 4) & 7) * 912931;
                    seed ^= seed >>> 13;
                    int color = Ui.lerp(0xFF000000 | theme.bgTop, 0xFF000000 | theme.bgMid, (seed & 15) / 15f);
                    batch.fill(px, py, Math.min(w, px + 4), Math.min(h, py + 4), color);
                }
            }
            previewBackground = batch.build();
            previewWidth = w;
            previewHeight = h;
            previewTheme = theme;
        }
        g.pose().pushMatrix();
        g.pose().translate(x, y);
        previewBackground.extract(g);
        g.pose().popMatrix();
    }

    @Override
    public void onClose() { minecraft.gui.setScreen(parent); }

    private static final class PreviewButton extends ButtonMenu {
        private final InterfaceStyle style;

        PreviewButton(int x, int y, int width, Component label, InterfaceStyle style, int index) {
            super(x, y, width, 20, label, pressed -> {}, index, 0.08f * index);
            this.style = style;
        }

        @Override
        public void setFocused(boolean focused) {
        }

        @Override
        public void playDownSound(net.minecraft.client.sounds.SoundManager soundManager) {
            InterfaceStyle.withStyle(style, () -> super.playDownSound(soundManager));
        }
    }
}
