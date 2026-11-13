package com.randomrun.ui.screen.account;

import com.randomrun.core.data.PlayerProfile;
import com.randomrun.infrastructure.util.LanguageManager;
import com.randomrun.ui.screen.main.AbstractRandomRunScreen;
import com.randomrun.ui.util.GuiText;
import com.randomrun.ui.util.Theme;
import com.randomrun.ui.util.Ui;
import com.randomrun.ui.widget.OverlayWidgets.LanguageDropdownWidget;
import com.randomrun.ui.widget.styled.Buttons.ButtonRainbow;

import java.util.List;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public class TrustScoreInfoScreen extends AbstractRandomRunScreen {
    private static final int PANEL_WIDTH = 320;
    private static final int PANEL_HEIGHT = 190;
    private static final int MAX = 200;

    private record Rule(String key, int value) {}

    private static final List<Rule> GAINS = List.of(
        new Rule("randomrun.trustscore.gain.run", PlayerProfile.BONUS_CLEAN_RUN),
        new Rule("randomrun.trustscore.gain.win", PlayerProfile.BONUS_VICTORY),
        new Rule("randomrun.trustscore.gain.battle", PlayerProfile.BONUS_BATTLE_VICTORY),
        new Rule("randomrun.trustscore.gain.daily", PlayerProfile.BONUS_DAILY_CAP)
    );

    private static final List<Rule> LOSSES = List.of(
        new Rule("randomrun.trustscore.loss.cheat", PlayerProfile.PENALTY_CHEAT),
        new Rule("randomrun.trustscore.loss.creative", PlayerProfile.PENALTY_CREATIVE),
        new Rule("randomrun.trustscore.loss.lan", PlayerProfile.PENALTY_LAN),
        new Rule("randomrun.trustscore.loss.suspicious", PlayerProfile.PENALTY_SUSPICIOUS)
    );

    private final Screen parent;
    private long openTime;

    public TrustScoreInfoScreen(Screen parent) {
        super(LanguageManager.text("randomrun.trustscore.title"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        super.init();
        openTime = System.currentTimeMillis();
        ButtonRainbow ok = new ButtonRainbow(width / 2 - 100, height - 30, 200, 20,
            LanguageManager.text("randomrun.button.understood"), button -> onClose(), 0, 0.1f);
        ok.setAnimationDirection(ButtonRainbow.AnimationDirection.HORIZONTAL_LEFT);
        addRenderableWidget(ok);
        addRenderableWidget(new LanguageDropdownWidget(10, height - 30, this, 0.2f));
    }

    private int top() {
        return Math.max(10, height / 2 - PANEL_HEIGHT / 2 - 16);
    }

    private static int score() {
        PlayerProfile profile = PlayerProfile.get();
        return profile == null ? 100 : profile.getTrustScore();
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        super.extractBackground(context, mouseX, mouseY, delta);
        float fade = Math.min(1f, (System.currentTimeMillis() - openTime) / 300f);
        float grow = Ui.easeOut((System.currentTimeMillis() - openTime) / 900f);
        Theme theme = Theme.current();
        int left = width / 2 - PANEL_WIDTH / 2, right = left + PANEL_WIDTH;
        int top = top(), bottom = top + PANEL_HEIGHT;
        Ui.panel(context, left, top, right, bottom, fade);
        Ui.glowBorder(context, left, top, right, bottom, 2, 0xFFFFFF);

        GuiText.centeredText(context, font, LanguageManager.text("randomrun.trustscore.header"), width / 2, top + 10, Ui.argb(0xFFFFFF, fade));
        Ui.divider(context, left + 12, right - 12, top + 23, theme.border, fade);

        int score = score();
        boolean ok = score >= PlayerProfile.LEADERBOARD_THRESHOLD;
        int scoreColor = score >= 100 ? 0x4ADE80 : ok ? 0xFACC15 : 0xF87171;
        int shown = Math.round(score * grow);
        Ui.centeredScaled(context, font, Component.literal(String.valueOf(shown)), width / 2f, top + 31, 2f, Ui.argb(scoreColor, fade));
        GuiText.centeredText(context, font, Component.literal("/ " + MAX), width / 2, top + 50, Ui.argb(0x71717A, fade));

        int barLeft = left + 20, barRight = right - 20, barY = top + 63;
        context.fill(barLeft, barY, barRight, barY + 6, Ui.argb(0x000000, 0.5f * fade));
        int fill = barLeft + Math.round((barRight - barLeft) * Math.min(1f, score / (float) MAX) * grow);
        context.fillGradient(barLeft, barY, fill, barY + 6, Ui.argb(scoreColor, fade), Ui.argb(Ui.lerp(0xFF000000 | scoreColor, 0xFF000000, 0.35f), fade));
        int mark = barLeft + Math.round((barRight - barLeft) * PlayerProfile.LEADERBOARD_THRESHOLD / (float) MAX);
        context.fill(mark, barY - 2, mark + 1, barY + 8, Ui.argb(0xFFFFFF, fade));
        GuiText.text(context, font, Component.literal(String.valueOf(PlayerProfile.LEADERBOARD_THRESHOLD)), mark - font.width(String.valueOf(PlayerProfile.LEADERBOARD_THRESHOLD)) / 2, barY + 12, Ui.argb(0xA1A1AA, fade), false);

        Component status = LanguageManager.text(ok ? "randomrun.trustscore.status.ok" : "randomrun.trustscore.status.low", PlayerProfile.LEADERBOARD_THRESHOLD);
        int chipWidth = font.width(status) + 8;
        Ui.chip(context, font, status, right - 20 - chipWidth, barY + 9, Ui.argb(ok ? 0x14532D : 0x7F1D1D, 0.85f * fade), Ui.argb(ok ? 0x4ADE80 : 0xFCA5A5, fade));

        int colTop = top + 90, mid = width / 2;
        column(context, left + 12, mid - 4, colTop, "randomrun.trustscore.gains", GAINS, 0x4ADE80, fade);
        column(context, mid + 4, right - 12, colTop, "randomrun.trustscore.losses", LOSSES, 0xF87171, fade);

        GuiText.centeredText(context, font, LanguageManager.text("randomrun.trustscore.newbie", PlayerProfile.START_SCORE, PlayerProfile.LEADERBOARD_THRESHOLD), width / 2, bottom - 26, Ui.argb(0xA1A1AA, fade));
        GuiText.centeredText(context, font, LanguageManager.text("randomrun.trustscore.footer"), width / 2, bottom - 14, Ui.argb(0x8A8A93, fade));
    }

    private void column(GuiGraphicsExtractor context, int left, int right, int top, String titleKey, List<Rule> rules, int accent, float fade) {
        Ui.card(context, left, top, right, top + 14 + 4 * 12 + 4, accent, fade);
        GuiText.text(context, font, LanguageManager.text(titleKey), left + 6, top + 4, Ui.argb(accent, fade), false);
        int y = top + 17;
        for (Rule rule : rules) {
            Component value = Component.literal((rule.value() > 0 ? "+" : "") + rule.value());
            int valueWidth = font.width(value);
            GuiText.text(context, font, Ui.ellipsize(font, LanguageManager.translate(rule.key()), right - left - valueWidth - 16), left + 6, y, Ui.argb(0xD4D4D8, fade), false);
            GuiText.text(context, font, value, right - valueWidth - 6, y, Ui.argb(accent, fade), false);
            y += 12;
        }
    }

    @Override
    public void onClose() {
        minecraft.gui.setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
