package com.randomrun.ui.screen.main;

import com.randomrun.infrastructure.RandomRunMod;
import com.randomrun.ui.widget.overlay.ModInfoWidget;
import com.randomrun.infrastructure.version.VersionChecker;
import com.randomrun.infrastructure.util.LanguageManager;
import com.randomrun.ui.widget.styled.ButtonYellow;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;

public class UpdateRequiredScreen extends AbstractRandomRunScreen {
    private static final int TITLE_OFFSET_Y = -60;
    private static final int CURRENT_VERSION_OFFSET_Y = -20;
    private static final int REQUIRED_VERSION_OFFSET_Y = 10;
    private static final int INSTRUCTION_OFFSET_Y = 50;
    
    private static final int COLOR_TITLE = 0xFF5555;
    private static final int COLOR_TEXT = 0xFFFFFF;
    private static final int COLOR_INSTRUCTION = 0xAAAAAA;
    
    private ModInfoWidget modInfoWidget;
    private String currentVersion;
    private String latestVersion;
    private Component titleText;
    private Component currentVersionText;
    private Component requiredVersionText;
    private Component instructionText;
    
    public UpdateRequiredScreen() {
        super(LanguageManager.text("randomrun.screen.update.title"));
    }
    
    @Override
    protected void init() {
        super.init();
        
        LanguageManager.ensureLanguage();
        
        modInfoWidget = new ModInfoWidget(width, height, font);
        
        VersionChecker checker = VersionChecker.getInstance();
        currentVersion = checker.getCurrentVersion();
        latestVersion = checker.getLatestVersion();
        
        titleText = Component.literal("§c§l" + LanguageManager.translate("randomrun.screen.update.header"));
        currentVersionText = Component.literal(LanguageManager.translate("randomrun.screen.update.current", "§e" + currentVersion));
        requiredVersionText = Component.literal(LanguageManager.translate("randomrun.screen.update.required", "§a" + latestVersion));
        instructionText = Component.literal("§7" + LanguageManager.translate("randomrun.screen.update.instruction"));
        
        initPlayOfflineButton();
    }
    
    private void initPlayOfflineButton() {
        int buttonWidth = 200;
        int buttonHeight = 20;
        int buttonX = (width - buttonWidth) / 2;
        int buttonY = height / 2 + 90;
        
        addRenderableWidget(new ButtonYellow(
            buttonX, buttonY, buttonWidth, buttonHeight,
            LanguageManager.text("randomrun.screen.update.play_offline"),
            button -> playOffline()
        ));
    }
    
    private void playOffline() {
        RandomRunMod.getInstance().getConfig().setOnlineMode(false);
        RandomRunMod.getInstance().saveConfig();
        
        if (this.minecraft != null) {
            this.minecraft.gui.setScreen(new MainModScreen(new TitleScreen()));
        }
    }
    
    @Override
    public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        super.extractRenderState(context, mouseX, mouseY, delta);
        
        renderUpdateMessage(context);
        
        if (modInfoWidget != null) {
            modInfoWidget.extractRenderState(context, mouseX, mouseY, delta);
        }
    }
    
    private void renderUpdateMessage(GuiGraphicsExtractor context) {
        int centerX = width / 2;
        int centerY = height / 2;
        
        com.randomrun.ui.util.GuiText.centeredText(context, font, titleText, 
            centerX, centerY + TITLE_OFFSET_Y, COLOR_TITLE);
        
        com.randomrun.ui.util.GuiText.centeredText(context, font, currentVersionText, 
            centerX, centerY + CURRENT_VERSION_OFFSET_Y, COLOR_TEXT);
        
        com.randomrun.ui.util.GuiText.centeredText(context, font, requiredVersionText, 
            centerX, centerY + REQUIRED_VERSION_OFFSET_Y, COLOR_TEXT);
        
        com.randomrun.ui.util.GuiText.centeredText(context, font, instructionText, 
            centerX, centerY + INSTRUCTION_OFFSET_Y, COLOR_INSTRUCTION);
    }
    
    @Override
    public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent event, boolean doubleClick) {
        double mouseX = event.x();
        double mouseY = event.y();
        int button = event.button();
        if (modInfoWidget != null && modInfoWidget.mouseClicked(new net.minecraft.client.input.MouseButtonEvent(mouseX, mouseY, event.buttonInfo()), doubleClick)) {
            return true;
        }
        return super.mouseClicked(new net.minecraft.client.input.MouseButtonEvent(mouseX, mouseY, event.buttonInfo()), doubleClick);
    }
    
    @Override
    public void onClose() {
        if (this.minecraft != null) {
            this.minecraft.gui.setScreen(new TitleScreen());
        }
    }
    
    @Override
    public boolean shouldCloseOnEsc() {
        return true;
    }
}
