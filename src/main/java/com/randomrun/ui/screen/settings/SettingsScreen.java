package com.randomrun.ui.screen.settings;



import com.randomrun.ui.widget.overlay.OverlayWidgets.LanguageDropdownWidget;
import com.randomrun.ui.widget.overlay.OverlayWidgets.ThemeSwitchWidget;
import com.randomrun.ui.widget.styled.Buttons.ButtonMenu;
import com.randomrun.infrastructure.RandomRunMod;
import com.randomrun.infrastructure.util.LanguageManager;
import com.randomrun.network.BackendSession;
import com.randomrun.ui.screen.auth.ChangePasswordScreen;
import com.randomrun.ui.screen.info.TrustScoreInfoScreen;
import com.randomrun.ui.screen.main.AbstractRandomRunScreen;
import com.randomrun.ui.theme.Theme;
import com.randomrun.ui.util.GuiText;
import com.randomrun.ui.util.Ui;
import java.util.function.Supplier;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Tooltip;
import net.minecraft.client.gui.screens.Screen;

public class SettingsScreen extends AbstractRandomRunScreen {
    private static final int TILE_WIDTH = 150;
    private static final int TILE_HEIGHT = 22;
    private final Screen parent;
    private long openTime;

    public SettingsScreen(Screen parent) {
        super(LanguageManager.text("randomrun.screen.settings.title"));
        this.parent = parent;
    }

    private int panelTop() { return height / 2 - 78; }
    private int panelBottom() { return height / 2 + (online() ? 64 : 44); }
    private boolean online() { return RandomRunMod.getInstance().getConfig().isOnlineMode() && BackendSession.isAuthenticated(); }

    @Override
    protected void init() {
        super.init();
        openTime = System.currentTimeMillis();
        int centerX = width / 2;
        int y = panelTop() + 34;
        tile(centerX - TILE_WIDTH - 3, y, "randomrun.settings.speedrun", "randomrun.settings.speedrun.tooltip", () -> new GameSettingsScreen(this), 0);
        tile(centerX + 3, y, "randomrun.settings.interface", "randomrun.settings.interface.tooltip", () -> new InterfaceSettingsScreen(this), 1);
        tile(centerX - TILE_WIDTH - 3, y + TILE_HEIGHT + 6, "randomrun.settings.controls", "randomrun.settings.controls.tooltip", () -> new ControlSettingsScreen(this), 2);
        tile(centerX + 3, y + TILE_HEIGHT + 6, "randomrun.settings.sound", "randomrun.settings.sound.tooltip", () -> new SoundSettingsScreen(this), 3);
        if (online()) {
            int accountY = y + (TILE_HEIGHT + 6) * 2 + 16;
            tile(centerX - TILE_WIDTH - 3, accountY, "randomrun.trustscore.button", "randomrun.settings.trust.tooltip", () -> new TrustScoreInfoScreen(this), 4);
            tile(centerX + 3, accountY, "randomrun.settings.changepassword", "randomrun.settings.password.tooltip",
                () -> new ChangePasswordScreen(this, BackendSession.playerName()), 5);
        }
        addRenderableWidget(new ButtonMenu(centerX - 100, height - 30, 200, 20, LanguageManager.text("randomrun.button.back"),
            button -> onClose(), 6, 0.3f));
        addRenderableWidget(new LanguageDropdownWidget(10, height - 30, this, 0.35f));
        addRenderableWidget(new ThemeSwitchWidget(34, height - 30, 0.4f));
    }

    private void tile(int x, int y, String key, String tooltipKey, Supplier<Screen> target, int index) {
        ButtonMenu button = addRenderableWidget(new ButtonMenu(x, y, TILE_WIDTH, TILE_HEIGHT, LanguageManager.text(key),
            pressed -> minecraft.gui.setScreen(target.get()), index, 0.04f * index));
        button.setTooltip(Tooltip.create(LanguageManager.text(tooltipKey)));
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        super.extractBackground(context, mouseX, mouseY, delta);
        float fade = Math.min(1f, (System.currentTimeMillis() - openTime) / 300f);
        int left = width / 2 - TILE_WIDTH - 15, right = width / 2 + TILE_WIDTH + 15;
        Ui.panel(context, left, panelTop(), right, panelBottom(), fade);
        Ui.glowBorder(context, left, panelTop(), right, panelBottom(), 2, 0xFFFFFF);
        GuiText.centeredText(context, font, title, width / 2, panelTop() + 10, Ui.argb(0xFFFFFF, fade));
        Ui.divider(context, left + 12, right - 12, panelTop() + 24, Theme.current().border, fade);
        if (online()) {
            int y = panelTop() + 34 + (TILE_HEIGHT + 6) * 2 + 4;
            GuiText.centeredText(context, font, LanguageManager.text("randomrun.settings.account", BackendSession.playerName()), width / 2, y, Ui.argb(0xA1A1AA, fade));
        }
    }

    @Override
    public void onClose() {
        minecraft.gui.setScreen(parent);
    }
}
