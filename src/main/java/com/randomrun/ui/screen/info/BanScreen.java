package com.randomrun.ui.screen.info;

import com.randomrun.ui.screen.main.AbstractRandomRunScreen;
import com.randomrun.ui.widget.styled.ButtonDefault;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;

public class BanScreen extends AbstractRandomRunScreen {
    private final String banReason;
    private static final int COLOR_PANEL_BG = 0x1a0000;
    private static final int COLOR_BORDER_RED = 0xFF0000;
    private static final int BORDER_WIDTH = 2;
    
    public BanScreen(String banReason) {
        super(com.randomrun.infrastructure.util.LanguageManager.text("randomrun.ban.title"));
        this.banReason = banReason != null ? banReason : "Unknown";
    }
    
    @Override
    protected void init() {
        super.init();
        
        addRenderableWidget(new ButtonDefault(
            width / 2 - 100, height - 40, 200, 20,
            Component.literal("Exit to Title"),
            button -> {
                if (minecraft != null) {
                    if (minecraft.level != null) {
                        minecraft.disconnectWithProgressScreen();
                    }
                    minecraft.gui.setScreen(null);
                }
            }
        ));
    }
    
    @Override
    public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        extractBackground(context, mouseX, mouseY, delta);
        
        int centerX = width / 2;
        int panelWidth = 400;
        int panelHeight = 200;
        int panelLeft = centerX - panelWidth / 2;
        int panelTop = height / 2 - panelHeight / 2 - 20;
        int panelRight = panelLeft + panelWidth;
        int panelBottom = panelTop + panelHeight;
        
        int panelAlpha = 0xD0;
        context.fill(panelLeft, panelTop, panelRight, panelBottom, (panelAlpha << 24) | COLOR_PANEL_BG);
        
        context.fill(panelLeft, panelTop, panelRight, panelTop + BORDER_WIDTH, 0xFF000000 | COLOR_BORDER_RED);
        context.fill(panelLeft, panelBottom - BORDER_WIDTH, panelRight, panelBottom, 0xFF000000 | COLOR_BORDER_RED);
        context.fill(panelLeft, panelTop, panelLeft + BORDER_WIDTH, panelBottom, 0xFF000000 | COLOR_BORDER_RED);
        context.fill(panelRight - BORDER_WIDTH, panelTop, panelRight, panelBottom, 0xFF000000 | COLOR_BORDER_RED);
        
        Component titleText = com.randomrun.infrastructure.util.LanguageManager.text("randomrun.ban.title")
            .withStyle(s -> s.withColor(0xFF5555).withBold(true));
        com.randomrun.ui.util.GuiText.centeredText(context, font, titleText, centerX, panelTop + 20, 0xFFFFFF);
        
        Component reasonLabel = com.randomrun.infrastructure.util.LanguageManager.text("randomrun.ban.reason", banReason);
        com.randomrun.ui.util.GuiText.centeredText(context, font, reasonLabel, centerX, panelTop + 50, 0xFFAA00);
        
        Component permanentText = com.randomrun.infrastructure.util.LanguageManager.text("randomrun.ban.permanent");
        com.randomrun.ui.util.GuiText.centeredText(context, font, permanentText, centerX, panelTop + 80, 0xFF5555);
        
        Component appealText = com.randomrun.infrastructure.util.LanguageManager.text("randomrun.ban.appeal");
        com.randomrun.ui.util.GuiText.centeredText(context, font, appealText, centerX, panelTop + 110, 0xAAAAAA);
        
        super.extractRenderState(context, mouseX, mouseY, delta);
    }
    
    @Override
    public boolean isPauseScreen() {
        return true;
    }
    
    @Override
    public boolean shouldCloseOnEsc() {
        return false;
    }
    
    @Override
    public void onClose() {
        if (minecraft != null) {
            if (minecraft.level != null) {
                minecraft.disconnectWithProgressScreen();
            }
            minecraft.gui.setScreen(null);
        }
    }
}
