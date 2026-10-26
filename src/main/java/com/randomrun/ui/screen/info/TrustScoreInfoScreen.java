package com.randomrun.ui.screen.info;

import com.randomrun.ui.screen.main.AbstractRandomRunScreen;
import com.randomrun.ui.widget.styled.ButtonDefault;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public class TrustScoreInfoScreen extends AbstractRandomRunScreen {
    private final Screen parent;
    private final List<Component> infoLines = new ArrayList<>();
    private static final int LINE_HEIGHT = 11;
    
    public TrustScoreInfoScreen(Screen parent) {
        super(com.randomrun.infrastructure.util.LanguageManager.text("randomrun.trustscore.title"));
        this.parent = parent;
        initInfoLines();
    }
    
    private void initInfoLines() {
        infoLines.add(com.randomrun.infrastructure.util.LanguageManager.text("randomrun.trustscore.header"));
        infoLines.add(Component.literal(""));
        
        infoLines.add(com.randomrun.infrastructure.util.LanguageManager.text("randomrun.trustscore.what_is"));
        infoLines.add(com.randomrun.infrastructure.util.LanguageManager.text("randomrun.trustscore.what_is_desc"));
        infoLines.add(Component.literal(""));
        
        infoLines.add(com.randomrun.infrastructure.util.LanguageManager.text("randomrun.trustscore.start_value"));
        infoLines.add(com.randomrun.infrastructure.util.LanguageManager.text("randomrun.trustscore.start_value_desc"));
        infoLines.add(Component.literal(""));
        
        infoLines.add(com.randomrun.infrastructure.util.LanguageManager.text("randomrun.trustscore.how_earn"));
        infoLines.add(com.randomrun.infrastructure.util.LanguageManager.text("randomrun.trustscore.earn_1"));
        infoLines.add(com.randomrun.infrastructure.util.LanguageManager.text("randomrun.trustscore.earn_2"));
        infoLines.add(com.randomrun.infrastructure.util.LanguageManager.text("randomrun.trustscore.earn_3"));
        infoLines.add(Component.literal(""));
        
        infoLines.add(com.randomrun.infrastructure.util.LanguageManager.text("randomrun.trustscore.penalties"));
        infoLines.add(com.randomrun.infrastructure.util.LanguageManager.text("randomrun.trustscore.penalty_1"));
        infoLines.add(com.randomrun.infrastructure.util.LanguageManager.text("randomrun.trustscore.penalty_2"));
        infoLines.add(com.randomrun.infrastructure.util.LanguageManager.text("randomrun.trustscore.penalty_3"));
        infoLines.add(com.randomrun.infrastructure.util.LanguageManager.text("randomrun.trustscore.penalty_4"));
        infoLines.add(Component.literal(""));
        
        infoLines.add(com.randomrun.infrastructure.util.LanguageManager.text("randomrun.trustscore.consequences"));
        infoLines.add(com.randomrun.infrastructure.util.LanguageManager.text("randomrun.trustscore.consequence_1"));
        infoLines.add(com.randomrun.infrastructure.util.LanguageManager.text("randomrun.trustscore.consequence_2"));
        infoLines.add(Component.literal(""));
        
        infoLines.add(com.randomrun.infrastructure.util.LanguageManager.text("randomrun.trustscore.important"));
        infoLines.add(com.randomrun.infrastructure.util.LanguageManager.text("randomrun.trustscore.important_desc"));
    }
    
    @Override
    protected void init() {
        super.init();
        
        addRenderableWidget(new com.randomrun.ui.widget.overlay.LanguageDropdownWidget(
            10, height - 30, this, 0.1f
        ));
        
        addRenderableWidget(new ButtonDefault(
            width / 2 - 100, height - 35, 200, 20,
            com.randomrun.infrastructure.util.LanguageManager.text("randomrun.button.understood"),
            button -> onClose()
        ));
    }
    
    @Override
    public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        extractBackground(context, mouseX, mouseY, delta);
        
        com.randomrun.ui.util.GuiText.centeredText(context, font, title, width / 2, 20, 0xFFFFFF);
        
        int startY = 45;
        for (int i = 0; i < infoLines.size(); i++) {
            Component line = infoLines.get(i);
            int y = startY + i * LINE_HEIGHT;
            com.randomrun.ui.util.GuiText.centeredText(context, font, line, width / 2, y, getLineColor(line));
        }
        
        super.extractRenderState(context, mouseX, mouseY, delta);
    }
    
    private int getLineColor(Component line) {
        String text = line.getString();
        
        if (text.contains("§c") || text.contains("ВАЖНО") || text.contains("IMPORTANT")) {
            return 0xFF5555;
        }
        if (text.contains("§e") || text.contains("Штрафы") || text.contains("Penalties")) {
            return 0xFFAA00;
        }
        if (text.contains("§a") || text.contains("+")) {
            return 0x55FF55;
        }
        
        return 0xAAAAAA;
    }
    
    @Override
    public void onClose() {
        if (minecraft != null) {
            minecraft.gui.setScreen(parent);
        }
    }
    
    @Override
    public boolean isPauseScreen() {
        return true;
    }
}
