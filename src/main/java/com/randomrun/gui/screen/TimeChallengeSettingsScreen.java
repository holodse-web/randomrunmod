package com.randomrun.gui.screen;

import com.randomrun.RandomRunMod;
import com.randomrun.config.ModConfig;
import com.randomrun.gui.widget.StyledButton2;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;

public class TimeChallengeSettingsScreen extends AbstractRandomRunScreen {
    private final Screen parent;
    private final ModConfig config;
    private final boolean isRefreshing;
    
    public TimeChallengeSettingsScreen(Screen parent) {
        this(parent, false);
    }
    
    public TimeChallengeSettingsScreen(Screen parent, boolean isRefreshing) {
        super(Text.translatable("randomrun.screen.time_challenge_settings.title"));
        this.parent = parent;
        this.config = RandomRunMod.getInstance().getConfig();
        this.isRefreshing = isRefreshing;
    }
    
    @Override
    protected void init() {
        super.init();
        int centerX = width / 2;
        int buttonWidth = 250;
        int buttonHeight = 20;
        int startY = 60;
        int spacing = 30;
        int buttonIndex = 0;
        
        // Time challenge toggle
        String challengeToggle = config.isTimeChallengeEnabled() 
            ? "§a" + Text.translatable("randomrun.toggle.enabled").getString()
            : "§c" + Text.translatable("randomrun.toggle.disabled").getString();
        addDrawableChild(new StyledButton2(
            centerX - buttonWidth / 2, startY,
            buttonWidth, buttonHeight,
            Text.translatable("randomrun.settings.time_challenge_enabled", challengeToggle),
            button -> {
                config.setTimeChallengeEnabled(!config.isTimeChallengeEnabled());
                RandomRunMod.getInstance().saveConfig();
                refreshScreen();
            },
            buttonIndex++, 0.1f, isRefreshing
        ));
        
        // Only show these settings if time challenge is enabled
        if (config.isTimeChallengeEnabled()) {
            // Use item difficulty toggle
            String difficultyToggle = config.isUseItemDifficulty() 
                ? "§a" + Text.translatable("randomrun.toggle.enabled").getString()
                : "§c" + Text.translatable("randomrun.toggle.disabled").getString();
            // Don't skip animation for these buttons when challenge is first enabled
            boolean skipSubAnimation = isRefreshing;
            addDrawableChild(new StyledButton2(
                centerX - buttonWidth / 2, startY + spacing,
                buttonWidth, buttonHeight,
                Text.translatable("randomrun.settings.use_difficulty", difficultyToggle),
                button -> {
                    config.setUseItemDifficulty(!config.isUseItemDifficulty());
                    RandomRunMod.getInstance().saveConfig();
                    refreshScreen();
                },
                buttonIndex++, 0.15f, skipSubAnimation
            ));
            
            // Manual time toggle
            String manualTimeToggle = config.isManualTimeEnabled() 
                ? "§a" + Text.translatable("randomrun.toggle.enabled").getString()
                : "§c" + Text.translatable("randomrun.toggle.disabled").getString();
            boolean isManualTime = config.isManualTimeEnabled();
            int manualTimeButtonWidth = isManualTime ? buttonWidth - 30 : buttonWidth;
            
            addDrawableChild(new StyledButton2(
                centerX - buttonWidth / 2, startY + spacing * 2,
                manualTimeButtonWidth, buttonHeight,
                Text.translatable("randomrun.settings.manual_time", manualTimeToggle),
                button -> {
                    config.setManualTimeEnabled(!config.isManualTimeEnabled());
                    RandomRunMod.getInstance().saveConfig();
                    refreshScreen();
                },
                buttonIndex++, 0.2f, skipSubAnimation
            ));
            
            // Edit button (only if manual time is enabled)
            if (isManualTime) {
                addDrawableChild(new StyledButton2(
                    centerX + buttonWidth / 2 - 25, startY + spacing * 2,
                    25, buttonHeight,
                    Text.literal("✏️"),
                    button -> MinecraftClient.getInstance().setScreen(new ManualTimeEditorScreen(this)),
                    buttonIndex++, 0.2f, skipSubAnimation
                ));
            }
        }
        
        // Back button (aligned with main menu)
        addDrawableChild(new StyledButton2(
            centerX - 50, height - 30,
            100, buttonHeight,
            Text.translatable("randomrun.button.back"),
            button -> MinecraftClient.getInstance().setScreen(parent),
            buttonIndex++, 0.25f, isRefreshing
        ));
    }
    
    private void refreshScreen() {
        MinecraftClient.getInstance().setScreen(new TimeChallengeSettingsScreen(parent, true));
    }
    
    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);
        
        // Title
        context.drawCenteredTextWithShadow(textRenderer, title, width / 2, 20, 0xFFFFFF);
    }
    
}
