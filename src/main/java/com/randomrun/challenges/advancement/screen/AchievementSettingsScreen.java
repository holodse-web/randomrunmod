package com.randomrun.challenges.advancement.screen;

import com.randomrun.main.RandomRunMod;
import com.randomrun.main.config.ModConfig;
import com.randomrun.ui.widget.styled.ButtonDefault;
import com.randomrun.ui.screen.main.AbstractRandomRunScreen;
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
            
        ButtonDefault toggleButton = new ButtonDefault(
            centerX - buttonWidth / 2, startY,
            buttonWidth, buttonHeight,
            Text.translatable("randomrun.settings.achievement_challenge_enabled", challengeToggle),
            button -> {
                if (config.isAchievementChallengeEnabled()) {
                    config.setAchievementChallengeEnabled(false);
                    RandomRunMod.getInstance().saveConfig();
                    refreshScreen();
                }
            },
            buttonIndex++, 0.1f, isRefreshing
        );

        // Блокируем кнопку, если режим уже выключен (так как он в разработке)
        if (!config.isAchievementChallengeEnabled()) {
            toggleButton.active = false;
        }
        
        addDrawableChild(toggleButton);
        
        if (config.isAchievementChallengeEnabled()) {
            // Use Difficulty Toggle
            String difficultyToggle = config.isUseAchievementDifficulty() 
                ? "§a" + Text.translatable("randomrun.toggle.enabled").getString()
                : "§c" + Text.translatable("randomrun.toggle.disabled").getString();

            addDrawableChild(new ButtonDefault(
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
        addDrawableChild(new ButtonDefault(
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
        // Заголовок
        context.drawCenteredTextWithShadow(textRenderer, title, width / 2, 20, 0xFFFFFF);
    }
}
