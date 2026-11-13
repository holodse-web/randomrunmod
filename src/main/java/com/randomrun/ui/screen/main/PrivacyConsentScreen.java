package com.randomrun.ui.screen.main;

import com.randomrun.infrastructure.RandomRunMod;
import com.randomrun.infrastructure.util.LanguageManager;
import com.randomrun.ui.util.GuiText;
import com.randomrun.ui.util.Theme;
import com.randomrun.ui.util.Ui;
import com.randomrun.ui.widget.OverlayWidgets.LanguageDropdownWidget;
import com.randomrun.ui.widget.styled.Buttons.ButtonMenu;
import com.randomrun.ui.widget.styled.Buttons.ButtonRainbow;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.util.FormattedCharSequence;

/**
 * First launch: the player decides whether the mod may talk to the RandomRun servers at all.
 * Until an answer is given the mod sends nothing over the network.
 */
public class PrivacyConsentScreen extends AbstractRandomRunScreen {
    private static final int PANEL_WIDTH = 300;
    private static final int ONLINE = 0x4ADE80;
    private static final int OFFLINE = 0x60A5FA;

    private final Screen parent;
    private long openTime;

    public PrivacyConsentScreen(Screen parent) {
        super(LanguageManager.text("randomrun.privacy.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        super.init();
        LanguageManager.ensureLanguage();
        if (openTime == 0) openTime = System.currentTimeMillis();
        int cx = width / 2, bottom = top() + panelHeight();
        ButtonRainbow online = new ButtonRainbow(cx - 146, bottom + 10, 144, 20,
            LanguageManager.text("randomrun.privacy.online"), button -> choose(true), 0, 0.1f);
        addRenderableWidget(online);
        addRenderableWidget(new ButtonMenu(cx + 2, bottom + 10, 144, 20,
            LanguageManager.text("randomrun.privacy.offline"), button -> choose(false), 1, 0.15f));
        addRenderableWidget(new ButtonMenu(cx - 100, height - 30, 200, 20,
            LanguageManager.text("randomrun.button.back"), button -> onClose(), 2, 0.2f));
        addRenderableWidget(new LanguageDropdownWidget(10, height - 30, this, 0.2f));
    }

    private void choose(boolean online) {
        RandomRunMod.getInstance().getConfig().choosePrivacy(online);
        RandomRunMod.getInstance().saveConfig();
        if (online) com.randomrun.network.VersionGate.start(false);
        minecraft.gui.setScreen(new MainModScreen(parent));
    }

    private List<FormattedCharSequence> wrap(String key) {
        return font.split(LanguageManager.text(key), PANEL_WIDTH - 44);
    }

    private List<List<FormattedCharSequence>> blocks() {
        List<List<FormattedCharSequence>> out = new ArrayList<>();
        out.add(wrap("randomrun.privacy.online.text"));
        out.add(wrap("randomrun.privacy.offline.text"));
        out.add(wrap("randomrun.privacy.later"));
        return out;
    }

    private int panelHeight() {
        List<List<FormattedCharSequence>> b = blocks();
        return 44 + (b.get(0).size() * 10 + 29) + (b.get(1).size() * 10 + 29) + b.get(2).size() * 10 + 10;
    }

    private int top() {
        return Math.max(10, height / 2 - (panelHeight() + 34) / 2);
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        super.extractBackground(context, mouseX, mouseY, delta);
        float fade = Math.min(1f, (System.currentTimeMillis() - openTime) / 300f);
        Theme theme = Theme.current();
        int accent = theme.accent;
        int left = width / 2 - PANEL_WIDTH / 2, right = left + PANEL_WIDTH;
        int top = top(), bottom = top + panelHeight();
        int cx = width / 2;
        Ui.panel(context, left, top, right, bottom, theme.panel, accent, fade);
        Ui.glowBorder(context, left, top, right, bottom, 2, accent);

        Ui.centeredScaled(context, font, LanguageManager.text("randomrun.privacy.header"), cx, top + 14, 1.3f, Ui.argb(accent, fade));
        Ui.divider(context, left + 14, right - 14, top + 34, accent, fade);

        List<List<FormattedCharSequence>> blocks = blocks();
        int[] colors = {ONLINE, OFFLINE, 0xA1A1AA};
        String[] labels = {"randomrun.privacy.online.label", "randomrun.privacy.offline.label", null};
        int y = top + 44;
        for (int i = 0; i < blocks.size(); i++) {
            List<FormattedCharSequence> lines = blocks.get(i);
            int h = lines.size() * 10 + 19;
            if (labels[i] != null) {
                context.fill(left + 14, y, right - 14, y + h, Ui.argb(0x000000, 0.3f * fade));
                context.fill(left + 14, y, left + 16, y + h, Ui.argb(colors[i], fade));
                GuiText.text(context, font, LanguageManager.text(labels[i]), left + 22, y + 4, Ui.argb(colors[i], fade), false);
                int ly = y + 15;
                for (FormattedCharSequence line : lines) {
                    GuiText.text(context, font, line, left + 22, ly, Ui.argb(0xD4D4D8, fade), false);
                    ly += 10;
                }
                y += h + 10;
            } else {
                int ly = y + 2;
                for (FormattedCharSequence line : lines) {
                    GuiText.centeredText(context, font, line, cx, ly, Ui.argb(colors[i], fade));
                    ly += 10;
                }
            }
        }
    }

    @Override
    public void onClose() {
        minecraft.gui.setScreen(parent != null ? parent : new TitleScreen());
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return true;
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
