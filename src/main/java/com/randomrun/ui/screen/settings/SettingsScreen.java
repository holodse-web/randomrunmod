package com.randomrun.ui.screen.settings;

import com.randomrun.infrastructure.RandomRunMod;
import com.randomrun.infrastructure.config.ModConfig;
import com.randomrun.infrastructure.util.LanguageManager;
import com.randomrun.ui.screen.auth.ChangePasswordScreen;
import com.randomrun.ui.screen.info.TrustScoreInfoScreen;
import com.randomrun.ui.screen.main.AbstractRandomRunScreen;
import com.randomrun.ui.widget.styled.ButtonMenu;
import com.randomrun.ui.widget.styled.SliderDefault;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;

public class SettingsScreen extends AbstractRandomRunScreen {
    private static final int BUTTON_WIDTH = 200;
    private static final int BUTTON_HEIGHT = 20;
    private static final int BUTTON_SPACING = 5;
    private static final int TITLE_OFFSET = 20;
    private static final int BACK_BUTTON_OFFSET = 30;
    
    private final Screen parent;
    private final ModConfig config;
    private ButtonMenu onlineSettingsButton;
    
    public SettingsScreen(Screen parent) {
        super(LanguageManager.text("randomrun.screen.settings.title"));
        this.parent = parent;
        this.config = RandomRunMod.getInstance().getConfig();
    }
    
    @Override
    protected void init() {
        super.init();
        
        int centerX = width / 2;
        int totalHeight = 45;
        int startY = (height - totalHeight) / 2 - TITLE_OFFSET;
        
        int row1Y = startY;
        addRenderableWidget(new ButtonMenu(
            centerX - BUTTON_WIDTH / 2, row1Y,
            BUTTON_WIDTH, BUTTON_HEIGHT,
            LanguageManager.text("randomrun.settings.speedrun"),
            button -> Minecraft.getInstance().gui.setScreen(new GameSettingsScreen(this)),
            0, 0.0f
        ));
        
        int row2Y = row1Y + BUTTON_HEIGHT + BUTTON_SPACING;
        addRenderableWidget(new ButtonMenu(
            centerX - BUTTON_WIDTH - 2, row2Y,
            BUTTON_WIDTH, BUTTON_HEIGHT,
            LanguageManager.text("randomrun.settings.interface"),
            button -> Minecraft.getInstance().gui.setScreen(new InterfaceSettingsScreen(this)),
            1, 0.05f
        ));
        
        addRenderableWidget(new ButtonMenu(
            centerX + 2, row2Y,
            BUTTON_WIDTH, BUTTON_HEIGHT,
            LanguageManager.text("randomrun.settings.controls"),
            button -> Minecraft.getInstance().gui.setScreen(new ControlSettingsScreen(this)),
            2, 0.1f
        ));
        
        int row3Y = row2Y + BUTTON_HEIGHT + BUTTON_SPACING;
        this.onlineSettingsButton = new ButtonMenu(
            centerX - BUTTON_WIDTH - 2, row3Y,
            BUTTON_WIDTH, BUTTON_HEIGHT,
            LanguageManager.text("randomrun.settings.online_in_development"),
            button -> {},
            3, 0.15f
        );
        this.onlineSettingsButton.active = false;
        addRenderableWidget(this.onlineSettingsButton);
        
        addRenderableWidget(new SliderDefault(
            centerX + 2, row3Y,
            BUTTON_WIDTH, BUTTON_HEIGHT,
            LanguageManager.text("randomrun.settings.volume"),
            config.getSoundVolume() / 100.0,
            value -> {
                config.setSoundVolume((int)(value * 100));
                RandomRunMod.getInstance().saveConfig();
            },
            0.2f, true
        ) {
            @Override
            protected void updateMessage() {
                int percent = (int)(this.value * 100);
                setMessage(LanguageManager.text("randomrun.settings.volume").append(": " + percent + "%"));
            }
        });
        
        if (RandomRunMod.getInstance().getConfig().isOnlineMode()) {
            addRenderableWidget(new ButtonMenu(
                10, 10,
                120, BUTTON_HEIGHT,
                LanguageManager.text("randomrun.trustscore.button"),
                button -> Minecraft.getInstance().gui.setScreen(new TrustScoreInfoScreen(this)),
                5, 0.3f
            ));
            
            addRenderableWidget(new ButtonMenu(
                width - 130, 10,
                120, BUTTON_HEIGHT,
                LanguageManager.text("randomrun.settings.changepassword"),
                button -> {
                    String playerName = Minecraft.getInstance().getUser().getName();
                    Minecraft.getInstance().gui.setScreen(new ChangePasswordScreen(this, playerName));
                },
                6, 0.35f
            ));
        }
        
        addRenderableWidget(new ButtonMenu(
            centerX - 100, height - BACK_BUTTON_OFFSET,
            BUTTON_WIDTH, BUTTON_HEIGHT,
            LanguageManager.text("randomrun.button.back"),
            button -> Minecraft.getInstance().gui.setScreen(parent),
            4, 0.25f
        ));
    }
    
    @Override
    public void extractRenderState(GuiGraphicsExtractor context, int mouseX, int mouseY, float delta) {
        super.extractRenderState(context, mouseX, mouseY, delta);
        
        com.randomrun.ui.util.GuiText.centeredText(context, font, title, width / 2, 20, 0xFFFFFF);
    }
    
    @Override
    public void tick() {
        super.tick();
        
        if (onlineSettingsButton != null) {
            onlineSettingsButton.active = false;
        }
    }
}
