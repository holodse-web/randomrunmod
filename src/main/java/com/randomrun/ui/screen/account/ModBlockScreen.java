package com.randomrun.ui.screen.account;

import com.randomrun.infrastructure.security.ModScannerService;
import com.randomrun.infrastructure.util.LanguageManager;
import com.randomrun.ui.screen.main.AbstractRandomRunScreen;
import com.randomrun.ui.util.GuiText;
import com.randomrun.ui.util.Theme;
import com.randomrun.ui.util.Ui;
import com.randomrun.ui.widget.styled.Buttons.ButtonMenu;
import com.randomrun.ui.widget.styled.Buttons.ButtonRainbow;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;

public class ModBlockScreen extends AbstractRandomRunScreen {
    private static final int PANEL_WIDTH = 300;
    private static final int ROW = 20;
    private static final int MAX_ROWS = 5;
    private static final int ORANGE = 0xFB923C;

    private final Screen parent;
    private final List<Map.Entry<String, ModScannerService.Reason>> findings;
    private final boolean hasMods;
    private long openTime;

    public ModBlockScreen(Screen parent) {
        super(LanguageManager.text("randomrun.modblock.title"));
        this.parent = parent;
        ModScannerService scanner = ModScannerService.getInstance();
        this.findings = new ArrayList<>(scanner.findings().entrySet());
        this.hasMods = scanner.hasSuspiciousMods();
    }

    @Override
    protected void init() {
        super.init();
        openTime = System.currentTimeMillis();
        int bottom = panelBottom();
        int cx = width / 2;
        ButtonMenu folder = new ButtonMenu(cx - 152, bottom + 10, 148, 20,
            LanguageManager.text(hasMods ? "randomrun.modblock.open_mods" : "randomrun.modblock.open_packs"),
            button -> openFolder(), 0, 0.1f);
        addRenderableWidget(folder);
        ButtonRainbow proceed = new ButtonRainbow(cx + 4, bottom + 10, 148, 20,
            LanguageManager.text("randomrun.modblock.continue"), button -> onClose(), 1, 0.15f);
        proceed.setAnimationDirection(ButtonRainbow.AnimationDirection.HORIZONTAL_LEFT);
        addRenderableWidget(proceed);
    }

    private void openFolder() {
        var dir = FabricLoader.getInstance().getGameDir().resolve(hasMods ? "mods" : "resourcepacks");
        try {
            com.mojang.blaze3d.Blaze3D.openPath(dir);
        } catch (Exception ignored) {
        }
    }

    private int visibleRows() {
        return Math.min(findings.size(), MAX_ROWS) + (findings.size() > MAX_ROWS ? 1 : 0);
    }

    private List<FormattedCharSequence> intro() {
        return font.split(LanguageManager.text("randomrun.modblock.detected"), PANEL_WIDTH - 32);
    }

    private int panelHeight() {
        return 40 + intro().size() * 10 + 8 + visibleRows() * ROW + 14 + 3 * 11 + 10;
    }

    private int panelTop() {
        return Math.max(12, height / 2 - panelHeight() / 2 - 18);
    }

    private int panelBottom() {
        return panelTop() + panelHeight();
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        super.extractBackground(context, mouseX, mouseY, delta);
        float fade = Math.min(1f, (System.currentTimeMillis() - openTime) / 300f);
        Theme theme = Theme.current();
        int left = width / 2 - PANEL_WIDTH / 2, right = left + PANEL_WIDTH;
        int top = panelTop(), bottom = panelBottom();
        Ui.panel(context, left, top, right, bottom, theme.panel, ORANGE, fade);
        Ui.glowBorder(context, left, top, right, bottom, 2, 0xFFE08A);

        float pulse = 0.75f + 0.25f * (float) Math.sin(System.currentTimeMillis() / 260.0);
        GuiText.centeredText(context, font, LanguageManager.text("randomrun.modblock.warning"), width / 2, top + 11, Ui.argb(Ui.lerp(0xFFFB923C, 0xFFFFE08A, pulse) & 0xFFFFFF, fade));
        Ui.divider(context, left + 12, right - 12, top + 25, ORANGE, fade);

        int y = top + 33;
        for (FormattedCharSequence line : intro()) {
            GuiText.centeredText(context, font, line, width / 2, y, Ui.argb(0xD4D4D8, fade));
            y += 10;
        }
        y += 8;

        for (int i = 0; i < Math.min(findings.size(), MAX_ROWS); i++) {
            var entry = findings.get(i);
            boolean pack = entry.getValue() == ModScannerService.Reason.XRAY_PACK;
            int rowLeft = left + 12, rowRight = right - 12;
            Ui.card(context, rowLeft, y, rowRight, y + ROW - 3, pack ? 0x67E8F9 : 0xF87171, fade);
            int badge = pack ? 0xFF0E7490 : 0xFF991B1B;
            context.fill(rowLeft + 3, y + 3, rowLeft + 14, y + 14, Ui.argb(badge, fade));
            GuiText.centeredText(context, font, Component.literal(pack ? "R" : "M"), rowLeft + 9, y + 5, Ui.argb(0xFFFFFF, fade));
            int textLeft = rowLeft + 19;
            Component reason = LanguageManager.text(entry.getValue().key);
            int reasonWidth = font.width(reason);
            GuiText.text(context, font, Ui.ellipsize(font, entry.getKey(), rowRight - textLeft - reasonWidth - 10), textLeft, y + 5, Ui.argb(0xFFFFFF, fade), false);
            GuiText.text(context, font, reason, rowRight - reasonWidth - 5, y + 5, Ui.argb(pack ? 0x67E8F9 : 0xF87171, fade), false);
            y += ROW;
        }
        if (findings.size() > MAX_ROWS) {
            GuiText.centeredText(context, font, LanguageManager.text("randomrun.modblock.more", findings.size() - MAX_ROWS), width / 2, y + 4, Ui.argb(0x8A8A93, fade));
            y += ROW;
        }

        y += 6;
        Ui.divider(context, left + 12, right - 12, y, theme.border, fade * 0.6f);
        y += 8;
        for (String key : List.of("randomrun.modblock.untrusted.1", "randomrun.modblock.untrusted.2", "randomrun.modblock.untrusted.3")) {
            GuiText.text(context, font, LanguageManager.text(key), left + 16, y, Ui.argb(0xA1A1AA, fade), false);
            y += 11;
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
