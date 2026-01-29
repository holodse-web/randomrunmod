package com.randomrun.challenges.advancement.screen;

import com.randomrun.main.RandomRunMod;
import com.randomrun.main.config.ModConfig;
import com.randomrun.ui.widget.StyledButton2;
import com.randomrun.ui.screen.AbstractRandomRunScreen;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;

public class AchievementSettingsScreen extends AbstractRandomRunScreen {
    private final Screen parent;
    private final ModConfig config;
    private final boolean isRefreshing;
    
    public AchievementSettingsScreen(Screen parent) {
        this(parent, false);
    }
    
    public AchievementSettingsScreen(Screen parent, boolean isRefreshing) {
        super(Text.translatable("randomrun.screen.achievement_settings.title"));
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
        
        // Achievement challenge toggle
        String challengeToggle = config.isAchievementChallengeEnabled() 
            ? "§a" + Text.translatable("randomrun.toggle.enabled").getString()
            : "§c" + Text.translatable("randomrun.toggle.disabled").getString();
            
        addDrawableChild(new StyledButton2(
            centerX - buttonWidth / 2, startY,
            buttonWidth, buttonHeight,
            Text.translatable("randomrun.settings.achievement_challenge_enabled", challengeToggle),
            button -> {
                config.setAchievementChallengeEnabled(!config.isAchievementChallengeEnabled());
                RandomRunMod.getInstance().saveConfig();
                refreshScreen();
            },
            buttonIndex++, 0.1f, isRefreshing
        ));
        
        // Use Difficulty Toggle
        String difficultyToggle = config.isUseAchievementDifficulty() 
            ? "§a" + Text.translatable("randomrun.toggle.enabled").getString()
            : "§c" + Text.translatable("randomrun.toggle.disabled").getString();
            
        if (config.isAchievementChallengeEnabled()) {
            addDrawableChild(new StyledButton2(
                centerX - buttonWidth / 2, startY + spacing,
                buttonWidth, buttonHeight,
                Text.translatable("randomrun.settings.use_difficulty", difficultyToggle),
                button -> {
                    config.setUseAchievementDifficulty(!config.isUseAchievementDifficulty());
                    RandomRunMod.getInstance().saveConfig();
                    refreshScreen();
                },
                buttonIndex++, 0.1f, isRefreshing
            ));
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
        MinecraftClient.getInstance().setScreen(new AchievementSettingsScreen(parent, true));
    }
    
    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);
        
        // Title
        context.drawCenteredTextWithShadow(textRenderer, title, width / 2, 20, 0xFFFFFF);
    }
}
