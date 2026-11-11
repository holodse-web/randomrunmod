package com.randomrun.ui.screen.main;

import com.randomrun.infrastructure.RandomRunMod;
import com.randomrun.infrastructure.util.LanguageManager;
import com.randomrun.infrastructure.version.VersionChecker;
import com.randomrun.ui.theme.Theme;
import com.randomrun.ui.util.GuiText;
import com.randomrun.ui.util.Ui;
import com.randomrun.ui.widget.overlay.OverlayWidgets.LanguageDropdownWidget;
import com.randomrun.ui.widget.styled.Buttons.ButtonMenu;
import java.util.List;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.FormattedCharSequence;

public class UpdateRequiredScreen extends AbstractRandomRunScreen {
    private static final int PANEL_WIDTH = 280;
    private static final int OLD = 0xF87171;
    private static final int NEW = 0x4ADE80;

    private String currentVersion = "?";
    private String latestVersion = "?";
    private long openTime;

    public UpdateRequiredScreen() {
        super(LanguageManager.text("randomrun.screen.update.title"));
    }

    @Override
    protected void init() {
        super.init();
        LanguageManager.ensureLanguage();
        VersionChecker checker = VersionChecker.getInstance();
        if (checker.getCurrentVersion() != null) currentVersion = checker.getCurrentVersion();
        if (checker.getLatestVersion() != null) latestVersion = checker.getLatestVersion();
        if (openTime == 0) Ui.playSound(SoundEvents.NOTE_BLOCK_CHIME.value(), 0.6f, 0.8f);
        openTime = System.currentTimeMillis();
        int cx = width / 2, bottom = top() + panelHeight();
        addRenderableWidget(new ButtonMenu(cx - 100, bottom + 10, 200, 20,
            LanguageManager.text("randomrun.screen.update.play_offline"), button -> playOffline(), 0, 0.1f));
        addRenderableWidget(new ButtonMenu(cx - 100, height - 30, 200, 20,
            LanguageManager.text("randomrun.button.back"), button -> onClose(), 2, 0.2f));
        addRenderableWidget(new LanguageDropdownWidget(10, height - 30, this, 0.2f));
    }

    private void playOffline() {
        RandomRunMod.getInstance().getConfig().setOnlineMode(false);
        RandomRunMod.getInstance().saveConfig();
        minecraft.gui.setScreen(new MainModScreen(new TitleScreen()));
    }

    private List<FormattedCharSequence> lines() {
        return font.split(LanguageManager.text("randomrun.screen.update.instruction"), PANEL_WIDTH - 36);
    }

    private int panelHeight() {
        return 116 + lines().size() * 10 + 30;
    }

    private int top() {
        return Math.max(10, height / 2 - (panelHeight() + 30) / 2);
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

        float pulse = 0.5f + 0.5f * (float) Math.sin(System.currentTimeMillis() / 300.0);
        int ring = 12 + Math.round(pulse * 2);
        context.fill(cx - ring, top + 24 - ring, cx + ring, top + 24 + ring, Ui.argb(accent, 0.12f * fade));
        context.fill(cx - 11, top + 13, cx + 11, top + 35, Ui.argb(Ui.lerp(0xFF000000 | accent, 0xFF000000, 0.65f), fade));
        Ui.border(context, cx - 11, top + 13, cx + 11, top + 35, 1, Ui.argb(accent, fade));
        drawArrow(context, cx, top + 17 - Math.round((float) Math.sin(System.currentTimeMillis() / 250.0) * 1.5f), fade);

        GuiText.centeredText(context, font, LanguageManager.text("randomrun.screen.update.header"), cx, top + 43, Ui.argb(accent, fade));
        Ui.divider(context, left + 14, right - 14, top + 56, accent, fade);

        int rowY = top + 66, half = (PANEL_WIDTH - 28 - 18) / 2;
        versionCard(context, left + 14, rowY, half, LanguageManager.text("randomrun.screen.update.yours"), currentVersion, OLD, fade);
        versionCard(context, right - 14 - half, rowY, half, LanguageManager.text("randomrun.screen.update.needed"), latestVersion, NEW, fade);
        int ay = rowY + 18;
        context.fill(cx - 4, ay - 1, cx, ay + 1, Ui.argb(accent, fade));
        for (int i = 0; i < 4; i++) context.fill(cx + i, ay - 4 + i, cx + i + 1, ay + 4 - i, Ui.argb(accent, fade));

        int y = rowY + 46;
        for (FormattedCharSequence line : lines()) {
            GuiText.centeredText(context, font, line, cx, y, Ui.argb(0xD4D4D8, fade));
            y += 10;
        }
        y += 8;
        Ui.card(context, left + 14, y, right - 14, y + 18, accent, fade);
        GuiText.centeredText(context, font, LanguageManager.text("randomrun.modified.hint"), cx, y + 5, Ui.argb(0xA1A1AA, fade));
    }

    private void versionCard(GuiGraphicsExtractor context, int x, int y, int w, Component label, String version, int color, float fade) {
        context.fill(x, y, x + w, y + 36, Ui.argb(0x000000, 0.35f * fade));
        Ui.border(context, x, y, x + w, y + 36, 1, Ui.argb(color, 0.6f * fade));
        context.fill(x + 1, y + 1, x + w - 1, y + 2, Ui.argb(color, 0.8f * fade));
        GuiText.centeredText(context, font, label, x + w / 2, y + 6, Ui.argb(0xA1A1AA, fade));
        Ui.centeredScaled(context, font, Component.literal(version), x + w / 2f, y + 18, 1.4f, Ui.argb(color, fade));
    }

    private void drawArrow(GuiGraphicsExtractor context, int cx, int y, float fade) {
        int color = Ui.argb(0xFFFFFF, fade);
        for (int i = 0; i < 6; i++) context.fill(cx - i - 1, y + i, cx + i + 1, y + i + 1, color);
        context.fill(cx - 2, y + 6, cx + 2, y + 13, color);
    }

    @Override
    public void onClose() {
        minecraft.gui.setScreen(new TitleScreen());
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
