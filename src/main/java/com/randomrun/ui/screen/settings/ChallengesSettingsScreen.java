/*
 * Copyright (c) 2026 Stanislav Kholod.
 * All rights reserved.
 */
package com.randomrun.ui.screen.settings;

import com.randomrun.ui.screen.main.AbstractRandomRunScreen;

import com.randomrun.challenges.time.screen.TimeChallengeSettingsScreen;
import com.randomrun.challenges.advancement.screen.AchievementSettingsScreen;
import com.randomrun.ui.widget.styled.ButtonDefault;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import com.randomrun.main.RandomRunMod;
import com.randomrun.main.config.ModConfig;
import net.minecraft.text.Text;

public class ChallengesSettingsScreen extends AbstractRandomRunScreen {
    private final Screen parent;
    private final ModConfig config;
    private final boolean isRefreshing;

    public ChallengesSettingsScreen(Screen parent) {
        this(parent, false);
    }
    
    public ChallengesSettingsScreen(Screen parent, boolean isRefreshing) {
        super(Text.translatable("randomrun.screen.time_challenge_settings.title"));
        this.parent = parent;
        this.config = RandomRunMod.getInstance().getConfig();
        this.isRefreshing = isRefreshing;
    }
    
    @Override
    protected void init() {
        super.init();
        
        // Show warning toast only if we are just opening the screen (not refreshing)
        if (!isRefreshing) {
            MinecraftClient.getInstance().getToastManager().add(new net.minecraft.client.toast.SystemToast(
                net.minecraft.client.toast.SystemToast.Type.PERIODIC_NOTIFICATION, 
                Text.translatable("randomrun.toast.speedrun_warning.title"), 
                Text.translatable("randomrun.toast.speedrun_warning.description")
            ));
        }
        
        int centerX = width / 2;
        int buttonWidth = 200;
        int buttonHeight = 20;
        int startY = 60;
        int spacing = 30;
        int i = 0;
        
        // Time Challenge settings (Original "Time Challenge" screen)
        addDrawableChild(new ButtonDefault(
            centerX - buttonWidth / 2, startY + spacing * i++,
            buttonWidth, buttonHeight,
            Text.translatable("randomrun.settings.time_challenge"),
            button -> MinecraftClient.getInstance().setScreen(new TimeChallengeSettingsScreen(this)),
            i, 0.1f, isRefreshing
        ));
        
        // Achievement Challenge
        addDrawableChild(new ButtonDefault(
            centerX - buttonWidth / 2, startY + spacing * i++,
            buttonWidth, buttonHeight,
            Text.translatable("randomrun.settings.achievement_challenge_menu"),
            button -> MinecraftClient.getInstance().setScreen(new AchievementSettingsScreen(this)),
            i, 0.12f, isRefreshing
        ));
        
        // Back button
        addDrawableChild(new ButtonDefault(
            centerX - 100 / 2, height - 30,
            100, buttonHeight,
            Text.translatable("randomrun.button.back"),
            button -> MinecraftClient.getInstance().setScreen(parent),
            i + 1, 0.15f
        ));
    }
    
    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);
        
        // Title
        context.drawCenteredTextWithShadow(textRenderer, title, width / 2, 20, 0xFFFFFF);
    }
}
