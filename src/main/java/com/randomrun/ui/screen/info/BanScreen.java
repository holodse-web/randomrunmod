package com.randomrun.ui.screen.info;



import com.randomrun.ui.widget.overlay.OverlayWidgets.LanguageDropdownWidget;
import com.randomrun.ui.widget.styled.Buttons.ButtonMenu;
import com.randomrun.infrastructure.RandomRunMod;
import com.randomrun.infrastructure.util.LanguageManager;
import com.randomrun.network.BackendSession;
import com.randomrun.ui.screen.main.AbstractRandomRunScreen;
import com.randomrun.ui.theme.Theme;
import com.randomrun.ui.util.GuiText;
import com.randomrun.ui.util.Heads;
import com.randomrun.ui.util.Ui;
import java.util.List;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.util.FormattedCharSequence;

public class BanScreen extends AbstractRandomRunScreen {
    public enum Kind {
        BANNED("randomrun.ban.title", "randomrun.ban.text", "randomrun.ban.hint", 0xF87171, 0xFF7F1D1D),
        MOD_MODIFIED("randomrun.modified.title", "randomrun.modified.text", "randomrun.modified.hint", 0xFB923C, 0xFF7C2D12);

        final String title, text, hint;
        final int accent, badge;

        Kind(String title, String text, String hint, int accent, int badge) {
            this.title = title;
            this.text = text;
            this.hint = hint;
            this.accent = accent;
            this.badge = badge;
        }
    }

    private static final int PANEL_WIDTH = 280;

    private final Screen parent;
    private final Screen offlineTarget;
    private final String playerName;
    private final Kind kind;
    private long openTime;

    public BanScreen(Screen parent, String playerName, Kind kind, Screen offlineTarget) {
        super(LanguageManager.text(kind.title));
        this.parent = parent;
        this.playerName = playerName;
        this.kind = kind;
        this.offlineTarget = offlineTarget;
    }

    @Override
    protected void init() {
        super.init();
        if (openTime == 0) Ui.playSound(SoundEvents.NOTE_BLOCK_BASS.value(), 0.6f, 0.5f);
        openTime = System.currentTimeMillis();
        int cx = width / 2, bottom = top() + panelHeight();
        addRenderableWidget(new ButtonMenu(cx - 100, bottom + 10, 200, 20,
            LanguageManager.text("randomrun.auth.button.play_offline"), button -> playOffline(), 0, 0.1f));
        addRenderableWidget(new ButtonMenu(cx - 100, height - 30, 200, 20,
            LanguageManager.text("randomrun.button.back"), button -> onClose(), 1, 0.15f));
        addRenderableWidget(new LanguageDropdownWidget(10, height - 30, this, 0.2f));
    }

    private void playOffline() {
        BackendSession.clear();
        RandomRunMod.getInstance().getConfig().setOnlineMode(false);
        RandomRunMod.getInstance().saveConfig();
        minecraft.gui.setScreen(offlineTarget);
    }

    private List<FormattedCharSequence> lines() {
        return font.split(LanguageManager.text(kind.text), PANEL_WIDTH - 36);
    }

    private int panelHeight() {
        return 78 + lines().size() * 10 + 34;
    }

    private int top() {
        return Math.max(10, height / 2 - panelHeight() / 2 - 26);
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        super.extractBackground(context, mouseX, mouseY, delta);
        float fade = Math.min(1f, (System.currentTimeMillis() - openTime) / 300f);
        Theme theme = Theme.current();
        int left = width / 2 - PANEL_WIDTH / 2, right = left + PANEL_WIDTH;
        int top = top(), bottom = top + panelHeight();
        context.fill(0, 0, width, height, Ui.argb(kind.badge, 0.18f * fade));
        Ui.panel(context, left, top, right, bottom, theme.panel, kind.accent, fade);
        Ui.glowBorder(context, left, top, right, bottom, 2, kind.accent);

        int cx = width / 2;
        float pulse = 0.5f + 0.5f * (float) Math.sin(System.currentTimeMillis() / 300.0);
        int ring = 12 + Math.round(pulse * 2);
        context.fill(cx - ring, top + 12 + 12 - ring, cx + ring, top + 12 + 12 + ring, Ui.argb(kind.accent, 0.12f * fade));
        context.fill(cx - 11, top + 13, cx + 11, top + 35, Ui.argb(kind.badge, fade));
        Ui.border(context, cx - 11, top + 13, cx + 11, top + 35, 1, Ui.argb(kind.accent, fade));
        Ui.centeredScaled(context, font, Component.literal(kind == Kind.BANNED ? "✖" : "⚠"), cx, top + 18, 1.5f, Ui.argb(0xFFFFFF, fade));

        GuiText.centeredText(context, font, LanguageManager.text(kind.title), cx, top + 43, Ui.argb(kind.accent, fade));
        Ui.divider(context, left + 14, right - 14, top + 56, kind.accent, fade);

        int nameWidth = font.width(playerName);
        int headX = cx - (nameWidth + 18) / 2;
        Heads.draw(context, playerName, headX, top + 62, 12, Ui.argb(kind.accent, fade));
        GuiText.text(context, font, playerName, headX + 18, top + 64, Ui.argb(0xFFFFFF, fade), true);

        int y = top + 82;
        for (FormattedCharSequence line : lines()) {
            GuiText.centeredText(context, font, line, cx, y, Ui.argb(0xD4D4D8, fade));
            y += 10;
        }
        y += 8;
        Ui.card(context, left + 14, y, right - 14, y + 18, kind.accent, fade);
        GuiText.centeredText(context, font, LanguageManager.text(kind.hint), cx, y + 5, Ui.argb(0xA1A1AA, fade));
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
